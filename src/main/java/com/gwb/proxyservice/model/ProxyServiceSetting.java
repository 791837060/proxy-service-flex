package com.gwb.proxyservice.model;

import java.util.ArrayList;
import java.util.List;

public class ProxyServiceSetting {

	private int port = 8333;
	private String basePath;
	private List<ProxyServletSetting> servletSettings = new ArrayList();
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public List<ProxyServletSetting> getServletSettings() {
		return servletSettings;
	}
	public void setServletSettings(List<ProxyServletSetting> servletSettings) {
		this.servletSettings = servletSettings;
	}
	public String getBasePath() {
		return basePath;
	}
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}


}
