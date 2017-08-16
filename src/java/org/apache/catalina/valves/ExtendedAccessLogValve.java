/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.valves;


import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;



/**
 * An implementation of the W3c Extended Log File Format. See
 * http://www.w3.org/TR/WD-logfile.html for more information about the format.
 *
 * The following fields are supported:
 * <ul>
 * <li><code>c-dns</code>:  Client hostname</li> 获取远程请求人的host,此时可能是代理服务器或者nginx的host,因为实现是request.getRemoteHost()
 * <li><code>c-ip</code>:  Client ip address</li> 获取远程请求人的IP,此时可能是代理服务器或者nginx的ip,因为实现是request.getRemoteAddr()
 * <li><code>bytes</code>:  bytes served</li>  打印发送的字节长度 ,代码 response.getContentCountLong(),如果没有该字段,则返回"-"
   cs表示从客户端到服务端的过程中获取信息
 * <li><code>cs-method</code>:  request method</li>  输出http的请求头 request.getMethod()
 * <li><code>cs-uri</code>:  The full uri requested</li> 输出 request.getRequestURI()?request.getQueryString()
 * <li><code>cs-uri-query</code>:  The query string</li>  输出request.getQueryString()
 * <li><code>cs-uri-stem</code>:  The uri without query string</li> 输出 request.getRequestURI(),默认是"-"
 * <li><code>date</code>:  The date in yyyy-mm-dd  format for GMT</li> 获取yyyy-mm-dd格式的日期
 * <li><code>s-dns</code>: The server dns entry </li> 获取tomcat所在服务器节点,用于多台tomcat服务器的时候,知道日志是从哪台机器上打印出来的---InetAddress.getLocalHost().getHostName();
 * <li><code>s-ip</code>:  The server ip address</li> 获取tomcat所在服务器节点,用于多台tomcat服务器的时候,知道日志是从哪台机器上打印出来的---InetAddress.getLocalHost().getHostAddress();
 * <li><code>cs(XXX)</code>:  The value of header XXX from client to server</li> 从request.getHeader中获取key对应的值
 sc 表示从服务端向客户端过程中获取信息
 * <li><code>sc(XXX)</code>: The value of header XXX from server to client </li> 从response.getHeader中获取key对应的值
 * <li><code>sc-status</code>:  The status code</li>  打印http的状态码 response.getStatus(),默认是-
 * <li><code>time</code>:  Time the request was served</li>  获取HH:mm:ss格式的日期
 * <li><code>time-taken</code>:  Time (in seconds) taken to serve the request</li> 获取HH:mm:ss格式的日期 + long类型的请求操作耗时时间
 * <li><code>x-A(XXX)</code>: Pull XXX attribute from the servlet context </li>  从request.getContext().getServletContext().getAttribute()中获取key对应的值
 * <li><code>x-C(XXX)</code>: Pull the first cookie of the name XXX </li>  从request.getCookies()中获取key对应的值
 * <li><code>x-O(XXX)</code>: Pull the all response header values XXX </li>  获取response.getHeaderValues(header)中list,转换成逗号分割的字符串
 * <li><code>x-R(XXX)</code>: Pull XXX attribute from the servlet request </li>  从request.getAttribute(attribute)中获取key对应的值
 * <li><code>x-S(XXX)</code>: Pull XXX attribute from the session </li>  从session.getAttribute(attribute)中获取key对应的值
 * <li><code>x-P(...)</code>:  Call request.getParameter(...) and URLencode it. Helpful to capture certain POST parameters.  获取URLEncoder.encode(request.getParameter(parameter))
 * </li>
 * 
 * </li>
 * <li>For any of the x-H(...) the following method will be called from the
 *                HttpServletRequest object </li>
 * <li><code>x-H(authType)</code>: getAuthType </li>  从request.getAuthType()获取值
 * <li><code>x-H(characterEncoding)</code>: getCharacterEncoding </li>  从request.getCharacterEncoding()获取值
 * <li><code>x-H(contentLength)</code>: getContentLength </li> 从request.getContentLength()获取值
 * <li><code>x-H(locale)</code>:  getLocale</li> 从request.getLocale()获取值
 * <li><code>x-H(protocol)</code>: getProtocol </li> 从request.getProtocol()获取值
 * <li><code>x-H(remoteUser)</code>:  getRemoteUser</li> 从request.getRemoteUser()获取值

 * <li><code>x-H(requestedSessionId)</code>: getRequestedSessionId</li> 从request.getRequestedSessionId()获取值
 * <li><code>x-H(requestedSessionIdFromCookie)</code>:isRequestedSessionIdFromCookie </li> 从request.isRequestedSessionIdFromCookie()获取值
 * <li><code>x-H(requestedSessionIdValid)</code>: isRequestedSessionIdValid</li> 从request.isRequestedSessionIdValid()获取值
 * <li><code>x-H(scheme)</code>:  getScheme</li> 从request.getScheme()获取值
 * <li><code>x-H(secure)</code>:  isSecure</li> 从request.isSecure()获取值
 * </ul>
 *
 *
 *
 * <p>
 * Log rotation can be on or off. This is dictated by the
 * <code>rotatable</code> property.
 * </p>
 *
 * <p>
 * For UNIX users, another field called <code>checkExists</code> is also
 * available. If set to true, the log file's existence will be checked before
 * each logging. This way an external log rotator can move the file
 * somewhere and Tomcat will start with a new file.
 * </p>
 *
 * <p>
 * For JMX junkies, a public method called <code>rotate</code> has
 * been made available to allow you to tell this instance to move
 * the existing log file to somewhere else and start writing a new log file.
 * </p>
 *
 * <p>
 * Conditional logging is also supported. This can be done with the
 * <code>condition</code> property.
 * If the value returned from ServletRequest.getAttribute(condition)
 * yields a non-null value, the logging will be skipped.
 * </p>
 *
 * <p>
 * For extended attributes coming from a getAttribute() call,
 * it is you responsibility to ensure there are no newline or
 * control characters.
 * </p>
 *
 *
 * @author Tim Funk
 * @author Peter Rossbach
 * 
 * @version $Id: ExtendedAccessLogValve.java 1205119 2011-11-22 18:17:22Z kkolinko $
 * 
 * 对访问日志的扩展----
 * 与AccessLogValve不同的是:
 * 1.使用的是全名,而不是字母缩写
 * 2.增加了若干个细节属性获取
 */

