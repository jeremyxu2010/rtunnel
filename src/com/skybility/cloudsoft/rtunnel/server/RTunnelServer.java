package com.skybility.cloudsoft.rtunnel.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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

import com.skybility.cloudsoft.rtunnel.common.RCtrlSegment;
import com.skybility.cloudsoft.rtunnel.common.RTunnelInputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelOutputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelProperties;
import com.skybility.cloudsoft.rtunnel.common.RTunnelSocketFactory;
import com.skybility.cloudsoft.rtunnel.common.SegmentUtils;
import com.skybility.cloudsoft.rtunnel.common.SocketType;


public class RTunnelServer extends Thread{

	private static Logger logger = LoggerFactory.getLogger(RTunnelServer.class);
	
	private ServerSocket rserverSocket;
	
	private int rserverPort;
	
	private volatile boolean main_keep_running;

	private static int SERVER_STATUS_CLOSED = 0;
	private static int SERVER_STATUS_READY = 1;
	
	private int server_statue = SERVER_STATUS_CLOSED;
	
	private SocketType sockType;
	
	private static final int DEFAULT_RTUNNEL_SERVER_PORT = RTunnelProperties.getIntegerProperty("defaultServerTunnelPort");
	
	private static final String DEFAULT_SOCK_TYPE = RTunnelProperties.getStringProperty("defaultSockType");

	private static final String DEFAULT_FORWARD_BIND_ADDRESS = RTunnelProperties.getStringProperty("defaultForwardBindAddress");
	
	private static final int FIRST_HANDSHAKE_TIMEOUT = RTunnelProperties.getIntegerProperty("firstHandshakeTimeout");

	private static RTunnelServer server;
	
	private String forwardBindAddress;

	public boolean keep_running;

	private Map<Integer, RTunnelServerHandler> rTunnelServerHandlers = new ConcurrentHashMap<Integer, RTunnelServerHandler>();

	protected com.skybility.cloudsoft.rtunnel.server.RTunnelServer.RServerLoopThread rServerLoopThread;
	
	public RTunnelServer(){
		super("RTunnelServer");
	}

	public static void main(String[] args) {
		server = new RTunnelServer();
		server.parseArgs(args);
		server.start();
	}

	@Override
	public void run() {
		ExitSignalHandler exitSignalHandler = new ExitSignalHandler();
		exitSignalHandler.install("INT");
		Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
		main_keep_running = true;
		
		Runnable serverLogicRunnable = new Runnable() {
			public void run() {
				keep_running = true;
				try {
					rserverSocket = RTunnelSocketFactory.getServerSocket(
							sockType, rserverPort);
				} catch (IOException e) {
					logger.error("create server socket error. ", e);
					cleanup();
					return;
				}
				
				if (server_statue == SERVER_STATUS_CLOSED) {
					rServerLoopThread = new RServerLoopThread(
							rserverSocket);
					rServerLoopThread.start();
					server_statue = SERVER_STATUS_READY;
					try {
						rServerLoopThread.join();
					} catch (InterruptedException e) {
						logger.error("thread join error. " + e);
						cleanup();
						return;
					}
				}
				cleanup();
				return;
			}
		};
		
		while(main_keep_running){
			Thread serverLogicThread = new Thread(serverLogicRunnable, "serverLogicThread");
			serverLogicThread.start();
			try {
				serverLogicThread.join();
			} catch (InterruptedException e) {
				logger.error("thread join error. " + e);
			}
			cleanup();
		}
	}
	
	class RServerLoopThread extends Thread{

		private ServerSocket rserverSocket;

		public RServerLoopThread(ServerSocket rserverSocket) {
			super("RServerLoopThread");
			this.rserverSocket = rserverSocket;
		}
		
