/*
 * Copyright MITRE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mitre.dsmiley.httpproxy;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gwb.proxyservice.util.ProxyConfigUtility;
import com.sun.jmx.snmp.ThreadContext;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.brotli.dec.BrotliInputStream;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * An HTTP reverse proxy/gateway servlet. It is designed to be extended for
 * customization
 * if desired. Most of the work is handled by
 * <a href="http://hc.apache.org/httpcomponents-client-ga/">Apache
 * HttpClient</a>.
 * <p>
 * There are alternatives to a servlet based proxy such as Apache mod_proxy if
 * that is available to you. However
 * this servlet is easily customizable by Java, secure-able by your web
 * application's security (e.g. spring-security),
 * portable across servlet engines, and is embeddable into another web
 * application.
 * </p>
 * <p>
 * Inspiration: http://httpd.apache.org/docs/2.0/mod/mod_proxy.html
 * </p>
 *
 * @author David Smiley dsmiley@apache.org
 */
@SuppressWarnings({ "deprecation", "serial" })
@Slf4j
public class ProxyServlet extends HttpServlet {

  /* INIT PARAMETER NAME CONSTANTS */

  /**
   * A boolean parameter name to enable logging of input and target URLs to the
   * servlet log.
   */
  public static final String P_LOG = "log";

  /** A boolean parameter name to enable forwarding of the client IP */
  public static final String P_FORWARDEDFOR = "forwardip";

  /** A boolean parameter name to keep HOST parameter as-is */
  public static final String P_PRESERVEHOST = "preserveHost";

  /** A boolean parameter name to keep COOKIES as-is */
  public static final String P_PRESERVECOOKIES = "preserveCookies";

  /** A boolean parameter name to have auto-handle redirects */
  public static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects"; // ClientPNames.HANDLE_REDIRECTS

  /** A integer parameter name to set the socket connection timeout (millis) */
  public static final String P_CONNECTTIMEOUT = "http.socket.timeout"; // CoreConnectionPNames.SO_TIMEOUT

  /** A integer parameter name to set the socket read timeout (millis) */
  public static final String P_READTIMEOUT = "http.read.timeout";

  /**
   * A boolean parameter whether to use JVM-defined system properties to configure
   * various networking aspects.
   */
  public static final String P_USESYSTEMPROPERTIES = "useSystemProperties";

  /** The parameter name for the target (destination) URI to proxy to. */
  protected static final String P_TARGET_URI = "targetUri";
  protected static final String ATTR_TARGET_URI = ProxyServlet.class.getSimpleName() + ".targetUri";
  protected static final String ATTR_TARGET_HOST = ProxyServlet.class.getSimpleName() + ".targetHost";

  /* MISC */

  protected boolean doLog = false;
  protected boolean doForwardIP = true;
  /** User agents shouldn't send the url fragment but what if it does? */
  protected boolean doSendUrlFragment = true;
  protected boolean doPreserveHost = false;
  // ul temp protected boolean doPreserveCookies = false;
  protected boolean doPreserveCookies = true;
  protected boolean doHandleRedirects = false;
  protected boolean useSystemProperties = false;
  protected int connectTimeout = -1;
  protected int readTimeout = -1;

  // These next 3 are cached here, and should only be referred to in
  // initialization logic. See the
  // ATTR_* parameters.
  /** From the configured parameter "targetUri". */
  protected String targetUri;
  protected URI targetUriObj;// new URI(targetUri)
  protected HttpHost targetHost;// URIUtils.extractHost(targetUriObj);

  private HttpClient proxyClient;

  @Override
  public String getServletInfo() {
    return "A proxy servlet by David Smiley, dsmiley@apache.org";
  }

  protected String getTargetUri(HttpServletRequest servletRequest) {
    return (String) servletRequest.getAttribute(ATTR_TARGET_URI);
  }

  protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
    return (HttpHost) servletRequest.getAttribute(ATTR_TARGET_HOST);
  }

  /**
   * Reads a configuration parameter. By default it reads servlet init parameters
   * but
   * it can be overridden.
   */
  protected String getConfigParam(String key) {
    return getServletConfig().getInitParameter(key);
  }

  @Override
  public void init() throws ServletException {
    String doLogStr = getConfigParam(P_LOG);
    if (doLogStr != null) {
      this.doLog = Boolean.parseBoolean(doLogStr);
    }

    String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
    if (doForwardIPString != null) {
      this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
    }

    String preserveHostString = getConfigParam(P_PRESERVEHOST);
    if (preserveHostString != null) {
      this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
    }

    String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
    if (preserveCookiesString != null) {
      this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
    }

    String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
    if (handleRedirectsString != null) {
      this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
    }

    String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
    if (connectTimeoutString != null) {
      this.connectTimeout = Integer.parseInt(connectTimeoutString);
    }

    String readTimeoutString = getConfigParam(P_READTIMEOUT);
    if (readTimeoutString != null) {
      this.readTimeout = Integer.parseInt(readTimeoutString);
    }

    String useSystemPropertiesString = getConfigParam(P_USESYSTEMPROPERTIES);
    if (useSystemPropertiesString != null) {
      this.useSystemProperties = Boolean.parseBoolean(useSystemPropertiesString);
    }

    initTarget();// sets target*

    proxyClient = createHttpClient(buildRequestConfig());
  }

  /**
   * Sub-classes can override specific behaviour of {@link RequestConfig}.
   */
  protected RequestConfig buildRequestConfig() {
    return RequestConfig.custom()
        .setRedirectsEnabled(doHandleRedirects)
        .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
        .setConnectTimeout(connectTimeout)
        .setSocketTimeout(readTimeout)
        .build();
  }

  protected void initTarget() throws ServletException {
    targetUri = getConfigParam(P_TARGET_URI);
    if (targetUri == null) {
      throw new ServletException(P_TARGET_URI + " is required.");
    }
    // test it's valid
    try {
      targetUriObj = new URI(targetUri);
    } catch (Exception e) {
      throw new ServletException("Trying to process targetUri init parameter: " + e, e);
    }
    targetHost = URIUtils.extractHost(targetUriObj);
  }

  /**
   * Called from {@link #init(javax.servlet.ServletConfig)}.
   * HttpClient offers many opportunities for customization.
   * In any case, it should be thread-safe.
   */
  protected HttpClient createHttpClient(final RequestConfig requestConfig) {
    HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig);
    if (useSystemProperties)
      clientBuilder = clientBuilder.useSystemProperties();
    return clientBuilder.build();
  }

  /**
   * The http client used.
   * 
   * @see #createHttpClient(RequestConfig)
   */
  protected HttpClient getProxyClient() {
    return proxyClient;
  }

  @Override
  public void destroy() {
    // Usually, clients implement Closeable:
    if (proxyClient instanceof Closeable) {
      try {
        ((Closeable) proxyClient).close();
      } catch (IOException e) {
        log("While destroying servlet, shutting down HttpClient: " + e, e);
      }
    } else {
      // Older releases require we do this:
      if (proxyClient != null) {
        proxyClient.getConnectionManager().shutdown();
      }
    }
    super.destroy();
  }

  @Override
  protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
      throws ServletException, IOException {
    // initialize request attributes from caches if unset by a subclass by this
    // point
    if (servletRequest.getAttribute(ATTR_TARGET_URI) == null) {
      servletRequest.setAttribute(ATTR_TARGET_URI, targetUri);
    }
    if (servletRequest.getAttribute(ATTR_TARGET_HOST) == null) {
      servletRequest.setAttribute(ATTR_TARGET_HOST, targetHost);
    }

    // Make the Request
    // note: we won't transfer the protocol version because I'm not sure it would
    // truly be compatible
    String method = servletRequest.getMethod();
    String proxyRequestUri = rewriteUrlFromRequest(servletRequest);
    HttpRequest proxyRequest;
    // spec: RFC 2616, sec 4.3: either of these two headers signal that there is a
    // message body.
    if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
        servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
      proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
    } else {
      proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
    }

    copyRequestHeaders(servletRequest, proxyRequest);

    setXForwardedForHeader(servletRequest, proxyRequest);

    HttpResponse proxyResponse = null;
    try {
      // Execute the request

      proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

      InputStream inputStream = logPrint(proxyResponse, servletRequest);

      // Process the response:

      // Pass the response code. This method with the "reason phrase" is deprecated
      // but it's the
      // only way to pass the reason along too.
      int statusCode = proxyResponse.getStatusLine().getStatusCode();
      // noinspection deprecation
      servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

      // Copying response headers to make sure SESSIONID or other Cookie which comes
      // from the remote
      // server will be saved in client when the proxied url was redirected to another
      // one.
      // See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
      copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

      if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
        // 304 needs special handling. See:
        // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
        // Don't send body entity/content!
        servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
      } else {
        // Send the content to the client
        // copyResponseEntity(proxyResponse, servletResponse, proxyRequest,
        // servletRequest);
        copyResponseEntityUl(inputStream, servletResponse, proxyRequest, servletRequest);
      }

      // 设置跨域，暂时不用。
      // String origin = servletRequest.getHeader("origin");
      servletResponse.setHeader("Access-Control-Allow-Origin", "*");
      servletResponse.setHeader("Access-Control-Allow-Credentials", "true");
      servletResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
      servletResponse.setHeader("Access-Control-Allow-Headers", "*");

    } catch (Exception e) {
      handleRequestException(proxyRequest, e);
    } finally {
      // make sure the entire entity was consumed, so the connection is released
      if (proxyResponse != null) {
        EntityUtils.consumeQuietly(proxyResponse.getEntity());
      }
      // Note: Don't need to close servlet outputStream:
      // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
    }
  }

  /**
   * 格式化输出JSON字符串
   * 
   * @return 格式化后的JSON字符串
   */
  private static String toPrettyFormat(String json) {
    if (json == null || "".equals(json)) {
      json = "{\"msg\":\"返回空\"}";
    }
    JsonParser jsonParser = new JsonParser();
    try {
      JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(jsonObject);
    } catch (Exception e) {
      // TODO: handle exception
    }
    return json;
  }

  private InputStream logPrint(HttpResponse proxyResponse, HttpServletRequest servletRequest) throws IOException {
    HttpEntity entity = proxyResponse.getEntity();

    InputStream in = entity.getContent();// 获取数据流

    // 保存流
    ByteArrayOutputStream bosSave = new ByteArrayOutputStream();
    BufferedInputStream br = new BufferedInputStream(in);
    byte[] b = new byte[1024];
    for (int c = 0; (c = br.read(b)) != -1;) {
      bosSave.write(b, 0, c);
    }
    b = null;
    br.close();

    in = new ByteArrayInputStream(bosSave.toByteArray());

    BufferedReader bufferedReader = null;
    if (proxyResponse.getLastHeader("content-encoding") != null &&
        proxyResponse.getLastHeader("content-encoding").getValue() != null &&
        proxyResponse.getLastHeader("content-encoding").getValue().equals("br")) {// check if getting brotli compressed
                                                                                  // stream
      bufferedReader = new BufferedReader(new InputStreamReader(new BrotliInputStream(in)));
    } else {
      bufferedReader = new BufferedReader(new InputStreamReader(in));
    }
    StringBuilder result = new StringBuilder();
    String str = null;
    while ((str = bufferedReader.readLine()) != null) {
      // System.out.println(str);
      result.append(str);
    }

    String resultJson = toPrettyFormat(result.toString());
    twoPrint("响应结果: " + resultJson);
    twoPrint("");

    twoPrint("可在终端运行的curl: ");
    // String curl2 = "curl --location --request "+requestMethod+"
    // '"+realRequestURL+"' --header 'content-type: application/json;charset=UTF-8'
    // --header 'x-cf-token: "+token+"' --header 'authorization: "+authorization+"'
    // --data-raw '"+requestBody+"'";
    String realRequestURL1 = "'" + realRequestURL + "'";
    String token1 = "'x-cf-token: " + token + "'";
    String cookie1 = "'cookie: " + cookie + "'";
    String authorization1 = "'authorization: " + authorization + "'";
    String requestBody1 = "'" + requestBody + "'";
    String curl2 = "curl " + realRequestURL1 + " \\\n"
    // + " -H 'pragma: no-cache' \\\n"
    // + " -H 'cache-control: no-cache' \\\n"
    // + " -H 'sec-ch-ua: \" Not A;Brand\";v=\"99\", \"Chromium\";v=\"99\", \"Google
    // Chrome\";v=\"99\"' \\\n"
    // + " -H 'accept: application/json, text/plain, */*' \\\n"
    // + " -H 'sec-ch-ua-mobile: ?0' \\\n"
        + "  -H 'content-type: application/json;charset=UTF-8' \\\n"
        // + " -H 'user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)
        // AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.83 Safari/537.36'
        // \\\n"
        // + " -H 'sec-ch-ua-platform: \"macOS\"' \\\n"
        // + " -H 'sec-fetch-site: same-site' \\\n"
        // + " -H 'sec-fetch-mode: cors' \\\n"
        // + " -H 'sec-fetch-dest: empty' \\\n"
        // + " -H 'accept-language: zh-CN,zh;q=0.9' \\\n"

        // + " -H "+cookie1+" \\\n"
        // + " -H "+token1+" \\\n"
        // + " -H "+authorization1+" \\\n";

        + "";
    if (!requestMethod.equals("GET")) {
      curl2 = curl2 + "  --data-raw " + requestBody1 + " \\\n";
    }
    curl2 = curl2 + "  --compressed";

    twoPrint(curl2);
    twoPrint("");

    // String curl3 = "curl --location --request POST '"+requestURL+"' --header
    // 'content-type: application/json;charset=UTF-8' --data-raw '"+requestBody+"'";
    // twoPrint(curl3);
    // twoPrint("");

    // RequestWrapper reqestWrapper = new
    // RequestWrapper((HttpServletRequest)servletRequest);
    // String curl = getCurl(reqestWrapper).replace(requestURL, realRequestURL);
    // twoPrint(curl);
    // twoPrint("");

    if (!requestMethod.equals("GET")) {
      // twoPrint("okPost:" +okPost(realRequestURL,requestBody,token,cookie));
      // twoPrint("okPost:" +okPost(realRequestURL,requestBody,token2,cookie2));
    } else {
      // twoPrint("okGet:" + okGet(realRequestURL,requestBody,token,cookie));
      // twoPrint("okGet:" + okGet(realRequestURL,requestBody,token2,cookie2));
    }

    twoPrint("");
    twoPrint(
        "_________________end________________________end________________________end________________________end________________________end_______ ");
    // 重置游标
    in.reset();
    return in;
  }

  public String okGet(String url, String requestBody, String token, String cookie) {
    OkHttpClient client = new OkHttpClient().newBuilder()
        .build();
    Request request = new Request.Builder()
        .url(url)
        .method("GET", null)
        .addHeader("Cookie", "locale=en-US; locale=zh-CN")
        .addHeader("x-cf-token", token)
        .addHeader("cookie", cookie)
        .build();
    Response response;
    try {
      response = client.newCall(request).execute();
      String responseBody = response.body().string();
      // esponseBody:{"data":{"pair":null}}
      log.info("responseBody:{} token:{}", responseBody, token);
      return responseBody;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return "{}";
  }

  public String okPost(String url, String requestBody, String token, String cookie) {
    OkHttpClient client = new OkHttpClient().newBuilder()
        .build();
    MediaType mediaType = MediaType.parse("application/json");
    RequestBody body = RequestBody.create(mediaType, requestBody);
    Request request = new Request.Builder()
        .url(url)
        .method("POST", body)
        .addHeader("x-cf-token", token)
        .addHeader("cookie", cookie)
        .addHeader("accept", "*/*")
        .addHeader("accept-language", "zh-CN,zh;q=0.9")
        .addHeader("cache-control", "no-cache")
        .addHeader("content-type", "application/json")
        .addHeader("pragma", "no-cache")
        .addHeader("sec-ch-ua", "\".Not/A)Brand\";v=\"99\", \"Google Chrome\";v=\"103\", \"Chromium\";v=\"103\"")
        .addHeader("sec-ch-ua-mobile", "?0")
        .addHeader("sec-ch-ua-platform", "\"macOS\"")
        .addHeader("sec-fetch-dest", "empty")
        .addHeader("sec-fetch-mode", "cors")
        .addHeader("sec-fetch-site", "same-site")
        .addHeader("user-agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36")
        .build();
    String responseBody = "{}";
    try {
      Response response = client.newCall(request).execute();
      responseBody = response.body().string();
      // responseBody:{"data":{"pair":null}}
      log.info("responseBody:{} token:{}", responseBody, token);

    } catch (IOException e) {
      log.error("PancakeSwapServiceImpl findPoolByPoolAddress", e);
      return null;
    }
    return responseBody;
  }

  protected void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
    // abort request, according to best practice with HttpClient
    if (proxyRequest instanceof AbortableHttpRequest) {
      AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
      abortableHttpRequest.abort();
    }
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    if (e instanceof ServletException) {
      throw (ServletException) e;
    }
    // noinspection ConstantConditions
    if (e instanceof IOException) {
      throw (IOException) e;
    }
    throw new RuntimeException(e);
  }

  protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
      HttpRequest proxyRequest) throws IOException {
    if (doLog) {
      log("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
          proxyRequest.getRequestLine().getUri());
    }
    return proxyClient.execute(getTargetHost(servletRequest), proxyRequest);
  }

  protected void doExecuteLog(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
      HttpRequest proxyRequest) throws IOException {
    if (doLog) {
      log("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
          proxyRequest.getRequestLine().getUri());
    }
    ResponseHandler<String> responseHandler = new BasicResponseHandler();
    String responseBody = proxyClient.execute(getTargetHost(servletRequest), proxyRequest, responseHandler);
    String toJSONString = JSON.toJSONString(responseBody);
    System.out.println("responseBody:" + toJSONString);
  }

  String parJson = "";

  protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
      HttpServletRequest servletRequest)
      throws IOException {
    HttpEntityEnclosingRequest eProxyRequest = new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
    // Add the input entity (streamed)
    // note: we don't bother ensuring we close the servletInputStream since the
    // container handles it

    RequestWrapperUl requestWrapper = new RequestWrapperUl(servletRequest);
    String par = HttpServletRequestReader.ReadAsChars(requestWrapper);

    parJson = toPrettyFormat(par.toString());
    InputStreamEntity inputStreamEntity = new InputStreamEntity(requestWrapper.getInputStream(),
        getContentLength(requestWrapper));
    eProxyRequest.setEntity(inputStreamEntity);
    return eProxyRequest;
  }

  // Get the header value as a long in order to more correctly proxy very large
  // requests
  private long getContentLength(HttpServletRequest request) {
    String contentLengthHeader = request.getHeader("Content-Length");
    if (contentLengthHeader != null) {
      return Long.parseLong(contentLengthHeader);
    }
    return -1L;
  }

  protected void closeQuietly(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      log(e.getMessage(), e);
    }
  }

  /**
   * These are the "hop-by-hop" headers that should not be copied.
   * 这些是不应该复制的“逐跳”标题。
   * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
   * I use an HttpClient HeaderGroup class instead of Set&lt;String&gt; because
   * this
   * approach does case insensitive lookup faster.
   */
  protected static final HeaderGroup hopByHopHeaders;
  static {
    hopByHopHeaders = new HeaderGroup();
    String[] headers = new String[] {
        "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
        "TE", "Trailers", "Transfer-Encoding", "Upgrade" };
    for (String header : headers) {
      hopByHopHeaders.addHeader(new BasicHeader(header, null));
    }
  }

  String requestURL = "";
  String realRequestURL = "";
  String requestMethod = "";
  String requestBody = "";
  String token = "";
  String cookie = "";
  String authorization = "";

  /*
   * String token2 = "";
   * String cookie2 = "";
   */

  /**
   * Copy request headers from the servlet client to the proxy request.
   * This is easily overridden to add your own.
   */
  protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) throws IOException {
    // Get an Enumeration of all of the header names sent by the client
    @SuppressWarnings("unchecked")
    Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
    while (enumerationOfHeaderNames.hasMoreElements()) {
      String headerName = enumerationOfHeaderNames.nextElement();
      copyRequestHeader(servletRequest, proxyRequest, headerName);
    }

    // ul temp String token = login();
    // Header authorization = proxyRequest.getFirstHeader("x-cf-token");
    // Header cookie = proxyRequest.getFirstHeader("cookie");
    /*
     * ul temp if(authorization==null){
     * proxyRequest.addHeader("authorization", token);
     * }else{
     * proxyRequest.setHeader("authorization", token);
     * }
     */

    // 传认证参数
    /*
     * if(authorization!=null){
     * proxyRequest.setHeader("x-cf-token", authorization.getValue());
     *//*
        * proxyRequest.setHeader("x-cf-token", authorization.getValue());
        * //--header 'cookie: _fbp=fb.1.1641436032934.768581463;
        * _ga=GA1.2.542383814.1644460300; csrftoken=1780f9d695d1476fb46744938f9a7013;
        * csrftoken=290c252a344e4c0f8c08b2ff1fdcbfa9' \
        * proxyRequest.setHeader("cookie", cookie.getValue());
        *//*
           * String cookie = headerGoogle("cookie");
           * proxyRequest.setHeader("cookie", cookie);
           * 
           * System.out.println("");
           * System.out.println("x-cf-token:"+authorization.getValue());
           * //System.out.println("cookie:"+cookie.getValue());
           * System.out.println("cookie:"+cookie);
           * System.out.println("");
           * }
           */

    String requestUR = servletRequest.getRequestURL().toString();
    String tokenFile = "token";
    String cookieFile = "cookie";
    String authorizationFile = "authorization";

    if (requestUR.contains("/local-")) {
      tokenFile = "local_" + tokenFile + "_local";
      cookieFile = "local_" + cookieFile + "_local";
      authorizationFile = "local_" + authorizationFile + "_local";
    }

    if (requestUR.contains("/durian/")) {
      tokenFile = "durian_" + tokenFile + "_durian";
      cookieFile = "durian_" + cookieFile + "_durian";
      authorizationFile = "durian_" + authorizationFile + "_durian";
    }

    if (requestUR.contains("/stg/")) {
      tokenFile = "stg_" + tokenFile + "_stg";
      cookieFile = "stg_" + cookieFile + "_stg";
      authorizationFile = "stg_" + authorizationFile + "_stg";
    }

    if (requestUR.contains("/lemon/")) {
      tokenFile = "lemon_" + tokenFile + "_lemon";
      cookieFile = "lemon_" + cookieFile + "_lemon";
      authorizationFile = "lemon_" + authorizationFile + "_lemon";
    }

    if (requestUR.contains("/kiwi/")) {
      tokenFile = "kiwi_" + tokenFile + "_kiwi";
      cookieFile = "kiwi_" + cookieFile + "_kiwi";
      authorizationFile = "kiwi_" + authorizationFile + "_kiwi";
    }

    if (requestUR.contains("/ignite_durian/")) {
      tokenFile = "durian_" + tokenFile + "_durian";
      cookieFile = "durian_" + cookieFile + "_durian";
      authorizationFile = "durian_" + authorizationFile + "_durian";
    }

    if (requestUR.contains("/ignite_lemon/")) {
      tokenFile = "lemon_" + tokenFile + "_lemon";
      cookieFile = "lemon_" + cookieFile + "_lemon";
      authorizationFile = "lemon_" + authorizationFile + "_lemon";
    }

    if (requestUR.contains("/ignite_stage/")) {
      tokenFile = "stg_" + tokenFile + "_stg";
      cookieFile = "stg_" + cookieFile + "_stg";
      authorizationFile = "stg_" + authorizationFile + "_stg";
    }

    if (requestUR.contains("/time/out")) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    /*
     * token = readConfig(tokenFile);
     * cookie = readConfig(cookieFile);
     * authorization = readConfig(authorizationFile);
     */

    Map<String, String> headMap = readAllLine(cookieFile);

    token = headMap.get("x-cf-token").trim();
    cookie = headMap.get("cookie").trim();
    authorization = headMap.get("authorization") == null ? "" : headMap.get("authorization").trim();

    /*
     * String tokenFile2 = "token_2";
     * String cookieFile2 = "cookie_2";
     */

    /*
     * if(requestUR.contains("/local-")){
     * tokenFile2 = tokenFile2+"_durian";
     * cookieFile2 = cookieFile2+"_durian";
     * }
     * 
     * if(requestUR.contains("/durian/")){
     * tokenFile2 = tokenFile2+"_durian";
     * cookieFile2 = cookieFile2+"_durian";
     * }
     * 
     * if(requestUR.contains("/stg/")){
     * tokenFile2 = tokenFile2+"_stg";
     * cookieFile2 = cookieFile2+"_stg";
     * }
     * 
     * if(requestUR.contains("/lemon/")){
     * tokenFile2 = tokenFile2+"_lemon";
     * cookieFile2 = cookieFile2+"_lemon";
     * }
     * 
     * if(requestUR.contains("/kiwi/")){
     * tokenFile2 = tokenFile2+"_kiwi";
     * cookieFile2 = cookieFile2+"_kiwi";
     * }
     * 
     * token2 = readConfig(tokenFile2);
     * cookie2 = readConfig(cookieFile2);
     */

    proxyRequest.setHeader("x-cf-token", token);
    proxyRequest.setHeader("cookie", cookie);

    if (!requestUR.contains("/local-") && requestUR.contains("/admin/")) {
      // System.out.println("1,authorization:"+authorization);
      proxyRequest.setHeader("authorization", authorization);
    }

    if (requestUR.contains("/local-")) {
      // System.out.println("1,local不要authorization:"+authorization);
      proxyRequest.removeHeaders("authorization");
    }

    // System.out.println("2,cookie: "+cookie);
    // System.out.println("3,x-cf-token: "+token);
    requestURL = servletRequest.getRequestURL().toString();

    // System.out.println("");
    // System.out.println("4,var token = '"+token+"';\n" + "5,var ck =
    // '"+cookie+"';\n" + "\n" + "// 添加或修改已存在 header\n" +
    // "pm.request.headers.upsert({\n" + " key: 'cookie',\n" + " value: ck\n" +
    // "});\n" + "\n" + "pm.request.headers.upsert({\n" + " key: 'x-cf-token',\n" +
    // " value: token\n" + "});");
    // System.out.println("");
    // 写死认证参数
    /*
     * String[] split = ProxyConfigUtility.x_cf_token_cookie.split("cookie: ");
     * proxyRequest.setHeader("x-cf-token", split[0]);
     * proxyRequest.setHeader("cookie", split[1]);
     */

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// 设置日期格式

    twoPrint(
        "_________________start___________________________start___________________________start___________________________start___________________________start__________");
    twoPrint("");
    twoPrint("本机请求URL: " + requestURL);
    requestMethod = proxyRequest.getRequestLine().getMethod();
    twoPrint("请求方法: " + requestMethod);
    twoPrint("请求时间: " + df.format(new Date()));

    // twoPrint("真实请求URL: "+proxyRequest.getRequestLine());
    realRequestURL = proxyRequest.getRequestLine().getUri();
    twoPrint("请求URL: " + realRequestURL);

    // twoPrint(proxyRequest.getRequestLine().getUri());
    if ("GET".equals(requestMethod)) {

    } else {
      twoPrint("请求body:" + parJson);
      requestBody = parJson;
    }
    HttpParams params = proxyRequest.getParams();

    Header[] allHeaders = proxyRequest.getAllHeaders();
    for (int i = 0; i < allHeaders.length; i++) {
      if (allHeaders[i].getName().equalsIgnoreCase("")) {
        System.out.println(allHeaders[i].getName() + ":" + allHeaders[i].getValue());
      }
    }
  }

  public void twoPrint(String log) {
    System.out.println(log);
    try {
      WritingFile.appendFile("/Users/zenghuikang/Downloads/request_log.txt", log + "\n", true);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private Map readAllLine(String name) {
    String A = "/Users/zenghuikang/Downloads/0a_cookie_and_token";
    File file = new File(A);
    String maxStr = A + "/" + name + ".json";
    // open the file
    FileReader file1 = null;
    try {
      file1 = new FileReader(maxStr);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    Map<String, String> tokenMap = new HashMap();
    try (BufferedReader buffer = new BufferedReader(file1)) {
      String line;
      while ((line = buffer.readLine()) != null) {
        // System.out.println(line);
        String[] arr = line.split(":");
        if (arr != null && arr.length == 2) {
          tokenMap.put(arr[0], arr[1]);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return tokenMap;
  }

  /*
   * private String readConfig(String name){
   * String A ="/Users/zenghuikang/Downloads/0a_cookie_and_token";
   * File file = new File(A);
   * String maxStr = A+"/"+name+".json";
   * //open the file
   * FileReader file1 = null;
   * try {
   * file1 = new FileReader(maxStr);
   * } catch (FileNotFoundException e) {
   * e.printStackTrace();
   * }
   * BufferedReader buffer = new BufferedReader(file1);
   * //read the 1st line
   * String line = null;
   * try {
   * line = buffer.readLine();
   * } catch (IOException e) {
   * e.printStackTrace();
   * }
   * //display the 1st line
   * //System.out.println(line);
   * return line;
   * }
   */

  private String headerGoogle2(String name) {
    String A = "/Users/zenghuikang/Downloads/cookie_and_token";
    File file = new File(A);
    List<String> strings = searchFileList(file, name, name);
    int max = 0;
    String maxStr = "";
    for (int i = 0; i < strings.size(); i++) {
      int i1 = strings.get(i).indexOf("(");
      String substring = "0";
      if (i1 == -1) {
        continue;
      }
      int i2 = strings.get(i).indexOf(")");
      substring = strings.get(i).substring(i1 + 1, i2);
      // System.out.println(substring);
      if (Integer.valueOf(substring) >= max) {
        max = Integer.valueOf(substring);
        maxStr = strings.get(i);
      }
    }
    System.out.println("maxStr:" + maxStr);
    if ("".equals(maxStr)) {
      maxStr = A + "/token.json";
    }
    // open the file
    FileReader file1 = null;
    try {
      file1 = new FileReader(maxStr);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    BufferedReader buffer = new BufferedReader(file1);
    // read the 1st line
    String line = null;
    try {
      line = buffer.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    // display the 1st line
    System.out.println(line);
    return line;
  }

  List<String> fileListData = new ArrayList();

  /**
   * 模糊查询相关文件
   * 
   * @param path     文件路径
   * @param fileName 需要找的文件
   */
  public List<String> searchFileList(File path, String fileName, String pre) {
    File[] files = path.listFiles(); // 列出所有的子文件
    for (File file : files) {
      if (file.isFile()) {// 如果是文件，则先模糊查询,判断是否相关
        if (matchStringByIndexOf(file.toString(), fileName, pre)) {
          fileListData.add(file.toString());
          // System.out.println(file.toString());
        }
      } else if (file.isDirectory())// 如果是文件夹，则输出文件夹的名字，并递归遍历该文件夹
      {
        // searchFileList(file,fileName);//递归遍历
      }
    }
    return fileListData;
  }

  /**
   * 模糊查询
   * 
   * @param str  需要查询的字符串
   * @param part 部分
   * @return true 代表查到的 false 代表没查到
   */
  public boolean matchStringByIndexOf(String str, String part, String pre) {
    int count = 0;
    int index = 0;
    if (str.endsWith(".json") && str.startsWith("/Users/zenghuikang/Downloads/" + pre)) {
      while ((index = str.indexOf(part, index)) != -1) {
        index = index + part.length();
        count++;
      }
      if (count < 1) {
        return false;
      }
      return true;
    }
    return false;
  }

  /**
   * Copy a request header from the servlet client to the proxy request.
   * This is easily overridden to filter out certain headers if desired.
   * 将请求标头从Servlet客户端复制到代理请求。 *如果需要，可以很容易地覆盖此字段以过滤出某些标头。
   */
  protected void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest,
      String headerName) {
    // Instead the content-length is effectively set via InputStreamEntity
    if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
      return;
    }
    if (hopByHopHeaders.containsHeader(headerName)) {
      return;
    }

    @SuppressWarnings("unchecked")
    Enumeration<String> headers = servletRequest.getHeaders(headerName);
    while (headers.hasMoreElements()) {// sometimes more than one value
      String headerValue = headers.nextElement();
      // In case the proxy host is running multiple virtual servers,
      // rewrite the Host header to ensure that we get content from
      // the correct virtual server
      if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
        HttpHost host = getTargetHost(servletRequest);
        headerValue = host.getHostName();
        if (host.getPort() != -1) {
          headerValue += ":" + host.getPort();
        }
      } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
        headerValue = getRealCookie(headerValue);
      }
      proxyRequest.addHeader(headerName, headerValue);
    }
  }

  /*
   * private String login() {
   * String token = "";
   * try {
   * LoginDto loginDto = new LoginDto();
   * loginDto.setCountry("+86");
   * 
   * //a123456
   * loginDto.setPassword("85ea42562e733c094a193f469ad440d6");
   * loginDto.setMobilephone("18423232323");
   * 
   * 
   * //loginDto.setPassword("aac41e037079cdb631fbc6fd6d31dddf");
   * //loginDto.setPassword("aac41e037079cdb631fbc6fd6d31dddf");
   * //loginDto.setMobilephone("18423232323");
   * String targetUrl = ProxyConfigUtility.targetUrlPp;
   * 
   * 
   * JSONObject jsonParam = JSON.parseObject(JSON.toJSONString(loginDto));
   * 
   * String url = targetUrl+"/app-token/api/authenticate";
   * // post请求返回结果
   * CloseableHttpClient httpClient = HttpClients.createDefault();
   * JSONObject jsonResult = null;
   * HttpPost httpPost = new HttpPost(url);
   * // 设置请求和传输超时时间
   * httpPost.setConfig(RequestConfig.custom().setSocketTimeout(20000).
   * setConnectTimeout(20000).build());
   * try {
   * if (null != jsonParam) {
   * // 解决中文乱码问题
   * StringEntity entity = new StringEntity(jsonParam.toString(), "utf-8");
   * entity.setContentEncoding("UTF-8");
   * entity.setContentType("application/json");
   * httpPost.setEntity(entity);
   * }
   * CloseableHttpResponse result = httpClient.execute(httpPost);
   * // 请求发送成功，并得到响应
   * if (result.getStatusLine().getStatusCode() ==
   * org.apache.http.HttpStatus.SC_OK) {
   * String str = "";
   * try {
   * // 读取服务器返回过来的json字符串数据
   * str = EntityUtils.toString(result.getEntity(), "utf-8");
   * // 把json字符串转换成json对象
   * jsonResult = JSONObject.parseObject(str);
   * } catch (Exception e) {
   * System.out.println("login post请求提交失败:" + url+e);
   * }
   * }
   * } catch (IOException e) {
   * System.out.println("login post请求提交失败:" + url+e);
   * } finally {
   * httpPost.releaseConnection();
   * }
   * 
   * if(jsonResult!=null){
   * String code = jsonResult.getString("code");
   * JSONObject data = jsonResult.getJSONObject("data");
   * token = data.getString("id_token");
   * //System.out.println("login id_token==>"+token);
   * if("000000".equals(code)) {
   * //System.out.println(" login http_request_success 数据执行中");
   * }else if("10602".equals(code)) {
   * System.out.println(" login 参数解密错误");
   * }else if("950".equals(code)) {
   * System.out.println(" login 客户未注册");
   * }else if("707".equals(code)) {
   * System.out.println(" login json格式错误");
   * }else if("951".equals(code)) {
   * System.out.println(" login type类型不存在，之允许 0 和 1");
   * }else if("10606".equals(code)) {
   * System.out.println(" login 请不要重复orderId");
   * }else if("10012".equals(code)) {
   * System.out.println(" login 未找到对应币种,请确认币种后操作");
   * }
   * }
   * return token;
   * } catch (Exception e) {
   * System.out.println("测试用代理登录失败"+e);
   * }
   * return token;
   * }
   */

  private void setXForwardedForHeader(HttpServletRequest servletRequest,
      HttpRequest proxyRequest) {
    if (doForwardIP) {
      String forHeaderName = "X-Forwarded-For";
      String forHeader = servletRequest.getRemoteAddr();
      String existingForHeader = servletRequest.getHeader(forHeaderName);
      if (existingForHeader != null) {
        forHeader = existingForHeader + ", " + forHeader;
      }
      proxyRequest.setHeader(forHeaderName, forHeader);

      String protoHeaderName = "X-Forwarded-Proto";
      String protoHeader = servletRequest.getScheme();
      proxyRequest.setHeader(protoHeaderName, protoHeader);
    }
  }

  /** Copy proxied response headers back to the servlet client. */
  protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    for (Header header : proxyResponse.getAllHeaders()) {
      copyResponseHeader(servletRequest, servletResponse, header);
    }
  }

  /**
   * Copy a proxied response header back to the servlet client.
   * This is easily overwritten to filter out certain headers if desired.
   */
  protected void copyResponseHeader(HttpServletRequest servletRequest,
      HttpServletResponse servletResponse, Header header) {
    String headerName = header.getName();
    if (hopByHopHeaders.containsHeader(headerName)) {
      return;
    }
    String headerValue = header.getValue();
    if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
        headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
      copyProxyCookie(servletRequest, servletResponse, headerValue);
    } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
      // LOCATION Header may have to be rewritten.
      servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
    } else {
      servletResponse.addHeader(headerName, headerValue);
    }
  }

  /**
   * Copy cookie from the proxy to the servlet client.
   * Replaces cookie path to local path and renames cookie to avoid collisions.
   */
  protected void copyProxyCookie(HttpServletRequest servletRequest,
      HttpServletResponse servletResponse, String headerValue) {
    // build path for resulting cookie
    String path = servletRequest.getContextPath(); // path starts with / or is empty string
    path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
    if (path.isEmpty()) {
      path = "/";
    }

    for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
      // set cookie name prefixed w/ a proxy value so it won't collide w/ other
      // cookies
      String proxyCookieName = doPreserveCookies ? cookie.getName()
          : getCookieNamePrefix(cookie.getName()) + cookie.getName();
      Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
      servletCookie.setComment(cookie.getComment());
      servletCookie.setMaxAge((int) cookie.getMaxAge());
      servletCookie.setPath(path); // set to the path of the proxy servlet
      // don't set cookie domain
      servletCookie.setSecure(cookie.getSecure());
      servletCookie.setVersion(cookie.getVersion());
      servletResponse.addCookie(servletCookie);
    }
  }

  /**
   * Take any client cookies that were originally from the proxy and prepare them
   * to send to the
   * proxy. This relies on cookie headers being set correctly according to RFC
   * 6265 Sec 5.4.
   * This also blocks any local cookies from being sent to the proxy.
   */
  protected String getRealCookie(String cookieValue) {
    StringBuilder escapedCookie = new StringBuilder();
    String cookies[] = cookieValue.split("[;,]");
    for (String cookie : cookies) {
      String cookieSplit[] = cookie.split("=");
      if (cookieSplit.length == 2) {
        String cookieName = cookieSplit[0].trim();
        if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
          cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
          if (escapedCookie.length() > 0) {
            escapedCookie.append("; ");
          }
          escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
        }
      }
    }
    return escapedCookie.toString();
  }

  /** The string prefixing rewritten cookies. */
  protected String getCookieNamePrefix(String name) {
    return "!Proxy!" + getServletConfig().getServletName();
  }

  /**
   * Copy response body data (the entity) from the proxy to the servlet client.
   */
  protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
      HttpRequest proxyRequest, HttpServletRequest servletRequest)
      throws IOException {
    HttpEntity entity = proxyResponse.getEntity();
    if (entity != null) {
      OutputStream servletOutputStream = servletResponse.getOutputStream();
      entity.writeTo(servletOutputStream);
    }
  }

  protected void copyResponseEntityUl(InputStream inputStream, HttpServletResponse servletResponse,
      HttpRequest proxyRequest, HttpServletRequest servletRequest)
      throws IOException {
    OutputStream servletOutputStream = servletResponse.getOutputStream();
    BufferedOutputStream bos1 = new BufferedOutputStream(servletOutputStream, 2048);
    // 第二次读流
    int len;
    byte[] bytes = new byte[2048];
    while ((len = inputStream.read(bytes, 0, 2048)) != -1) {
      bos1.write(bytes, 0, len);
    }
    bos1.flush();
    bos1.close();
  }

  /**
   * Reads the request URI from {@code servletRequest} and rewrites it,
   * considering targetUri.
   * It's used to make the new request.
   */
  protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
    StringBuilder uri = new StringBuilder(500);
    uri.append(getTargetUri(servletRequest));
    // Handle the path given to the servlet
    String pathInfo = servletRequest.getPathInfo();
    if (pathInfo != null) {// ex: /my/path.html
      // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%"
      // characters
      uri.append(encodeUriQuery(pathInfo, true));
    }
    // Handle the query string & fragment
    String queryString = servletRequest.getQueryString();// ex:(following '?'): name=value&foo=bar#fragment
    String fragment = null;
    // split off fragment from queryString, updating queryString if found
    if (queryString != null) {
      int fragIdx = queryString.indexOf('#');
      if (fragIdx >= 0) {
        fragment = queryString.substring(fragIdx + 1);
        queryString = queryString.substring(0, fragIdx);
      }
    }

    queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
    if (queryString != null && queryString.length() > 0) {
      uri.append('?');
      // queryString is not decoded, so we need encodeUriQuery not to encode "%"
      // characters, to avoid double-encoding
      uri.append(encodeUriQuery(queryString, false));
    }

    if (doSendUrlFragment && fragment != null) {
      uri.append('#');
      // fragment is not decoded, so we need encodeUriQuery not to encode "%"
      // characters, to avoid double-encoding
      uri.append(encodeUriQuery(fragment, false));
    }
    return uri.toString();
  }

  protected String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
    return queryString;
  }

  /**
   * For a redirect response from the target server, this translates
   * {@code theUrl} to redirect to
   * and translates it to one the original client can use.
   */
  protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
    // TODO document example paths
    final String targetUri = getTargetUri(servletRequest);
    if (theUrl.startsWith(targetUri)) {
      /*-
       * The URL points back to the back-end server.
       * Instead of returning it verbatim we replace the target path with our
       * source path in a way that should instruct the original client to
       * request the URL pointed through this Proxy.
       * We do this by taking the current request and rewriting the path part
       * using this servlet's absolute path and the path from the returned URL
       * after the base target URL.
       */
      StringBuffer curUrl = servletRequest.getRequestURL();// no query
      int pos;
      // Skip the protocol part
      if ((pos = curUrl.indexOf("://")) >= 0) {
        // Skip the authority part
        // + 3 to skip the separator between protocol and authority
        if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
          // Trim everything after the authority part.
          curUrl.setLength(pos);
        }
      }
      // Context path starts with a / if it is not blank
      curUrl.append(servletRequest.getContextPath());
      // Servlet path starts with a / if it is not blank
      curUrl.append(servletRequest.getServletPath());
      curUrl.append(theUrl, targetUri.length(), theUrl.length());
      return curUrl.toString();
    }
    return theUrl;
  }

  /** The target URI as configured. Not null. */
  public String getTargetUri() {
    return targetUri;
  }

  /**
   * Encodes characters in the query or fragment part of the URI.
   *
   * <p>
   * Unfortunately, an incoming URI sometimes has characters disallowed by the
   * spec. HttpClient
   * insists that the outgoing proxied request has a valid URI because it uses
   * Java's {@link URI}.
   * To be more forgiving, we must escape the problematic characters. See the URI
   * class for the
   * spec.
   *
   * @param in            example: name=value&amp;foo=bar#fragment
   * @param encodePercent determine whether percent characters need to be encoded
   */
  protected static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
    // Note that I can't simply use URI.java to encode because it will escape
    // pre-existing escaped things.
    StringBuilder outBuf = null;
    Formatter formatter = null;
    for (int i = 0; i < in.length(); i++) {
      char c = in.charAt(i);
      boolean escape = true;
      if (c < 128) {
        if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%')) {
          escape = false;
        }
      } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {// not-ascii
        escape = false;
      }
      if (!escape) {
        if (outBuf != null) {
          outBuf.append(c);
        }
      } else {
        // escape
        if (outBuf == null) {
          outBuf = new StringBuilder(in.length() + 5 * 3);
          outBuf.append(in, 0, i);
          formatter = new Formatter(outBuf);
        }
        // leading %, 0 padded, width 2, capital hex
        formatter.format("%%%02X", (int) c);// TODO
      }
    }
    return outBuf != null ? outBuf : in;
  }

  protected static final BitSet asciiQueryChars;
  static {
    char[] c_unreserved = "_-!.~'()*".toCharArray();// plus alphanum
    char[] c_punct = ",;:$&+=".toCharArray();
    char[] c_reserved = "?/[]@".toCharArray();// plus punct

    asciiQueryChars = new BitSet(128);
    for (char c = 'a'; c <= 'z'; c++) {
      asciiQueryChars.set((int) c);
    }
    for (char c = 'A'; c <= 'Z'; c++) {
      asciiQueryChars.set((int) c);
    }
    for (char c = '0'; c <= '9'; c++) {
      asciiQueryChars.set((int) c);
    }
    for (char c : c_unreserved) {
      asciiQueryChars.set((int) c);
    }
    for (char c : c_punct) {
      asciiQueryChars.set((int) c);
    }
    for (char c : c_reserved) {
      asciiQueryChars.set((int) c);
    }

    asciiQueryChars.set((int) '%');// leave existing percent escapes in place
  }

  private String getCurl(RequestWrapper req) {
    Charset charset = getCharset(req);
    String body = req.hasBody() ? req.getBody(charset) : null;

    StringBuilder sb = new StringBuilder();

    sb.append("curl")
        .append(" -X ").append(req.getMethod())
        .append(" \"").append(getFullRequest(req)).append("\"");

    // headers
    Enumeration<String> headerNames = req.getHeaderNames();
    if (headerNames != null) {
      while (headerNames.hasMoreElements()) {
        String headerName = headerNames.nextElement();
        sb.append(" -H \"").append(headerName).append(": ").append(req.getHeader(headerName)).append("\"");
      }
    }

    // body
    if (body != null) {
      // escape quotes
      body = body.replaceAll("\"", "\\\\\"");
      sb.append(" -d \"").append(body).append("\"");
    }
    return sb.toString();
  }

  private String getFullRequest(HttpServletRequest req) {
    StringBuilder sb = new StringBuilder();
    sb.append(req.getRequestURL().toString());

    String sep = "?";
    for (Map.Entry<String, String[]> me : req.getParameterMap().entrySet()) {
      for (String value : me.getValue()) {
        sb.append(sep).append(me.getKey()).append("=").append(value);
        sep = "&";
      }
    }
    return sb.toString();
  }

  private static final class RequestWrapper extends HttpServletRequestWrapper {
    private byte[] requestInputStream;

    public RequestWrapper(HttpServletRequest req) throws IOException {
      super(req);

      try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        copy(req.getInputStream(), os);
        // do we need to close inputStream?
        requestInputStream = os.toByteArray();
      }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new BufferedServletInputStream(this.requestInputStream);
    }

    boolean hasBody() {
      return this.requestInputStream != null && requestInputStream.length != 0;
    }

    String getBody(Charset charset) {
      return hasBody() ? arrayToString(this.requestInputStream, charset) : null;
    }
  }

  private static final class BufferedServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream is;

    public BufferedServletInputStream(byte[] buffer) {
      this.is = new ByteArrayInputStream(buffer);
    }

    @Override
    public int available() {
      return this.is.available();
    }

    @Override
    public int read() {
      return this.is.read();
    }

    @Override
    public int read(byte[] buf, int off, int len) {
      return this.is.read(buf, off, len);
    }

    @Override
    public boolean isFinished() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReady() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * @return true if byte array is gzipped by looking at gzip magic
   */
  static boolean isGzip(byte[] bytes) {
    if (bytes.length < 2) {
      return false;
    }
    int head = (bytes[0] & 0xff) | ((bytes[1] << 8) & 0xff00);
    return GZIPInputStream.GZIP_MAGIC == head;
  }

  static String arrayToString(byte[] arr, Charset charset) {
    // content could be compressed
    if (isGzip(arr)) {
      return fromGzipped(arr, charset);
    }
    return new String(arr, charset);
  }

  private static String fromGzipped(byte[] arr, Charset charset) {
    try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(arr))) {
      return copyToString(gis, charset);
    } catch (IOException e) {
      log.warn("Error converting to String: " + e.getMessage());
      return "";
    }
  }

  private static Charset getCharset(HttpServletRequest req) {
    String encoding = req.getCharacterEncoding();
    if (encoding != null && !encoding.isEmpty()) {
      try {
        return Charset.forName(encoding);
      } catch (UnsupportedCharsetException e) {
        log.warn("Unsupported charset [{}]: {}", encoding, e.getMessage());
      }
    }
    return Charset.defaultCharset();
  }

  static int copy(InputStream in, OutputStream out) throws IOException {
    int byteCount = 0;
    byte[] buffer = new byte[BUFFER_SIZE];
    int bytesRead = -1;
    while ((bytesRead = in.read(buffer)) != -1) {
      out.write(buffer, 0, bytesRead);
      byteCount += bytesRead;
    }
    out.flush();
    return byteCount;
  }

  static String copyToString(InputStream in, Charset charset) throws IOException {
    StringBuilder out = new StringBuilder();
    InputStreamReader reader = new InputStreamReader(in, charset);
    char[] buffer = new char[BUFFER_SIZE];
    int bytesRead = -1;
    while ((bytesRead = reader.read(buffer)) != -1) {
      out.append(buffer, 0, bytesRead);
    }
    return out.toString();
  }

  private static final int BUFFER_SIZE = 4096;
}
