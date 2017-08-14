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


package org.apache.catalina.startup;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Startup event listener for a <b>Host</b> that configures the properties
 * of that Host, and the associated defined contexts.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id: HostConfig.java 1142206 2011-07-02 11:45:44Z markt $
 * 
 * 参见HostRuleSet类
 * 初始化Host、以及Host的上下文环境
 * 定义server.xml中Server/Service/Engine/Host的子标签
 */
public class HostConfig
    implements LifecycleListener {
    
    protected static org.apache.juli.logging.Log log=
         org.apache.juli.logging.LogFactory.getLog( HostConfig.class );

    // ----------------------------------------------------- Instance Variables


    /**
     * App base.
     * 如果Host配置的getAppBase()属性是相对路径,则根据catalina.base属性转换成绝对路径
     * 如果配置的就是绝对路径,则就不需要与catalina.base属性关联
     * return Host配置的getAppBase()的绝对路径,所有项目都在该目录下
     */
    protected File appBase = null;


    /**
     * Config base.
     * 设置configBase,为变量catalina.base/conf/service/Engine在server.xml中的名称/host.getName()路径
     * 例如:D:\soft\tomcat6\conf\Catalina\localhost
     */
    protected File configBase = null;


    /**
     * The Java class name of the Context configuration class we should use.
     * Content容器的初始化对象
     */
    protected String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * The Java class name of the Context implementation we should use.
     * Content容器对象
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * The Host we are associated with.
     */
    protected Host host = null;

    
    /**
     * The JMX ObjectName of this component.
     */
    protected ObjectName oname = null;
    

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * Should we deploy XML Context config files?
     * 调用Host的isDeployXML()方法
     * deployXML为true,deployXML表示要把项目中的META-INF/context.xml文件部署到D:\soft\tomcat6\conf\Catalina\localhost\项目目录下
     * 
这个属性的目的是为了提高tomcat的安全性，控制web应用程序是否能使用META-INF/contex.xml。如果设为false，则各应用程序只能访问
$CATALINA_HOME/conf/<engine>/<host>/<app>.xml。默认值为True。
     */
    protected boolean deployXML = false;


    /**
     * Should we unpack WAR files when auto-deploying applications in the
     * <code>appBase</code> directory?
     * 调用Host的isUnpackWARs()方法,tomcat在webapps文件夹中发现war文件时，是否自动将其解压
     */
    protected boolean unpackWARs = false;


    /**
     * Map of deployed applications.
     * 部署的项目集合
     * key是项目路径/host-manager,value是该项目的对象DeployedApplication
     */
    protected HashMap deployed = new HashMap();

    
    /**
     * List of applications which are being serviced, and shouldn't be 
     * deployed/undeployed/redeployed at the moment.
     */
    protected ArrayList serviced = new ArrayList();
    

    /**
     * Attribute value used to turn on/off XML validation
     * 调用Host的getXmlValidation()方法
     */
    protected boolean xmlValidation = false;


    /**
     * Attribute value used to turn on/off XML namespace awarenes.
     * 调用Host的getXmlNamespaceAware()方法
     */
    protected boolean xmlNamespaceAware = false;


    /**
     * The <code>Digester</code> instance used to parse context descriptors.
     * 解析context标签的解析器
     */
    protected static Digester digester = createDigester();

    /**
     * The list of Wars in the appBase to be ignored because they are invalid
     * (e.g. contain /../ sequences).
     * 被忽略的war文件名称,因为他们这些名称是被设置为无效的war包名称
     */
    protected Set<String> invalidWars = new HashSet<String>();

    // ------------------------------------------------------------- Properties


    /**
     * Return the Context configuration class name.
     */
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * Set the Context configuration class name.
     *
     * @param configClass The new Context configuration class name.
     */
    public void setConfigClass(String configClass) {

        this.configClass = configClass;

    }


    /**
     * Return the Context implementation class name.
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * Set the Context implementation class name.
     *
     * @param contextClass The new Context implementation class name.
     */
    public void setContextClass(String contextClass) {

        this.contextClass = contextClass;

    }


    /**
     * Return the deploy XML config file flag for this component.
     */
    public boolean isDeployXML() {

        return (this.deployXML);

    }


    /**
     * Set the deploy XML config file flag for this component.
     *
     * @param deployXML The new deploy XML flag
     */
    public void setDeployXML(boolean deployXML) {

        this.deployXML= deployXML;

    }


    /**
     * Return the unpack WARs flag.
     */
    public boolean isUnpackWARs() {

        return (this.unpackWARs);

    }


    /**
     * Set the unpack WARs flag.
     *
     * @param unpackWARs The new unpack WARs flag
     */
    public void setUnpackWARs(boolean unpackWARs) {

        this.unpackWARs = unpackWARs;

    }
    
    
     /**
     * Set the validation feature of the XML parser used when
     * parsing xml instances.
     * @param xmlValidation true to enable xml instance validation
     */
    public void setXmlValidation(boolean xmlValidation){
        this.xmlValidation = xmlValidation;
    }

    /**
     * Get the server.xml &lt;host&gt; attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getXmlValidation(){
        return xmlValidation;
    }

    /**
     * Get the server.xml &lt;host&gt; attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     *
     */
    public boolean getXmlNamespaceAware(){
        return xmlNamespaceAware;
    }


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param xmlNamespaceAware true to enable namespace awareness
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware){
        this.xmlNamespaceAware=xmlNamespaceAware;
    }    


    // --------------------------------------------------------- Public Methods


    /**
     * Process the START event for an associated Host.
     *
     * @param event The lifecycle event that has occurred
     * 程序的入口类
     */
    public void lifecycleEvent(LifecycleEvent event) {

      //周期性的调用事件
        if (event.getType().equals(Lifecycle.PERIODIC_EVENT)){
          check();
        }
            
        // Identify the host we are associated with
        try {
            host = (Host) event.getLifecycle();
            if (host instanceof StandardHost) {
                setDeployXML(((StandardHost) host).isDeployXML());
                setUnpackWARs(((StandardHost) host).isUnpackWARs());
                setXmlNamespaceAware(((StandardHost) host).getXmlNamespaceAware());
                setXmlValidation(((StandardHost) host).getXmlValidation());
            }
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT)){
          start();
        }else if (event.getType().equals(Lifecycle.STOP_EVENT)){
          stop();
        }
    }

    
    /**
     * Add a serviced application to the list.
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }
    
    
    /**
     * Is application serviced ?
     * @return state of the application
     * 例如:参数name为/manager,即表示项目名称的uri
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }
    

    /**
     * Removed a serviced application from the list.
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }

    
    /**
     * Get the instant where an application was deployed.
     * @return 0L if no application with that name is deployed, or the instant
     * on which the application was deployed
     * 返回部署时间
     */
    public long getDeploymentTime(String name) {
    	DeployedApplication app = (DeployedApplication) deployed.get(name);
    	if (app == null) {
    		return 0L;
    	} else {
    		return app.timestamp;
    	}
    }
    
    
    /**
     * Has the specified application been deployed? Note applications defined
     * in server.xml will not have been deployed.
     * @return <code>true</code> if the application has been deployed and
     * <code>false</code> if the applciation has not been deployed or does not
     * exist
     * 判断是否项目已经被部署了
     * 参数name为/host-manager
     */
    public boolean isDeployed(String name) {
        DeployedApplication app = (DeployedApplication) deployed.get(name);
        if (app == null) {
            return false;
        } else {
            return true;
        }
    }
    
    
    // ------------------------------------------------------ Protected Methods

    
    /**
     * Create the digester which will be used to parse context config files.
     * 获取ConText标签对象
     */
    protected static Digester createDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        // Add object creation rule
        digester.addObjectCreate("Context", "org.apache.catalina.core.StandardContext","className");
        // Set the properties on that object (it doesn't matter if extra 
        // properties are set)
        digester.addSetProperties("Context");//设置属性
        return (digester);
    }
    

    /**
     * Return a File object representing the "application root" directory
     * for our associated Host.
     * 如果Host配置的getAppBase()属性是相对路径,则根据catalina.base属性转换成绝对路径
     * 如果配置的就是绝对路径,则就不需要与catalina.base属性关联
     * return Host配置的getAppBase()的绝对路径,所以项目都在该目录下
     */
    protected File appBase() {

        if (appBase != null) {
            return appBase;
        }

        File file = new File(host.getAppBase());
        if (!file.isAbsolute()){
          file = new File(System.getProperty("catalina.base"),host.getAppBase());
        }
        try {
            appBase = file.getCanonicalFile();
        } catch (IOException e) {
            appBase = file;
        }
        return (appBase);

    }


    /**
     * Return a File object representing the "configuration root" directory
     * for our associated Host.
     * 设置configBase,为变量catalina.base/conf/service/Engine在server.xml中的名称/host.getName()路径
     * 例如:D:\soft\tomcat6\conf\Catalina\localhost
     */
    protected File configBase() {

        if (configBase != null) {
            return configBase;
        }

        File file = new File(System.getProperty("catalina.base"), "conf");
        Container parent = host.getParent();
        if ((parent != null) && (parent instanceof Engine)) {
            file = new File(file, parent.getName());
        }
        file = new File(file, host.getName());
        try {
            configBase = file.getCanonicalFile();
        } catch (IOException e) {
            configBase = file;
        }
        return (configBase);

    }

    /**
     * Get the name of the configBase.
     * For use with JMX management.
     * 设置configBase,为变量catalina.base/conf/service/Engine在server.xml中的名称/host.getName()路径
     * 例如:D:\soft\tomcat6\conf\Catalina\localhost
     */
    public String getConfigBaseName() {
        return configBase().getAbsolutePath();
    }

    /**
     * Given a context path, get the config file name.
     * 转换成项目名称
     * 即参数/manager转变化成manager
     */
    protected String getConfigFile(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename);
    }

    
    /**
     * Given a context path, get the docBase.
     */
    protected String getDocBase(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename);
    }

    
    /**
     * Deploy applications for any directories or WAR files that are found
     * in our "application root" directory.
     * 部署属于该host的项目
     */
    protected void deployApps() {

        File appBase = appBase();//Host配置的getAppBase()的绝对路径,设置该绝对路径,例如D:\soft\tomcat6\webapps
        /**
         * 设置configBase,为变量catalina.base/conf/service/Engine在server.xml中的名称/host.getName()路径
         * 例如:D:\soft\tomcat6\conf\Catalina\localhost
         */
        File configBase = configBase();
        /**
         * 对webapp目录下的所有项目进行正则表达式匹配,如果匹配上的,则要被忽略被加载部署,即匹配上的项目不会被加载部署
         */
        String[] filteredAppPaths = filterAppPaths(appBase.list());
        // Deploy XML descriptors from configBase 生成Context对象,并且为该对象生成监控文件
        deployDescriptors(configBase, configBase.list());
        // Deploy WARs, and loop if additional descriptors are found 加载war项目文件
        deployWARs(appBase, filteredAppPaths);
        // Deploy expanded folders 加载部署普通的项目
        deployDirectories(appBase, filteredAppPaths);
        
    }


    /**
     * Filter the list of application file paths to remove those that match
     * the regular expression defined by {@link Host#getDeployIgnore()}.
     *  
     * @param unfilteredAppPaths    The list of application paths to filtert 所有项目集合
     * 
     * @return  The filtered list of application paths
     * 对webapp目录下的项目进行忽略匹配,如果匹配上的,则要被忽略,即匹配上的项目不会被加载
     */
    protected String[] filterAppPaths(String[] unfilteredAppPaths) {
        Pattern filter = host.getDeployIgnorePattern();//该host上要忽略的项目的正则表达式
        if (filter == null) {//如果为null,说明全部项目都可用
            return unfilteredAppPaths;
        }
        //最终没有匹配上正则的集合,需要被返回
        List<String> filteredList = new ArrayList<String>();
        Matcher matcher = null;
        //循环项目集合的所有项目
        for (String appPath : unfilteredAppPaths) {
            if (matcher == null) {
                matcher = filter.matcher(appPath);
            } else {
                matcher.reset(appPath);
            }
            if (matcher.matches()) {//匹配上正则的需要添加日志,说明该项目要被忽略
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("hostConfig.ignorePath", appPath));
                }
            } else {
                filteredList.add(appPath);
            }
        }
        return filteredList.toArray(new String[filteredList.size()]);
    }


    /**
     * Deploy applications for any directories or WAR files that are found
     * in our "application root" directory.
     */
    protected void deployApps(String name) {

        File appBase = appBase();
        File configBase = configBase();
        String baseName = getConfigFile(name);
        String docBase = getDocBase(name);
        
        // Deploy XML descriptors from configBase
        //在D:\soft\tomcat6\conf\Catalina\localhost下查找该项目的xml的context配置文件
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists()){//如果存在,则部署该文件
          deployDescriptor(name, xml, baseName + ".xml");
        }
        // Deploy WARs, and loop if additional descriptors are found
        //在webapp目录下查找war文件,找到则部署
        File war = new File(appBase, docBase + ".war");
        if (war.exists())
            deployWAR(name, war, docBase + ".war");
        // Deploy expanded folders
        //在webapp目录下查找文件,找到则部署
        File dir = new File(appBase, docBase);
        if (dir.exists())
            deployDirectory(name, dir, docBase);
        
        //注意由于部署是有顺序的,即如果上面三个中任意一个找到了该项目,并且部署完成后,后面的步骤都是不会被真正部署的
    }


    /**
     * Deploy XML context descriptors.
     * 加载属于该host的配置文件列表,即例如D:\soft\tomcat6\conf\Catalina\localhost下的配置文件集合,
     * 注意该文件必须是xml文件
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;
        
        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File contextXml = new File(configBase, files[i]);
            if (files[i].toLowerCase().endsWith(".xml")) {

                // Calculate the context path and make sure it is unique
                String nameTmp = files[i].substring(0, files[i].length() - 4);//取消文件名的.xml后缀,得到本身文件名
                String contextPath = "/" + nameTmp.replace('#', '/');//例如源文件为manager.xml,则contextPath为/manager
                if (nameTmp.equals("ROOT")) {
                    contextPath = "";
                }

                if (isServiced(contextPath))
                    continue;
                
                String file = files[i];

                deployDescriptor(contextPath, contextXml, file);
                
            }

        }

    }


    /**
     * @param contextPath 项目路径,例如/host-manager
     * @param contextXml 该项目的配置文件路径 D:\workspace\tomcat\src\conf\Catalina\localhost\host-manager.xml
     * @param file 该项目的配置文件名称 host-manager.xml
     * 
     * 加载属于该host的配置文件列表,即例如D:\soft\tomcat6\conf\Catalina\localhost下的配置文件集合,
     * 
     * 因为加载的流程是先加载D:\soft\tomcat6\conf\Catalina\localhost下的项目，然后加载webapp下的war项目，然后加载webapp下其他项目
     * 如果webapp下的项目名称与D:\soft\tomcat6\conf\Catalina\localhost下重名，则不会再被加载，以D:\soft\tomcat6\conf\Catalina\localhost为准,因此有时配置文件要先清空D:\soft\tomcat6\conf\Catalina\localhost目录
     */
    protected void deployDescriptor(String contextPath, File contextXml, String file) {
        if (deploymentExists(contextPath)) {//如果已经部署了该项目,则return
            return;
        }
        
        DeployedApplication deployedApp = new DeployedApplication(contextPath);

        // Assume this is a configuration descriptor and deploy it
        if(log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployDescriptor", file));
        }

        Context context = null;
        try {
            synchronized (digester) {
                try {
                    context = (Context) digester.parse(contextXml);
                    if (context == null) {
                        log.error(sm.getString("hostConfig.deployDescriptor.error",
                                file));
                        return;
                    }
                } finally {
                    digester.reset();
                }
            }
            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener = (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setConfigFile(contextXml.getAbsolutePath());//设置 D:\workspace\tomcat\src\conf\Catalina\localhost\host-manager.xml配置文件路径
            context.setPath(contextPath);
            // Add the associated docBase to the redeployed list if it's a WAR
            boolean isExternalWar = false;//如果该项目是war项目,则设置isExternalWar属性为true
            boolean isExternal = false;//如果该项目部署在内部,而不是外部引用,则设置isExternal属性为true
            if (context.getDocBase() != null) {//项目名称
              //找到项目在webapp下的具体路径
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(appBase(), context.getDocBase());
                }
                // If external docBase, register .xml as redeploy first
                if (!docBase.getCanonicalPath().startsWith(appBase().getAbsolutePath() + File.separator)) {//如果该项目部署在内部,而不是外部引用,则设置isExternal属性为true
                    isExternal = true;
                    deployedApp.redeployResources.put
                        (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
                    if (docBase.getAbsolutePath().toLowerCase().endsWith(".war")) {//如果该项目是war项目,则设置isExternalWar属性为true
                        isExternalWar = true;
                    }
                } else {
                    log.warn(sm.getString("hostConfig.deployDescriptor.localDocBaseSpecified",
                             docBase));
                    // Ignore specified docBase
                    context.setDocBase(null);
                }
            }
            host.addChild(context);
            // Get paths for WAR and expanded WAR in appBase
            String name = null;//host-manager
            String path = context.getPath();///host-manager
            if (path.equals("")) {
                name = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    name = path.substring(1);
                } else {
                    name = path;
                }
            }
            File expandedDocBase = new File(appBase(), name);//D:\workspace\tomcat\src\webapps/host-manager
            if (context.getDocBase() != null) {
                // first assume docBase is absolute
                expandedDocBase = new File(context.getDocBase());
                if (!expandedDocBase.isAbsolute()) {
                    // if docBase specified and relative, it must be relative to appBase
                    expandedDocBase = new File(appBase(), context.getDocBase());
                }
            }
            // Add the eventual unpacked WAR and all the resources which will be
            // watched inside it
            //如果是war包,并且可以解压包的,则进入if
            if (isExternalWar && unpackWARs) {
                deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                        new Long(expandedDocBase.lastModified()));
                deployedApp.redeployResources.put
                    (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                addWatchedResources(deployedApp, expandedDocBase.getAbsolutePath(), context);
            } else {
                // Find an existing matching war and expanded folder
                if (!isExternal) {
                    File warDocBase = new File(expandedDocBase.getAbsolutePath() + ".war");
                    if (warDocBase.exists()) {
                        deployedApp.redeployResources.put(warDocBase.getAbsolutePath(),
                                new Long(warDocBase.lastModified()));
                    }
                }
                if (expandedDocBase.exists()) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            new Long(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp, 
                            expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
                // Add the context XML to the list of files which should trigger a redeployment
                if (!isExternal) {
                    deployedApp.redeployResources.put
                        (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                   file), t);
        }

        if (context != null && host.findChild(context.getName()) != null) {
            deployed.put(contextPath, deployedApp);
        }
    }


    /**
     * Deploy WAR files.加载war项目文件
     */
    protected void deployWARs(File appBase, String[] files) {
        
        if (files == null)
            return;
        
        for (int i = 0; i < files.length; i++) {
            
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            
            //是war包,并且不是非法的
            if (files[i].toLowerCase().endsWith(".war") && dir.isFile() && !invalidWars.contains(files[i]) ) {
                
                // Calculate the context path and make sure it is unique
              //根据war包的名称+前缀/,得到contextPath,即例如/s.war,后续会去掉后缀
                String contextPath = "/" + files[i].replace('#','/');
                int period = contextPath.lastIndexOf(".");
                contextPath = contextPath.substring(0, period);//最终得到/s
                
                // Check for WARs with /../ /./ or similar sequences in the name
                if (!validateContextPath(appBase, contextPath)) {//校验war包的名称是否有问题,不允许有相对路径
                    log.error(sm.getString("hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }

                if (contextPath.equals("/ROOT"))
                    contextPath = "";
                
                if (isServiced(contextPath))
                    continue;
                
                String file = files[i];
                
                deployWAR(contextPath, dir, file);
                
            }
            
        }
        
    }

    /**
     * 校验contextPath路径
     * @param appBase  webapp目录路径
     * @param contextPath 例如:/manager
     * @return
     */
    private boolean validateContextPath(File appBase, String contextPath) {
        // More complicated than the ideal as the canonical path may or may
        // not end with File.separator for a directory
        
        StringBuilder docBase;
        String canonicalDocBase = null;
        
        try {
            String canonicalAppBase = appBase.getCanonicalPath();
            docBase = new StringBuilder(canonicalAppBase);
            
            //将contextPath于appBase相结合,最终docBase为D:\soft\tomcat6\webapps\manager
            if (canonicalAppBase.endsWith(File.separator)) {
                docBase.append(contextPath.substring(1).replace(
                        '/', File.separatorChar));
            } else {
                docBase.append(contextPath.replace('/', File.separatorChar));
            }
            // At this point docBase should be canonical but will not end
            // with File.separator
            
            canonicalDocBase =
                (new File(docBase.toString())).getCanonicalPath();
    
            // If the canonicalDocBase ends with File.separator, add one to
            // docBase before they are compared
            if (canonicalDocBase.endsWith(File.separator)) {
                docBase.append(File.separator);
            }
        } catch (IOException ioe) {
            return false;
        }
        
        // Compare the two. If they are not the same, the contextPath must
        // have /../ like sequences in it 
        return canonicalDocBase.equals(docBase.toString());
    }

    /**
     * 部署war包,如果war包名字是s.war
     * @param contextPath 则参数为/s
     * @param war war包File全路径对象
     * @param file 文件名称:s.war
     */
    protected void deployWAR(String contextPath, File war, String file) {
        
      //如果已经部署了contextPath,则停止部署
        if (deploymentExists(contextPath))
            return;
        
        // Checking for a nested /META-INF/context.xml
        JarFile jar = null;
        JarEntry entry = null;
        InputStream istream = null;
        BufferedOutputStream ostream = null;
        //查看该war项目的context配置文件
        File xml = new File(configBase, file.substring(0, file.lastIndexOf(".")) + ".xml");
        //xml不存在,但是deployXML为true,deployXML表示要把项目中的META-INF/context.xml文件部署到D:\soft\tomcat6\conf\Catalina\localhost\项目目录下
        //如果deployXML为false,则表示不能加载war包里面的context.xml配置文件
        //注意:war包中不是肯定有META-INF/context.xml文件的
        if (deployXML && !xml.exists()) {
            try {
                jar = new JarFile(war);
                entry = jar.getJarEntry(Constants.ApplicationContextXml);
                if (entry != null) {
                    istream = jar.getInputStream(entry);
                    
                    configBase.mkdirs();
                    
                    ostream =
                        new BufferedOutputStream
                        (new FileOutputStream(xml), 1024);
                    byte buffer[] = new byte[1024];
                    while (true) {
                        int n = istream.read(buffer);
                        if (n < 0) {
                            break;
                        }
                        ostream.write(buffer, 0, n);
                    }
                    ostream.flush();
                    ostream.close();
                    ostream = null;
                    istream.close();
                    istream = null;
                    entry = null;
                    jar.close();
                    jar = null;
                }
            } catch (Exception e) {
                // Ignore and continue
                if (ostream != null) {
                    try {
                        ostream.close();
                    } catch (Throwable t) {
                        ;
                    }
                    ostream = null;
                }
                if (istream != null) {
                    try {
                        istream.close();
                    } catch (Throwable t) {
                        ;
                    }
                    istream = null;
                }
            } finally {
                entry = null;
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (Throwable t) {
                        ;
                    }
                    jar = null;
                }
            }
        }
        
        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        // Deploy the application in this WAR file
        if(log.isInfoEnabled()) 
            log.info(sm.getString("hostConfig.deployJar", file));

        try {
            Context context = null;
            //由于war包中不是肯定有META-INF/context.xml文件的,因此xml.exists()还是可能为false
            //如果xml存在,则加载xml文件
            if (deployXML && xml.exists()) {
                synchronized (digester) {
                    try {
                        context = (Context) digester.parse(xml);
                        if (context == null) {
                            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                    file));
                            return;
                        }
                    } finally {
                        digester.reset();
                    }
                }
                context.setConfigFile(xml.getAbsolutePath());
            } else {//默认生成空的StandardContext对象
                context = (Context) Class.forName(contextClass).newInstance();
            }

            // Populate redeploy resources with the WAR file
            deployedApp.redeployResources.put(war.getAbsolutePath(), new Long(war.lastModified()));

            if (deployXML && xml.exists()) {
                deployedApp.redeployResources.put(xml.getAbsolutePath(), new Long(xml.lastModified()));
            }
            //为该context添加HostCongig监听器
            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setPath(contextPath);
            context.setDocBase(file);
            //这步解war包的
            host.addChild(context);
            // If we're unpacking WARs, the docBase will be mutated after
            // starting the context
            //如果可以解压缩war包,则添加到监控中
            if (unpackWARs && (context.getDocBase() != null)) {
                String name = null;
                String path = context.getPath();
                if (path.equals("")) {
                    name = "ROOT";
                } else {
                    if (path.startsWith("/")) {
                        name = path.substring(1);
                    } else {
                        name = path;
                    }
                }
                name = name.replace('/', '#');
                File docBase = new File(name);
                if (!docBase.isAbsolute()) {
                    docBase = new File(appBase(), name);
                }
                deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
                addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
            } else {
                addWatchedResources(deployedApp, null, context);
            }
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployJar.error", file), t);
        }
        
        deployed.put(contextPath, deployedApp);
    }


    /**
     * Deploy directories.
     * 部署webapp下的项目
     * 参数:appBase表示webapp的目录文件
     * 参数files表示webapp下的所有文件集合
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;
        
        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            if (dir.isDirectory()) {

                // Calculate the context path and make sure it is unique
                String contextPath = "/" + files[i].replace('#','/');///docs项目路径
                if (files[i].equals("ROOT"))
                    contextPath = "";

                if (isServiced(contextPath))
                    continue;

                deployDirectory(contextPath, dir, files[i]);
            
            }

        }

    }

    
    /**
     * @param contextPath 项目路径/docs
     * @param dir 项目所在具体本地磁盘位置D:\workspace\tomcat\src\webapps\docs
     * @param file dir文件名字
     */
    protected void deployDirectory(String contextPath, File dir, String file) {
        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        if (deploymentExists(contextPath))//如果该项目已经被部署,则退出
            return;

        // Deploy the application in this directory
        if( log.isInfoEnabled() ) 
            log.info(sm.getString("hostConfig.deployDir", file));
        try {
            Context context = null;
            File xml = new File(dir, Constants.ApplicationContextXml);//先加载D:\workspace\tomcat\src\webapps\docs\META-INF\context.xml文件,生成Context对象
            File xmlCopy = null;
            //加载META-INF/context.xml配置文件生成Context对象,并且把该文件内容copy到D:\soft\tomcat6\conf\Catalina\localhost目录下
            if (deployXML && xml.exists()) {
                // Will only do this on initial deployment. On subsequent
                // deployments the copied xml file means we'll use
                // deployDescriptor() instead
                synchronized (digester) {
                    try {
                        context = (Context) digester.parse(xml);
                        if (context == null) {
                            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                    xml));
                            return;
                        }
                    } finally {
                        digester.reset();
                    }
                }
                configBase.mkdirs();
                //将配置文件copy到config目录下,统一管理context配置文件
                xmlCopy = new File(configBase, file + ".xml");
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new FileInputStream(xml);
                    os = new FileOutputStream(xmlCopy);
                    IOTools.flow(is, os);
                    // Don't catch IOE - let the outer try/catch handle it
                } finally {
                    try {
                        if (is != null) is.close();
                    } catch (IOException e){
                        // Ignore
                    }
                    try {
                        if (os != null) os.close();
                    } catch (IOException e){
                        // Ignore
                    }
                }
                context.setConfigFile(xmlCopy.getAbsolutePath());
            } else {//如果没有context.xml文件,则默认生成无构造函数参数的context对象
                context = (Context) Class.forName(contextClass).newInstance();
            }

            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setPath(contextPath);
            context.setDocBase(file);
            host.addChild(context);
            deployedApp.redeployResources.put(dir.getAbsolutePath(),
                    new Long(dir.lastModified()));
            if (xmlCopy != null) {
                deployedApp.redeployResources.put
                (xmlCopy.getAbsolutePath(), new Long(xmlCopy.lastModified()));
            }
            addWatchedResources(deployedApp, dir.getAbsolutePath(), context);
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployDir.error", file), t);
        }

        deployed.put(contextPath, deployedApp);
    }

    
    /**
     * Check if a webapp is already deployed in this host.
     * 
     * @param contextPath of the context which will be checked
     * 检查该项目是否已经部署了,或者该host已经包含该项目了,true表示已经部署了
     * 参数contextPath格式为/manager
     */
    protected boolean deploymentExists(String contextPath) {
        return (deployed.containsKey(contextPath) || (host.findChild(contextPath) != null));
    }
    

    /**
     * Add watched resources to the specified Context.
     * @param app HostConfig deployed app 每一个项目对应一个DeployedApplication对象
     * @param docBase web app docBase 项目存储路径D:\workspace\tomcat\src\webapps\host-manager
     * @param context web application context 该项目的上下文Context对象
     * 为该项目添加定期加载对象监控
     */
    protected void addWatchedResources(DeployedApplication app, String docBase, Context context) {
        // FIXME: Feature idea. Add support for patterns (ex: WEB-INF/*, WEB-INF/*.xml), where
        //        we would only check if at least one resource is newer than app.timestamp
        File docBaseFile = null;
        if (docBase != null) {
            docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(appBase(), docBase);
            }
        }
        
        //默认加载D:\workspace\tomcat\src\conf\context.xml, WEB-INF/web.xml, D:\workspace\tomcat\src\conf\web.xml三个文件
        //context.findWatchedResources()查找需要监控的文件
        String[] watchedResources = context.findWatchedResources();
        for (int i = 0; i < watchedResources.length; i++) {
            File resource = new File(watchedResources[i]);
            if (!resource.isAbsolute()) {//如果是相对路径,则将其转化成绝对路径
                if (docBase != null) {
                    resource = new File(docBaseFile, watchedResources[i]);
                } else {
                    if(log.isDebugEnabled())
                        log.debug("Ignoring non-existent WatchedResource '" 
                            + resource.getAbsolutePath() + "'");
                   continue;
                }
            }
            if(log.isDebugEnabled())
                log.debug("Watching WatchedResource '" + resource.getAbsolutePath() + "'");
            app.reloadResources.put(resource.getAbsolutePath(), 
                    new Long(resource.lastModified()));
        }
    }
    

    /**
     * Check resources for redeployment and reloading.
     * 检查每一个被部署的项目的资源信息
     */
    protected synchronized void checkResources(DeployedApplication app) {
      //获取部署的时候要监听的资源信息,该监听主要是这些资源的时间进行监听
        String[] resources = (String[]) app.redeployResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);//获得每一个资源
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name + "] redeploy resource " + resource);
            /**
             * 如果资源存在
             * 1.则从缓存中拿到资源的当时最后修改时间
             * 2.如果目前资源的时间比当时记录的时间大,说明被改过,如果没有被改过,则无所谓
             *    如果资源被更改了,则将该项目移除,后续方法会继续重新加载该项目
             */
            if (resource.exists()) {//查看资源是否存在
                long lastModified = ((Long) app.redeployResources.get(resources[i])).longValue();
                if ((!resource.isDirectory()) && resource.lastModified() > lastModified) {//如果目前资源的时间比当时记录的时间大,说明被改过,则删除这些资源信息
                    // Undeploy application
                    if (log.isInfoEnabled())
                        log.info(sm.getString("hostConfig.undeploy", app.name));
                    ContainerBase context = (ContainerBase) host.findChild(app.name);
                    try {
                        host.removeChild(context);
                    } catch (Throwable t) {
                        log.warn(sm.getString
                                 ("hostConfig.context.remove", app.name), t);
                    }
                    try {
                        context.destroy();
                    } catch (Throwable t) {
                        log.warn(sm.getString
                                 ("hostConfig.context.destroy", app.name), t);
                    }
                    // Delete other redeploy resources
                    for (int j = i + 1; j < resources.length; j++) {
                        try {
                            File current = new File(resources[j]);
                            current = current.getCanonicalFile();
                            if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath() + File.separator))
                                    || (current.getAbsolutePath().startsWith(configBase().getAbsolutePath()))) {
                                if (log.isDebugEnabled())
                                    log.debug("Delete " + current);
                                ExpandWar.delete(current);
                            }
                        } catch (IOException e) {
                            log.warn(sm.getString
                                    ("hostConfig.canonicalizing", app.name), e);
                        }
                    }
                    deployed.remove(app.name);
                    return;
                }
            } else {
                // There is a chance the the resource was only missing
                // temporarily eg renamed during a text editor save
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    // Ignore
                }
                // Recheck the resource to see if it was really deleted
                if (resource.exists()) {
                    continue;
                }
                long lastModified =
                    ((Long) app.redeployResources.get(resources[i])).longValue();

                if (lastModified == 0L) {
                    continue;
                }
                // Undeploy application
                if (log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.undeploy", app.name));
                ContainerBase context = (ContainerBase) host.findChild(app.name);
                try {
                    host.removeChild(context);
                } catch (Throwable t) {
                    log.warn(sm.getString
                             ("hostConfig.context.remove", app.name), t);
                }
                if (context != null) {
                    try {
                        context.destroy();
                    } catch (Throwable t) {
                        log.warn(sm.getString
                                ("hostConfig.context.destroy", app.name), t);
                    }
                }
                // Delete all redeploy resources
                for (int j = i + 1; j < resources.length; j++) {
                    try {
                        File current = new File(resources[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath() + File.separator))
                            || (current.getAbsolutePath().startsWith(configBase().getAbsolutePath()))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                // Delete reload resources as well (to remove any remaining .xml descriptor)
                String[] resources2 = (String[]) app.reloadResources.keySet().toArray(new String[0]);
                for (int j = 0; j < resources2.length; j++) {
                    try {
                        File current = new File(resources2[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath() + File.separator))
                            || ((current.getAbsolutePath().startsWith(configBase().getAbsolutePath())
                                 && (current.getAbsolutePath().endsWith(".xml"))))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                deployed.remove(app.name);
                return;
            }
        }
        
        /**
         * 监控的目的是如果改变了,则重新加载
         */
        resources = (String[]) app.reloadResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name + "] reload resource " + resource);
            long lastModified = ((Long) app.reloadResources.get(resources[i])).longValue();
            if ((!resource.exists() && lastModified != 0L) 
                || (resource.lastModified() != lastModified)) {
                // Reload application
                if(log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.reload", app.name));
                Container context = host.findChild(app.name);
                try {
                    ((Lifecycle) context).stop();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // If the context was not started (for example an error 
                // in web.xml) we'll still get to try to start
                try {
                    ((Lifecycle) context).start();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // Update times
                app.reloadResources.put(resources[i], new Long(resource.lastModified()));
                app.timestamp = System.currentTimeMillis();
                return;
            }
        }
    }
    
    
    /**
     * Process a "start" event for this Host.
     * 部署所有的项目到host中
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            ObjectName hostON = new ObjectName(host.getObjectName());
            oname = new ObjectName(hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent(this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        //判断是否要部署所有属于该host的项目
        if (host.getDeployOnStartup()){
          deployApps();
        }
    }


    /**
     * Process a "stop" event for this Host.
     * 移除所有host的所有子容器
     */
    public void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.stop"));

        undeployApps();

        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
            } catch (Exception e) {
                log.error(sm.getString("hostConfig.jmx.unregister", oname), e);
            }
        }
        oname = null;
        appBase = null;
        configBase = null;

    }


    /**
     * Undeploy all deployed applications.
     * 卸载全部已经部署的项目
     * 1.清空deployed集合
     * 2.从host中删除这些子容器
     */
    protected void undeployApps() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.undeploying"));

        // Soft undeploy all contexts we have deployed
        DeployedApplication[] apps = 
            (DeployedApplication[]) deployed.values().toArray(new DeployedApplication[0]);
        for (int i = 0; i < apps.length; i++) {
            try {
                host.removeChild(host.findChild(apps[i].name));
            } catch (Throwable t) {
                log.warn(sm.getString
                        ("hostConfig.context.remove", apps[i].name), t);
            }
        }
        
        deployed.clear();

    }


    /**
     * Check status of all webapps.
     * 周期性的调用事件
     * lifecycleEvent方法调用
     * 
     * 如果是自动部署,则定期要对所有资源和部署的项目重新部署
     */
    protected void check() {
        //该host是否是自动加载,如果是,则进行自动加载
        if (host.getAutoDeploy()) {
            // Check for resources modification to trigger redeployment
          //查找目前部署的项目集合,对每一个项目进行资源检查,以及重新部署
            DeployedApplication[] apps = (DeployedApplication[]) deployed.values().toArray(new DeployedApplication[0]); 
            for (int i = 0; i < apps.length; i++) {
                if (!isServiced(apps[i].name))
                  /**
                   * 检查资源是否更改
                   * 1.有些资源被更改了,则重新加载即可
                   * 2.有些资源被更改了，则要移除该项目
                   */
                    checkResources(apps[i]);
            }
            // Hotdeploy applications
            //如果checkResources(apps[i])方法没有移除该项目,因此调用该方法不会有任何问题,因为该方法不会被重新加载,原因是重新加载前会判断项目在内存中是否存在
            deployApps();
        }

    }

    
    /**
     * Check status of a specific webapp, for use with stuff like management webapps.
     * 参见check方法
     */
    public void check(String name) {
        DeployedApplication app = (DeployedApplication) deployed.get(name);
        if (app != null) {
            checkResources(app);
        } else {
            deployApps(name);
        }
    }

    /**
     * Add a new Context to be managed by us.
     * Entry point for the admin webapp, and other JMX Context controlers.
     */
    public void manageApp(Context context)  {

        String contextPath = context.getPath();
        
        if (deployed.containsKey(contextPath))
            return;

        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        // Add the associated docBase to the redeployed list if it's a WAR
        boolean isWar = false;
        if (context.getDocBase() != null) {
            File docBase = new File(context.getDocBase());
            if (!docBase.isAbsolute()) {
                docBase = new File(appBase(), context.getDocBase());
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                                          new Long(docBase.lastModified()));
            if (docBase.getAbsolutePath().toLowerCase().endsWith(".war")) {
                isWar = true;
            }
        }
        host.addChild(context);
        // Add the eventual unpacked WAR and all the resources which will be
        // watched inside it
        if (isWar && unpackWARs) {
            String name = null;
            String path = context.getPath();
            if (path.equals("")) {
                name = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    name = path.substring(1);
                } else {
                    name = path;
                }
            }
            File docBase = new File(name);
            if (!docBase.isAbsolute()) {
                docBase = new File(appBase(), name);
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
            addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
        } else {
            addWatchedResources(deployedApp, null, context);
        }
        deployed.put(contextPath, deployedApp);
    }

    /**
     * Remove a webapp from our control.
     * Entry point for the admin webapp, and other JMX Context controlers.
     * 移除一个context项目
     */
    public void unmanageApp(String contextPath) {
        if(isServiced(contextPath)) {
            deployed.remove(contextPath);
            host.removeChild(host.findChild(contextPath));
        }
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * This class represents the state of a deployed application, as well as 
     * the monitored resources.
     * 部署一个应用对象
     */
    protected class DeployedApplication {
    	public DeployedApplication(String name) {
    		this.name = name;
    	}
    	
    	/**
    	 * Application context path. The assertion is that 
    	 * (host.getChild(name) != null).
    	 * 项目路径名称,例如:/host-manager
    	 */
    	public String name;
    	
    	/**
    	 * Any modification of the specified (static) resources will cause a 
    	 * redeployment of the application. If any of the specified resources is
    	 * removed, the application will be undeployed. Typically, this will
    	 * contain resources like the context.xml file, a compressed WAR path.
         * The value is the last modification time.
         * key是xxx.war,value是该war文件的最后修改时间
         * 或者key就是待加载的项目,value是该项目的最后修改时间
         * 
         * 也存储了D:\soft\tomcat6\conf\Catalina\localhost\manager.xml对应的key路径，和该文件对应的最后修改时间
         * 
         * 也存储了war包解压缩后的路径和最后修改时间。(war文件是有后缀名,而解压缩后的是没有后缀名,因此两个是都可以存储在该变量中的)
         * 
         * 监控的目的是如果改变了,则删除该context,然后重新部署,详见checkResources方法
    	 */
    	public LinkedHashMap redeployResources = new LinkedHashMap();

    	/**
    	 * Any modification of the specified (static) resources will cause a 
    	 * reload of the application. This will typically contain resources
    	 * such as the web.xml of a webapp, but can be configured to contain
    	 * additional descriptors.
         * The value is the last modification time.
         * 要定期需要加载的配置文件
         * key是文件路径,value是该文件的最后修改时间
         * 其中key包括web.xml
         * 
         * 被WatchedResources监控的配置文件,例如:
         * D:\workspace\tomcat\src\conf\context.xml, WEB-INF/web.xml, D:\workspace\tomcat\src\conf\web.xml
         * 
         * 监控的目的是如果改变了,则重新加载,详见checkResources方法
    	 */
    	public HashMap reloadResources = new HashMap();

    	/**
    	 * Instant where the application was last put in service.
    	 * 该项目被部署的时间
    	 */
    	public long timestamp = System.currentTimeMillis();
    }

}