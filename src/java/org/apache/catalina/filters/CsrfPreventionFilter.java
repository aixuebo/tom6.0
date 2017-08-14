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

package org.apache.catalina.filters;

import java.io.IOException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides basic CSRF protection for a web application. The filter assumes
 * 
 * that:
 * <ul>
 * <li>The filter is mapped to /*</li>
 * <li>{@link HttpServletResponse#encodeRedirectURL(String)} and
 * {@link HttpServletResponse#encodeURL(String)} are used to encode all URLs
 * returned to the client
 * </ul>
 * 提供基于CSRF解析的web应用
 * CSRF（Cross-site request forgery），中文名称：跨站请求伪造,防止CSRF攻击,参见tomcat下网络攻击视频
 */
public class CsrfPreventionFilter extends FilterBase {

    private static final Log log =
        LogFactory.getLog(CsrfPreventionFilter.class);
    
    private String randomClass = SecureRandom.class.getName();
    
    private Random randomSource;//是randomClass对应的实例对象---用于实现随机数,因为Random是伪随机数,有bug,不安全

    /**
     * 安全的path,即该path不怕攻击
     */
    private final Set<String> entryPoints = new HashSet<String>();
    
    private int nonceCacheSize = 5;//每一个用户有多少个token可以被缓存

    @Override
    protected Log getLogger() {
        return log;
    }

    /**
     * Entry points are URLs that will not be tested for the presence of a valid
     * nonce. They are used to provide a way to navigate back to a protected
     * application after navigating away from it. Entry points will be limited
     * to HTTP GET requests and should not trigger any security sensitive
     * actions.
     * 
     * @param entryPoints   Comma separated list of URLs to be configured as
     *                      entry points.
     * 设置不怕攻击的path路径集合
     */
    public void setEntryPoints(String entryPoints) {
        String values[] = entryPoints.split(",");
        for (String value : values) {
            this.entryPoints.add(value.trim());
        }
    }