public class ExtendedAccessLogValve
    extends AccessLogValve
    implements Lifecycle {

    private static Log log = LogFactory.getLog(ExtendedAccessLogValve.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information about this implementation.
     */
    protected static final String extendedAccessLogInfo =
        "org.apache.catalina.valves.ExtendedAccessLogValve/2.1";


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this implementation.
     */
    public String getInfo() {
        return (extendedAccessLogInfo);
    }


    // --------------------------------------------------------- Public Methods


    // -------------------------------------------------------- Private Methods

    /**
     *  Wrap the incoming value into quotes and escape any inner
     *  quotes with double quotes.
     *  结果是用单引号包装所有的字符,并且字符中如果有单引号,则将其转换成双引号
     *  @param value - The value to wrap quotes around
     *  @return '-' if empty of null. Otherwise, toString() will
     *     be called on the object and the value will be wrapped
     *     in quotes and any quotes will be escaped with 2
     *     sets of quotes.
     */
    private String wrap(Object value) {
        String svalue;
        // Does the value contain a " ? If so must encode it
        if (value == null || "-".equals(value))
            return "-";

        try {
            svalue = value.toString();
            if ("".equals(svalue))
                return "-";
        } catch (Throwable e) {
            /* Log error */
            return "-";
        }

        /* Wrap all quotes in double quotes.将所有单引号转换成双引号 */
        StringBuffer buffer = new StringBuffer(svalue.length() + 2);//用单引号包装字符串,所以多2个字节
        buffer.append('\'');//单引号
        int i = 0;
        while (i < svalue.length()) {
            int j = svalue.indexOf('\'', i);//找到下一个单引号位置
            if (j == -1) {
                buffer.append(svalue.substring(i));
                i = svalue.length();
            } else {
                buffer.append(svalue.substring(i, j + 1));
                buffer.append('"');//将单引号转换成双引号
                i = j + 2;
            }
        }

        buffer.append('\'');
        return buffer.toString();
    }

    /**
     * Open the new log file for the date specified by <code>dateStamp</code>.
     */
    protected synchronized void open() {
        super.open();
        if (currentLogFile.length()==0) {//文件头写入若干行内容
            writer.println("#Fields: " + pattern);
            writer.println("#Version: 2.0");
            writer.println("#Software: " + ServerInfo.getServerInfo());
        }
    }


    // ------------------------------------------------------ Lifecycle Methods

    //获取当前日期yyyy-MM-dd
    protected static class DateElement implements AccessLogElement {
        // Milli-seconds in 24 hours 表示24小时
        private static final long INTERVAL = (1000 * 60 * 60 * 24);
        
        //每一个线程持有一个独立的时间格式化对象
        private static final ThreadLocal<ElementTimestampStruct> currentDate =
                new ThreadLocal<ElementTimestampStruct>() {
            protected ElementTimestampStruct initialValue() {
                return new ElementTimestampStruct("yyyy-MM-dd");//默认精确到天
            }
        };
                
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            ElementTimestampStruct eds = currentDate.get();
            long millis = eds.currentTimestamp.getTime();//获取以前存储的时间
            if (date.getTime() > (millis + INTERVAL -1) ||
                    date.getTime() < millis) {//说明时间已经超过了24小时,要重新格式化新的时间
                eds.currentTimestamp.setTime(
                        date.getTime() - (date.getTime() % INTERVAL));//重新设置新的时间--设置为当前日期最接近的天
                eds.currentTimestampString =
                    eds.currentTimestampFormat.format(eds.currentTimestamp);//重新格式化新的时间
            }
            buf.append(eds.currentTimestampString);            
        }
    }
    
    //输出当前时间--格式HH:mm:ss
    protected static class TimeElement implements AccessLogElement {
        // Milli-seconds in a second 
        private static final long INTERVAL = 1000;//精确到秒
        
        private static final ThreadLocal<ElementTimestampStruct> currentTime =
                new ThreadLocal<ElementTimestampStruct>() {
            protected ElementTimestampStruct initialValue() {
                return new ElementTimestampStruct("HH:mm:ss");//格式化时间
            }
        };
            
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            ElementTimestampStruct eds = currentTime.get();
            long millis = eds.currentTimestamp.getTime();
            if (date.getTime() > (millis + INTERVAL -1) || //说明超过了一秒
                    date.getTime() < millis) {
                eds.currentTimestamp.setTime(
                        date.getTime() - (date.getTime() % INTERVAL));//更新成最近的一秒
                eds.currentTimestampString =
                    eds.currentTimestampFormat.format(eds.currentTimestamp);
            }
            buf.append(eds.currentTimestampString);//追加时间
        }
    }
    
    //从request.getHeader中获取key对应的值
    protected class RequestHeaderElement implements AccessLogElement {
        private String header;
        
        public RequestHeaderElement(String header) {
            this.header = header;
        }
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            buf.append(wrap(request.getHeader(header)));
        }
    }
    
    //从response.getHeader中获取key对应的值
    protected class ResponseHeaderElement implements AccessLogElement {
        private String header;
        
        public ResponseHeaderElement(String header) {
            this.header = header;
        }
        
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            buf.append(wrap(response.getHeader(header)));
        }
    }
    
    //从request.getContext().getServletContext().getAttribute()中获取key对应的值
    protected class ServletContextElement implements AccessLogElement {
        private String attribute;
        
        public ServletContextElement(String attribute) {
            this.attribute = attribute;
        }
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            buf.append(wrap(request.getContext().getServletContext().getAttribute(attribute)));
        }
    }
    
    //从request.getCookies()中获取key对应的值
    protected class CookieElement implements AccessLogElement {
        private String name;
        
        public CookieElement(String name) {
            this.name = name;
        }
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            Cookie[] c = request.getCookies();
            for (int i = 0; c != null && i < c.length; i++) {
                if (name.equals(c[i].getName())) {
                    buf.append(wrap(c[i].getValue()));
                }
            }
        }
    }
    
    /**
     * write a specific response header - x-O(xxx)
     */
    protected class ResponseAllHeaderElement implements AccessLogElement {
        private String header;

        public ResponseAllHeaderElement(String header) {
            this.header = header;
        }
        
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
           if (null != response) {
                String[] values = response.getHeaderValues(header);
                if(values.length > 0) {
                    StringBuffer buffer = new StringBuffer();
                    for (int i = 0; i < values.length; i++) {
                        String string = values[i];
                        buffer.append(string) ;
                        if(i+1<values.length)
                            buffer.append(",");
                    }
                    buf.append(wrap(buffer.toString()));
                    return ;
                }
            }
            buf.append("-");
        }
    }
    
    //从request.getAttribute(attribute)中获取key对应的值
    protected class RequestAttributeElement implements AccessLogElement { 
        private String attribute;
        
        public RequestAttributeElement(String attribute) {
            this.attribute = attribute;
        }
        
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            buf.append(wrap(request.getAttribute(attribute)));
        }        
    }
    
    //从session.getAttribute(attribute)中获取key对应的值
    protected class SessionAttributeElement implements AccessLogElement {
        private String attribute;
        
        public SessionAttributeElement(String attribute) {
            this.attribute = attribute;
        }
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            HttpSession session = null;
            if (request != null) {
                session = request.getSession(false);
                if (session != null)
                    buf.append(wrap(session.getAttribute(attribute)));
            }
        }
    }
    
    protected class RequestParameterElement implements AccessLogElement {
        private String parameter;
        
        public RequestParameterElement(String parameter) {
            this.parameter = parameter;
        }
        /**
         *  urlEncode the given string. If null or empty, return null.
         */
        private String urlEncode(String value) {
            if (null==value || value.length()==0) {
                return null;
            }
            return URLEncoder.encode(value);
        }   
        
        public void addElement(StringBuffer buf, Date date, Request request,
                Response response, long time) {
            buf.append(wrap(urlEncode(request.getParameter(parameter))));
        }
    }
    
    protected class PatternTokenizer {
        private StringReader sr = null;
        private StringBuffer buf = new StringBuffer();
        private boolean ended = false;//true表示解析完成
        private boolean subToken;//遇见-字符
        private boolean parameter;//遇见(
        
        public PatternTokenizer(String str) {
            sr = new StringReader(str);
        }
        
        public boolean hasSubToken() {
            return subToken;
        }
        
        public boolean hasParameter() {
            return parameter;
        }
        
        public String getToken() throws IOException {
            if(ended)
                return null ;
            
            String result = null;
            subToken = false;
            parameter = false;
            
            int c = sr.read();//每次读取一个字节
            while (c != -1) {
                switch (c) {
                case ' '://遇见空格就结束
                    result = buf.toString();
                    buf = new StringBuffer();
                    buf.append((char) c);
                    return result;
                case '-'://遇见-也结束
                    result = buf.toString();
                    buf = new StringBuffer();
                    subToken = true;
                    return result;
                case '('://遇见(也结束
                    result = buf.toString();
                    buf = new StringBuffer();
                    parameter = true;
                    return result;
                case ')':
                    result = buf.toString();
                    buf = new StringBuffer();
                    break;
                default:
                    buf.append((char) c);
                }
                c = sr.read();
            }
            ended = true;
            if (buf.length() != 0) {
                return buf.toString();
            } else {
                return null;
            }
        }
        
        //获取()内容
        public String getParameter()throws IOException {
            String result;
            if (!parameter) {
                return null;
            }
            parameter = false;
            int c = sr.read();
            while (c != -1) {
                if (c == ')') {//获取()内容
                    result = buf.toString();
                    buf = new StringBuffer();
                    return result;
                }
                buf.append((char) c);
                c = sr.read();
            }
            return null;
        }
        
        public String getWhiteSpaces() throws IOException {
            if(isEnded())
                return "" ;
            StringBuffer whiteSpaces = new StringBuffer();
            if (buf.length() > 0) {
                whiteSpaces.append(buf);
                buf = new StringBuffer();
            }
            int c = sr.read();
            while (Character.isWhitespace((char) c)) {
                whiteSpaces.append((char) c);
                c = sr.read();
            }
            if (c == -1) {
                ended = true;
            } else {
                buf.append((char) c);
            }
            return whiteSpaces.toString();
        }
        
        public boolean isEnded() {
            return ended;
        }
        
        public String getRemains() throws IOException {
            StringBuffer remains = new StringBuffer();
            for(int c = sr.read(); c != -1; c = sr.read()) {
                remains.append((char) c);
            }
            return remains.toString();
        }
        
    }
    
    //入口
    protected AccessLogElement[] createLogElements() {
        if (log.isDebugEnabled()) {
            log.debug("decodePattern, pattern =" + pattern);
        }
        List<AccessLogElement> list = new ArrayList<AccessLogElement>();

        PatternTokenizer tokenizer = new PatternTokenizer(pattern);
        try {

            // Ignore leading whitespace.
            tokenizer.getWhiteSpaces();

            if (tokenizer.isEnded()) {
                log.info("pattern was just empty or whitespace");
                return null;
            }

            String token = tokenizer.getToken();
            while (token != null) {
                if (log.isDebugEnabled()) {
                    log.debug("token = " + token);
                }
                AccessLogElement element = getLogElement(token, tokenizer);
                if (element == null) {
                    break;
                }
                list.add(element);
                String whiteSpaces = tokenizer.getWhiteSpaces();
                if (whiteSpaces.length() > 0) {
                    list.add(new StringElement(whiteSpaces));
                }
                if (tokenizer.isEnded()) {
                    break;
                }
                token = tokenizer.getToken();
            }
            if (log.isDebugEnabled()) {
                log.debug("finished decoding with element size of: " + list.size());
            }
            return list.toArray(new AccessLogElement[0]);
        } catch (IOException e) {
            log.error("parse error", e);
            return null;
        }
    }
    
    protected AccessLogElement getLogElement(String token, PatternTokenizer tokenizer) throws IOException {
        if ("date".equals(token)) {
            return new DateElement();//记录当前日期
        } else if ("time".equals(token)) {
            if (tokenizer.hasSubToken()) {
                String nextToken = tokenizer.getToken();
                if ("taken".equals(nextToken)) {
                    return new ElapsedTimeElement(false);//打印请求到response的处理时间                
                }
            } else {
                return new TimeElement();//记录当前时间
            }
        } else if ("bytes".equals(token)) {
            return new ByteSentElement(true);//打印发送的字节长度 ,代码 response.getContentCountLong(),如果没有该字段,则返回"-"
        } else if ("cached".equals(token)) {
            /* I don't know how to evaluate this! */
            return new StringElement("-");
        } else if ("c".equals(token)) {
            String nextToken = tokenizer.getToken();
            if ("ip".equals(nextToken)) {
                return new RemoteAddrElement();//获取远程请求人的IP,此时可能是代理服务器或者nginx的ip,因为实现是request.getRemoteAddr()
            } else if ("dns".equals(nextToken)) {
                return new HostElement();//获取远程请求人的host,此时可能是代理服务器或者nginx的host,因为实现是request.getRemoteHost()
            }
        } else if ("s".equals(token)) {
            String nextToken = tokenizer.getToken();
            if ("ip".equals(nextToken)) {
                return new LocalAddrElement();//获取tomcat所在服务器节点,用于多台tomcat服务器的时候,知道日志是从哪台机器上打印出来的---InetAddress.getLocalHost().getHostAddress();
            } else if ("dns".equals(nextToken)) {
                return new AccessLogElement() {
                    public void addElement(StringBuffer buf, Date date,
                            Request request, Response response, long time) {
                        String value;
                        try {
                            value = InetAddress.getLocalHost().getHostName();//获取本地的host
                        } catch (Throwable e) {
                            value = "localhost";
                        }
                        buf.append(value);
                    }
                };
            }
        } else if ("cs".equals(token)) {
            return getClientToServerElement(tokenizer);
        } else if ("sc".equals(token)) {
            return getServerToClientElement(tokenizer);
        } else if ("sr".equals(token) || "rs".equals(token)) {
            return getProxyElement(tokenizer);
        } else if ("x".equals(token)) {
            return getXParameterElement(tokenizer);
        }
        log.error("unable to decode with rest of chars starting: " + token);
        return null;
    }
    
    protected AccessLogElement getClientToServerElement(
            PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("method".equals(token)) {
                return new MethodElement(); //输出http的请求头 request.getMethod()
            } else if ("uri".equals(token)) {
                if (tokenizer.hasSubToken()) {
                    token = tokenizer.getToken();
                    if ("stem".equals(token)) {
                        return new RequestURIElement();//输出 request.getRequestURI(),默认是"-"
                    } else if ("query".equals(token)) {
                        return new AccessLogElement() {//输出request.getQueryString()
                            public void addElement(StringBuffer buf, Date date,
                                    Request request, Response response,
                                    long time) {
                                String query = request.getQueryString();
                                if (query != null) {
                                    buf.append(query);
                                } else {
                                    buf.append('-');
                                }
                            }
                        };
                    }
                } else {
                    return new AccessLogElement() {
                        public void addElement(StringBuffer buf, Date date,
                                Request request, Response response, long time) {//输出 request.getRequestURI()?request.getQueryString()
                            String query = request.getQueryString();
                            if (query == null) {
                                buf.append(request.getRequestURI());
                            } else {
                                buf.append(request.getRequestURI());
                                buf.append('?');
                                buf.append(request.getQueryString());
                            }
                        }
                    };
                }
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                log.error("No closing ) found for in decode");
                return null;
            }
            return new RequestHeaderElement(parameter);//从request.getHeader中获取key对应的值
        }
        log.error("The next characters couldn't be decoded: "
                + tokenizer.getRemains());
        return null;
    }
    
    protected AccessLogElement getServerToClientElement(
            PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("status".equals(token)) {
                return new HttpStatusCodeElement();//打印http的状态码 response.getStatus(),默认是-
            } else if ("comment".equals(token)) {
                return new StringElement("?");
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                log.error("No closing ) found for in decode");
                return null;
            }
            return new ResponseHeaderElement(parameter);//从response.getHeader中获取key对应的值
        }
        log.error("The next characters couldn't be decoded: "
                + tokenizer.getRemains());
        return null;
    }
    
    protected AccessLogElement getProxyElement(PatternTokenizer tokenizer)
        throws IOException {
        String token = null;
        if (tokenizer.hasSubToken()) {
            token = tokenizer.getToken();
            return new StringElement("-");
        } else if (tokenizer.hasParameter()) {
            tokenizer.getParameter();
            return new StringElement("-");
        }
        log.error("The next characters couldn't be decoded: " + token);
        return null;
    }
    
    protected AccessLogElement getXParameterElement(PatternTokenizer tokenizer)
            throws IOException {
        if (!tokenizer.hasSubToken()) {
            log.error("x param in wrong format. Needs to be 'x-#(...)' read the docs!");
            return null;
        }
        String token = tokenizer.getToken();
        if (!tokenizer.hasParameter()) {
            log.error("x param in wrong format. Needs to be 'x-#(...)' read the docs!");
            return null;
        }
        String parameter = tokenizer.getParameter();
        if (parameter == null) {
            log.error("No closing ) found for in decode");
            return null;
        }
        if ("A".equals(token)) {
            return new ServletContextElement(parameter);
        } else if ("C".equals(token)) {
            return new CookieElement(parameter);
        } else if ("R".equals(token)) {
            return new RequestAttributeElement(parameter);
        } else if ("S".equals(token)) {
            return new SessionAttributeElement(parameter);
        } else if ("H".equals(token)) {
            return getServletRequestElement(parameter);
        } else if ("P".equals(token)) {
            return new RequestParameterElement(parameter);
        } else if ("O".equals(token)) {
            return new ResponseAllHeaderElement(parameter);
        }
        log.error("x param for servlet request, couldn't decode value: "
                + token);
        return null;
    }
    
    protected AccessLogElement getServletRequestElement(String parameter) {
        if ("authType".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getAuthType()));
                }
            };
        } else if ("remoteUser".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getRemoteUser()));
                }
            };
        } else if ("requestedSessionId".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getRequestedSessionId()));
                }
            };
        } else if ("requestedSessionIdFromCookie".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(""
                            + request.isRequestedSessionIdFromCookie()));
                }
            };
        } else if ("requestedSessionIdValid".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap("" + request.isRequestedSessionIdValid()));
                }
            };
        } else if ("contentLength".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap("" + request.getContentLength()));
                }
            };
        } else if ("characterEncoding".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getCharacterEncoding()));
                }
            };
        } else if ("locale".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getLocale()));
                }
            };
        } else if ("protocol".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap(request.getProtocol()));
                }
            };
        } else if ("scheme".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(request.getScheme());
                }
            };
        } else if ("secure".equals(parameter)) {
            return new AccessLogElement() {
                public void addElement(StringBuffer buf, Date date,
                        Request request, Response response, long time) {
                    buf.append(wrap("" + request.isSecure()));
                }
            };
        }
        log.error("x param for servlet request, couldn't decode value: "
                + parameter);
        return null;
    }

    //记录每一个线程下的时间格式
    private static class ElementTimestampStruct {
        private Date currentTimestamp = new Date(0);//当前时间,因为是Date对象,所以后期可以通过setTime方式更改时间
        private SimpleDateFormat currentTimestampFormat;//一个线程持有一个格式,因为SimpleDateFormat是非线程安全的
        private String currentTimestampString;
        
        ElementTimestampStruct(String format) {
            currentTimestampFormat = new SimpleDateFormat(format);
            currentTimestampFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    }
}
