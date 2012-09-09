package com.skybility.cloudsoft.rtunnel.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skybility.cloudsoft.rtunnel.common.AdvancedProperties;
import com.skybility.cloudsoft.rtunnel.common.LoggerHelper;
import com.skybility.cloudsoft.rtunnel.common.RCtrlSegment;
import com.skybility.cloudsoft.rtunnel.common.RTunnelInputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelOutputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelSocketFactory;
import com.skybility.cloudsoft.rtunnel.common.SegmentUtils;
import com.skybility.cloudsoft.rtunnel.common.SocketType;
import com.skybility.cloudsoft.rtunnel.common.Timer;
import com.skybility.cloudsoft.rtunnel.common.TunnelStatus;
import com.skybility.cloudsoft.rtunnel.event.ServerTunnelStatusChangedEvent;

public class RTunnelServerHandler extends Thread {

	private int forwardPort;
	private ServerSocket tcpServerSock;
	private Socket ctrl_sock;
	private RTunnelInputStream ctrl_sock_in;
	private RTunnelOutputStream ctrl_sock_out;
	
	private long lastCheckHeartbeatTimestamp = -1L;
	private Timer heartbeatTimer = null;
	private Timer ackHeartbeatTimer = null;
	private static int heartbeatInterval = AdvancedProperties.getInstance().requireInteger("heartbeatInterval");
	private static int heartbeatTimeout = AdvancedProperties.getInstance().requireInteger("heartbeatTimeout");
	private HeartbeatReadThread heartbeatReadThread;
	
	private Map<Integer, Socket> tcpSocks = new ConcurrentHashMap<Integer, Socket>();
	
	private Map<Integer, Socket> rSocks = new ConcurrentHashMap<Integer, Socket>();
	private boolean keep_running;
	
	private static Logger logger = LoggerFactory.getLogger(RTunnelServer.class);
	private RTunnelServer server;
	
	private String forwardBindAddress;
	private SocketType sockType;
	
	public RTunnelServerHandler(String forwardBindAddress, int forwardPort, Socket sock, RTunnelInputStream sock_in, RTunnelOutputStream sock_out, RTunnelServer server, SocketType sockType) {
		super("RTunnelServerHandler");
		this.forwardBindAddress = forwardBindAddress;
		this.forwardPort = forwardPort;
		this.ctrl_sock = sock;
		this.sockType = sockType;
		this.ctrl_sock_in = sock_in;
		this.ctrl_sock_out = sock_out;
		this.server = server;
		this.server.registerServerHandler(this.forwardPort, this);
	}
	
	@Override
	public void run() {
		try {
			keep_running = true;
			LoggerHelper.startLogging(LoggerHelper.generateServerLogRTunnelid(forwardPort));
			logger.info("start to initialize forward tcp socket(forwardPort=" + forwardPort +").");
			try {
				tcpServerSock = RTunnelSocketFactory.getServerSocket(
						sockType, forwardBindAddress, forwardPort);
			} catch (IOException e) {
				logger.info("create forward port socket fails.");
			}
			
			if (tcpServerSock != null) {
				RCtrlSegment ctrlSegment = RCtrlSegment.constructACKTcpServerPortRCtrlSegment(0);
				logger.debug("write control segment " + ctrlSegment);
				ctrl_sock_out.writeCtlSegment(ctrlSegment);
			} else {
				logger.info("establish tunnel fails.");
				RCtrlSegment ctrlSegment = RCtrlSegment.constructACKTcpServerPortRCtrlSegment(1);
				logger.debug("write control segment " + ctrlSegment);
				ctrl_sock_out.writeCtlSegment(ctrlSegment);
				logger.info("the tunnel is broken, close it.");
				cleanup();
				return;
			}
		} catch (IOException e) {
			logger.info("the tunnel is broken, close it.");
			cleanup();
			return;
		}
		
		TcpServerLoopThread tcpServerLoopThread = new TcpServerLoopThread(
				tcpServerSock);
		tcpServerLoopThread.start();
		
		logger.info("the tunnel is established successfully.");
		server.dispatchEvent(new ServerTunnelStatusChangedEvent(forwardPort,TunnelStatus.OK));
		
		heartbeatTimer = new Timer("heartbeatTimer",
				new HeartbeatTimerTask());
		heartbeatReadThread = new HeartbeatReadThread();
		ackHeartbeatTimer = new Timer("ackHeartbeatTimer",
				new AckHeartbeatTimerTask());
		lastCheckHeartbeatTimestamp = System.currentTimeMillis();
		
		heartbeatReadThread.start();
		heartbeatTimer.schedule(0, heartbeatInterval);
		ackHeartbeatTimer.schedule(0, heartbeatInterval);
		
		try {
			tcpServerLoopThread.join();
		} catch (InterruptedException e) {
			logger.debug("thread join error. " + e);
			logger.info("the tunnel is broken, close it.");
			cleanup();
			return;
		}
	}
	
	void cleanup(){
		logger.debug("start cleanup");
		keep_running = false;
		server.dispatchEvent(new ServerTunnelStatusChangedEvent(forwardPort,TunnelStatus.ERROR));
		if(heartbeatReadThread != null && !heartbeatReadThread.isInterrupted()){
			heartbeatReadThread.interrupt();
		}
		if(ackHeartbeatTimer != null){
			ackHeartbeatTimer.destroy();
		}
		if(heartbeatTimer != null){
			heartbeatTimer.destroy();
		}
		for(Socket sock : tcpSocks.values()){
			IOUtils.closeQuietly(sock);
		}
		tcpSocks.clear();
		IOUtils.closeQuietly(tcpServerSock);
		for(Socket sock : rSocks.values()){
			IOUtils.closeQuietly(sock);
		}
		rSocks.clear();
		IOUtils.closeQuietly(ctrl_sock);
		server.unregisterServerHandler(this.forwardPort);
		LoggerHelper.stopLogging();
	}
	
