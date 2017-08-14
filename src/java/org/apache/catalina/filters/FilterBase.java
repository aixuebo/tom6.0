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

import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for filters that provides generic initialisation and a simple
 * no-op destruction. 
 * 
 * @author xxd
 */
public abstract class FilterBase implements Filter {
    
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    protected abstract Log getLogger();
    
    //反射方式初始化每一个变量对应的值
    public void init(FilterConfig filterConfig) throws ServletException {
        
        @SuppressWarnings("unchecked") // Servlet 2.5 doesn't use generics
        Enumeration paramNames = filterConfig.getInitParameterNames();//所有设置的参数集合
        
        while (paramNames.hasMoreElements()) {
            String paramName = (String) paramNames.nextElement();//获取每一个参数
            //paramName是过滤器中的一个属性,因此为该过滤器的该属性设置具体的值
            if (!IntrospectionUtils.setProperty(this, paramName,//注意:此时的this 不是FilterBase,而是最终实现的具体的类,即子类
                    filterConfig.getInitParameter(paramName))) {//filterConfig.getInitParameter(paramName)就是获取具体的值
                String msg = sm.getString("filterbase.noSuchProperty",
                        paramName, this.getClass().getName());
                if (isConfigProblemFatal()) {//说明是致命的,因此结束程序
                    throw new ServletException(msg);
                } else {//说明该filter不是致命的错误,因此打印日志
                    getLogger().warn(msg);
                }
            }
        }    
    }

    public void destroy() {
        // NOOP
    }

    /**
     * Determines if an exception when calling a setter or an unknown
     * configuration attribute triggers the failure of the this filter which in
     * turn will prevent the web application from starting.
     *
     * @return <code>true</code> if a problem should trigger the failure of this
     *         filter, else <code>false</code>
     * 配置文件是否是致命的
     * true表示致命的,因此过滤器过不去,则会有异常,导致失败
     * false表示非致命的过滤器,即使不通过,也就是打印一个日志
     */
    protected boolean isConfigProblemFatal() {
        return false;
    }
}
