package com.skybility.cloudsoft.rtunnel.common;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

public class NetworkInterfaceHelper {
	public static final String IPVERSION_IPV4 = "ipv4";
	public static final String IPVERSION_IPV6 = "ipv6";

	public static InetAddress getInetAddress(String netIfName, String ipVersion) throws IOException{
		try {
			Enumeration<NetworkInterface> nets = NetworkInterface
					.getNetworkInterfaces();
			for (NetworkInterface netIf : Collections.list(nets)) {
				if(netIfName.equals(netIf.getName()) && netIf.isUp()){
					Enumeration<InetAddress> inetAddresses = netIf
							.getInetAddresses();
					if(IPVERSION_IPV4.equals(ipVersion)){
						while (inetAddresses.hasMoreElements()) {
							InetAddress inetAddr = inetAddresses.nextElement();
							if (inetAddr instanceof Inet4Address) {
								return inetAddr;
							}
						}
					} else if (IPVERSION_IPV6.equals(ipVersion)){
						while (inetAddresses.hasMoreElements()) {
							InetAddress inetAddr = inetAddresses.nextElement();
							if (inetAddr instanceof Inet6Address) {
								return inetAddr;
							}
						}
					}
				}
			}
		} catch (SocketException e) {
			throw new SocketException("Unresolved address");
		}
		throw new SocketException("Unresolved address");
	}
}