	class TcpServerLoopThread extends Thread{
		private ServerSocket tcpServerSock;

		public TcpServerLoopThread(ServerSocket tcpServerSock) {
			super("TcpServerLoopThread");
			this.tcpServerSock = tcpServerSock;
		}

		@Override
		public void run() {
			while(keep_running){
				try {
					Socket tcpSock = tcpServerSock.accept();
					int tcpSockBindPort = tcpSock.getPort();
					tcpSocks.put(tcpSockBindPort, tcpSock);
					logger.debug("forwardPort=" + forwardPort + ", tcpSockBindPort=" + tcpSockBindPort);
					int bindInfo = (int)(
						(int)(0xffff & forwardPort) << 16   |
			            (int)(0xffff & tcpSockBindPort) << 0
		            );
					RCtrlSegment ctrlSegment = RCtrlSegment.constructNewTcpSocketRCtrlSegment(bindInfo);
					logger.debug("write control segment " + ctrlSegment);
					synchronized (ctrl_sock_out) {
						ctrl_sock_out.writeCtlSegment(ctrlSegment);
					}
				} catch (IOException e) {
					logger.info("the tunnel is broken, close it.");
					cleanup();
					break;
				}
			}
		}
	}
	
	class HeartbeatTimerTask implements Runnable{

		@Override
		public void run() {
			try {
				RCtrlSegment ctrlSegment = RCtrlSegment.constructHeartBeatRCtrlSegment();
				logger.debug("write heartbeat segment " + ctrlSegment);
				synchronized (ctrl_sock_out) {
					ctrl_sock_out.writeCtlSegment(ctrlSegment);
				}
				logger.debug("write heartbeat segment success.");
			} catch (IOException e) {
				logger.debug("send heartbeat error. " + e);
			}
		}
	}
	
	class AckHeartbeatTimerTask implements Runnable{

		@Override
		public void run() {
			long currentTimestamp = System.currentTimeMillis();
			long costTime = currentTimestamp - lastCheckHeartbeatTimestamp;
			if(costTime > heartbeatTimeout){
				logger.info("heartbeat timed out, maybe the tunnel is broken, close it.");
				cleanup();
			}
		}

	}
	
	class HeartbeatReadThread extends Thread {

		@Override
		public void run() {
			while(keep_running){
				try {
					RCtrlSegment ctrlSegment = ctrl_sock_in.readCtlSegment();
					logger.debug("read heartbeat segment " + ctrlSegment);
					if(ctrlSegment.getType() == RCtrlSegment.HEART_BEAT_FLAG){
						byte[] contentBytes = ctrlSegment.getContent();
						RCtrlSegment ackCtrlSegment = RCtrlSegment.constructACKHeartBeatRCtrlSegment(contentBytes);
						logger.debug("write ack heartbeat segment " + ctrlSegment);
						synchronized (ctrl_sock_out) {
							ctrl_sock_out.writeCtlSegment(ackCtrlSegment);
						}
					} else if(ctrlSegment.getType() == RCtrlSegment.ACK_HEART_BEAT_FLAG){
						long sendTime = SegmentUtils.bytesToLong(ctrlSegment.getContent());
						long receiveTime = System.currentTimeMillis();
						lastCheckHeartbeatTimestamp = receiveTime;
						long costTime = receiveTime - sendTime;
						if(costTime > heartbeatTimeout){
							logger.info("heartbeat timed out, maybe the tunnel is broken, close it.");
							cleanup();
						}
					} else if(ctrlSegment.getType() == RCtrlSegment.CLOSE_TUNNEL_FLAG){
						cleanup();
					}
				} catch (IOException e) {
					logger.debug("read heartbeat error. " + e);
					try {
						Thread.sleep(heartbeatInterval);
					} catch (InterruptedException e1) {
						break;
					}
				}
			}
		}

	}
	
	
	
	class RTunnelServerPipeShutdownHook extends Thread {

		private Socket tcp_sock;
		private Socket rsock;

		public RTunnelServerPipeShutdownHook(Socket tcp_sock, Socket rsock) {
			super("RTunnelServerPipeShutdownHook");
			this.tcp_sock = tcp_sock;
			this.rsock = rsock;
		}

		@Override
		public void run() {
			tcpSocks.remove(tcp_sock.getPort());
			rSocks.remove(rsock.getPort());
			IOUtils.closeQuietly(tcp_sock);
			IOUtils.closeQuietly(rsock);
		}
	}



	public void ackNewTcpSocket(int tcpSockBindPort, Socket rsock) {
		Socket tcp_sock = tcpSocks.get(tcpSockBindPort);
		if(tcp_sock != null){
			rSocks.put(rsock.getPort(), rsock);
			RTunnelServerPipeThread pipeThread = new RTunnelServerPipeThread(tcp_sock, rsock);
			pipeThread.setShutdownHook(new RTunnelServerPipeShutdownHook(tcp_sock, rsock));
			pipeThread.start();
		}
	}

}
