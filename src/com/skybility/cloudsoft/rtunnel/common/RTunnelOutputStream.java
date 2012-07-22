package com.skybility.cloudsoft.rtunnel.common;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTunnelOutputStream extends FilterOutputStream {
	
	private static Logger logger = LoggerFactory.getLogger(RTunnelOutputStream.class);

	public RTunnelOutputStream(OutputStream out) {
		super(out);
	}

	public void writeCtlSegment(RCtrlSegment ackSeg) throws IOException {
		out.write(ackSeg.getBytes());
		out.flush();
		if(logger.isDebugEnabled()){
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for(byte c : ackSeg.getBytes()){
				sb.append(c);
				sb.append(",");
			}
			if(ackSeg.getBytes().length > 0){
				sb.setLength(sb.length() - 1);
			}
			sb.append("]");
			logger.debug("write segment: " + sb.toString());
		}
	}

}
