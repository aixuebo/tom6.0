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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometEvent;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Valve that implements the default basic behavior for the
 * <code>StandardHost</code> container implementation.
 * <p>
 * <b>USAGE CONSTRAINT</b>:  This implementation is likely to be useful only
 * when processing HTTP requests.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id: StandardHostValve.java 939336 2010-04-29 15:00:41Z kkolinko $
 * 
 * 
 * Host级别的基础value做了什么工作
 * 
1.void invoke(Request request, Response response) 和 event(Request request, Response response, CometEvent event)方法逻辑一样
a.找到请求对应的Context项目对象
Context context = request.getContext();
b.调用context的管道处理请求
context.getPipeline().getFirst().invoke(request, response);
c.当管道处理请求后,继续做一些事儿,注意测试servlet都已经全部请求回来了,因此主要的还是对response做很多处理,以及记录请求时间等操作
d.request.getSession(false); 为请求创建session,目的是符合servlet规范
e.response.setSuspended(false);
f.Throwable t = (Throwable) request.getAttribute(Globals.EXCEPTION_ATTR);获取请求处理中是否有异常,返回异常对象
g.是处理异常,还是处理状态
        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }
h.继续给其他Host的value处理

3.status(Request request, Response response) 说明程序正常请求结束.没有出现异常
a.获取返回码int statusCode = response.getStatus();
b.Context context = request.getContext(); 获取conext项目对象
c.if (!response.isError()) return 如果response返回的没有异常,则不再处理该任务了,说明完全正确
d.以下内容说明response返回的是一个异常状态码
e.通过状态号码找到对应的错误页面
ErrorPage errorPage = context.findErrorPage(statusCode);
f.设置属性信息
request.setAttribute(Globals.STATUS_CODE_ATTR,new Integer(statusCode));//存储response返回的状态码----javax.servlet.error.status_code
request.setAttribute(Globals.ERROR_MESSAGE_ATTR, message);//如果response返回的错误码,此时存储对应的错误信息文字---javax.servlet.error.message
request.setAttribute(ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,errorPage.getLocation());//比如 出现response错误码,则跳转到错误页面,该key对应的value就是错误地址url----org.apache.catalina.core.DISPATCHER_REQUEST_PATH
request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,new Integer(ApplicationFilterFactory.ERROR));//比如 出现response错误码,则跳转到错误页面,该key对应的value就是错误类型原因导致的跳转----org.apache.catalina.core.DISPATCHER_TYPE
因为上面是可能要重定向到其他页面的,所以都要设置在request属性里面

Wrapper wrapper = request.getWrapper();找到对应的servlet
request.setAttribute(Globals.SERVLET_NAME_ATTR,wrapper.getName());//说明在请求哪个servlet的时候出现的错误---javax.servlet.error.servlet_name
request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,request.getRequestURI());//请求什么url的时候出现的错误----javax.servlet.error.request_uri
custom(request, response, errorPage) 内部跳转到错误页面,使用include或者forward 内部跳转到错误页面

4.throwable(Request request, Response response,Throwable throwable)  处理请求过程中出现异常的情况
a.Context context = request.getContext();获取项目对象
b.找到异常对应的错误页面,内部跳转到错误页面即可,该操作与status中f步骤差不多

总结:
1.基本上没做什么工作,就是找到对应的conext,走conext的流程而已。
2.出现异常的时候,或者定义好的错误页面的时候,跳转到错误页面
3.该类存在的意义就是可以自定义一些value切入进来,此时的request和response可以监控该host下所有的项目，所有servlet的都可以被监控到

 * 
 */

