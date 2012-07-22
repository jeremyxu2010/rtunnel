package com.skybility.cloudsoft.rtunnel.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skybility.cloudsoft.rtunnel.common.RSegment;
import com.skybility.cloudsoft.rtunnel.common.RTunnelInputStream;
import com.skybility.cloudsoft.rtunnel.common.RTunnelProperties;
import com.skybility.cloudsoft.rtunnel.common.SegmentUtils;
import com.skybility.cloudsoft.rtunnel.common.Timer;

public class RTunnelServerPipeThread extends Thread{

	private Socket tcp_sock;
	private Socket rsock;
	private InputStream tcp_in;
	private OutputStream tcp_out;
	private RTunnelInputStream rtunnel_in;
	private OutputStream rtunnel_out;
	
	private volatile boolean tcp2rtunneIsRunning = true;
	private volatile boolean rtunnel2tcpIsRunning = true;
	
	private Thread tcp2rtunnelThread;
	private Thread rtunnel2tcpThread;
	private Timer pingTimer = null;
	private Timer ackPingTimer = null;
	
	private static int bufSize = RTunnelProperties.getIntegerProperty("pipeBufSize");
	
	private static int idleTimeout = RTunnelProperties.getIntegerProperty("idleTimeout");
	
	private static int readTimeout = RTunnelProperties.getIntegerProperty("readTimeout");
	
	private static int rSegmentMaxSize = RTunnelProperties.getIntegerProperty("rSegmentMaxSize");
	
	private static int pingInterval = RTunnelProperties.getIntegerProperty("pingInterval");
	
	private static int ackPingTimeout = RTunnelProperties.getIntegerProperty("ackPingTimeout");
	
	private static Logger logger = LoggerFactory.getLogger(RTunnelServerPipeThread.class);
	
	private long lastAckPingTimestamp = -1L;
	
	private Thread shutdownHook;

	public RTunnelServerPipeThread(Socket tcp_sock, Socket rsock) {
		super("RTunnelServerPipeThread");
		this.tcp_sock = tcp_sock;
		this.rsock = rsock;
		try {
			this.tcp_sock.setSoTimeout(idleTimeout);
			this.rsock.setSoTimeout(readTimeout);
			this.tcp_in = this.tcp_sock.getInputStream();
			this.tcp_out = this.tcp_sock.getOutputStream();
			this.rtunnel_in = new RTunnelInputStream(this.rsock.getInputStream());
			this.rtunnel_out = this.rsock.getOutputStream();
		} catch (IOException e) {
			logger.debug("get stream error. ", e);
		}
	}
	
	@Override
	public void run() {
		Runnable tcp2rtunnelRunnable = new Runnable(){
			@Override
			public void run() {
				byte[] buf = new byte[bufSize];
				int len = -1;
				while(tcp2rtunneIsRunning){
					try {
						len = tcp_in.read(buf);
					} catch (IOException e) {
						logger.debug("broken pipe." + e);
						killRtunnel2tcpThread();
						break;
					}
					if(len != -1){
						if(len > 0){
							try {
								int segNum = (len + rSegmentMaxSize -1)/rSegmentMaxSize;
								for(int i=0; i<segNum; i++){
									byte[] toWriteBytes;
									if((i+1)*rSegmentMaxSize > len){
										toWriteBytes = new byte[len - i*rSegmentMaxSize];
									} else {
										toWriteBytes = new byte[rSegmentMaxSize];
									}
									System.arraycopy(buf, i*rSegmentMaxSize, toWriteBytes, 0, toWriteBytes.length);
									RSegment dataRSegment = RSegment.contructDataRSegment(toWriteBytes);
									logger.debug("send data segment: " + dataRSegment);
									synchronized (rtunnel_out) {
											byte[] segBytes = dataRSegment.getBytes();
											rtunnel_out.write(segBytes, 0, segBytes.length);
											rtunnel_out.flush();
									}
								}
							} catch (IOException e) {
								logger.debug("broken pipe." + e);
								killRtunnel2tcpThread();
								break;
							}
						}
					} else {
						logger.debug("broken pipe.");
						killRtunnel2tcpThread();
						break;
					}
				}
			}
		};
		Runnable rtunnel2tcpRunnable = new Runnable(){
			@Override
			public void run() {
				while(rtunnel2tcpIsRunning){
					try {
						int flag = rtunnel_in.read();
						byte[] contentLenBytes = new byte[4];
						logger.debug("read flag: " + RSegment.SEG_TYPE_DESC.get(flag));
						if(flag == RSegment.DATA_FLAG){
							rtunnel_in.readFully(contentLenBytes);
							int contentLength = SegmentUtils.bytesToInt(contentLenBytes);
							logger.debug("receive data segment: [ type=DATA_SEG, length="+contentLength+" ]");
							byte[] contentBytes = new byte[contentLength];
							rtunnel_in.readFully(contentBytes);
							try {
								tcp_out.write(contentBytes, 0, contentBytes.length);
								tcp_out.flush();
							} catch (IOException e) {
								logger.debug("broken pipe. " + e);
								killTcp2rtunnelThread();
								break;
							}
						} else if(flag == RSegment.HEART_BEAT_FLAG){
							rtunnel_in.readFully(contentLenBytes);
							int contentLength = SegmentUtils.bytesToInt(contentLenBytes);
							byte[] contentBytes = new byte[contentLength];
							rtunnel_in.readFully(contentBytes);
							RSegment ackPingRSegment = RSegment.contructACKHeartBeatRSegment(contentBytes);
							synchronized (rtunnel_out) {
								try {
									byte[] segBytes = ackPingRSegment.getBytes();
									rtunnel_out.write(segBytes, 0, segBytes.length);
									rtunnel_out.flush();
								} catch (IOException e) {
									logger.debug("broken pipe. " + e);
									killTcp2rtunnelThread();
									break;
								}
							}
						} else if(flag == RSegment.ACK_HEART_BEAT_FLAG){
							rtunnel_in.readFully(contentLenBytes);
							int contentLength = SegmentUtils.bytesToInt(contentLenBytes);
							byte[] contentBytes = new byte[contentLength];
							rtunnel_in.readFully(contentBytes);
							if(contentBytes.length == 8){
								long sendTime = SegmentUtils.bytesToLong(contentBytes);
								long receiveTime = System.currentTimeMillis();
								lastAckPingTimestamp = receiveTime;
								long costTime = receiveTime - sendTime;
								logger.debug("ping cost time: "+ costTime +"ms");
								if(receiveTime - sendTime > ackPingTimeout){
									logger.debug("ack ping timeout: receiveTime=" + receiveTime + ", sendTime=" + sendTime);
									killTcp2rtunnelThread();
									break;
								}
							}
						} else {
							logger.debug("broken pipe. ");
							killTcp2rtunnelThread();
							break;
						}
					} catch (IOException e) {
						logger.debug("broken pipe. " + e);
						killTcp2rtunnelThread();
						break;
					}
				}
			}
		};
		
		tcp2rtunnelThread = new Thread(tcp2rtunnelRunnable, "tcp2rtunnelThread");
		rtunnel2tcpThread = new Thread(rtunnel2tcpRunnable, "rtunnel2tcpThread");
		pingTimer = new Timer("pingTimer", new PingTimerTask());
		ackPingTimer = new Timer("ackPingTimer", new AckPingTimerTask());
		tcp2rtunnelThread.start();
		rtunnel2tcpThread.start();
		pingTimer.schedule(0, pingInterval);
		ackPingTimer.schedule(0, pingInterval);
		
		try {
			tcp2rtunnelThread.join();
			rtunnel2tcpThread.join();
		} catch (InterruptedException e) {
			//ignore exception
		}
		
		ackPingTimer.destroy();
		pingTimer.destroy();
		closeStream();
		
		if(shutdownHook != null){
			shutdownHook.start();
		}
		
	}
	
