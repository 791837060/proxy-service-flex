package com.gwb.proxyservice.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.gwb.proxyservice.model.ProxyServiceSetting;
import com.gwb.proxyservice.model.ProxyServletSetting;
import redis.clients.jedis.*;

public final class ProxyConfigUtility {



	private ProxyConfigUtility() {

	}
	private static final String ELEMENT_DEFAULT_SETTING = "defaultSettings";
	private static final String ELEMENT_PORT = "port";
	private static final String ELEMENT_BASEPATH = "basePath";
	private static final String ELEMENT_PROXYSERVICES = "ProxyServices";
	private static final String ELEMENT_PROXYURL = "proxyUrl";
	private static final String ELEMENT_TARGETURL = "targetUrl";


	public static ShardedJedisPool pool;
	static {
		JedisPoolConfig config = new JedisPoolConfig();
		config.setMaxTotal(100);
		config.setMaxIdle(50);
		config.setMaxWaitMillis(3000);
		config.setTestOnBorrow(true);
		config.setTestOnReturn(true);
		// 集群
		JedisShardInfo jedisShardInfo1 = new JedisShardInfo("127.0.0.1", 6379);
		jedisShardInfo1.setPassword("Credit2016Admin");
		List<JedisShardInfo> list = new LinkedList<JedisShardInfo>();
		list.add(jedisShardInfo1);
		pool = new ShardedJedisPool(config, list);
	}

	public static ProxyServiceSetting readProxyServiceConfig(String uri) throws DocumentException {
		ProxyServiceSetting serviceSetting = new ProxyServiceSetting();

		String baseUri = ProxyConfigUtility.class.getResource("/").getPath();
		String filePath = baseUri + uri;
		// 创建saxReader对象
        SAXReader reader = new SAXReader();
        // 通过read方法读取一个文件 转换成Document对象
        Document doc = reader.read(new File(filePath));
        Element root = doc.getRootElement();
        System.err.println(root.getName());
		Element defaultSetting = root.element(ELEMENT_DEFAULT_SETTING);
		int port = Integer.parseInt(defaultSetting.element(ELEMENT_PORT).getTextTrim());
		String basePath = defaultSetting.elementTextTrim(ELEMENT_BASEPATH);

		Element servlets = root.element(ELEMENT_PROXYSERVICES);
		Iterator<Element> servletIterator = servlets.elementIterator();
		List<ProxyServletSetting> servletSettings = new ArrayList();
		while (servletIterator.hasNext()) {
			ProxyServletSetting servletSetting = new ProxyServletSetting();
			Element servlet = servletIterator.next();
			String proxyUrl = servlet.elementTextTrim(ELEMENT_PROXYURL);
			/*//创建redis核心对象
			ShardedJedis jedis = pool.getResource();
			String targetUrlTest = jedis.get("targetUrlTest");
			//取值并打印
			System.out.println(targetUrlTest);
			//释放资源
			jedis.close();*/

			//String targetUrl = targetUrlPp;


			/*if(targetUrlTest.contains("ulhttp")){
				targetUrl =targetUrlLocal;
			}*/


			String targetUrl = servlet.elementTextTrim(ELEMENT_TARGETURL);
			servletSetting.setProxyUrl(proxyUrl);
			servletSetting.setTargetUrl(targetUrl);

			servletSettings.add(servletSetting);

		}

        serviceSetting.setPort(port);
        serviceSetting.setBasePath(basePath);
        serviceSetting.setServletSettings(servletSettings);

		return serviceSetting;
	}

}
