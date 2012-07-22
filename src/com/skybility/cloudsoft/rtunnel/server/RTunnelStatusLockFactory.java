package com.skybility.cloudsoft.rtunnel.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RTunnelStatusLockFactory {
	
	private static ConcurrentMap<String, Object> map = new ConcurrentHashMap<String, Object>();
	
	public static Object getRTunnelStatusLock(String ctNo){
		if(!map.containsKey(ctNo)){
			map.putIfAbsent(ctNo, new Object());
		}
		return map.get(ctNo);
	}
}
