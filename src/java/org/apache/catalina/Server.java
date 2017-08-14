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


package org.apache.catalina;

import org.apache.catalina.deploy.NamingResources;

/**
 * A <code>Server</code> element represents the entire Catalina
 * servlet container.  Its attributes represent the characteristics of
 * the servlet container as a whole.  A <code>Server</code> may contain
 * one or more <code>Services</code>, and the top level set of naming
 * resources.
 * <p>
 * Normally, an implementation of this interface will also implement
 * <code>Lifecycle</code>, such that when the <code>start()</code> and
 * <code>stop()</code> methods are called, all of the defined
 * <code>Services</code> are also started or stopped.
 * <p>
 * In between, the implementation must open a server socket on the port number
 * specified by the <code>port</code> property.  When a connection is accepted,
 * the first line is read and compared with the specified shutdown command.
 * If the command matches, shutdown of the server is initiated.
 * <p>
 * <strong>NOTE</strong> - The concrete implementation of this class should
 * register the (singleton) instance with the <code>ServerFactory</code>
 * class in its constructor(s).
 *
 * @author Craig R. McClanahan
 * @version $Id: Server.java 939350 2010-04-29 15:36:29Z kkolinko $
 * 1.一个tomcat节点就是一个server
 * 2.一个server提供若干个service服务,因此有增删改差service服务的方法
 * 3.一个server本身有一个小端口用于监听shutdown命令,一旦shutdown命令接收到了,则停止该tomcat节点上所有的service服务
 * 4.初始化该server
 * 5.获取该server的info信息
 * 6.返回全局的命名资源NamingResources
 */

public interface Server {


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Server implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo();


    /**
     * Return the global naming resources.
     */
    public NamingResources getGlobalNamingResources();


    /**
     * Set the global naming resources.
     * 
     * @param globalNamingResources The new global naming resources
     */
    public void setGlobalNamingResources
        (NamingResources globalNamingResources);


    /**
     * Return the port number we listen to for shutdown commands.
     * 监听shutdow该server的小服务端口
     */
    public int getPort();


    /**
     * Set the port number we listen to for shutdown commands.
     *
     * @param port The new port number
     */
    public void setPort(int port);


    /**
     * Return the shutdown command string we are waiting for.
     * shutdown该server的命令
     */
    public String getShutdown();


    /**
     * Set the shutdown command we are waiting for.
     *
     * @param shutdown The new shutdown command
     */
    public void setShutdown(String shutdown);


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Service to the set of defined Services.
     *
     * @param service The Service to be added
     * 为爱server添加各种服务
     */
    public void addService(Service service);


    /**
     * Wait until a proper shutdown command is received, then return.
     * 等待一个shutdow命令被接收,然后才会被返回
     */
    public void await();


    /**
     * Return the specified Service (if it exists); otherwise return
     * <code>null</code>.
     *
     * @param name Name of the Service to be returned
     * 找到一个子服务
     */
    public Service findService(String name);


    /**
     * Return the set of Services defined within this Server.
     * 所有的服务
     */
    public Service[] findServices();


    /**
     * Remove the specified Service from the set associated from this
     * Server.
     *
     * @param service The Service to be removed
     * 移除一个服务
     */
    public void removeService(Service service);

    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     *
     * @exception LifecycleException If this server was already initialized.
     * 初始化
     */
    public void initialize()
    throws LifecycleException;
}
