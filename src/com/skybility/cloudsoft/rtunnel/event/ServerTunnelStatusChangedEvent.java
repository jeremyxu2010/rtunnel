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
 
public class ServerTunnelStatusChangedEvent {
	private int forwardPort;
	private TunnelStatus status;
	
	public ServerTunnelStatusChangedEvent(int forwardPort, TunnelStatus status) {
	    super();
	    this.forwardPort = forwardPort;
	    this.status = status;
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
		return "ServerTunnelStatusChangedEvent[forwardPort="+forwardPort+",status="+status+"]";
	}
}
