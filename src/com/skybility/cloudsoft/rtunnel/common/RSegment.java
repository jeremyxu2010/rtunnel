package com.skybility.cloudsoft.rtunnel.common;

import java.util.HashMap;
import java.util.Map;

public class RSegment {
	
	public static final byte DATA_FLAG = (byte) 0x01;
	public static final byte HEART_BEAT_FLAG = (byte) 0x02;
	public static final byte ACK_HEART_BEAT_FLAG = (byte) 0x04;
	
	public static Map<Integer, String> SEG_TYPE_DESC = new HashMap<Integer, String>();
    static {
    	SEG_TYPE_DESC.put((int)DATA_FLAG, "DATA_SEG");
    	SEG_TYPE_DESC.put((int)HEART_BEAT_FLAG, "HEART_BEAT_SEG");
    	SEG_TYPE_DESC.put((int)ACK_HEART_BEAT_FLAG, "ACK_HEART_BEAT_FLAG");
    }
	private byte segType;
	private int contentLen;
	private byte[] content;
	
	private RSegment(byte segType, int contentLen, byte[] content) {
		this.segType = segType;
		this.contentLen = contentLen;
		this.content = content;
	}

	public static RSegment contructDataRSegment(byte[] content){
		return new RSegment(RSegment.DATA_FLAG, content.length, content);
	}
	
	public static RSegment contructHeartBeatRSegment(){
		long time = System.currentTimeMillis();
		byte[] timeBytes = SegmentUtils.longToBytes(time);
		return new RSegment(RSegment.HEART_BEAT_FLAG, timeBytes.length, timeBytes);
	}
	
	public static RSegment contructACKHeartBeatRSegment(byte[] bytes){
		byte[] toBytes = new byte[bytes.length];
		System.arraycopy(bytes, 0, toBytes, 0, bytes.length);
		return new RSegment(RSegment.ACK_HEART_BEAT_FLAG, toBytes.length, toBytes);
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
