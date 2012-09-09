/*$Id: $
 --------------------------------------
  Skybility
 ---------------------------------------
  Copyright By Skybility ,All right Reserved
 * author   date   comment
 * jeremy  2012-8-2  Created
*/ 
package com.skybility.cloudsoft.rtunnel.event; 

import com.skybility.cloudsoft.rtunnel.common.TunnelStatus;
 
public class ClientTunnelStatusChangedEvent {
	private String rTcpHost;
	private int rTcpPort;
	private String rserverHost;
	private int forwardPort;
	private TunnelStatus status;
	
	public ClientTunnelStatusChangedEvent(String rTcpHost, int rTcpPort, String rserverHost, int forwardPort,
            TunnelStatus status) {
	    super();
	    this.rTcpHost = rTcpHost;
	    this.rTcpPort = rTcpPort;
	    this.rserverHost = rserverHost;
	    this.forwardPort = forwardPort;
	    this.status = status;
    }
	
	public String getrTcpHost() {
		return rTcpHost;
	}
	public void setrTcpHost(String rTcpHost) {
		this.rTcpHost = rTcpHost;
	}
	public int getrTcpPort() {
		return rTcpPort;
	}
	public void setrTcpPort(int rTcpPort) {
		this.rTcpPort = rTcpPort;
	}
	public String getRserverHost() {
		return rserverHost;
	}
	public void setRserverHost(String rserverHost) {
		this.rserverHost = rserverHost;
	}
	public int getForwardPort() {
		return forwardPort;
	}
	public void setForwardPort(int forwardPort) {
		this.forwardPort = forwardPort;
	}
	public TunnelStatus getStatus() {
		return status;
	}
	public void setStatus(TunnelStatus status) {
		this.status = status;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ClientTunnelStatusChangedEvent[rTcpHost=");
		sb.append(rTcpHost);
		sb.append(",rTcpPort="+rTcpPort);
		sb.append(",rserverHost=");
		sb.append(rserverHost);
		sb.append(",forwardPort="+forwardPort);
		sb.append(",status=");
		sb.append(status.toString());
		sb.append("]");
		return sb.toString();
	}
}
