package com.skybility.cloudsoft.rtunnel.common;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTunnelProperties {
	private static Properties props = new Properties();
	private static String propsFile = "/rtunnel.properties"; 
	private static Logger logger = LoggerFactory.getLogger(RTunnelProperties.class);
	static{
		try {
			props.load(RTunnelProperties.class.getResourceAsStream(propsFile));
		} catch (IOException e) {
			logger.error("load properties file fails. " + e);
			System.exit(1);
		}
	}
	
	public static int getIntegerProperty(String name){
		String s_prop = (String)props.get(name);
		int prop = -1;
		if(s_prop != null){
			try {
				prop = Integer.parseInt(s_prop.trim());
			} catch (NumberFormatException e) {
				logger.error(String.format("parse %s property fails. %s", name, e.toString()));
			}
		}
		return prop;
	}
	
	public static String getStringProperty(String name){
		String prop = (String)props.get(name);
		if(prop != null && prop.trim().length() > 0){
			return prop.trim();
		} else {
			return null;
		}
	}
	
}
