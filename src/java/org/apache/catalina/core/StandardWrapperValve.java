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


package org.apache.catalina.core;


import java.io.IOException;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometEvent;
import org.apache.catalina.CometProcessor;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.log.SystemLogHandler;

/**
 * Valve that implements the default basic behavior for the
 * <code>StandardWrapper</code> container implementation.
 *
 * @author Craig R. McClanahan
 * @version $Id: StandardWrapperValve.java 939336 2010-04-29 15:00:41Z kkolinko $
 * 
 * Wrapper容器级别的基础value做了什么工作---因为Wrapper控制的是servlet,因此该容器是最核心的,但是他只是影响了一个servlet而已,不会影响很大范围的其他servlet
1.统计信息:以下在多线程环境下统计,因此是volatile
    private volatile long processingTime;//处理所有的servlet的总时间
    private volatile long maxTime;//处理servlet的最长的时间是多少
    private volatile long minTime = Long.MAX_VALUE;//处理servlet的最短的时间是多少
    private volatile int requestCount;//一共多少次servlet被请求了
    private volatile int errorCount;//失败的servlet请求次数
2.exception(Request request, Response response,Throwable exception) 在处理servlet的过程中出现异常,如何处理
    	request.setAttribute(Globals.EXCEPTION_ATTR, exception);//设置异常对象到javax.servlet.error.exception的key中
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);//设置状态码
3.invoke(Request request, Response response)
a.requestCount++;//记录一共多少次servlet被请求了
b.获取servlet容器的父容器,即项目对象所在容器Context,判断该容器是否可用,如果不可用,说项目都不可用,肯定有问题
response.sendError 设置500状态码
c.如果项目本身是可用的,则判断该servlet是否可用,wrapper.isUnavailable()
如果servlet不可用.则看long available = wrapper.getAvailable();servlet的生命期,如果是一个long值,则告诉客户端你过一会再来请求我,可能就好了。
response.setDateHeader("Retry-After", available);
response.sendError(503)
如果available是long的max,说明该页面一直都会不存在,所以直接就返回404,告诉客户端以后你就别访问我了
response.sendError(504)
d.servlet = wrapper.allocate();//分配一个servlet实例,该过程可能是分配的servlet要确保线程安全
如果创建过程中失败,则调用第2步骤
e.对CometProcessor这种servlet单独实现的逻辑,暂时不懂什么意思,后续再研究
f.设置分发信息
request.setAttribute(ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,request.getRequestPathMB());//设置要发送给哪个servlet的url----org.apache.catalina.core.DISPATCHER_REQUEST_PATH
request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,REQUEST);//说明分发类型是正常的request请求分发----org.apache.catalina.core.DISPATCHER_TYPE
g.创建filter过滤器链条对象
ApplicationFilterFactory factory = ApplicationFilterFactory.getInstance();
ApplicationFilterChain filterChain = factory.createFilterChain(request, wrapper, servlet);
h.request.setComet(false); 意义不明
i.String jspFile = wrapper.getJspFile();获取该servlet是否是jsp文件
            if (jspFile != null)
            	request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            else
            	request.removeAttribute(Globals.JSP_FILE_ATTR);
j.filterChain.doFilter(request.getRequest(), response.getResponse());
 该方法执行filter以及servlet的service方法
k.filterChain.release(); 释放该filter内容
l.wrapper.deallocate(servlet);销毁该servlet
m.如果servlet不可用,则将其从加载器中卸载掉
      if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
n.统计执行所有servlet的总时间、最大事件和最小时间
        long time=t2-t1;//处理的总事件
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;

4.event(Request request, Response response, CometEvent event) 同invoke逻辑一样

总结:
1.统计servlet的执行总次数以及总时间等信息
2.创建servlet实例,执行servlet中init方法,过滤器方法 以及service方法,以及销毁方法
可见每一个servlet生命周期
3.出现异常的时候,将异常信息设置到request中,由上一次Context或者Host处理异常信息
4.该类存在的意义就是可以自定义一些value切入进来,此时的request和response可以监控该servlet的请求
 */

