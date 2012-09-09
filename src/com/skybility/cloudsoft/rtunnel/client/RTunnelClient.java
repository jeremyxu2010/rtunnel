/*$Id: $
 --------------------------------------
  Skybility
 ---------------------------------------
  Copyright By Skybility ,All right Reserved
 * author   date   comment
 * jeremy  2012-7-26  Created
*/ 
package com.skybility.cloudsoft.rtunnel.client; 

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skybility.cloudsoft.rtunnel.common.AdvancedProperties;
import com.skybility.cloudsoft.rtunnel.common.LoggerHelper;
import com.skybility.cloudsoft.rtunnel.common.NetworkInterfaceHelper;
import com.skybility.cloudsoft.rtunnel.common.RCtrlSegment;
import com.skybility.cloudsoft.rtunnel.common.RTunnelInputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelOutputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelSocketFactory;
import com.skybility.cloudsoft.rtunnel.common.SegmentUtils;
import com.skybility.cloudsoft.rtunnel.common.SocketType;
import com.skybility.cloudsoft.rtunnel.common.Timer;
import com.skybility.cloudsoft.rtunnel.common.TunnelStatus;
import com.skybility.cloudsoft.rtunnel.event.ClientTunnelStatusChangedEvent;
import com.skybility.cloudsoft.rtunnel.event.EventDispatcher;
import com.skybility.cloudsoft.rtunnel.listener.EventListener;
 
public class RTunnelClient{

	private static InetAddress LOCAL_INET_ADDRESS = null;
	
	static {
		try {
			LOCAL_INET_ADDRESS = InetAddress.getByName("0.0.0.0");
		} catch (UnknownHostException e) {
			// ignore exception
		}
	}
	
	private volatile boolean keep_running;
	private volatile boolean main_keep_running;
	private String rtunnelBindInterfaceName;
	
	private Socket ctrl_sock;
	private SocketType sockType = SocketType.valueOf(AdvancedProperties.getInstance().requireString("defaultSockType").toUpperCase());
	private static final int DEFAULT_RTUNNEL_SERVER_PORT = AdvancedProperties.getInstance().requireInteger("defaultServerTunnelPort");
	private static final String DEFAULT_IP_VERSION = AdvancedProperties.getInstance().getAsString("defaultIpVersion");
	private static final String DEFAULT_RTUNNEL_BIND_ADDRESS = AdvancedProperties.getInstance().requireString("defaultRtunnelBindAddress");
	private static final int heartbeatInterval = AdvancedProperties.getInstance().requireInteger("heartbeatInterval");
	private static final int heartbeatTimeout = AdvancedProperties.getInstance().requireInteger("heartbeatTimeout");
	private static final int closeTunnelWaitTimeout = AdvancedProperties.getInstance().requireInteger("closeTunnelWaitTimeout");
	private static final int statusNotifyInterval = AdvancedProperties.getInstance().requireInteger("statusNotifyInterval");
	
	private static Logger logger = LoggerFactory.getLogger(RTunnelClient.class);
	
	private RTunnelInputStream ctrl_sock_in;

	private RTunnelOutputStream ctrl_sock_out;
	
	private Timer heartbeatTimer = null;
	
	private Timer ackHeartbeatTimer = null;
	
	private Timer statusNotifyTimer = null;
	
	private Map<Integer, Socket> tcpSocks = new ConcurrentHashMap<Integer, Socket>();
	
	private Map<Integer, Socket> rSocks = new ConcurrentHashMap<Integer, Socket>();
	
	private EventDispatcher<ClientTunnelStatusChangedEvent> eventDispatcher = new EventDispatcher<ClientTunnelStatusChangedEvent>("ClientEventDispatcher");
	
	private Object closeTunnelLock = new Object();
	
	public void setRserverHost(String rserverHost) {
    	this.rserverHost = rserverHost;
    }

	public void setRserverPort(int rserverPort) {
    	this.rserverPort = rserverPort;
    }

	public void setForwardPort(int forwardPort) {
    	this.forwardPort = forwardPort;
    }
	
	public void setRTcpPort(int rTcpPort) {
		this.rTcpPort = rTcpPort;
	}

	public void setRTcpHost(String rTcpHost) {
		this.rTcpHost = rTcpHost;
	}

	private String rserverHost;
	private int rserverPort;
	private int forwardPort;
	private int rTcpPort;


	private String rTcpHost;
	
	private long lastCheckHeartbeatTimestamp = -1L;
	private Thread clientLogicThread;
	
	public RTunnelClient(){
	}
	
	public void addEventListener(EventListener<ClientTunnelStatusChangedEvent> l){
		eventDispatcher.addListener(l);
	}
	
	public void removeEventListener(EventListener<ClientTunnelStatusChangedEvent> l){
		eventDispatcher.removeListener(l);
	}
	