	private void closeStream() {
		if(this.tcp_in != null){
			try {
				this.tcp_in.close();
			} catch (IOException e) {
				//quiet close stream
			}
		}
		if(this.tcp_out != null){
			try {
				this.tcp_out.close();
			} catch (IOException e) {
				//quiet close stream
			}
		}
		if(this.rtunnel_in != null){
			try {
				this.rtunnel_in.close();
			} catch (IOException e) {
				//quiet close stream
			}
		}
		if(this.rtunnel_out != null){
			try {
				this.rtunnel_out.close();
			} catch (IOException e) {
				//quiet close stream
			}
		}
	}

	public void setShutdownHook(Thread shutdownHook) {
		this.shutdownHook = shutdownHook;
	}

	private void killRtunnel2tcpThread() {
		rtunnel2tcpIsRunning = false;
		if(rtunnel_in != null){
			try {
				rtunnel_in.close();
			} catch (IOException e1) {
				//quiet close sock
			}
		}
		if(tcp_out != null){
			try {
				tcp_out.close();
			} catch (IOException e1) {
				//quiet close sock
			}
		}
		if(!rtunnel2tcpThread.isInterrupted()){
			rtunnel2tcpThread.interrupt();
		}
	}
	
	private void killTcp2rtunnelThread() {
		tcp2rtunneIsRunning = false;
		if(tcp_in != null){
			try {
				tcp_in.close();
			} catch (IOException e1) {
				//quiet close sock
			}
		}
		if(rtunnel_out != null){
			try {
				rtunnel_out.close();
			} catch (IOException e1) {
				//quiet close sock
			}
		}
		if(!tcp2rtunnelThread.isInterrupted()){
			tcp2rtunnelThread.interrupt();
		}
	}
	
	class PingTimerTask implements Runnable{

		@Override
		public void run() {
			RSegment pingRSegment = RSegment.contructHeartBeatRSegment();
			synchronized (rtunnel_out) {
				try {
					byte[] segBytes = pingRSegment.getBytes();
					rtunnel_out.write(segBytes, 0, segBytes.length);
					rtunnel_out.flush();
				} catch (IOException e) {
					//ignore exception
				}
			}
		}
	}
	
	class AckPingTimerTask implements Runnable{

		@Override
		public void run() {
			long currentTimestamp = System.currentTimeMillis();
			if(lastAckPingTimestamp > -1 && (currentTimestamp - lastAckPingTimestamp) > ackPingTimeout){
				logger.debug("ack ping timeout: currentTimestamp=" + currentTimestamp + ", lastAckPingTimestamp=" + lastAckPingTimestamp);
				killTcp2rtunnelThread();
				killRtunnel2tcpThread();
			}
		}

	}

}
