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
import java.util.concurrent.Semaphore;

import javax.servlet.ServletException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;


/**
 * <p>Implementation of a Valve that limits concurrency.</p>
 *  该value限制并发数量--即控制进入一个容器的线程数量,一次只能有若干个线程进入该容器
 * <p>This Valve may be attached to any Container, depending on the granularity
 * of the concurrency control you wish to perform.</p>
 * 该value可以附加在任意容器下,依赖更细粒度的并发控制
 *
 * @author Remy Maucherat
 * @version $Id: SemaphoreValve.java 939353 2010-04-29 15:50:43Z kkolinko $
 */

public class SemaphoreValve
    extends ValveBase
    implements Lifecycle {


    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information related to this implementation.
     */
    private static final String info =
        "org.apache.catalina.valves.SemaphoreValve/1.0";


    /**
     * The string manager for this package.
     */
    private StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Semaphore.
     */
    protected Semaphore semaphore = null;
    

    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * Has this component been started yet?
     */
    private boolean started = false;


    // ------------------------------------------------------------- Properties

    
    /**
     * Concurrency level of the semaphore.
     * 设置并发数量
     */
    protected int concurrency = 10;
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    

    /**
     * Fairness of the semaphore.
     * 是否是公平锁
     */
    protected boolean fairness = false;
    public boolean getFairness() { return fairness; }
    public void setFairness(boolean fairness) { this.fairness = fairness; }
    

    /**
     * Block until a permit is available.
     * 是否获取锁的时候阻塞
     */
    protected boolean block = true;
    public boolean getBlock() { return block; }
    public void setBlock(boolean block) { this.block = block; }
    

    /**
     * Block interruptibly until a permit is available.
     */
    protected boolean interruptible = false;
    public boolean getInterruptible() { return interruptible; }
    public void setInterruptible(boolean interruptible) { this.interruptible = interruptible; }
    

    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }


    /**
     * Prepare for the beginning of active use of the public methods of this
     * component.  This method should be called after <code>configure()</code>,
     * and before any of the public methods of the component are utilized.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        //开启一个并发量的线程池,采用是否公平锁
        semaphore = new Semaphore(concurrency, fairness);

    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        semaphore = null;

    }

    
    // --------------------------------------------------------- Public Methods


    /**
     * Return descriptive information about this Valve implementation.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * Do concurrency control on the request using the semaphore.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        if (controlConcurrency(request, response)) {//说明需要进行线程控制
            boolean shouldRelease = true;
            try {
                if (block) {//说明获取锁的时候使用阻塞方式
                    if (interruptible) {
                        try {
                            semaphore.acquire();//阻塞获取
                        } catch (InterruptedException e) {
                            shouldRelease = false;
                            permitDenied(request, response);//说明获取失败了
                            return;
                        }  
                    } else {
                        semaphore.acquireUninterruptibly();
                    }
                } else {//说明使用非阻塞的方式获取锁
                    if (!semaphore.tryAcquire()) {//尝试获取失败
                        shouldRelease = false;
                        permitDenied(request, response);//说明获取失败了
                        return;
                    }
                }
                getNext().invoke(request, response);//获取到锁了,继续执行
            } finally {
                if (shouldRelease) {
                    semaphore.release();
                }
            }
        } else {//说明不需要进行线程控制
            getNext().invoke(request, response);
        }

    }

    
    /**
     * Subclass friendly method to add conditions.
     * 子类去实现,true表示要进行线程控制,fasle表示该请求不需要进行线程控制,即可以对某些关键请求做线程控制,那么子类就通过request的uri进行判断即可
     */
    public boolean controlConcurrency(Request request, Response response) {
        return true;
    }
    

    /**
     * Subclass friendly method to add error handling when a permit isn't granted.
     * 如何拒绝的话,子类如何做
     */
    public void permitDenied(Request request, Response response)
        throws IOException, ServletException {
    }
    

}