	public void clearEventListeners() {
		eventDispatcher.clearEventListeners();
    }

	protected Logger log() {
		return logger;
	}
	public void start(){
		Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
		eventDispatcher.startDispatch();
		main_keep_running = true;
		
		Runnable clientLogicRunnable = new Runnable() {
			public void run() {
				keep_running = true;
				LoggerHelper.startLogging(LoggerHelper.generateClientLogRTunnelid(rTcpPort));
				logger.info("begin to establish tunnel with transit server(forwardPort=" + forwardPort + ").");
				try {
					if (rtunnelBindInterfaceName != null) {
						ctrl_sock = RTunnelSocketFactory.getSocket(sockType,
								rserverHost, rserverPort,
								NetworkInterfaceHelper.getInetAddress(
										rtunnelBindInterfaceName,
										DEFAULT_IP_VERSION), 0);
					} else {
						ctrl_sock = RTunnelSocketFactory.getSocket(sockType,
								rserverHost, rserverPort,
								InetAddress.getByName(DEFAULT_RTUNNEL_BIND_ADDRESS), 0);
					}
				} catch (IOException e) {
					logger.info("connect to transit server fails, will try to establish it.");
					cleanup();
					return;
				}
				
				if (ctrl_sock != null) {
					try {
						ctrl_sock_in = new RTunnelInputStream(
								ctrl_sock.getInputStream());
						ctrl_sock_out = new RTunnelOutputStream(
								ctrl_sock.getOutputStream());
					} catch (IOException e) {
						logger.debug("get stream fails. " + e);
						logger.info("the tunnel with transit server is broken, will try to establish it.");
						cleanup();
						return;
					}

					RCtrlSegment ctrlSegment = RCtrlSegment.constructTcpServerPortRCtrlSegment(forwardPort, rTcpPort);
					logger.debug("write control segment " + ctrlSegment);
					RCtrlSegment ctrlSegment2 = null;
					try {
						synchronized (ctrl_sock_out) {
							ctrl_sock_out.writeCtlSegment(ctrlSegment);
						}
						ctrlSegment2 = ctrl_sock_in.readCtlSegment();
					} catch (IOException e) {
						logger.debug("io error. " + e);
						logger.info("the tunnel with transit server is broken, will try to establish it.");
						cleanup();
						return;
					}
					logger.debug("read control segment " + ctrlSegment);

					if (ctrlSegment2.getType() == RCtrlSegment.ACK_TCP_SERVER_PORT_FLAG) {
						if(ctrlSegment2.getContent().length > 0 && ctrlSegment2.getContent()[0] == (byte)0){
							eventDispatcher.dispatchEvent(new ClientTunnelStatusChangedEvent(rTcpHost, rTcpPort, rserverHost, forwardPort,TunnelStatus.OK));
							statusNotifyTimer = new Timer("statusNotifyTimer", new StatusNotifyTimerTask(new ClientTunnelStatusChangedEvent(rTcpHost, rTcpPort, rserverHost, forwardPort,TunnelStatus.ALIVE)));
							statusNotifyTimer.schedule(0, statusNotifyInterval);
							logger.info("the tunnel to transit server is ready.");
							
							TcpSocketAcceptThread tcpSocketAcceptThread = new TcpSocketAcceptThread(
									ctrl_sock_in);
							
							
							heartbeatTimer = new Timer("heartbeatTimer", new HeartbeatTimerTask());
							ackHeartbeatTimer = new Timer("ackHeartbeatTimer", new AckHeartbeatTimerTask());
							lastCheckHeartbeatTimestamp = System.currentTimeMillis();

							heartbeatTimer.schedule(0, heartbeatInterval);
							ackHeartbeatTimer.schedule(0, heartbeatInterval);
							tcpSocketAcceptThread.start();
							
							try {
								tcpSocketAcceptThread.join();
							} catch (InterruptedException e) {
								logger.debug("thread join error. " + e);
								logger.info("the tunnel with transit server is broken, will try to establish it.");
								cleanup();
								return;
							}
						} else {
							logger.debug("tcp server forward error. ");
							logger.info("the tunnel with transit server is broken, will try to establish it.");
							cleanup();
							return;
						}
					} else {
						logger.debug("broken pipe error. ");
						logger.info("the tunnel with transit server is broken, will try to establish it.");
						cleanup();
						return;
					}
				}
			}
		};
		while(main_keep_running){
			clientLogicThread = new Thread(clientLogicRunnable, "clientLogicThread");
			clientLogicThread.start();
			try {
				clientLogicThread.join();
				Thread.sleep(5000L);
			} catch (InterruptedException e) {
				logger.debug("thread join error. " + e);
			}
		}
	}
	
