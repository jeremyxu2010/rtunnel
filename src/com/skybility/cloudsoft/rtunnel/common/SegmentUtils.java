/*$Id: $
 --------------------------------------
  Skybility
 ---------------------------------------
  Copyright By Skybility ,All right Reserved
 * author   date   comment
 * jeremy  2012-7-22  Created
*/ 
package com.skybility.cloudsoft.rtunnel.common; 
 
public class SegmentUtils {
	public static int bytesToInt(byte[] bytes){
		return (int)(((bytes[0]&0xff)<<24) | ((bytes[1]&0xff)<<16) | ((bytes[2]&0xff)<<8) | (bytes[3]&0xff));
	}
	
	public static byte[] intToBytes(int v) {
	    byte[] bytes = new byte[ 4 ];
	    bytes[0] = (byte)(v >>> 24);
	    bytes[1] = (byte)(v >>> 16);
	    bytes[2] = (byte)(v >>>  8);
	    bytes[3] = (byte)(v >>>  0);
	    return bytes;
	}
	
	public static byte[] longToBytes(long v) {
	    byte[] bytes = new byte[ 8 ];
	    bytes[0] = (byte)(v >>> 56);
	    bytes[1] = (byte)(v >>> 48);
	    bytes[2] = (byte)(v >>> 40);
	    bytes[3] = (byte)(v >>> 32);
	    bytes[4] = (byte)(v >>> 24);
	    bytes[5] = (byte)(v >>> 16);
	    bytes[6] = (byte)(v >>>  8);
	    bytes[7] = (byte)(v >>>  0);
	    return bytes;
	}
	
	public static long bytesToLong(byte[] bytes) {
		if (bytes == null || bytes.length != 8) return 0x0;
	    // ----------
	    return (long)(
	            // (Below) convert to longs before shift because digits
	            //         are lost with ints beyond the 32-bit limit
	            (long)(0xff & bytes[0]) << 56  |
	            (long)(0xff & bytes[1]) << 48  |
	            (long)(0xff & bytes[2]) << 40  |
	            (long)(0xff & bytes[3]) << 32  |
	            (long)(0xff & bytes[4]) << 24  |
	            (long)(0xff & bytes[5]) << 16  |
	            (long)(0xff & bytes[6]) << 8   |
	            (long)(0xff & bytes[7]) << 0
	            );
	}
}
