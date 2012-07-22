package com.skybility.cloudsoft.rtunnel.common;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTunnelInputStream extends FilterInputStream {
	
	private static Logger logger = LoggerFactory.getLogger(RTunnelInputStream.class);

	public RTunnelInputStream(InputStream in) {
		super(in);
	}
	
	public RCtrlSegment readCtlSegment() throws IOException  {
		try{
			byte[] flagBytes = new byte[1];
			byte[] contentLenBytes = new byte[4];
			readFully(flagBytes);
			readFully(contentLenBytes);
			byte flag = flagBytes[0];
			int contentLen = SegmentUtils.bytesToInt(contentLenBytes);
			byte[] contentBytes = new byte[contentLen];
			readFully(contentBytes);
			RCtrlSegment result = new RCtrlSegment(flag, contentLen, contentBytes);
			if(logger.isDebugEnabled()){
				StringBuilder sb = new StringBuilder();
				sb.append("[");
				for(byte c : result.getBytes()){
					sb.append(c);
					sb.append(",");
				}
				if(result.getBytes().length > 0){
					sb.setLength(sb.length() - 1);
				}
				sb.append("]");
				logger.debug("read segment: " + sb.toString());
			}
			return result;
		}catch(IOException e){
			e.printStackTrace();
			throw e;
		}
	}
	
	public void readFully(byte b[], int off, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n , len -n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }
	
    public void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

}