	private void sendCloseTunnelSegment(){
		try {
			RCtrlSegment ctrlSegment = RCtrlSegment.constructCloseTunnelRCtrlSegment();
			logger.debug("write close tunnel segment " + ctrlSegment);
			synchronized (ctrl_sock_out) {
				ctrl_sock_out.writeCtlSegment(ctrlSegment);
			}
			logger.debug("write close tunnel segment success.");
		} catch (IOException e) {
			logger.debug("send close tunnel segment error. " + e);
		}
	}
	
	public void stop(){
		main_keep_running=false;
		if(clientLogicThread != null && !clientLogicThread.isInterrupted()){
			clientLogicThread.interrupt();
		}
		cleanup();
		try {
	        Thread.sleep(5000L);
        } catch (InterruptedException e) {
        	logger.error("thread is interrupted.");
        }
		eventDispatcher.stopDispatch();
	}
	
	public void cleanup() {
		logger.debug("start cleanup");
		
		Thread sendCloseTunnelSegmentThread = new Thread(new Runnable(){
			@Override
            public void run() {
				sendCloseTunnelSegment();
				synchronized (closeTunnelLock) {
					closeTunnelLock.notifyAll();
                }
            }});
		sendCloseTunnelSegmentThread.start();
		synchronized (closeTunnelLock) {
			try {
	            closeTunnelLock.wait(closeTunnelWaitTimeout);
            } catch (InterruptedException e) {
	            //ignore exception
            }
        }
		
		if(ackHeartbeatTimer != null){
			ackHeartbeatTimer.destroy();
		}
		if(heartbeatTimer != null){
			heartbeatTimer.destroy();
		}
		if(statusNotifyTimer != null){
			statusNotifyTimer.destroy();
		}
		eventDispatcher.dispatchEvent(new ClientTunnelStatusChangedEvent(rTcpHost, rTcpPort, rserverHost, forwardPort,TunnelStatus.ERROR));
		
		keep_running = false;
		for(Socket sock : tcpSocks.values()){
			IOUtils.closeQuietly(sock);
		}
		tcpSocks.clear();
		for(Socket sock : rSocks.values()){
			IOUtils.closeQuietly(sock);
		}
		rSocks.clear();
		IOUtils.closeQuietly(this.ctrl_sock);
		logger.info("the tunnel to transit server is closed.");
		LoggerHelper.stopLogging();
    }
	
	private class TcpSocketAcceptThread extends Thread {

		
		private RTunnelInputStream ctrl_sock_in;

		public TcpSocketAcceptThread(RTunnelInputStream ctrl_sock_in) {
			super("TcpSocketAcceptThread");
			this.ctrl_sock_in = ctrl_sock_in;
		}
		
		@Override
		public void run() {
			while(keep_running){
				RCtrlSegment ctrlSegment = null;
				try{
					ctrlSegment = this.ctrl_sock_in.readCtlSegment();
				} catch (IOException e){
					logger.debug("io error. " + e);
					logger.info("the tunnel with transit server is broken, will try to establish it.");
					break;
				}
				if(ctrlSegment.getType() == RCtrlSegment.NEW_TCP_SOCKET_FLAG){
					logger.debug("read control segment " + ctrlSegment);
					final int serverTcpSockBindInfo = SegmentUtils.bytesToInt(ctrlSegment.getContent());
					Runnable tcpSocketHandleRunnable = new Runnable() {
						public void run() {
							try {
								Socket tcp_sock = new Socket(rTcpHost,
										rTcpPort, LOCAL_INET_ADDRESS, 0);
								tcpSocks.put(tcp_sock.getLocalPort(), tcp_sock);
								Socket rsock = null;
								if (rtunnelBindInterfaceName != null) {

									rsock = RTunnelSocketFactory
											.getSocket(
													sockType,
													rserverHost,
													rserverPort,
													NetworkInterfaceHelper
															.getInetAddress(
																	rtunnelBindInterfaceName,
																	DEFAULT_IP_VERSION),
													0);
								} else {
									rsock = RTunnelSocketFactory
											.getSocket(
													sockType,
													rserverHost,
													rserverPort,
													InetAddress
															.getByName(DEFAULT_RTUNNEL_BIND_ADDRESS),
													0);
								}
								rSocks.put(rsock.getLocalPort(), rsock);
								@SuppressWarnings("resource")
                                RTunnelOutputStream rsock_out = new RTunnelOutputStream(
										rsock.getOutputStream());
								RCtrlSegment ctrlSegment = RCtrlSegment.constructACKNewTcpSocketRCtrlSegment(serverTcpSockBindInfo);
								logger.debug("write control segment "
										+ ctrlSegment);
								rsock_out.writeCtlSegment(ctrlSegment);
								RTunnelClientPipeThread pipeThread = new RTunnelClientPipeThread(
										tcp_sock, rsock);
								pipeThread
										.setShutdownHook(new RTunnelClientPipeShutdownHook(
												tcp_sock, rsock));
								pipeThread.start();
							} catch (IOException e) {
								logger.debug("io error. " + e);
							}
						}
					};
					Thread tcpSocketHandleThread = new Thread(tcpSocketHandleRunnable);
					tcpSocketHandleThread.start();
				} else if (ctrlSegment.getType() == RCtrlSegment.HEART_BEAT_FLAG){
					logger.debug("read heartbeat segment " + ctrlSegment);
					byte[] contentBytes = ctrlSegment.getContent();
					RCtrlSegment ackCtrlSegment = RCtrlSegment.constructACKHeartBeatRCtrlSegment(contentBytes);
					logger.debug("write ack heartbeat segment " + ctrlSegment);
					synchronized (ctrl_sock_out) {
						try {
							ctrl_sock_out.writeCtlSegment(ackCtrlSegment);
						} catch (IOException e) {
							logger.debug("io error. " + e);
							logger.info("the tunnel with transit server is broken, will try to establish it.");
							break;
						}
					}
				} else if(ctrlSegment.getType() == RCtrlSegment.ACK_HEART_BEAT_FLAG){
					long sendTime = SegmentUtils.bytesToLong(ctrlSegment.getContent());
					long receiveTime = System.currentTimeMillis();
					lastCheckHeartbeatTimestamp = receiveTime;
					long costTime = receiveTime - sendTime;
					if(costTime > heartbeatTimeout){
						logger.info("heartbeat is timed out, the tunnel maybe broken, will try to establish it.");
						break;
					}
				}
			}
		}

	}
	
