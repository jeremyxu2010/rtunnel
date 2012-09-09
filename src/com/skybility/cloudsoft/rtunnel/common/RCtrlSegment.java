package com.skybility.cloudsoft.rtunnel.common;

import java.util.HashMap;
import java.util.Map;


public class RCtrlSegment {
	public static final String US_ASCII_CHARSET = "US-ASCII";
	public static final byte TCP_SERVER_PORT_FLAG = (byte) 0x01;
	public static final byte ACK_TCP_SERVER_PORT_FLAG = (byte) 0x02;
    public static final byte NEW_TCP_SOCKET_FLAG = (byte) 0x03;
    public static final byte ACK_NEW_TCP_SOCKET_FLAG = (byte) 0x04;
    public static final byte HEART_BEAT_FLAG = (byte) 0x05;
    public static final byte ACK_HEART_BEAT_FLAG = (byte) 0x06;
    public static final byte CLOSE_TUNNEL_FLAG = (byte)0x07;
    private static Map<Integer, String> SEG_TYPE_DESC = new HashMap<Integer, String>();
    static {
    	SEG_TYPE_DESC.put((int)TCP_SERVER_PORT_FLAG, "TCP_SERVER_PORT");
    	SEG_TYPE_DESC.put((int)ACK_TCP_SERVER_PORT_FLAG, "ACK_TCP_SERVER_PORT");
    	SEG_TYPE_DESC.put((int)NEW_TCP_SOCKET_FLAG, "NEW_TCP_SOCKET");
    	SEG_TYPE_DESC.put((int)ACK_NEW_TCP_SOCKET_FLAG, "ACK_NEW_TCP_SOCKET");
    	SEG_TYPE_DESC.put((int)HEART_BEAT_FLAG, "HEART_BEAT");
    	SEG_TYPE_DESC.put((int)ACK_HEART_BEAT_FLAG, "ACK_HEART_BEAT");
    	SEG_TYPE_DESC.put((int)CLOSE_TUNNEL_FLAG, "CLOSE_TUNNEL");
    }
	private byte segType;
	private int contentLen;
	private byte[] content;
    
	public RCtrlSegment(byte segType, byte[] content) {
		super();
		this.segType = segType;
		this.contentLen = content.length;
		this.content = content;
	}
	
	public static RCtrlSegment constructTcpServerPortRCtrlSegment(int tcpPort, int rTcpPort){
		byte[] portBytes = SegmentUtils.intToBytes(tcpPort);
		byte[] tcpPortBytes = SegmentUtils.intToBytes(rTcpPort);
		byte[] resultBytes = new byte[portBytes.length+tcpPortBytes.length];
		System.arraycopy(portBytes, 0, resultBytes, 0, portBytes.length);
		System.arraycopy(tcpPortBytes, 0, resultBytes, portBytes.length, tcpPortBytes.length);
		return new RCtrlSegment(RCtrlSegment.TCP_SERVER_PORT_FLAG, resultBytes);
	}
	
	public static RCtrlSegment constructACKTcpServerPortRCtrlSegment(int result){
		byte[] resultBytes = new byte[]{(byte)(result >>> 0)};
		return new RCtrlSegment(RCtrlSegment.ACK_TCP_SERVER_PORT_FLAG, resultBytes);
	}
	
	public static RCtrlSegment constructNewTcpSocketRCtrlSegment(int serverSockBindInfo){
		byte[] resultBytes = SegmentUtils.intToBytes(serverSockBindInfo);
		return new RCtrlSegment(RCtrlSegment.NEW_TCP_SOCKET_FLAG, resultBytes);
	}
	
	public static RCtrlSegment constructACKNewTcpSocketRCtrlSegment(int serverSockBindInfo){
		byte[] resultBytes = SegmentUtils.intToBytes(serverSockBindInfo);
		return new RCtrlSegment(RCtrlSegment.ACK_NEW_TCP_SOCKET_FLAG, resultBytes);
	}
	
	public static RCtrlSegment constructHeartBeatRCtrlSegment(){
		long time = System.currentTimeMillis();
		byte[] timeBytes = SegmentUtils.longToBytes(time);
		return new RCtrlSegment(RCtrlSegment.HEART_BEAT_FLAG, timeBytes);
	}
	
	public static RCtrlSegment constructACKHeartBeatRCtrlSegment(byte[] timeBytes){
		byte[] resultBytes = new byte[timeBytes.length];
		System.arraycopy(timeBytes, 0, resultBytes, 0, timeBytes.length);
		return new RCtrlSegment(RCtrlSegment.ACK_HEART_BEAT_FLAG, resultBytes);
	}
	

	public static RCtrlSegment constructCloseTunnelRCtrlSegment() {
		byte[] resultBytes = new byte[0];
		return new RCtrlSegment(RCtrlSegment.CLOSE_TUNNEL_FLAG, resultBytes);
    }
	
	public byte[] getBytes(){
		byte[] bytes = new byte[content.length + 5];
		bytes[0] = segType;
		System.arraycopy(SegmentUtils.intToBytes(this.contentLen), 0, bytes, 1, 4);
		System.arraycopy(this.content, 0, bytes, 5, this.content.length);
		return bytes;
	}
	
	@Override
	public String toString() {
		return "[ type=" + SEG_TYPE_DESC.get((int)this.segType) + ", contentLen=" + this.contentLen +"]";
	}
	
	public byte getType(){
    	return this.segType;
    }
	
	public int getContentLen() {
		return contentLen;
	}
	
	public byte[] getContent() {
		return content;
	}

	
}
