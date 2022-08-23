package com.gwb.proxyservice.model;

public class ProxyServletSetting {

	private String proxyUrl;
	private String targetUrl;
	public String getProxyUrl() {
		return proxyUrl;
	}
	public void setProxyUrl(String proxyUrl) {
		this.proxyUrl = proxyUrl;
	}
	public String getTargetUrl() {
		return targetUrl;
	}
	public void setTargetUrl(String targetUrl) {
		this.targetUrl = targetUrl;
	}

	@Override
	public String toString() {

		return String.format("proxyUrl:%s,targetUrl:%s", proxyUrl,targetUrl);
	}

}
