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

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.PeriodicEventListener;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.Wrapper;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.InstanceSupport;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Standard implementation of the <b>Wrapper</b> interface that represents
 * an individual servlet definition.  No child Containers are allowed, and
 * the parent Container must be a Context.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id: StandardWrapper.java 1431946 2013-01-11 09:23:06Z kkolinko $
 */
public class StandardWrapper
    extends ContainerBase
    implements ServletConfig, Wrapper, NotificationEmitter {

    protected static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( StandardWrapper.class );

    protected static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardWrapper component with the default basic Valve.
     * 解析web.xml的web-app/servlet标签的时候,创建一个Wrapper对象
     * 调用addChild方法,添加子容器
     */
    public StandardWrapper() {

        super();
        swValve=new StandardWrapperValve();
        pipeline.setBasic(swValve);
        broadcaster = new NotificationBroadcasterSupport();

        if (restrictedServlets == null) {
            restrictedServlets = new Properties();
            try {
                InputStream is = 
                    this.getClass().getClassLoader().getResourceAsStream
                        ("org/apache/catalina/core/RestrictedServlets.properties");
                if (is != null) {
                    restrictedServlets.load(is);
                } else {
                    log.error(sm.getString("standardWrapper.restrictedServletsResource"));
                }
            } catch (IOException e) {
                log.error(sm.getString("standardWrapper.restrictedServletsResource"), e);
            }
        }
        
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The date and time at which this servlet will become available (in
     * milliseconds since the epoch), or zero if the servlet is available.
     * If this value equals Long.MAX_VALUE, the unavailability of this
     * servlet is considered permanent.
     * 返回一个servlet的有效时间,如果该时间为Long.MAX_VALUE,则说明每一个请求过来都要返回SC_NOT_FOUND错误码,即404.
     * 
     */
    protected long available = 0L;
    
    /**
     * The broadcaster that sends j2ee notifications. 
     */
    protected NotificationBroadcasterSupport broadcaster = null;
    

    /**
     * The facade associated with this wrapper.
     */
    protected StandardWrapperFacade facade = new StandardWrapperFacade(this);

    /**
     * The descriptive information string for this implementation.
     */
    protected static final String info = "org.apache.catalina.core.StandardWrapper/1.0";


    /**
     * The (single) initialized instance of this servlet.
     */
    protected Servlet instance = null;


    /**
     * The support object for our instance listeners.
     * 添加web.xml中的 listen-class对象
     */
    protected InstanceSupport instanceSupport = new InstanceSupport(this);


    /**
     * The context-relative URI of the JSP file for this servlet.
     * 解析web.xml中web-app/servlet/jsp-file标签
     * 该值被设置表示该servlet对应的是一个jsp文件,而不是servlet-calss
     */
    protected String jspFile = null;


    /**
     * The load-on-startup order value (negative value means load on
     * first call) for this servlet.
     * 解析web.xml中web-app/servlet/load-on-startup标签,设置servlet的优先级
     */
    protected int loadOnStartup = -1;


    /**
     * Mappings associated with the wrapper.
解析web.xml中的web-app/servlet-mapping标签
两个参数为子标签servlet-name、url-pattern
通过servlet-name,可以找到目前的Wrapper对象,那么为该对象添加addMapping方法,参数就是url-pattern

即:存储web-app/servlet-mapping/url-pattern集合,因为一个servletName对应多个url-pattern匹配规则
     */
    protected ArrayList mappings = new ArrayList();


    /**
     * The initialization parameters for this servlet, keyed by
     * parameter name.
     * 解析web.xml中"web-app/servlet/init-param标签,为每一个servlet添加属性
     */
    protected HashMap parameters = new HashMap();


    /**
     * The security role references for this servlet, keyed by role name
     * used in the servlet.  The corresponding value is the role name of
     * the web application itself.
     * 解析web.xml中web-app/servlet/security-role-ref标签
     * 用于解析安全访问相关事情
     * 该标签下有两个表情role-link、role-name
     * key是name,value是link
     */
    protected HashMap references = new HashMap();


    /**
     * The run-as identity for this servlet.
     * 解析web.xml中web-app/servlet/run-as/role-name标签
     * 属于EJB知识
     */
    protected String runAs = null;

    /**
     * The notification sequence number.
     */
    protected long sequenceNumber = 0;

    /**
     * The fully qualified servlet class name for this servlet.
     * 解析web.xml的web-app/servlet/servlet-class标签
     */
    protected String servletClass = null;


    /**
     * Does this servlet implement the SingleThreadModel interface?
     * 是不是单线程模型,如果是false,表示每次都返回同一个servlet实例
如果servlet没有实现SingleThreadModel模型,即多线程模型,则他仅仅进行init方法,然后立即返回该实例.
如果servlet实现了SingleThreadModel模型,即多线程模型,则该servlet必须确保不能再被重复分配,直到该servlet调用deallocate方法销毁之后才允许再次使用

servlet instanceof SingleThreadModel 是否是该实例
     */
    protected boolean singleThreadModel = false;


    /**
     * Are we unloading our servlet instance at the moment?
     * true表示此时此刻,servlet正在进行销毁工作,即正在执行unload方法
     */
    protected boolean unloading = false;


    /**
     * Maximum number of STM instances.
     * 最多允许创建多少个该servlet实例
     * 参见allocate方法
     */
    protected int maxInstances = 20;

    /**
     * The count of allocations that are currently active (even if they
     * are for the same instance, as will be true on a non-STM servlet).
     * 已经正在使用的servlet实例个数
     * 参见allocate方法
     */
    protected AtomicInteger countAllocated = new AtomicInteger(0);
    
    /**
     * Number of instances currently loaded for a STM servlet.
     * 当前实例化该servlet的class实例次数,即new该servlet类的次数
     * 参见allocate方法
     */
    protected int nInstances = 0;


    /**
     * Stack containing the STM instances.
     * 装servlet实例的队列
     * 参见allocate方法
     */
    protected Stack instancePool = null;

    
    /**
     * Wait time for servlet unload in ms.
     * 获取父类StandardContext.getUnloadDelay()值
     * unload销毁时,缓冲池中有事例时,进行延迟休息间隔,参见unload方法
     */
    protected long unloadDelay = 2000;
    

    /**
     * True if this StandardWrapper is for the JspServlet
     * 说明web.xml中web-app/servlet/jsp-file标签存在,即该servlet是一个jsp文件
     * 或者设置的servlet的class是org.apache.jasper.servlet.JspServlet,他是一个特殊的class,是jsp对应的class
     */
    protected boolean isJspServlet;


    /**
     * The ObjectName of the JSP monitoring mbean
     */
    protected ObjectName jspMonitorON;


    /**
     * Should we swallow System.out
     * 获取父类StandardContext.getSwallowOutput()值
     * 是否允许我们将日志输出到其他地方
     */
    protected boolean swallowOutput = false;

    // To support jmx attributes
    protected StandardWrapperValve swValve;
    //加载servlet的时间,参见loadServlet方法
    protected long loadTime=0;
    //加载servlet的时候,生成classloader的时间,参见loadServlet方法
    protected int classLoadTime=0;
    
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>Servlet.init</code> is invoked.
     */
    protected static Class[] classType = new Class[]{ServletConfig.class};
    
    
    /**
     * Static class array used when the SecurityManager is turned on and 
     * <code>Servlet.service</code>  is invoked.
     */                                                 
    protected static Class[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};
    
    /**
     * Restricted servlets (which can only be loaded by a privileged webapp).
     * 读取org/apache/catalina/core/RestrictedServlets.properties文件
     * 参见isServletAllowed方法,判断该servlet是否允许被加载
     */
    protected static Properties restrictedServlets = null;

    //操作paramter属性,即servlet/init标签的锁
    private final ReentrantReadWriteLock parametersLock = new ReentrantReadWriteLock();
    //操作web-app/servlet-mapping标签标签的锁
    private final ReentrantReadWriteLock mappingsLock = new ReentrantReadWriteLock();
    //操作web-app/servlet/security-role-ref标签的锁,参见references
    private final ReentrantReadWriteLock referencesLock = new ReentrantReadWriteLock();

    // ------------------------------------------------------------- Properties


    /**
     * 返回一个servlet的有效时间,如果该时间为Long.MAX_VALUE,则说明每一个请求过来都要返回SC_NOT_FOUND错误码,即404.
     * Return the available date/time for this servlet, in milliseconds since
     * the epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error.  If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.  If it is zero,
     * the servlet is currently available.
     * 多久以后你可以再次访问我,可能我就变成有效的了
     */
    public long getAvailable() {

        return (this.available);

    }


    /**
     * Set the available date/time for this servlet, in milliseconds since the
     * epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error. If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available) {

        long oldAvailable = this.available;
        if (available > System.currentTimeMillis())
            this.available = available;
        else
            this.available = 0L;
        support.firePropertyChange("available", new Long(oldAvailable),
                                   new Long(this.available));

    }


    /**
     * Return the number of active allocations of this servlet, even if they
     * are all for the same instance (as will be true for servlets that do
     * not implement <code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {

        return (this.countAllocated.get());

    }

    /**
     * 获取该servlet所在的引擎名字
     */
    public String getEngineName() {
        return ((StandardContext)getParent()).getEngineName();
    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }


    /**
     * Return the InstanceSupport object for this Wrapper instance.
     */
    public InstanceSupport getInstanceSupport() {

        return (this.instanceSupport);

    }


    /**
     * Return the context-relative URI of the JSP file for this servlet.
     */
    public String getJspFile() {

        return (this.jspFile);

    }


    /**
     * Set the context-relative URI of the JSP file for this servlet.
     *
     * @param jspFile JSP file URI
     * 解析web.xml中web-app/servlet/jsp-file标签
     */
    public void setJspFile(String jspFile) {

        String oldJspFile = this.jspFile;
        this.jspFile = jspFile;
        support.firePropertyChange("jspFile", oldJspFile, this.jspFile);

        // Each jsp-file needs to be represented by its own JspServlet and
        // corresponding JspMonitoring mbean, because it may be initialized
        // with its own init params
        isJspServlet = true;

    }


    /**
     * Return the load-on-startup order value (negative value means
     * load on first call).
     */
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup < 0) {
            /*
             * JspServlet must always be preloaded, because its instance is
             * used during registerJMX (when registering the JSP
             * monitoring mbean)
             */
             return Integer.MAX_VALUE;
        } else {
            return (this.loadOnStartup);
        }
    }


    /**
     * Set the load-on-startup order value (negative value means
     * load on first call).
     *
     * @param value New load-on-startup value
     * 解析web.xml中web-app/servlet/load-on-startup标签,设置servlet的优先级
     */
    public void setLoadOnStartup(int value) {

        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup",
                                   new Integer(oldLoadOnStartup),
                                   new Integer(this.loadOnStartup));

    }



    /**
     * Set the load-on-startup order value from a (possibly null) string.
     * Per the specification, any missing or non-numeric value is converted
     * to a zero, so that this servlet will still be loaded at startup
     * time, but in an arbitrary order.
     *
     * @param value New load-on-startup value
     * 解析web.xml中web-app/servlet/load-on-startup标签,设置servlet的优先级
     */
    public void setLoadOnStartupString(String value) {

        try {
            setLoadOnStartup(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            setLoadOnStartup(0);
        }
    }

    public String getLoadOnStartupString() {
        return Integer.toString( getLoadOnStartup());
    }


    /**
     * Return maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     */
    public int getMaxInstances() {

        return (this.maxInstances);

    }


    /**
     * Set the maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     *
     * @param maxInstances New value of maxInstances
     */
    public void setMaxInstances(int maxInstances) {

        int oldMaxInstances = this.maxInstances;
        this.maxInstances = maxInstances;
        support.firePropertyChange("maxInstances", oldMaxInstances,
                                   this.maxInstances);

    }


    /**
     * Set the parent Container of this Wrapper, but only if it is a Context.
     *
     * @param container Proposed parent Container
     * 父类一定是Container对象
     */
    public void setParent(Container container) {

        if ((container != null) &&
            !(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardWrapper.notContext"));
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext)container).getSwallowOutput();
            unloadDelay = ((StandardContext)container).getUnloadDelay();
        }
        super.setParent(container);

    }


    /**
     * Return the run-as identity for this servlet.
     */
    public String getRunAs() {

        return (this.runAs);

    }


    /**
     * Set the run-as identity for this servlet.
     *
     * @param runAs New run-as identity value
     * 解析web.xml中web-app/servlet/run-as/role-name标签
     * 属于EJB知识
     */
    public void setRunAs(String runAs) {

        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);

    }


    /**
     * Return the fully qualified servlet class name for this servlet.
     */
    public String getServletClass() {

        return (this.servletClass);

    }


    /**
     * Set the fully qualified servlet class name for this servlet.
     *
     * @param servletClass Servlet class name
     * 解析web.xml的web-app/servlet/servlet-class标签
     */
    public void setServletClass(String servletClass) {

        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass,
                                   this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }



    /**
     * Set the name of this servlet.  This is an alias for the normal
     * <code>Container.setName()</code> method, and complements the
     * <code>getServletName()</code> method required by the
     * <code>ServletConfig</code> interface.
     *
     * @param name The new name of this servlet
     * 解析web.xml的web-app/servlet/servlet-name标签
     */
    public void setServletName(String name) {

        setName(name);

    }


    /**
     * Return <code>true</code> if the servlet class represented by this
     * component implements the <code>SingleThreadModel</code> interface.
     * 先加载servlet,在loadServlet方法中会设置singleThreadModel属性,因此最终返回singleThreadModel属性即可
     */
    public boolean isSingleThreadModel() {

        try {
            loadServlet();
        } catch (Throwable t) {
            ;
        }
        return (singleThreadModel);

    }


    /**
     * Is this servlet currently unavailable?
     */
    public boolean isUnavailable() {

        if (available == 0L)
            return (false);
        else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return (false);
        } else
            return (true);

    }


    /**
     * Gets the names of the methods supported by the underlying servlet.
     *
     * This is the same set of methods included in the Allow response header
     * in response to an OPTIONS request method processed by the underlying
     * servlet.
     *
     * @return Array of names of the methods supported by the underlying
     * servlet
     * 该servelet支持哪些方法
     */
    public String[] getServletMethods() throws ServletException {

        Class servletClazz = loadServlet().getClass();
        if (!javax.servlet.http.HttpServlet.class.isAssignableFrom(
                                                        servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        HashSet allow = new HashSet();
        allow.add("TRACE");
        allow.add("OPTIONS");
	
        Method[] methods = getAllDeclaredMethods(servletClazz);
        for (int i=0; methods != null && i<methods.length; i++) {
            Method m = methods[i];
	    
            if (m.getName().equals("doGet")) {
                allow.add("GET");
                allow.add("HEAD");
            } else if (m.getName().equals("doPost")) {
                allow.add("POST");
            } else if (m.getName().equals("doPut")) {
                allow.add("PUT");
            } else if (m.getName().equals("doDelete")) {
                allow.add("DELETE");
            }
        }

        String[] methodNames = new String[allow.size()];
        return (String[]) allow.toArray(methodNames);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    public void backgroundProcess() {
        super.backgroundProcess();
        
        if (!started)
            return;
        
        if (getServlet() != null && (getServlet() instanceof PeriodicEventListener)) {
            ((PeriodicEventListener) getServlet()).periodicEvent();
        }
    }
    
    
    /**
     * Extract the root cause from a servlet exception.
     * 
     * @param e The servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null)
                rootCause = rootCauseCheck;
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }


    /**
     * Refuse to add a child Container, because Wrappers are the lowest level
     * of the Container hierarchy.
     *
     * @param child Child container to be added
     * 最后一个容器,不允许添加子容器了
     */
    public void addChild(Container child) {

        throw new IllegalStateException
            (sm.getString("standardWrapper.notChild"));

    }


    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     * 解析web.xml中"web-app/servlet/init-param标签,为每一个servlet添加属性
     */
    public void addInitParameter(String name, String value) {

        try {
            parametersLock.writeLock().lock();
            parameters.put(name, value);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("addInitParameter", name);

    }


    /**
     * Add a new listener interested in InstanceEvents.
     *
     * @param listener The new listener
     * 添加web.xml中的 listen-class对象集合
     */
    public void addInstanceListener(InstanceListener listener) {

        instanceSupport.addInstanceListener(listener);

    }


    /**
     * Add a mapping associated with the Wrapper.
     *
     * @param mapping The new wrapper mapping
解析web.xml中的web-app/servlet-mapping标签
两个参数为子标签servlet-name、url-pattern
通过servlet-name,可以找到目前的Wrapper对象,那么为该对象添加addMapping方法,参数就是url-pattern
     */
    public void addMapping(String mapping) {

        try {
            mappingsLock.writeLock().lock();
            mappings.add(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        fireContainerEvent("addMapping", mapping);

    }


    /**
     * Add a new security role reference record to the set of records for
     * this servlet.
     *
     * @param name Role name used within this servlet
     * @param link Role name used within the web application
     * 解析web.xml中web-app/servlet/security-role-ref标签
     * 用于解析安全访问相关事情
     * 该标签下有两个表情role-link、role-name
     */
    public void addSecurityReference(String name, String link) {

        try {
            referencesLock.writeLock().lock();
            references.put(name, link);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("addSecurityReference", name);

    }


    /**
     * Return the associated servlet instance.
     */
    public Servlet getServlet() {
        return instance;
    }
    
    
    /**
     * Allocate an initialized instance of this Servlet that is ready to have
     * its <code>service()</code> method called.  If the servlet class does
     * not implement <code>SingleThreadModel</code>, the (only) initialized
     * instance may be returned immediately.  If the servlet class implements
     * <code>SingleThreadModel</code>, the Wrapper implementation must ensure
     * that this instance is not allocated again until it is deallocated by a
     * call to <code>deallocate()</code>.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if a loading error occurs
    如果servlet没有实现SingleThreadModel模型,即多线程模型,则他仅仅进行init方法,然后立即返回该实例.
    如果servlet实现了SingleThreadModel模型,即多线程模型,则该servlet必须确保不能再被重复分配,直到该servlet调用deallocate方法销毁之后才允许再次使用
     * 分配一个servlet实例
     */
    public Servlet allocate() throws ServletException {

        // If we are currently unloading this servlet, throw an exception
        if (unloading)
            throw new ServletException(sm.getString("standardWrapper.unloading", getName()));

        boolean newInstance = false;
        
        //不是单线程模型,因此每次返回相同的servlet实例
        // If not SingleThreadedModel, return the same instance every time
        if (!singleThreadModel) {

            // Load and initialize our instance if necessary
          //如果还没有实例化该实例,则要进行实例化servlet
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            if (log.isDebugEnabled())
                                log.debug("Allocating non-STM instance");
                            //实例化servlet
                            instance = loadServlet();
                            // For non-STM, increment here to prevent a race
                            // condition with unload. Bug 43683, test case #3
                            if (!singleThreadModel) {
                                newInstance = true;
                                countAllocated.incrementAndGet();
                            }
                        } catch (ServletException e) {
                            throw e;
                        } catch (Throwable e) {
                            throw new ServletException
                                (sm.getString("standardWrapper.allocate"), e);
                        }
                    }
                }
            }

            if (!singleThreadModel) {
                if (log.isTraceEnabled())
                    log.trace("  Returning non-STM instance");
                // For new instances, count will have been incremented at the
                // time of creation
                //如果刚刚实例化servlet,则直接返回即可,不需要累加,因为累加操作已经在上面的代码中累加过了
                if (!newInstance) {
                    countAllocated.incrementAndGet();
                }
                return (instance);
            }
        }

        /**
         * 实例化servlet队列中获取一个servlet实例
         */
        synchronized (instancePool) {
          //如果目前正在使用的servlet实例 比 已经实例化的实例数量少,则直接从缓冲队列中获取一个即可,同时累加countAllocated数量,即累加正在使用的次数
            while (countAllocated.get() >= nInstances) {
                // Allocate a new instance if possible, or else wait
              //重新创建一个实例,并且存放到缓冲队列中,然后累加实例个数
                if (nInstances < maxInstances) {
                    try {
                        instancePool.push(loadServlet());
                        nInstances++;
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new ServletException
                            (sm.getString("standardWrapper.allocate"), e);
                    }
                } else {//如果缓冲池已经满了,不能继续添加,则要等待
                    try {
                        instancePool.wait();
                    } catch (InterruptedException e) {
                        ;
                    }
                }
            }
            if (log.isTraceEnabled())
                log.trace("  Returning allocated STM instance");
            countAllocated.incrementAndGet();
            return (Servlet) instancePool.pop();

        }

    }


    /**
     * Return this previously allocated servlet to the pool of available
     * instances.  If this servlet class does not implement SingleThreadModel,
     * no action is actually required.
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException if a deallocation error occurs
如果servlet没有实现SingleThreadModel模型,即多线程模型,则他仅仅进行init方法,然后立即返回该实例.
如果servlet实现了SingleThreadModel模型,即多线程模型,则该servlet必须确保不能再被重复分配,直到该servlet调用deallocate方法销毁之后才允许再次使用
一个servlet执行完毕之后,对该servlet进行销毁
     */
    public void deallocate(Servlet servlet) throws ServletException {

        // If not SingleThreadModel, no action is required
      //减少一个被使用实例的次数,如果singleThreadModel=true,说明是单线程模型.则只能有一个实例,因此不需要countAllocated.decrementAndGet()方法被调用,因为后面的线程池中会进行减少操作
        if (!singleThreadModel) {
            countAllocated.decrementAndGet();
            return;
        }

        // Unlock and free this instance
        //从线程池中删除,即将该servlet加入到线程池中,并且减少正在使用的实例个数
        synchronized (instancePool) {
            countAllocated.decrementAndGet();
            instancePool.push(servlet);
            instancePool.notify();
        }

    }


    /**
     * Return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the requested initialization parameter
     */
    public String findInitParameter(String name) {

        try {
            parametersLock.readLock().lock();
            return ((String) parameters.get(name));
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    /**
     * Return the names of all defined initialization parameters for this
     * servlet.
     */
    public String[] findInitParameters() {

        try {
            parametersLock.readLock().lock();
            String results[] = new String[parameters.size()];
            return ((String[]) parameters.keySet().toArray(results));
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    /**
     * Return the mappings associated with this wrapper.
     */
    public String[] findMappings() {

        try {
            mappingsLock.readLock().lock();
            return (String[]) mappings.toArray(new String[mappings.size()]);
        } finally {
            mappingsLock.readLock().unlock();
        }

    }


    /**
     * Return the security role link for the specified security role
     * reference name, if any; otherwise return <code>null</code>.
     *
     * @param name Security role reference used within this servlet
     */
    public String findSecurityReference(String name) {

        try {
            referencesLock.readLock().lock();
            return ((String) references.get(name));
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * Return the set of security role reference names associated with
     * this servlet, if any; otherwise return a zero-length array.
     */
    public String[] findSecurityReferences() {

        try {
            referencesLock.readLock().lock();
            String results[] = new String[references.size()];
            return ((String[]) references.keySet().toArray(results));
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * FIXME: Fooling introspection ...
     * 返回该servlet本身,该方法用处暂时不大
     */
    public Wrapper findMappingObject() {
        return (Wrapper) getMappingObject();
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  Servlets whose classnames begin with
     * <code>org.apache.catalina.</code> (so-called "container" servlets)
     * are loaded by the same classloader that loaded this class, rather than
     * the classloader for the current web application.
     * This gives such classes access to Catalina internals, which are
     * prevented for classes loaded for web applications.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if some other loading problem occurs
     */
    public synchronized void load() throws ServletException {
        instance = loadServlet();
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     * 1.根据servletClass生成servlet实例
     * 2.isServletAllowed(servlet)判断该实例是否是servlet实例
     * 3.操作父类容器
                // Annotation processing
                if (!((Context) getParent()).getIgnoreAnnotations()) {
                    if (getParent() instanceof StandardContext) {
                       ((StandardContext)getParent()).getAnnotationProcessor().processAnnotations(servlet);
                       ((StandardContext)getParent()).getAnnotationProcessor().postConstruct(servlet);
                    }
                }

      4.为该servlet的监听器发送beforeInit事件
      instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_INIT_EVENT,servlet);
      5.调用servlet的事例的init方法
      6.如果是jsp文件而不是具体的servlet类,则产生DummyRequest和DummyResponse对象,并且调用service方法
if ((loadOnStartup >= 0) && (jspFile != null)) {
                    // Invoking jspInit
                    DummyRequest req = new DummyRequest();
                    req.setServletPath(jspFile);
                    req.setQueryString(Constants.PRECOMPILE + "=true");
                    DummyResponse res = new DummyResponse();

                    if( Globals.IS_SECURITY_ENABLED) {
                        Object[] args = new Object[]{req, res};
                        SecurityUtil.doAsPrivilege("service",
                                                   servlet,
                                                   classTypeUsedInService,
                                                   args);
                        args = null;
                    } else {
                        servlet.service(req, res);
                    }
                }
      7.为该servlet的监听器发送afterInit事件
      instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,servlet);
      8.设置是否为单线程模式 singleThreadModel = servlet instanceof SingleThreadModel;
      9.发送load事件
      fireContainerEvent("load", this);
     */
    public synchronized Servlet loadServlet() throws ServletException {

        // Nothing to do if we already have an instance or an instance pool
        if (!singleThreadModel && (instance != null))
            return instance;

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1=System.currentTimeMillis();
            // If this "servlet" is really a JSP file, get the right class.
            // HOLD YOUR NOSE - this is a kludge that avoids having to do special
            // case Catalina-specific code in Jasper - it also requires that the
            // servlet path be replaced by the <jsp-file> element content in
            // order to be completely effective
            String actualClass = servletClass;
            if ((actualClass == null) && (jspFile != null)) {
                Wrapper jspWrapper = (Wrapper)
                    ((Context) getParent()).findChild(Constants.JSP_SERVLET_NAME);
                if (jspWrapper != null) {
                    actualClass = jspWrapper.getServletClass();
                    // Merge init parameters
                    String paramNames[] = jspWrapper.findInitParameters();
                    for (int i = 0; i < paramNames.length; i++) {
                        if (parameters.get(paramNames[i]) == null) {
                            parameters.put
                                (paramNames[i], 
                                 jspWrapper.findInitParameter(paramNames[i]));
                        }
                    }
                }
            }

            // Complain if no servlet class has been specified
            if (actualClass == null) {
                unavailable(null);
                throw new ServletException(sm.getString("standardWrapper.notClass", getName()));
            }

            // Acquire an instance of the class loader to be used
            Loader loader = getLoader();
            if (loader == null) {
                unavailable(null);
                throw new ServletException(sm.getString("standardWrapper.missingLoader", getName()));
            }

            ClassLoader classLoader = loader.getClassLoader();

            // Special case class loader for a container provided servlet
            //  
            if (isContainerProvidedServlet(actualClass) && ! ((Context)getParent()).getPrivileged() ) { 
                // If it is a priviledged context - using its own
                // class loader will work, since it's a child of the container
                // loader
                classLoader = this.getClass().getClassLoader();
            }

            // Load the specified servlet class from the appropriate class loader
            Class classClass = null;
            try {
                if (SecurityUtil.isPackageProtectionEnabled()){
                    final ClassLoader fclassLoader = classLoader;
                    final String factualClass = actualClass;
                    try{
                        classClass = (Class)AccessController.doPrivileged(
                                new PrivilegedExceptionAction(){
                                    public Object run() throws Exception{
                                        if (fclassLoader != null) {
                                            return fclassLoader.loadClass(factualClass);
                                        } else {
                                            return Class.forName(factualClass);
                                        }
                                    }
                        });
                    } catch(PrivilegedActionException pax){
                        Exception ex = pax.getException();
                        if (ex instanceof ClassNotFoundException){
                            throw (ClassNotFoundException)ex;
                        } else {
                            getServletContext().log( "Error loading "
                                + fclassLoader + " " + factualClass, ex );
                        }
                    }
                } else {
                    if (classLoader != null) {
                        classClass = classLoader.loadClass(actualClass);
                    } else {
                        classClass = Class.forName(actualClass);
                    }
                }
            } catch (ClassNotFoundException e) {
                unavailable(null);
                getServletContext().log( "Error loading " + classLoader + " " + actualClass, e );
                throw new ServletException
                    (sm.getString("standardWrapper.missingClass", actualClass),
                     e);
            }

            if (classClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.missingClass", actualClass));
            }

            // Instantiate and initialize an instance of the servlet class itself
            try {
                servlet = (Servlet) classClass.newInstance();
                // Annotation processing
                if (!((Context) getParent()).getIgnoreAnnotations()) {
                    if (getParent() instanceof StandardContext) {
                       ((StandardContext)getParent()).getAnnotationProcessor().processAnnotations(servlet);
                       ((StandardContext)getParent()).getAnnotationProcessor().postConstruct(servlet);
                    }
                }
            } catch (ClassCastException e) {
                unavailable(null);
                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.notServlet", actualClass), e);
            } catch (Throwable e) {
                unavailable(null);
              
                // Added extra log statement for Bugzilla 36630:
                // http://issues.apache.org/bugzilla/show_bug.cgi?id=36630
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("standardWrapper.instantiate", actualClass), e);
                }

                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.instantiate", actualClass), e);
            }

            // Check if loading the servlet in this web application should be
            // allowed
            //检查该servlet实例是否允许
            if (!isServletAllowed(servlet)) {
                throw new SecurityException
                    (sm.getString("standardWrapper.privilegedServlet",
                                  actualClass));
            }

            // Special handling for ContainerServlet instances
            if ((servlet instanceof ContainerServlet) &&
                  (isContainerProvidedServlet(actualClass) ||
                    ((Context)getParent()).getPrivileged() )) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime=(int) (System.currentTimeMillis() -t1);
            // Call the initialization method of this servlet
            try {
                instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_INIT_EVENT,servlet);

                if( Globals.IS_SECURITY_ENABLED) {
                    boolean success = false;
                    try {
                        Object[] args = new Object[]{ facade };
                        SecurityUtil.doAsPrivilege("init",
                                                   servlet,
                                                   classType,
                                                   args);
                        success = true;
                    } finally {
                        if (!success) {
                            // destroy() will not be called, thus clear the reference now
                            SecurityUtil.remove(servlet);
                        }
                    }
                } else {
                    servlet.init(facade);
                }

                // Invoke jspInit on JSP pages
                if ((loadOnStartup >= 0) && (jspFile != null)) {
                    // Invoking jspInit
                    DummyRequest req = new DummyRequest();
                    req.setServletPath(jspFile);
                    req.setQueryString(Constants.PRECOMPILE + "=true");
                    DummyResponse res = new DummyResponse();

                    if( Globals.IS_SECURITY_ENABLED) {
                        Object[] args = new Object[]{req, res};
                        SecurityUtil.doAsPrivilege("service",
                                                   servlet,
                                                   classTypeUsedInService,
                                                   args);
                        args = null;
                    } else {
                        servlet.service(req, res);
                    }
                }
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet);
            } catch (UnavailableException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                unavailable(f);
                throw f;
            } catch (ServletException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // If the servlet wanted to be unavailable it would have
                // said so, so do not call unavailable(null).
                throw f;
            } catch (Throwable f) {
                getServletContext().log("StandardWrapper.Throwable", f );
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // If the servlet wanted to be unavailable it would have
                // said so, so do not call unavailable(null).
                throw new ServletException
                    (sm.getString("standardWrapper.initException", getName()), f);
            }

            // Register our newly initialized instance
            singleThreadModel = servlet instanceof SingleThreadModel;
            if (singleThreadModel) {
                if (instancePool == null)
                    instancePool = new Stack();
            }
            fireContainerEvent("load", this);

            loadTime=System.currentTimeMillis() -t1;
        } finally {
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }
        return servlet;

    }


    /**
     * Remove the specified initialization parameter from this servlet.
     *
     * @param name Name of the initialization parameter to remove
     */
    public void removeInitParameter(String name) {

        try {
            parametersLock.writeLock().lock();
            parameters.remove(name);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("removeInitParameter", name);

    }


    /**
     * Remove a listener no longer interested in InstanceEvents.
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener) {

        instanceSupport.removeInstanceListener(listener);

    }


    /**
     * Remove a mapping associated with the wrapper.
     *
     * @param mapping The pattern to remove
     */
    public void removeMapping(String mapping) {

        try {
            mappingsLock.writeLock().lock();
            mappings.remove(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        fireContainerEvent("removeMapping", mapping);

    }


    /**
     * Remove any security role reference for the specified role name.
     *
     * @param name Security role used within this servlet to be removed
     */
    public void removeSecurityReference(String name) {

        try {
            referencesLock.writeLock().lock();
            references.remove(name);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("removeSecurityReference", name);

    }


    /**
     * Return a String representation of this component.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardWrapper[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Process an UnavailableException, marking this servlet as unavailable
     * for the specified amount of time.
     *
     * @param unavailable The exception that occurred, or <code>null</code>
     *  to mark this servlet as permanently unavailable
     *  参见loadServlet方法
     *  加载servlet的时候,出现异常时,调用该方法,传递异常对象
     */
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null)
            setAvailable(Long.MAX_VALUE);
        else if (unavailable.isPermanent())
            setAvailable(Long.MAX_VALUE);
        else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0)
                unavailableSeconds = 60;        // Arbitrary default
            setAvailable(System.currentTimeMillis() + (unavailableSeconds * 1000L));
        }
    }


    /**
     * Unload all initialized instances of this servlet, after calling the
     * <code>destroy()</code> method for each instance.  This can be used,
     * for example, prior to shutting down the entire servlet engine, or
     * prior to reloading all of the classes from the Loader associated with
     * our Loader's repository.
     *
     * @exception ServletException if an exception is thrown by the
     *  destroy() method
     *  stop的时候调用该方法,即销毁该servlet
     *  
     1.设置unloading属性,表示目前正在进行unload方法。并且等候缓冲池中关闭
     2.产生beforeDestroy事件
       instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_DESTROY_EVENT, instance);
     3.调用destroy方法
     4.产生afterDestroy事件
       instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_DESTROY_EVENT, instance);
     5.针对父类操作
            if (!((Context) getParent()).getIgnoreAnnotations()) {
               ((StandardContext)getParent()).getAnnotationProcessor().preDestroy(instance);
            }
     6.是单线程模式的,也要进行调用destroy方法,以及调用父类的((StandardContext)getParent()).getAnnotationProcessor().preDestroy(s);方法
     7.发送unload事件
     */
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // Loaf a while if the current instance is allocated
        // (possibly more than once if non-STM)
        if (countAllocated.get() > 0) {
            int nRetries = 0;//尝试次数最多21次
            long delay = unloadDelay / 20;//unload销毁时,缓冲池中有事例时,进行延迟休息间隔
            while ((nRetries < 21) && (countAllocated.get() > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting",countAllocated.toString()));
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    ;
                }
                nRetries++;
            }
        }

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        // Call the servlet destroy() method
        try {
            instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_DESTROY_EVENT, instance);

            if( Globals.IS_SECURITY_ENABLED) {
                try {
                    SecurityUtil.doAsPrivilege("destroy",
                                               instance);
                } finally {
                    SecurityUtil.remove(instance);
                }
            } else {
                instance.destroy();
            }
            
            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_DESTROY_EVENT, instance);

            // Annotation processing
            if (!((Context) getParent()).getIgnoreAnnotations()) {
               ((StandardContext)getParent()).getAnnotationProcessor().preDestroy(instance);
            }

        } catch (Throwable t) {
            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_DESTROY_EVENT, instance, t);
            instance = null;
            instancePool = null;
            nInstances = 0;
            fireContainerEvent("unload", this);
            unloading = false;
            throw new ServletException(sm.getString("standardWrapper.destroyException", getName()),t);
        } finally {
            // Write captured output
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }

        // Deregister the destroyed instance
        instance = null;
        //是单线程模式的,也要进行调用destroy方法,以及调用父类的((StandardContext)getParent()).getAnnotationProcessor().preDestroy(s);方法
        if (singleThreadModel && (instancePool != null)) {
            try {
                while (!instancePool.isEmpty()) {
                    Servlet s = (Servlet) instancePool.pop();
                    if (Globals.IS_SECURITY_ENABLED) {
                        try {
                            SecurityUtil.doAsPrivilege("destroy", s);
                        } finally {
                            SecurityUtil.remove(s);
                        }
                    } else {
                        s.destroy();
                    }
                    // Annotation processing
                    if (!((Context) getParent()).getIgnoreAnnotations()) {
                       ((StandardContext)getParent()).getAnnotationProcessor().preDestroy(s);
                    }
                }
            } catch (Throwable t) {
                instancePool = null;
                nInstances = 0;
                unloading = false;
                fireContainerEvent("unload", this);
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException",
                                  getName()), t);
            }
            instancePool = null;
            nInstances = 0;
        }

        singleThreadModel = false;

        unloading = false;
        fireContainerEvent("unload", this);

    }


    // -------------------------------------------------- ServletConfig Methods


    /**
     * Return the initialization parameter value for the specified name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(String name) {

        return (findInitParameter(name));

    }


    /**
     * Return the set of initialization parameter names defined for this
     * servlet.  If none are defined, an empty Enumeration is returned.
     */
    public Enumeration getInitParameterNames() {

        try {
            parametersLock.readLock().lock();
            return (new Enumerator(parameters.keySet()));
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    /**
     * Return the servlet context with which this servlet is associated.
     */
    public ServletContext getServletContext() {

        if (parent == null)
            return (null);
        else if (!(parent instanceof Context))
            return (null);
        else
            return (((Context) parent).getServletContext());

    }


    /**
     * Return the name of this servlet.
     */
    public String getServletName() {

        return (getName());

    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    public void setProcessingTime(long processingTime) {
        swValve.setProcessingTime(processingTime);
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    public void setMaxTime(long maxTime) {
        swValve.setMaxTime(maxTime);
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    public void setMinTime(long minTime) {
        swValve.setMinTime(minTime);
    }

    public int getRequestCount() {
        return swValve.getRequestCount();
    }

    public void setRequestCount(int requestCount) {
        swValve.setRequestCount(requestCount);
    }

    public int getErrorCount() {
        return swValve.getErrorCount();
    }

    public void setErrorCount(int errorCount) {
           swValve.setErrorCount(errorCount);
    }

    /**
     * Increment the error count used for monitoring.
     */
    public void incrementErrorCount(){
        swValve.setErrorCount(swValve.getErrorCount() + 1);
    }

    public long getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    // -------------------------------------------------------- Package Methods


    // -------------------------------------------------------- protected Methods


    /**
     * Add a default Mapper implementation if none have been configured
     * explicitly.
     *
     * @param mapperClass Java class name of the default Mapper
     */
    protected void addDefaultMapper(String mapperClass) {

        ;       // No need for a default Mapper on a Wrapper

    }


    /**
     * Return <code>true</code> if the specified class name represents a
     * container provided servlet class that should be loaded by the
     * server class loader.
     *
     * @param classname Name of the class to be checked
     */
    protected boolean isContainerProvidedServlet(String classname) {

        if (classname.startsWith("org.apache.catalina.")) {
            return (true);
        }
        try {
            Class clazz =
                this.getClass().getClassLoader().loadClass(classname);
            return (ContainerServlet.class.isAssignableFrom(clazz));
        } catch (Throwable t) {
            return (false);
        }

    }


    /**
     * Return <code>true</code> if loading this servlet is allowed.
     * 判断该servlet的class是否能被允许加载
     * 1.如果他的父类Context的getPrivileged=true,则说明这个class是允许被加载的
     * 2.如果servlet是ContainerServlet实例,说明是可以被加载的
     * 3.如果servlet的所有父类,没有在org/apache/catalina/core/RestrictedServlets.properties文件中.或者再文件中但是不是restricted,都可以表示成可以加载
     */
    protected boolean isServletAllowed(Object servlet) {

        // Privileged webapps may load all servlets without restriction
        if (((Context) getParent()).getPrivileged()) {
            return true;
        }
        
        if (servlet instanceof ContainerServlet) {
            return (false);
        }

        Class clazz = servlet.getClass();
        while (clazz != null && !clazz.getName().equals("javax.servlet.http.HttpServlet")) {
            if ("restricted".equals(restrictedServlets.getProperty(clazz.getName()))) {
                return (false);
            }
            clazz = clazz.getSuperclass();
        }
        
        return (true);

    }


    protected Method[] getAllDeclaredMethods(Class c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods == null) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
	    System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
	    System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

	    thisMethods = allMethods;
	}

	return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component, pre-loading the servlet if the load-on-startup
     * value is set appropriately.
     *
     * @exception LifecycleException if a fatal error occurs during startup
     */
    public void start() throws LifecycleException {
    
        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Start up this component
        super.start();

        if( oname != null )
            registerJMX((StandardContext)getParent());
        
        // Load and initialize an instance of this servlet if requested
        // MOVED TO StandardContext START() METHOD

        setAvailable(0L);
        
        // Send j2ee.state.running notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

    }


    /**
     * Stop this component, gracefully shutting down the servlet if it has
     * been initialized.
     *
     * @exception LifecycleException if a fatal error occurs during shutdown
     */
    public void stop() throws LifecycleException {

        setAvailable(Long.MAX_VALUE);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Shut down our servlet instance (if it has been initialized)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString
                      ("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stop();

        // Send j2ee.state.stoppped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        if( oname != null ) {
            Registry.getRegistry(null, null).unregisterComponent(oname);
            
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        if (isJspServlet && jspMonitorON != null ) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }

    }

    protected void registerJMX(StandardContext ctx) {

        String parentName = ctx.getName();
        parentName = ("".equals(parentName)) ? "/" : parentName;

        String hostName = ctx.getParent().getName();
        hostName = (hostName==null) ? "DEFAULT" : hostName;

        String domain = ctx.getDomain();

        String webMod= "//" + hostName + parentName;
        String onameStr = domain + ":j2eeType=Servlet,name=" + getName() +
                          ",WebModule=" + webMod + ",J2EEApplication=" +
                          ctx.getJ2EEApplication() + ",J2EEServer=" +
                          ctx.getJ2EEServer();
        try {
            oname=new ObjectName(onameStr);
            controller=oname;
            Registry.getRegistry(null, null)
                .registerComponent(this, oname, null );
            
            // Send j2ee.object.created notification 
            if (this.getObjectName() != null) {
                Notification notification = new Notification(
                                                "j2ee.object.created", 
                                                this.getObjectName(), 
                                                sequenceNumber++);
                broadcaster.sendNotification(notification);
            }
        } catch( Exception ex ) {
            log.info("Error registering servlet with jmx " + this, ex);
        }

        if (isJspServlet) {
            // Register JSP monitoring mbean
            onameStr = domain + ":type=JspMonitor,name=" + getName()
                       + ",WebModule=" + webMod
                       + ",J2EEApplication=" + ctx.getJ2EEApplication()
                       + ",J2EEServer=" + ctx.getJ2EEServer();
            try {
                jspMonitorON = new ObjectName(onameStr);
                Registry.getRegistry(null, null)
                    .registerComponent(instance, jspMonitorON, null);
            } catch( Exception ex ) {
                log.info("Error registering JSP monitoring with jmx " +
                         instance, ex);
            }
        }
    }
    

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
    		NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    protected MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"servlet is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"servlet is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"servlet is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopped"},
					Notification.class.getName(),
					"servlet start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"servlet is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"servlet is deleted"
					)
    		};
    		
    	}
    	
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) 
        throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    }
    
    
     // ------------------------------------------------------------- Attributes
        
        
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStateManageable() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }
        
        
}
