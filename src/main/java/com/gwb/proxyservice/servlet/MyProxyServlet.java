package com.gwb.proxyservice.servlet;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.mitre.dsmiley.httpproxy.ProxyServlet;

public class MyProxyServlet extends ProxyServlet{

	@Override
	protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
			HttpRequest proxyRequest) throws IOException {
		HttpResponse response = super.doExecute(servletRequest, servletResponse, proxyRequest);
		String origin = servletRequest.getHeader("origin");
		response.setHeader("Access-Control-Allow-Origin",origin);
		response.setHeader("Access-Control-Allow-Credentials","true");
		response.setHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");
		response.setHeader("Access-Control-Allow-Headers",
				"Accept,Authorization,Cache-Control,Content-Type,DNT,If-Modified-Since,Keep-Alive,Origin,User-Agent,X-Mx-ReqToken,X-Requested-With");

		return response;
	}





}
