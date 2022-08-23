package com.gwb.proxyservice;

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.gwb.proxyservice.model.ProxyServiceSetting;
import com.gwb.proxyservice.model.ProxyServletSetting;
import com.gwb.proxyservice.servlet.MyProxyServlet;
import com.gwb.proxyservice.util.ProxyConfigUtility;


public class App {
	public static void main(String[] args) throws Exception {

		ProxyServiceSetting serviceSetting = ProxyConfigUtility.readProxyServiceConfig("proxySetting.xml");

		Server server = new Server(serviceSetting.getPort());
		ServletContextHandler servletContextHandler = new ServletContextHandler(server, serviceSetting.getBasePath());
		for (ProxyServletSetting setting : serviceSetting.getServletSettings()) {
			HttpServlet proxyServlet = new MyProxyServlet();
			ServletHolder servletHolder = new ServletHolder();
			servletHolder.setServlet(proxyServlet);
			servletHolder.setInitParameter("targetUri", setting.getTargetUrl());
			servletContextHandler.addServlet(servletHolder, setting.getProxyUrl());
		}

		server.start();
		System.out.println("反向代理免登录服务启动完成！");
	}


}