	private class HeartbeatTimerTask implements Runnable{

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
	
	private class AckHeartbeatTimerTask  implements Runnable{

		@Override
		public void run() {
			long currentTimestamp = System.currentTimeMillis();
			long costTime = currentTimestamp - lastCheckHeartbeatTimestamp;
			if(costTime > heartbeatTimeout){
				logger.info("heartbeat is timed out, the tunnel maybe broken, will try to establish it.");
				cleanup();
			}
		}

	}
	
	private class StatusNotifyTimerTask implements Runnable{

		private ClientTunnelStatusChangedEvent event;

		public StatusNotifyTimerTask(ClientTunnelStatusChangedEvent event) {
	        this.event = event;
        }

		@Override
		public void run() {
			eventDispatcher.dispatchEvent(this.event);
		}
	}
	
	private class RTunnelClientPipeShutdownHook extends Thread {

		private Socket tcp_sock;
		private Socket rsock;

		public RTunnelClientPipeShutdownHook(Socket tcp_sock, Socket rsock) {
			super("RTunnelClientPipeShutdownHook");
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
	
	private class ShutdownHookThread extends Thread {
		@Override
		public void run() {
			logger.info("tunnel task shutdown normally, clean system resource.");
			RTunnelClient.this.stop();
		}
	}
	
	private void parseArgs(String[] args) {
		try {
			Options options = new Options();
			options.addOption("h", true, "rtunnel server host");
			options.addOption("p", true, "rtunnel server port");
			options.addOption("R", true, "forward arguments");
			options.addOption("t", true, "socket type");
			options.addOption("i", true, "bind network interface name");
			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse( options, args);
			String serverHost = cmd.getOptionValue("h");
			if(serverHost != null){
				this.rserverHost = serverHost;
			}
			String s_serverPort = cmd.getOptionValue("p", Integer.toString(DEFAULT_RTUNNEL_SERVER_PORT));
			if (s_serverPort != null){
				try {
					this.rserverPort = Integer.parseInt(s_serverPort);
				} catch (NumberFormatException e) {
					logger.debug("arguments error.", e);
					this.rserverPort = DEFAULT_RTUNNEL_SERVER_PORT;
				}
			}
			String forwardArgs = cmd.getOptionValue("R");
			if(forwardArgs != null){
				String[] argParts = forwardArgs.split(":");
				this.forwardPort = Integer.parseInt(argParts[0]);
				this.rTcpHost = argParts[1];
				this.rTcpPort = Integer.parseInt(argParts[2]);
			}
			this.rtunnelBindInterfaceName = cmd.getOptionValue("i");
		} catch (ParseException e) {
			logger.debug("parse arguments error.", e);
			this.printUsage();
		}
	}
	
	private void printUsage() {
		System.out.println(String.format("java -Dlogback.configurationFile=conf/rtunnel_logback.xml %s -h rtunnel_server_ip -p rtunnel_server_port -R port:host:hostport -i iface_name", RTunnelClient.class.getName()));
	}

	public static void main(String[] args) {
		RTunnelClient client = new RTunnelClient();
		client.parseArgs(args);
		client.start();
    }
	
}