    /**
     * Sets the number of previously issued nonces that will be cached on a LRU
     * basis to support parallel requests, limited use of the refresh and back
     * in the browser and similar behaviors that may result in the submission
     * of a previous nonce rather than the current one. If not set, the default
     * value of 5 will be used.
     * 
     * @param nonceCacheSize    The number of nonces to cache
     */
    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }
    
    /**
     * Specify the class to use to generate the nonces. Must be in instance of
     * {@link Random}.
     * 
     * @param randomClass   The name of the class to use
     */
    public void setRandomClass(String randomClass) {
        this.randomClass = randomClass;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Set the parameters
        super.init(filterConfig);//初始化web.xml中配置的属性,即randomClass等属性赋值
        
        try {
            Class<?> clazz = Class.forName(randomClass);
            randomSource = (Random) clazz.newInstance();//创建实例对象
        } catch (ClassNotFoundException e) {
            ServletException se = new ServletException(sm.getString(
                    "csrfPrevention.invalidRandomClass", randomClass), e);
            throw se;
        } catch (InstantiationException e) {
            ServletException se = new ServletException(sm.getString(
                    "csrfPrevention.invalidRandomClass", randomClass), e);
            throw se;
        } catch (IllegalAccessException e) {
            ServletException se = new ServletException(sm.getString(
                    "csrfPrevention.invalidRandomClass", randomClass), e);
            throw se;
        }
    }


    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        ServletResponse wResponse = null;
        
        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse) {
            
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            boolean skipNonceCheck = false;//true表示不需要进行校验,该请求一定是安全的
            
            if (Constants.METHOD_GET.equals(req.getMethod())) {//是get方法请求的
                String path = req.getServletPath();
                if (req.getPathInfo() != null) {
                    path = path + req.getPathInfo();
                }
                
                if (entryPoints.contains(path)) {//说明该path不需要校验
                    skipNonceCheck = true;
                }
            }

            //从session中获取该用户的token
            HttpSession session = req.getSession(false);

            @SuppressWarnings("unchecked")
            LruCache<String> nonceCache = (session == null) ? null
                    : (LruCache<String>) session.getAttribute(
                            Constants.CSRF_NONCE_SESSION_ATTR_NAME);//从session获取

            if (!skipNonceCheck) {//说明需要校验
                String previousNonce =
                    req.getParameter(Constants.CSRF_NONCE_REQUEST_PARAM);//用户请求的token内容

                if (nonceCache == null || previousNonce == null || //说明session中或者客户端请求中不带有token,因此说明请求非法
                        !nonceCache.contains(previousNonce)) {//说明session中不包含用户请求的token
                    res.sendError(HttpServletResponse.SC_FORBIDDEN);//不允许访问
                    return;//return后就不会继续链式调用了,filter就终止了
                }
            }
            
            if (nonceCache == null) {//如果没有缓存,为用户创建缓存
                nonceCache = new LruCache<String>(nonceCacheSize);
                if (session == null) {
                    session = req.getSession(true);
                }
                session.setAttribute(
                        Constants.CSRF_NONCE_SESSION_ATTR_NAME, nonceCache);
            }
            
            String newNonce = generateNonce();//产生token随机数
            
            nonceCache.add(newNonce);//缓存起来
            
            wResponse = new CsrfResponseWrapper(res, newNonce);//包装为带有token的response,即用户产生了新的token,每一次请求token都是不同的,防止攻击,因此要把新的token返回给用户
        } else {
            wResponse = response;
        }
        
        chain.doFilter(request, wResponse);
    }


    //该过滤器也是致命的过滤器
    @Override
    protected boolean isConfigProblemFatal() {
        return true;
    }


    /**
     * Generate a once time token (nonce) for authenticating subsequent
     * requests. This will also add the token to the session. The nonce
     * generation is a simplified version of ManagerBase.generateSessionId().
     * 产生token随机数
     */
    protected String generateNonce() {
        byte random[] = new byte[16];

        // Render the result as a String of hexadecimal digits
        StringBuilder buffer = new StringBuilder();

        randomSource.nextBytes(random);
       
        for (int j = 0; j < random.length; j++) {
            byte b1 = (byte) ((random[j] & 0xf0) >> 4);
            byte b2 = (byte) (random[j] & 0x0f);
            if (b1 < 10)
                buffer.append((char) ('0' + b1));
            else
                buffer.append((char) ('A' + (b1 - 10)));
            if (b2 < 10)
                buffer.append((char) ('0' + b2));
            else
                buffer.append((char) ('A' + (b2 - 10)));
        }

        return buffer.toString();
    }

    //response的包装其,包装看一个新的token
    protected static class CsrfResponseWrapper
            extends HttpServletResponseWrapper {

        private String nonce;

        public CsrfResponseWrapper(HttpServletResponse response, String nonce) {
            super(response);
            this.nonce = nonce;
        }

        //主要对url进行添加token
        @Override
        @Deprecated
        public String encodeRedirectUrl(String url) {
            return encodeRedirectURL(url);
        }

        @Override
        public String encodeRedirectURL(String url) {
            return addNonce(super.encodeRedirectURL(url));
        }

        @Override
        @Deprecated
        public String encodeUrl(String url) {
            return encodeURL(url);
        }

        @Override
        public String encodeURL(String url) {
            return addNonce(super.encodeURL(url));
        }
        
        /**
         * Return the specified URL with the nonce added to the query string. 
         *
         * @param url URL to be modified
         * @param nonce The nonce to add
         * 主要对url进行添加token
         * 即对url的?号带有的参数部分追加token内容,该token是最新的token信息
         */
        private String addNonce(String url) {

            if ((url == null) || (nonce == null))
                return (url);

            String path = url;
            String query = "";//URL的?号之后的内容
            String anchor = "";//获取#之后的内容
            int pound = path.indexOf('#');
            if (pound >= 0) {
                anchor = path.substring(pound);//获取#之后的内容
                path = path.substring(0, pound);
            }
            int question = path.indexOf('?');
            if (question >= 0) {
                query = path.substring(question);
                path = path.substring(0, question);
            }
            StringBuilder sb = new StringBuilder(path);
            if (query.length() >0) {//说明存在?,因此是&追加一个参数
                sb.append(query);
                sb.append('&');
            } else {
                sb.append('?');//说明不存在?,因此追加参数的时候要添加?
            }
            sb.append(Constants.CSRF_NONCE_REQUEST_PARAM);//追加token=token值
            sb.append('=');
            sb.append(nonce);
            sb.append(anchor);//追加#内容
            return (sb.toString());
        }
    }
    
    //缓存,存储每一个用户的token信息
    protected static class LruCache<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        // Although the internal implementation uses a Map, this cache
        // implementation is only concerned with the keys.
        private final Map<T,T> cache;
        
        public LruCache(final int cacheSize) {
            cache = new LinkedHashMap<T,T>() {
                private static final long serialVersionUID = 1L;
                //每次put都会调用该方法,该方法返回true,则需要清空Map内容,保留cacheSize个
                @Override
                protected boolean removeEldestEntry(Map.Entry<T,T> eldest) {
                    if (size() > cacheSize) {
                        return true;
                    }
                    return false;
                }
            };
        }
        
        public void add(T key) {
            synchronized (cache) {
                cache.put(key, null);
            }
        }

        public boolean contains(T key) {
            synchronized (cache) {
                return cache.containsKey(key);
            }
        }
    }
}