final class StandardHostValve
    extends ValveBase {


    private static Log log = LogFactory.getLog(StandardHostValve.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information related to this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardHostValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Valve implementation.
     */
    public String getInfo() {

        return (info);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Select the appropriate child Context to process this request,
     * based on the specified request URI.  If no matching Context can
     * be found, return an appropriate HTTP error.
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

        // Select the Context to be used for this Request
        Context context = request.getContext();
        if (context == null) {
            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        // Bind the context CL to the current thread
        if( context.getLoader() != null ) {
            // Not started - it should check for availability first
            // This should eventually move to Engine, it's generic.
            Thread.currentThread().setContextClassLoader
                    (context.getLoader().getClassLoader());
        }

        // Ask this Context to process this request
        context.getPipeline().getFirst().invoke(request, response);

        // Access a session (if present) to update last accessed time, based on a
        // strict interpretation of the specification
        if (Globals.STRICT_SERVLET_COMPLIANCE) {
            request.getSession(false);
        }

        // Error page processing
        response.setSuspended(false);

        Throwable t = (Throwable) request.getAttribute(Globals.EXCEPTION_ATTR);

        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }

        // Restore the context classloader
        Thread.currentThread().setContextClassLoader
            (StandardHostValve.class.getClassLoader());

    }


    /**
     * Process Comet event.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve context used to forward to the next Valve
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    public final void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {

        // Select the Context to be used for this Request
        Context context = request.getContext();

        // Bind the context CL to the current thread
        //因为要接下来去Context的value,因此要切换成Context的类加载器
        if( context.getLoader() != null ) {
            // Not started - it should check for availability first
            // This should eventually move to Engine, it's generic.
            Thread.currentThread().setContextClassLoader
                    (context.getLoader().getClassLoader());
        }

        // Ask this Context to process this request
        context.getPipeline().getFirst().event(request, response, event);

        // Access a session (if present) to update last accessed time, based on a
        // strict interpretation of the specification
        //是否严格要求servlet合规
        //为用户创建session对象
        if (Globals.STRICT_SERVLET_COMPLIANCE) {
            request.getSession(false);
        }

        // Error page processing
        response.setSuspended(false);

        //确定请求过程中是否有异常,返回异常对象进行绑定该key
        Throwable t = (Throwable) request.getAttribute(Globals.EXCEPTION_ATTR);

        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }

        //因为要接下来去Host的其他value,因此要切换成host的类加载器
        // Restore the context classloader
        Thread.currentThread().setContextClassLoader
            (StandardHostValve.class.getClassLoader());

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Handle the specified Throwable encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void throwable(Request request, Response response,
                             Throwable throwable) {
        Context context = request.getContext();
        if (context == null)
            return;

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // If this is an aborted request from a client just log it and return
        if (realError instanceof ClientAbortException ) {
            if (log.isDebugEnabled()) {
                log.debug
                    (sm.getString("standardHost.clientAbort",
                        realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = findErrorPage(context, throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = findErrorPage(context, realError);
        }

        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));
            request.setAttribute
                (Globals.STATUS_CODE_ATTR,
                 new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR,
                              throwable.getMessage());
            request.setAttribute(Globals.EXCEPTION_ATTR,
                              realError);
            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());
            request.setAttribute(Globals.EXCEPTION_TYPE_ATTR,
                              realError.getClass());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        } else {
            // A custom error-page has not been defined for the exception
            // that was thrown during request processing. Check if an
            // error-page for error code 500 was specified and if so,
            // send that page back as the response.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // The response is an error
            response.setError();

            status(request, response);
        }


    }


    /**
     * Handle the HTTP status code (and corresponding message) generated
     * while processing the specified Request to produce the specified
     * Response.  Any exceptions that occur during generation of the error
     * report are logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     */
    protected void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // Handle a custom error page for this status code
        Context context = request.getContext();
        if (context == null)
            return;

        /* Only look for error pages when isError() is set.
         * isError() is set when response.sendError() is invoked. This
         * allows custom error pages without relying on default from
         * web.xml.
         */
        if (!response.isError())
            return;

        //通过状态号码找到对应的错误页面
        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute(Globals.STATUS_CODE_ATTR,
                              new Integer(statusCode));//存储response返回的状态码----javax.servlet.error.status_code

            String message = response.getMessage();
            if (message == null)
                message = "";
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR, message);//如果response返回的错误码,此时存储对应的错误信息文字---javax.servlet.error.message
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());//比如 出现response错误码,则跳转到错误页面,该key对应的value就是错误地址url----org.apache.catalina.core.DISPATCHER_REQUEST_PATH
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));//比如 出现response错误码,则跳转到错误页面,该key对应的value就是错误类型原因导致的跳转----org.apache.catalina.core.DISPATCHER_TYPE


            Wrapper wrapper = request.getWrapper();//找到对应的servlet
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());//说明在请求哪个servlet的时候出现的错误---javax.servlet.error.servlet_name
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());//请求什么url的时候出现的错误----javax.servlet.error.request_uri
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (ClientAbortException e) {
                    // Ignore
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }

    }


    /**
     * Find and return the ErrorPage instance for the specified exception's
     * class, or an ErrorPage instance for the closest superclass for which
     * there is such a definition.  If no associated ErrorPage instance is
     * found, return <code>null</code>.
     *
     * @param context The Context in which to search
     * @param exception The exception for which to find an ErrorPage
     */
    protected static ErrorPage findErrorPage
        (Context context, Throwable exception) {

        if (exception == null)
            return (null);
        Class<?> clazz = exception.getClass();
        String name = clazz.getName();
        while (!Object.class.equals(clazz)) {
            ErrorPage errorPage = context.findErrorPage(name);
            if (errorPage != null)
                return (errorPage);
            clazz = clazz.getSuperclass();
            if (clazz == null)
                break;
            name = clazz.getName();
        }
        return (null);

    }


    /**
     * Handle an HTTP status code or Java exception by forwarding control
     * to the location included in the specified errorPage object.  It is
     * assumed that the caller has already recorded any request attributes
     * that are to be forwarded to this page.  Return <code>true</code> if
     * we successfully utilized the specified error page location, or
     * <code>false</code> if the default error report should be rendered.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param errorPage The errorPage directive we are obeying
     * 内部跳转到错误页面
     */
    protected boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("Processing " + errorPage);

        request.setPathInfo(errorPage.getLocation());//设置重定向的url

        try {
            // Forward control to the specified location
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());

            if (response.isCommitted()) {
                // Response is committed - including the error page is the
                // best we can do 
                rd.include(request.getRequest(), response.getResponse());
            } else {
                // Reset the response (keeping the real error code and message)
                response.resetBuffer(true);

                rd.forward(request.getRequest(), response.getResponse());

                // If we forward, the response is suspended again
                response.setSuspended(false);
            }

            // Indicate that we have successfully processed this custom page
            return (true);

        } catch (Throwable t) {

            // Report our failure to process this custom page
            container.getLogger().error("Exception Processing " + errorPage, t);
            return (false);

        }

    }


}
