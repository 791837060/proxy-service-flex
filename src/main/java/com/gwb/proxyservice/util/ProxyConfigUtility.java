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

	//String targetUrl = "https://gridcloud.91cpct.com/stage-api";


	// 包含ulhttp则无效  如 ulhttps://webapp.91cpct.com

	//public static String targetUrl = "https://v2ppapi.coinflex.com";
	//public static String targetUrl = "https://v2ppapi.coinflex.com";

	//没有用的字段  uselessfield

	//cookie:
	///public static String x_cf_token_cookie = "eyJhbGciOiJIUzI1NiJ9.eyJhY2NvdW50SWQiOiIxMDEyOSIsImxvZ2luSWQiOiI2MzUzNTgyNCIsImlhdCI6MTY1MDAwNDAxMiwiZXhwIjoxNjUwMDQ3MjEyfQ.e45WXgfL3sFJzc0i-2PGRYJNmaSkuNKqepjKDd82gnkcookie: hubspotutk=b1f66c24224a91e1e3cfe3ac5dc99f01; __hs_opt_out=no; _fbp=fb.1.1636106597800.1519054498; _ga=GA1.2.286209416.1639123413; __cf_bm=VBGDZFM.XpYp4hJGrt97d4bFaCwsoaWpLu7B4BOXF9Q-1650003984-0-AVZj7/+H6RhxbBYZ5RSO8M3uJ5mw7wsIexBPxEDkzU7HlSx4Zu6I0QlP58uqnbgpoYOQwD5ocrkO0xEAhp5uMxEIsfbQ6hEu1JiR3nsATNwfCAS7aGkcGzmGA71w/CUJAg==; __hstc=30769248.b1f66c24224a91e1e3cfe3ac5dc99f01.1636106544441.1649929094667.1650003985557.91; __hssrc=1; __hssc=30769248.1.1650003985557; csrftoken=4a23ca59f63a4a4d88c38810d5fa3ab8";

/*

	public static String targetUrl = "https://v2stgapi.coinflex.com";
	public static String x_cf_token_cookie = "eyJhbGciOiJIUzI1NiJ9.eyJhY2NvdW50SWQiOiIxMDEyOSIsImxvZ2luSWQiOiI2MzUzNTgyNCIsImlhdCI6MTY1MDAwNDAxMiwiZXhwIjoxNjUwMDQ3MjEyfQ.e45WXgfL3sFJzc0i-2PGRYJNmaSkuNKqepjKDd82gnkcookie: hubspotutk=b1f66c24224a91e1e3cfe3ac5dc99f01; __hs_opt_out=no; _fbp=fb.1.1636106597800.1519054498; _ga=GA1.2.286209416.1639123413; __cf_bm=VBGDZFM.XpYp4hJGrt97d4bFaCwsoaWpLu7B4BOXF9Q-1650003984-0-AVZj7/+H6RhxbBYZ5RSO8M3uJ5mw7wsIexBPxEDkzU7HlSx4Zu6I0QlP58uqnbgpoYOQwD5ocrkO0xEAhp5uMxEIsfbQ6hEu1JiR3nsATNwfCAS7aGkcGzmGA71w/CUJAg==; __hstc=30769248.b1f66c24224a91e1e3cfe3ac5dc99f01.1636106544441.1649929094667.1650003985557.91; __hssrc=1; __hssc=30769248.1.1650003985557; csrftoken=4a23ca59f63a4a4d88c38810d5fa3ab8";
*/

/*

	public static String targetUrl = "http://api-durian.cfdev.pro";
	public static String x_cf_token_cookie = "eyJhbGciOiJIUzI1NiJ9.eyJhY2NvdW50SWQiOiIxMDEyOSIsImxvZ2luSWQiOiI2MzUzNTgyNCIsImlhdCI6MTY1MDAwNDAxMiwiZXhwIjoxNjUwMDQ3MjEyfQ.e45WXgfL3sFJzc0i-2PGRYJNmaSkuNKqepjKDd82gnkcookie: hubspotutk=b1f66c24224a91e1e3cfe3ac5dc99f01; __hs_opt_out=no; _fbp=fb.1.1636106597800.1519054498; _ga=GA1.2.286209416.1639123413; __cf_bm=VBGDZFM.XpYp4hJGrt97d4bFaCwsoaWpLu7B4BOXF9Q-1650003984-0-AVZj7/+H6RhxbBYZ5RSO8M3uJ5mw7wsIexBPxEDkzU7HlSx4Zu6I0QlP58uqnbgpoYOQwD5ocrkO0xEAhp5uMxEIsfbQ6hEu1JiR3nsATNwfCAS7aGkcGzmGA71w/CUJAg==; __hstc=30769248.b1f66c24224a91e1e3cfe3ac5dc99f01.1636106544441.1649929094667.1650003985557.91; __hssrc=1; __hssc=30769248.1.1650003985557; csrftoken=4a23ca59f63a4a4d88c38810d5fa3ab8";
*/


/*

	public static String targetUrl = "http://api-kiwi.cfdev.pro";
	public static String x_cf_token_cookie = "eyJhbGciOiJIUzI1NiJ9.eyJhY2NvdW50SWQiOiIxMDEyOSIsImxvZ2luSWQiOiI2MzUzNTgyNCIsImlhdCI6MTY1MDAwNDAxMiwiZXhwIjoxNjUwMDQ3MjEyfQ.e45WXgfL3sFJzc0i-2PGRYJNmaSkuNKqepjKDd82gnkcookie: hubspotutk=b1f66c24224a91e1e3cfe3ac5dc99f01; __hs_opt_out=no; _fbp=fb.1.1636106597800.1519054498; _ga=GA1.2.286209416.1639123413; __cf_bm=VBGDZFM.XpYp4hJGrt97d4bFaCwsoaWpLu7B4BOXF9Q-1650003984-0-AVZj7/+H6RhxbBYZ5RSO8M3uJ5mw7wsIexBPxEDkzU7HlSx4Zu6I0QlP58uqnbgpoYOQwD5ocrkO0xEAhp5uMxEIsfbQ6hEu1JiR3nsATNwfCAS7aGkcGzmGA71w/CUJAg==; __hstc=30769248.b1f66c24224a91e1e3cfe3ac5dc99f01.1636106544441.1649929094667.1650003985557.91; __hssrc=1; __hssc=30769248.1.1650003985557; csrftoken=4a23ca59f63a4a4d88c38810d5fa3ab8";
*/








//public static String targetUrlLocal = "http://192.168.0.100:8403";

	/*


	 redis-cli -h 127.0.0.1 -p 6379 -a Credit2016Admin set targetUrlTest http://192.168.0.100:8403
	 redis-cli -h 127.0.0.1 -p 6379 -a Credit2016Admin set appLogin http://192.168.0.100:8403/app-token/api/authenticate


	 redis-cli -h 127.0.0.1 -p 6379 -a Credit2016Admin set targetUrlTest https://webapp.91cpct.com
	 redis-cli -h 127.0.0.1 -p 6379 -a Credit2016Admin set appLogin https://webapp.91cpct.com/app-token/api/authenticate

*/


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
