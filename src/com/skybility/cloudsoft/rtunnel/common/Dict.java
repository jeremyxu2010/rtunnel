package com.skybility.cloudsoft.rtunnel.common;

import java.util.Map;
/**
 * 装饰一个Map，提供便利方法获取值，所有getXXX方法均返回对象，可能是null，所有require均会返回非null的值，如果值是null，会抛出异常。
 * @author atlas
 *
 */
public interface Dict {
	
	<T> T get(String key,T defaultValue);

	<T> T get(String key);

	String getAsString(String key);

	Integer getAsInteger(String key);

	Long getAsLong(String key);

	Double getAsDouble(String key);

	Boolean getAsBoolean(String key);

	String requireString(String key);

	<T> T require(String key);

	int requireInteger(String key);

	long requireLong(String key);

	double requireDouble(String key);

	Boolean requireBoolean(String key);

	@SuppressWarnings("rawtypes")
    Map getMap();
}
