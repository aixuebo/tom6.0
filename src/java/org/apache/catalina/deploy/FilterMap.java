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


package org.apache.catalina.deploy;


import org.apache.catalina.util.RequestUtil;
import java.io.Serializable;


/**
 * Representation of a filter mapping for a web application, as represented
 * in a <code>&lt;filter-mapping&gt;</code> element in the deployment
 * descriptor.  Each filter mapping must contain a filter name plus either
 * a URL pattern or a servlet name.
 * 代表一个web应用下的filter映射
 * @author Craig R. McClanahan
 * @version $Id: FilterMap.java 939350 2010-04-29 15:36:29Z kkolinko $
 * 
 * 定义一个web.xml中的filter标签
 * filter对应FilterDef类
    <filter>
        <filter-name>encodingFilter</filter-name>
        <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
        <init-param>
            <param-name>encoding</param-name>
            <param-value>UTF-8</param-value>
        </init-param>
        <init-param>
            <param-name>forceEncoding</param-name>
            <param-value>true</param-value>
        </init-param>
    </filter>

mapping对应org.apache.catalina.deploy.FilterMap类
    <filter-mapping>
        <filter-name>encodingFilter</filter-name>
        <url-pattern>/</url-pattern>
        <servlet-name>EncryptionFilteredServlet</servlet-name>//注意url-pattern和servlet-name是二选一的,/*表示任意url都要执行该url,servlet表示只要是走该servlet的,都会走该过滤器
        <dispatcher>REQUEST|INCLUDE|FORWARD|ERROR</disaptcher>
                设定Filter对应的请求方式，有RQUEST,INCLUDE,FORWAR,ERROR四种，默认为REQUEST.
    </filter-mapping>
 */

public class FilterMap implements Serializable {


    // ------------------------------------------------------------- Properties

  private static final long serialVersionUID = 1L;
    /**
     * The name of this filter to be executed when this mapping matches
     * a particular request.
     */
    public static final int ERROR = 1;
    public static final int FORWARD = 2;
    public static final int FORWARD_ERROR =3;  
    public static final int INCLUDE = 4;
    public static final int INCLUDE_ERROR  = 5;
    public static final int INCLUDE_ERROR_FORWARD  =6;
    public static final int INCLUDE_FORWARD  = 7;
    public static final int REQUEST = 8;
    public static final int REQUEST_ERROR = 9;
    public static final int REQUEST_ERROR_FORWARD = 10;
    public static final int REQUEST_ERROR_FORWARD_INCLUDE = 11;
    public static final int REQUEST_ERROR_INCLUDE = 12;
    public static final int REQUEST_FORWARD = 13;
    public static final int REQUEST_INCLUDE = 14;
    public static final int REQUEST_FORWARD_INCLUDE= 15;
    
    // represents nothing having been set. This will be seen 
    // as equal to a REQUEST
    //没有设置dispatcher标签,因此NOT_SET = -1 
    private static final int NOT_SET = -1;
    
    private int dispatcherMapping = NOT_SET;//没有设置dispatcher标签内容
    
