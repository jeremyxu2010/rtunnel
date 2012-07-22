package com.skybility.cloudsoft.rtunnel.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RTunnelSocketFactory {
	
	public static ServerSocket getServerSocket(SocketType type, String forwardBindAddress,  int port) throws IOException{
		if(SocketType.TCP.equals(type)){
			ServerSocket serverSocket = new ServerSocket();
			serverSocket.bind(new InetSocketAddress(InetAddress.getByName(forwardBindAddress), port));
			return serverSocket;
		} else {
			//TODO
			return null;
		}
	}
	
	public static ServerSocket getServerSocket(SocketType type, int port) throws IOException{
		if(SocketType.TCP.equals(type)){
			ServerSocket serverSocket = new ServerSocket(port);
			return serverSocket;
		} else {
			//TODO
			return null;
		}
	}
	
	public static Socket getSocket(SocketType type, String serverHost, int serverPort, InetAddress localBindAddress, int localBindPort) throws IOException{
		if(SocketType.TCP.equals(type)){
			return new Socket(serverHost, serverPort, localBindAddress, localBindPort);
		} else {
			//TODO
			return null;
		}
	}

}
