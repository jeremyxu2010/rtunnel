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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import com.skybility.cloudsoft.rtunnel.common.LoggerHelper;
import com.skybility.cloudsoft.rtunnel.common.NetworkInterfaceHelper;
import com.skybility.cloudsoft.rtunnel.common.RCtrlSegment;
import com.skybility.cloudsoft.rtunnel.common.RTunnelInputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelOutputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelProperties;
import com.skybility.cloudsoft.rtunnel.common.RTunnelSocketFactory;
import com.skybility.cloudsoft.rtunnel.common.SegmentUtils;
import com.skybility.cloudsoft.rtunnel.common.SocketType;
import com.skybility.cloudsoft.rtunnel.common.Timer;

public class RTunnelClient extends Thread{
	


	private static Logger logger = LoggerFactory.getLogger(RTunnelClient.class);
	
	private static InetAddress LOCAL_INET_ADDRESS = null;
	private static RTunnelClient client;
	
	static {
		try {
			LOCAL_INET_ADDRESS = InetAddress.getByName("0.0.0.0");
		} catch (UnknownHostException e) {
			// ignore exception
		}
	}
	
	public RTunnelClient(){
		super("RTunnelClient");
	}
	
	public static void main(String[] args) {
		client = new RTunnelClient();
		client.parseArgs(args);
		client.start();
	}

	private int rserverPort;
	private String rserverHost;
	private int forwardPort;
	private String rTcpHost;
	private int rTcpPort;
	
	private RTunnelInputStream ctrl_sock_in;

	private RTunnelOutputStream ctrl_sock_out;
	
	private static int CLIENT_STATUS_INIT = 0;
	private static int CLIENT_STATUS_READY = 1;
	
	private int client_statue = CLIENT_STATUS_INIT;
	
	private volatile boolean keep_running;
	private volatile boolean main_keep_running;
	
	private Map<Integer, Socket> tcpSocks = new ConcurrentHashMap<Integer, Socket>();
	
	private Map<Integer, Socket> rSocks = new ConcurrentHashMap<Integer, Socket>();
	private SocketType sockType;
	private Socket ctrl_sock;
	
	private Timer heartbeatTimer = null;
	
	private Timer ackHeartbeatTimer = null;
	
	private static int heartbeatInterval = RTunnelProperties.getIntegerProperty("heartbeatInterval");
	
	private static int heartbeatTimeout = RTunnelProperties.getIntegerProperty("heartbeatTimeout");
	
	private static final String DEFAULT_SOCK_TYPE = RTunnelProperties.getStringProperty("defaultSockType");
	private static final int DEFAULT_RTUNNEL_SERVER_PORT = RTunnelProperties.getIntegerProperty("defaultServerTunnelPort");
	private static final String DEFAULT_RTUNNEL_BIND_ADDRESS = RTunnelProperties.getStringProperty("defaultRtunnelBindAddress");
	private static final String DEFAULT_IP_VERSION = RTunnelProperties.getStringProperty("defaultIpVersion");
	
	private long lastCheckHeartbeatTimestamp = -1L;

	private String rtunnelBindInterfaceName;

