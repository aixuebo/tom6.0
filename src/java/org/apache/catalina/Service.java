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

import org.apache.catalina.connector.Connector;

/**
 * A <strong>Service</strong> is a group of one or more
 * <strong>Connectors</strong> that share a single <strong>Container</strong>
 * to process their incoming requests.  This arrangement allows, for example,
 * a non-SSL and SSL connector to share the same population of web apps.
 * <p>
 * A given JVM can contain any number of Service instances; however, they are
 * completely independent of each other and share only the basic JVM facilities
 * and classes on the system class path.
 *
 * @author Craig R. McClanahan
 * @version $Id: Service.java 939350 2010-04-29 15:36:29Z kkolinko $
 * 
 * 提供功能
   1.该service的info信息
   2.该service的name
   3.Container getContainer();该service所属的容器,因为一个service只持有一个Engine,因此Engine就是Container,因此这里容器就是Container
   4.Server getServer();表示该service属于哪个server
   5.该service可以提供很多种连接请求方式
     addConnector(Connector connector);删除、list等信息,可以知道有多少个连接
   6.对Connector的连接池的管理
   addExecutor(Executor ex);list、删除
   7.service的init初始化方法
      
 */
public interface Service {

    // ------------------------------------------------------------- Properties


    /**
     * Return the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     * Engine引擎就是serice中唯一的容器
     */
    public Container getContainer();

    /**
     * Set the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     *
     * @param container The new Container
     */
    public void setContainer(Container container);

    /**
     * Return descriptive information about this Service implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo();

    /**
     * Return the name of this Service.
     */
    public String getName();

    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    public void setName(String name);

    /**
     * Return the <code>Server</code> with which we are associated (if any).
     * 该service所属的server
     */
    public Server getServer();

    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    public void setServer(Server server);

    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * @param connector The Connector to be added
     * 该service可以提供很多种连接请求方式
     */
    public void addConnector(Connector connector);

    /**
     * Find and return the set of Connectors associated with this Service.
     */
    public Connector[] findConnectors();

    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    public void removeConnector(Connector connector);

    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     *
     * @exception LifecycleException If this server was already initialized.
     */
    public void initialize() throws LifecycleException;

    /**
     * Adds a named executor to the service
     * @param ex Executor
     * 添加线程池信息
     */
    public void addExecutor(Executor ex);

    /**
     * Retrieves all executors
     * @return Executor[]
     */
    public Executor[] findExecutors();

    /**
     * Retrieves executor by name, null if not found
     * @param name String
     * @return Executor
     */
    public Executor getExecutor(String name);
    
    /**
     * Removes an executor from the service
     * @param ex Executor
     */
    public void removeExecutor(Executor ex);

}