final class StandardWrapperValve
    extends ValveBase {

    // ----------------------------------------------------- Instance Variables


    // Some JMX statistics. This vavle is associated with a StandardWrapper.
    // We expose the StandardWrapper as JMX ( j2eeType=Servlet ). The fields
    // are here for performance.
	//以下在多线程环境下统计,因此是volatile
    private volatile long processingTime;//处理所有的servlet的总时间
    private volatile long maxTime;//处理servlet的最长的时间是多少
    private volatile long minTime = Long.MAX_VALUE;//处理servlet的最短的时间是多少
    private volatile int requestCount;//一共多少次servlet被请求了
    private volatile int errorCount;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * Invoke the servlet we are managing, respecting the rules regarding
     * servlet lifecycle and SingleThreadModel support.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve context used to forward to the next Valve
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Initialize local variables we may need
        boolean unavailable = false;//true表示该servlet不能用,比如所属的项目context不可用
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        requestCount++;//记录一共多少次servlet被请求了
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();
        
        // Check for the application being marked unavailable
        if (!context.getAvailable()) {
        	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardContext.isUnavailable"));
            unavailable = true;//设置失效
        }

        // Check for the servlet being marked unavailable
        if (!unavailable && wrapper.isUnavailable()) {//说明此时项目可用,但是servlet本身是不可用的
            container.getLogger().info(sm.getString("standardWrapper.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {//说明该servlet是有有效期的,说明在一段期间内他是不存在的,后期可以继续请求我,我可能存在
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        sm.getString("standardWrapper.isUnavailable",
                                wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {//说明servlet没有有效期,直接返回404就好,根本不存在
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("standardWrapper.notFound",
                                wrapper.getName()));
            }
            unavailable = true;//设置失效
        }

        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();//分配一个servlet实例
            }
        } catch (UnavailableException e) {
            container.getLogger().error(
                    sm.getString("standardWrapper.allocateException",
                            wrapper.getName()), e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
            	response.setDateHeader("Retry-After", available);
            	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);//创建servlet实例失败
            servlet = null;
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);//创建servlet实例失败
            servlet = null;
        }

        // Identify if the request is Comet related now that the servlet has been allocated
        boolean comet = false;
        if (servlet instanceof CometProcessor 
                && request.getAttribute("org.apache.tomcat.comet.support") == Boolean.TRUE) {
            comet = true;
            request.setComet(true);
        }
        
        // Acknowledge the request
        try {
            response.sendAcknowledgement();
        } catch (IOException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().warn(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }
        MessageBytes requestPathMB = null;
        if (request != null) {
            requestPathMB = request.getRequestPathMB();
        }
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
             ApplicationFilterFactory.REQUEST_INTEGER);//说明分发类型是正常的request请求分发
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
             requestPathMB);//设置请求路径
        // Create the filter chain for this request
        ApplicationFilterFactory factory =
            ApplicationFilterFactory.getInstance();
        ApplicationFilterChain filterChain =
            factory.createFilterChain(request, wrapper, servlet);
        // Reset comet flag value after creating the filter chain
        request.setComet(false);

        // Call the filter chain for this request
        // NOTE: This also calls the servlet's service() method
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
            	request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);//请求的servlet是一个jsp文件,因此该key对应的value就是jsp文件
            else
            	request.removeAttribute(Globals.JSP_FILE_ATTR);
            if ((servlet != null) && (filterChain != null)) {
                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        if (comet) {
                            filterChain.doFilterEvent(request.getEvent());
                            request.setComet(true);
                        } else {
                            filterChain.doFilter(request.getRequest(), 
                                    response.getResponse());
                        }
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    if (comet) {
                        request.setComet(true);
                        filterChain.doFilterEvent(request.getEvent());
                    } else {
                        filterChain.doFilter
                            (request.getRequest(), response.getResponse());
                    }
                }

            }
            request.removeAttribute(Globals.JSP_FILE_ATTR);
        } catch (ClientAbortException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            //            throwable = e;
            //            exception(request, response, e);
            wrapper.unavailable(e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString("standardWrapper.serviceException",
                                 wrapper.getName()), rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            if (request.isComet()) {
                // If this is a Comet request, then the same chain will be used for the
                // processing of all subsequent events.
                filterChain.reuse();//说明这个是Comet请求,filter链条将要重新走一下
            } else {
                filterChain.release();
            }
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);//一个servlet执行完毕之后,对该servlet进行销毁
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();//销毁该servlet
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }
        long t2=System.currentTimeMillis();

        long time=t2-t1;//处理的总事件
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;

    }


    /**
     * Process a Comet event. The main differences here are to not use sendError
     * (the response is committed), to avoid creating a new filter chain
     * (which would work but be pointless), and a few very minor tweaks. 
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    public void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {
        
        // Initialize local variables we may need
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        // FIXME: Add a flag to count the total amount of events processed ? requestCount++;
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();

        // Check for the application being marked unavailable
        boolean unavailable = !context.getAvailable() || wrapper.isUnavailable();
        
        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            // The response is already committed, so it's not possible to do anything
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
            servlet = null;
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        MessageBytes requestPathMB = null;
        if (request != null) {
            requestPathMB = request.getRequestPathMB();
        }
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
             ApplicationFilterFactory.REQUEST_INTEGER);
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
             requestPathMB);
        // Get the current (unchanged) filter chain for this request
        ApplicationFilterChain filterChain = 
            (ApplicationFilterChain) request.getFilterChain();

        // Call the filter chain for this request
        // NOTE: This also calls the servlet's event() method
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
                request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            else
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            if ((servlet != null) && (filterChain != null)) {

                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        filterChain.doFilterEvent(request.getEvent());
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    filterChain.doFilterEvent(request.getEvent());
                }

            }
            request.removeAttribute(Globals.JSP_FILE_ATTR);
        } catch (ClientAbortException e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString("standardWrapper.serviceException",
                                 wrapper.getName()), rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            filterChain.reuse();
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        long t2=System.currentTimeMillis();

        long time=t2-t1;
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Handle the specified ServletException encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param exception The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    private void exception(Request request, Response response,
                           Throwable exception) {
    	request.setAttribute(Globals.EXCEPTION_ATTR, exception);//设置异常对象到javax.servlet.error.exception的key中
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);//设置状态码

    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public void setMinTime(long minTime) {
        this.minTime = minTime;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    // Don't register in JMX

    public ObjectName createObjectName(String domain, ObjectName parent)
            throws MalformedObjectNameException
    {
        return null;
    }
}
