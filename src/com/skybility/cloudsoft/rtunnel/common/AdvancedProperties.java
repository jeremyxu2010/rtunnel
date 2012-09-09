package com.skybility.cloudsoft.rtunnel.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

public class AdvancedProperties extends DictImpl implements Dict {

	private static final AdvancedProperties instance;
	static {
		String config = System.getProperty("conf.path");
		if (config == null) {
			throw new RuntimeException("config file path not set");
		}
		Properties props = new Properties();
		InputStream is = null;
		try {
			is = new FileInputStream(config);
			props.load(is);
			instance = new AdvancedProperties(props);
		} catch (IOException e) {
			throw new RuntimeException("load config file " + config + " failed");
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	public static AdvancedProperties getInstance() {
		return instance;
	}

    @SuppressWarnings("rawtypes")
    private AdvancedProperties(Map map) {
		super(map);
	}
}