		@Override
		public void run() {
			while(keep_running){
				Socket sock = null;
				
				try {
					sock = rserverSocket.accept();
				} catch (IOException e) {
					logger.error("accept sock fails. " + e);
					continue;
				}
				
				final Socket newSock = sock;
				
				Runnable acceptSocketRunnable = new Runnable() {
	                public void run() {
		                RTunnelInputStream sock_in = null;
		                RTunnelOutputStream sock_out = null;
		                try {
			                sock_in = new RTunnelInputStream(newSock.getInputStream());
			                sock_out = new RTunnelOutputStream(newSock.getOutputStream());
		                } catch (IOException e) {
			                logger.error("get stream fails. " + e);
			                return;
		                }
		                RCtrlSegment ctrlSegment = null;
		                try {
		                	newSock.setSoTimeout(FIRST_HANDSHAKE_TIMEOUT);
			                ctrlSegment = sock_in.readCtlSegment();
			                newSock.setSoTimeout(0);
			                logger.debug("read control segment " + ctrlSegment);
		                } catch (IOException e) {
			                try {
			                	newSock.close();
			                } catch (IOException e1) {
				                // quiet close sock
			                }
			                logger.error("read control segment fails. " + e);
			                return;
		                }
		                if (ctrlSegment.getType() == RCtrlSegment.TCP_SERVER_PORT_FLAG) {
			                byte[] tcpServerPortInfoBytes = ctrlSegment.getContent();
			                byte[] forwardPortBytes = new byte[4];
			                byte[] tcpPortBytes = new byte[4];
			                System.arraycopy(tcpServerPortInfoBytes, 0, forwardPortBytes, 0, 4);
			                System.arraycopy(tcpServerPortInfoBytes, 4, tcpPortBytes, 0, 4);
			                int forward_tcp_port = SegmentUtils.bytesToInt(forwardPortBytes);
			                int tcp_port = SegmentUtils.bytesToInt(tcpPortBytes);
			                RTunnelServerHandler rtunnelServerHandler = new RTunnelServerHandler(forwardBindAddress,
			                        forward_tcp_port, newSock, sock_in, sock_out, server, tcp_port, sockType);
			                rtunnelServerHandler.start();
		                } else if (ctrlSegment.getType() == RCtrlSegment.ACK_NEW_TCP_SOCKET_FLAG) {
			                int bindInfo = SegmentUtils.bytesToInt(ctrlSegment.getContent());
			                int forward_tcp_port = (int) (bindInfo >>> 16);
			                int tcpSockBindPort = (int) (bindInfo & 0xffff);
			                logger.debug("forward_tcp_port=" + forward_tcp_port + ", tcpSockBindPort="
			                        + tcpSockBindPort);
			                RTunnelServerHandler rTunnelServerHandler = rTunnelServerHandlers.get(forward_tcp_port);
			                rTunnelServerHandler.ackNewTcpSocket(tcpSockBindPort, newSock);
		                }
	                }
                };
                Thread acceptSocketThread = new Thread(acceptSocketRunnable);
                acceptSocketThread.start();
			}
		}

	}

	private void parseArgs(String[] args) {
		try {
			Options options = new Options();
			options.addOption("p", true, "rtunnel server port");
			options.addOption("t", true, "socket type");
			options.addOption("b", true, "forward bind address");
			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse( options, args);
			String s_serverPort = cmd.getOptionValue("p", Integer.toString(DEFAULT_RTUNNEL_SERVER_PORT));
			if (s_serverPort != null){
				try {
					this.rserverPort = Integer.parseInt(s_serverPort);
				} catch (NumberFormatException e) {
					logger.error("arguments error.", e);
					this.rserverPort = DEFAULT_RTUNNEL_SERVER_PORT;
				}
			}
			String sockType = cmd.getOptionValue("t", DEFAULT_SOCK_TYPE);
			if("tcp".equals(sockType)){
				this.sockType = SocketType.TCP;
			} else {
				this.sockType = SocketType.UDP;
			}
			this.forwardBindAddress = cmd.getOptionValue("b", DEFAULT_FORWARD_BIND_ADDRESS);
		} catch (ParseException e) {
			logger.error("parse arguments error.", e);
			this.printUsage();
		}
	}

	private void printUsage() {
		System.out.println(String.format("java -Dlogback.configurationFile=conf/rtunnel_logback.xml %s -p rtunnel_server_port", RTunnelServer.class.getName()));
	}
	
	private void cleanup(){
		keep_running = false;

		for(RTunnelServerHandler rTunnelServerHandler : rTunnelServerHandlers.values()){
			rTunnelServerHandler.cleanup();
		}
		rTunnelServerHandlers.clear();
		server_statue = SERVER_STATUS_CLOSED;
		if(rserverSocket != null){
			try {
				rserverSocket.close();
			} catch (IOException e) {
				//quiet close sock
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
			logger.info("Exit Signal handler called for signal "+sig);
			main_keep_running=false;
			if(server != null && !server.isInterrupted()){
				server.interrupt();
			}
			cleanup();
			if (oldHandler != SIG_DFL && oldHandler != SIG_IGN) {
                oldHandler.handle(sig);
            }
		}
		
	}
	
	class ShutdownHookThread extends Thread {
		public ShutdownHookThread() {
			super("ShutdownHookThread");
		}
		
		@Override
		public void run() {
			logger.info("Shutdown hook called");
			main_keep_running=false;
			if(server != null && !server.isInterrupted()){
				server.interrupt();
			}
			cleanup();
		}
	}

	public void registerServerHandler(int tcpServerPort, RTunnelServerHandler rTunnelServerHandler) {
		rTunnelServerHandlers.put(tcpServerPort, rTunnelServerHandler);
	}
	
	public void unregisterServerHandler(int tcpServerPort) {
		rTunnelServerHandlers.remove(tcpServerPort);
	}
}