    /**
     * 对应标签filter-name,非常重要
     */
    private String filterName = null;    

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }


    /**
     * The servlet name this mapping matches.
     * 对应servlet-name标签,说明该拦截器拦截该servlet,即按照servlet拦截
     */
    private String[] servletNames = new String[0];

    public String[] getServletNames() {
        return (this.servletNames);
    }

    //添加一个servlet的name
    public void addServletName(String servletName) {
        if ("*".equals(servletName)) {
            this.matchAllServletNames = true;
        } else {
            String[] results = new String[servletNames.length + 1];
            System.arraycopy(servletNames, 0, results, 0, servletNames.length);
            results[servletNames.length] = servletName;
            servletNames = results;
        }
    }

    
    /**
     * The flag that indicates this mapping will match all url-patterns
     * 是否匹配所有url,即只要是url,都匹配
     */
    private boolean matchAllUrlPatterns = false;
    
    public boolean getMatchAllUrlPatterns() {
        return matchAllUrlPatterns;
    }
    

    /**
     * The flag that indicates this mapping will match all servlet-names
     * 是否匹配所有servlet,即只要是servlet,都匹配
     */
    private boolean matchAllServletNames = false;
    
    public boolean getMatchAllServletNames() {
        return matchAllServletNames;
    }

    
    /**
     * The URL pattern this mapping matches.
     * 对应url-pattern标签,说明该拦截器通过url拦截
     */
    private String[] urlPatterns = new String[0];

    public String[] getURLPatterns() {
        return (this.urlPatterns);
    }

    public void addURLPattern(String urlPattern) {
        if ("*".equals(urlPattern)) {
            this.matchAllUrlPatterns = true;//说明匹配所有的url
        } else {
            String[] results = new String[urlPatterns.length + 1];
            System.arraycopy(urlPatterns, 0, results, 0, urlPatterns.length);
            results[urlPatterns.length] = RequestUtil.URLDecode(urlPattern);
            urlPatterns = results;
        }
    }
    
    /**
     *
     * This method will be used to set the current state of the FilterMap
     * representing the state of when filters should be applied:
     *
     *        ERROR
     *        FORWARD
     *        FORWARD_ERROR
     *        INCLUDE
     *        INCLUDE_ERROR
     *        INCLUDE_ERROR_FORWARD
     *        REQUEST
     *        REQUEST_ERROR
     *        REQUEST_ERROR_INCLUDE
     *        REQUEST_ERROR_FORWARD_INCLUDE
     *        REQUEST_INCLUDE
     *        REQUEST_FORWARD,
     *        REQUEST_FORWARD_INCLUDE
     * 匹配dispatcher标签
     */
    public void setDispatcher(String dispatcherString) {
        String dispatcher = dispatcherString.toUpperCase();
        
        if (dispatcher.equals("FORWARD")) {

            // apply FORWARD to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = FORWARD; break;
                case ERROR : dispatcherMapping = FORWARD_ERROR; break;
                case INCLUDE  :  dispatcherMapping = INCLUDE_FORWARD; break;
                case INCLUDE_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_FORWARD; break;
                case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case REQUEST_ERROR_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                case REQUEST_INCLUDE : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
            }
        } else if (dispatcher.equals("INCLUDE")) {
            // apply INCLUDE to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = INCLUDE; break;
                case ERROR : dispatcherMapping = INCLUDE_ERROR; break;
                case FORWARD  :  dispatcherMapping = INCLUDE_FORWARD; break;
                case FORWARD_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_INCLUDE; break;
                case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case REQUEST_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                case REQUEST_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
            }
        } else if (dispatcher.equals("REQUEST")) {
            // apply REQUEST to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = REQUEST; break;
                case ERROR : dispatcherMapping = REQUEST_ERROR; break;
                case FORWARD  :  dispatcherMapping = REQUEST_FORWARD; break;
                case FORWARD_ERROR  :  dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case INCLUDE  :  dispatcherMapping = REQUEST_INCLUDE; break;
                case INCLUDE_ERROR  :  dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case INCLUDE_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
                case INCLUDE_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
            }
        }  else if (dispatcher.equals("ERROR")) {
            // apply ERROR to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = ERROR; break;
                case FORWARD  :  dispatcherMapping = FORWARD_ERROR; break;
                case INCLUDE  :  dispatcherMapping = INCLUDE_ERROR; break;
                case INCLUDE_FORWARD : dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_ERROR; break;
                case REQUEST_INCLUDE : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case REQUEST_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case REQUEST_FORWARD_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
            }
        }
    }
    
    //返回配置的dispatcherMapping属性
    public int dispatcherMapping() {
        // per the SRV.6.2.5 absence of any dispatcher elements is
        // equivelant to a REQUEST value
        if (dispatcherMapping == NOT_SET) return REQUEST;//默认走request
        else return dispatcherMapping; 
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Render a String representation of this object.
     * 描述一个过滤器的过程
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("FilterMap[");
        sb.append("filterName=");
        sb.append(this.filterName);
        for (int i = 0; i < servletNames.length; i++) {//该过滤器匹配哪些servlet
            sb.append(", servletName=");
            sb.append(servletNames[i]);
        }
        for (int i = 0; i < urlPatterns.length; i++) {//该过滤器匹配哪些url
            sb.append(", urlPattern=");
            sb.append(urlPatterns[i]);
        }
        sb.append("]");
        return (sb.toString());

    }


}
