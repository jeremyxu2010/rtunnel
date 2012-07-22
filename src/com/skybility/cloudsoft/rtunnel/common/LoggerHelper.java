package com.skybility.cloudsoft.rtunnel.common;

import org.slf4j.MDC;

public class LoggerHelper {
	public static final String RTUNNEL_ID = "rtunnelid";
	
	private static String CLIENT_LOGGER_PREFIX = "client_";
	private static String SERVER_LOGGER_PREFIX = "server_";

	/**
	 * Adds the test name to MDC so that sift appender can use it and log the
	 * new log events to a different file
	 * 
	 * @param rtunnelid
	 *            name of the new log file
	 * @throws Exception
	 */
	public static void startLogging(String rtunnelid){
		MDC.put(RTUNNEL_ID, rtunnelid);
	}

	/**
	 * Removes the key (log file name) from MDC
	 * 
	 * @return name of the log file, if one existed in MDC
	 */
	public static String stopLogging() {
		String name = MDC.get(RTUNNEL_ID);
		MDC.remove(RTUNNEL_ID);
		return name;
	}
	
	public static String generateClientLogRTunnelid(int forwardPort){
		return CLIENT_LOGGER_PREFIX + "_" + forwardPort;
	}
	
	public static String generateServerLogRTunnelid(int forwardPort){
		return SERVER_LOGGER_PREFIX + "_" + forwardPort;
	}
}