	@Override
	public void run() {
		ExitSignalHandler exitSignalHandler = new ExitSignalHandler();
		exitSignalHandler.install("INT");
		Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
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
				
				if (ctrl_sock != null
						&& client_statue == CLIENT_STATUS_INIT) {
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

					RCtrlSegment ctrlSegment = RCtrlSegment.contructTcpServerPortRCtrlSegment(forwardPort, rTcpPort);
					logger.debug("write control segment " + ctrlSegment);
					RCtrlSegment ctrlSegment2 = null;
					try {
						ctrl_sock_out.writeCtlSegment(ctrlSegment);
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
							client_statue = CLIENT_STATUS_READY;
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
			Thread clientLogicThread = new Thread(clientLogicRunnable, "clientLogicThread");
			clientLogicThread.start();
			try {
				clientLogicThread.join();
				Thread.sleep(5000L);
			} catch (InterruptedException e) {
				logger.debug("thread join error. " + e);
			}
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
			String sockType = cmd.getOptionValue("t", DEFAULT_SOCK_TYPE);
			if("tcp".equals(sockType)){
				this.sockType = SocketType.TCP;
			} else {
				this.sockType = SocketType.UDP;
			}
			this.rtunnelBindInterfaceName = cmd.getOptionValue("i");
		} catch (ParseException e) {
			logger.debug("parse arguments error.", e);
			this.printUsage();
		}
	}
	
	private void cleanup() {
		logger.debug("start cleanup");
		if(ackHeartbeatTimer != null){
			ackHeartbeatTimer.destroy();
		}
		if(heartbeatTimer != null){
			heartbeatTimer.destroy();
		}
		client_statue=CLIENT_STATUS_INIT;
		keep_running = false;
		for(Socket sock : tcpSocks.values()){
			if(sock != null){
				try {
					sock.close();
				} catch (IOException e) {
					//quiet close sock
				}
			}
		}
		tcpSocks.clear();
		for(Socket sock : rSocks.values()){
			if(sock != null){
				try {
					sock.close();
				} catch (IOException e) {
					//quiet close sock
				}
			}
		}
		rSocks.clear();
		if(this.ctrl_sock != null){
			try {
				this.ctrl_sock.close();
			} catch (IOException e) {
				//quiet close sock
			}
		}
		logger.info("the tunnel to transit server is closed.");
		LoggerHelper.stopLogging();
	}

	private void printUsage() {
		System.out.println(String.format("java -Dlogback.configurationFile=conf/rtunnel_logback.xml %s -h rtunnel_server_ip -p rtunnel_server_port -R port:host:hostport -t tcp -i iface_name", RTunnelClient.class.getName()));
	}
	
	class TcpSocketAcceptThread extends Thread {

		
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
								RTunnelOutputStream rsock_out = new RTunnelOutputStream(
										rsock.getOutputStream());
								RCtrlSegment ctrlSegment = RCtrlSegment.contructACKNewTcpSocketRCtrlSegment(serverTcpSockBindInfo);
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
					RCtrlSegment ackCtrlSegment = RCtrlSegment.contructACKHeartBeatRCtrlSegment(contentBytes);
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
	
	class HeartbeatTimerTask implements Runnable{

		@Override
		public void run() {
			try {
				RCtrlSegment ctrlSegment = RCtrlSegment.contructHeartBeatRCtrlSegment();
				logger.debug("write heartbeat segment " + ctrlSegment);
				ctrl_sock_out.writeCtlSegment(ctrlSegment);
				logger.debug("write heartbeat segment success.");
			} catch (IOException e) {
				logger.debug("send heartbeat error. " + e);
			}
		}
	}
	
	class AckHeartbeatTimerTask  implements Runnable{

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
	
	class ExitSignalHandler implements SignalHandler{
		
		private SignalHandler oldHandler;

	    // Static method to install the signal handler
	    public ExitSignalHandler install(String signalName) {
	        Signal exitSignal = new Signal(signalName);
	        ExitSignalHandler exitHandler = new ExitSignalHandler();
	        exitHandler.oldHandler = Signal.handle(exitSignal,exitHandler);
	        return exitHandler;
	    }

		@Override
		public void handle(Signal sig) {
			logger.info("tunnel task shutdown normally, clean system resource.");
			cleanup();
			if (oldHandler != SIG_DFL && oldHandler != SIG_IGN) {
                oldHandler.handle(sig);
            }
		}
	}
	
	class RTunnelClientPipeShutdownHook extends Thread {

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
			if(tcp_sock != null){
				try {
					tcp_sock.close();
				} catch (IOException e) {
					//quiet close sock
				}
			}
			if(rsock != null){
				try {
					rsock.close();
				} catch (IOException e) {
					//quiet close sock
				}
			}
		}
	}
	
	class ShutdownHookThread extends Thread {
		@Override
		public void run() {
			logger.info("tunnel task shutdown normally, clean system resource.");
			main_keep_running=false;
			if(!client.isInterrupted()){
				client.interrupt();
			}
			cleanup();
		}
	}
}
