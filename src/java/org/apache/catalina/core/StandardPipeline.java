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


import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Standard implementation of a processing <b>Pipeline</b> that will invoke
 * a series of Valves that have been configured to be called in order.  This
 * implementation can be used for any type of Container.
 *
 * <b>IMPLEMENTATION WARNING</b> - This implementation assumes that no
 * calls to <code>addValve()</code> or <code>removeValve</code> are allowed
 * while a request is currently being processed.  Otherwise, the mechanism
 * by which per-thread state is maintained will need to be modified.
 *
 * @author Craig R. McClanahan
 * 
 * 
1.protected Container container = null;因为一个管道肯定属于一个容器的,即一个容器拥有一个管道,因此管道对象需要持有容器对象
2.监听器相关的增删改差
3.getBasic/setBasic 设置和获取基础value
a.设置属性this.basic = valve;,即设置基础value是谁
b.如果该管道不仅仅有基础value,还已经添加了其他value,则递归所有的value的nextValue方法,找到最后一个value,将基础value赋值给最后一个value的nextValue上
4.void addValve(Valve valve) 添加一个value
比如连续调用addValue3次,分别添加value1,value2,value3,则最终结果是
first就是value1,然后value1的nextValue是value2,value2的nextValue是value3,value3的nextValue是basicValue
5.Valve[] getValves() 按照value的添加顺序,返回所有的value集合,最后一个是basicValue
6.可以允许删除一个Value
7.Valve getFirst() 获取第一个value
 */

public class StandardPipeline
    implements Pipeline, Contained, Lifecycle 
 {

    private static Log log = LogFactory.getLog(StandardPipeline.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new StandardPipeline instance with no associated Container.
     */
    public StandardPipeline() {

        this(null);

    }


    /**
     * Construct a new StandardPipeline instance that is associated with the
     * specified Container.
     *
     * @param container The container we should be associated with
     */
    public StandardPipeline(Container container) {

        super();
        setContainer(container);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The basic Valve (if any) associated with this Pipeline.
     */
    protected Valve basic = null;


    /**
     * The Container with which this Pipeline is associated.
     */
    protected Container container = null;


    /**
     * Descriptive information about this implementation.
     */
    protected String info = "org.apache.catalina.core.StandardPipeline/1.0";


    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Has this component been started yet?
     */
    protected boolean started = false;


    /**
     * The first valve associated with this Pipeline.
     */
    protected Valve first = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Return descriptive information about this implementation class.
     */
    public String getInfo() {

        return (this.info);

    }


    // ------------------------------------------------------ Contained Methods


    /**
     * Return the Container with which this Pipeline is associated.
     */
    public Container getContainer() {

        return (this.container);

    }


    /**
     * Set the Container with which this Pipeline is associated.
     *
     * @param container The new associated container
     */
    public void setContainer(Container container) {

        this.container = container;

    }


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
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }

    /**
     * Prepare for active use of the public methods of this Component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public synchronized void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("standardPipeline.alreadyStarted"));

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        started = true;

        // Start the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).start();
            registerValve(current);
        	current = current.getNext();
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(START_EVENT, null);

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);

    }


    /**
     * Gracefully shut down active use of the public methods of this Component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public synchronized void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("standardPipeline.notStarted"));

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Stop the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).stop();
            unregisterValve(current);
        	current = current.getNext();
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }

    private void registerValve(Valve valve) {

        if( valve instanceof ValveBase &&
                ((ValveBase)valve).getObjectName()==null ) {
            try {
                
                String domain=((ContainerBase)container).getDomain();
                if( container instanceof StandardContext ) {
                    domain=((StandardContext)container).getEngineName();
                }
                if( container instanceof StandardWrapper) {
                    Container ctx=((StandardWrapper)container).getParent();
                    domain=((StandardContext)ctx).getEngineName();
                }
                ObjectName vname=((ValveBase)valve).createObjectName(
                        domain,
                        ((ContainerBase)container).getJmxName());
                if( vname != null ) {
                    ((ValveBase)valve).setObjectName(vname);
                    Registry.getRegistry(null, null).registerComponent
                        (valve, vname, valve.getClass().getName());
                    ((ValveBase)valve).setController
                        (((ContainerBase)container).getJmxName());
                }
            } catch( Throwable t ) {
                log.info( "Can't register valve " + valve , t );
            }
        }
    }
    
    private void unregisterValve(Valve valve) {
        if( valve instanceof ValveBase ) {
            try {
                ValveBase vb=(ValveBase)valve;
                if( vb.getController()!=null &&
                        vb.getController() == 
                        ((ContainerBase)container).getJmxName() ) {
                    
                    ObjectName vname=vb.getObjectName();
                    Registry.getRegistry(null, null).getMBeanServer()
                        .unregisterMBean(vname);
                    ((ValveBase)valve).setObjectName(null);
                }
            } catch( Throwable t ) {
                log.info( "Can't unregister valve " + valve , t );
            }
        }
    }    

    // ------------------------------------------------------- Pipeline Methods


    /**
     * <p>Return the Valve instance that has been distinguished as the basic
     * Valve for this Pipeline (if any).
     */
    public Valve getBasic() {

        return (this.basic);

    }


    /**
     * <p>Set the Valve instance that has been distinguished as the basic
     * Valve for this Pipeline (if any).  Prioer to setting the basic Valve,
     * the Valve's <code>setContainer()</code> will be called, if it
     * implements <code>Contained</code>, with the owning Container as an
     * argument.  The method may throw an <code>IllegalArgumentException</code>
     * if this Valve chooses not to be associated with this Container, or
     * <code>IllegalStateException</code> if it is already associated with
     * a different Container.</p>
     *
     * @param valve Valve to be distinguished as the basic Valve
     */
    public void setBasic(Valve valve) {

        // Change components if necessary
        Valve oldBasic = this.basic;
        if (oldBasic == valve)
            return;

        // Stop the old component if necessary
        if (oldBasic != null) {
            if (started && (oldBasic instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldBasic).stop();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.setBasic: stop", e);
                }
            }
            if (oldBasic instanceof Contained) {
                try {
                    ((Contained) oldBasic).setContainer(null);
                } catch (Throwable t) {
                    ;
                }
            }
        }

        // Start the new component if necessary
        if (valve == null)
            return;
        if (valve instanceof Contained) {
            ((Contained) valve).setContainer(this.container);
        }
        if (valve instanceof Lifecycle) {
            try {
                ((Lifecycle) valve).start();
            } catch (LifecycleException e) {
                log.error("StandardPipeline.setBasic: start", e);
                return;
            }
        }

        //a.设置属性this.basic = valve;,即设置基础value是谁
        //b.如果该管道不仅仅有基础value,还已经添加了其他value,则递归所有的value的nextValue方法,找到最后一个value,将基础value赋值给最后一个value的nextValue上
        // Update the pipeline
        Valve current = first;
        while (current != null) {
        	if (current.getNext() == oldBasic) {
        		current.setNext(valve);
        		break;
        	}
        	current = current.getNext();
        }
        
        this.basic = valve;

    }


    /**
     * <p>Add a new Valve to the end of the pipeline associated with this
     * Container.  Prior to adding the Valve, the Valve's
     * <code>setContainer()</code> method will be called, if it implements
     * <code>Contained</code>, with the owning Container as an argument.
     * The method may throw an
     * <code>IllegalArgumentException</code> if this Valve chooses not to
     * be associated with this Container, or <code>IllegalStateException</code>
     * if it is already associated with a different Container.</p>
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException if this Container refused to
     *  accept the specified Valve
     * @exception IllegalArgumentException if the specifie Valve refuses to be
     *  associated with this Container
     * @exception IllegalStateException if the specified Valve is already
     *  associated with a different Container
比如连续调用addValue3次,分别添加value1,value2,value3,则最终结果是
first就是value1,然后value1的nextValue是value2,value2的nextValue是value3,value3的nextValue是basicValue
     */
    public void addValve(Valve valve) {
    
        // Validate that we can add this Valve 如果该value需要关联一个容器对象，则设置该容器与他关联
        if (valve instanceof Contained)
            ((Contained) valve).setContainer(this.container);

        // Start the new component if necessary
        if (started) {
            if (valve instanceof Lifecycle) {
                try {
                    ((Lifecycle) valve).start();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.addValve: start: ", e);
                }
            }
            // Register the newly added valve
            registerValve(valve);
        }

        // Add this Valve to the set associated with this Pipeline
        /**
         * 按照添加Value的顺序，组装Value队列,例如目前有v1 v2 v3 basic。目前要添加v4，则结果为v1 v2 v3 v4 basic
         */
        if (first == null) {
        	first = valve;
        	valve.setNext(basic);
        } else {
            Valve current = first;
            while (current != null) {
				if (current.getNext() == basic) {
					current.setNext(valve);
					valve.setNext(basic);
					break;
				}
				current = current.getNext();
			}
        }

    }


    /**
     * Return the set of Valves in the pipeline associated with this
     * Container, including the basic Valve (if any).  If there are no
     * such Valves, a zero-length array is returned.
     * 按照Value添加的顺序，遍历所有Value，最终将其组装成数组返回
     */
    public Valve[] getValves() {

    	ArrayList valveList = new ArrayList();
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
        	valveList.add(current);
        	current = current.getNext();
        }

        return ((Valve[]) valveList.toArray(new Valve[0]));

    }

    public ObjectName[] getValveObjectNames() {

    	ArrayList valveList = new ArrayList();
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
        	if (current instanceof ValveBase) {
        		valveList.add(((ValveBase) current).getObjectName());
        	}
        	current = current.getNext();
        }

        return ((ObjectName[]) valveList.toArray(new ObjectName[0]));

    }

    /**
     * Remove the specified Valve from the pipeline associated with this
     * Container, if it is found; otherwise, do nothing.  If the Valve is
     * found and removed, the Valve's <code>setContainer(null)</code> method
     * will be called if it implements <code>Contained</code>.
     *
     * @param valve Valve to be removed
     * 移除一个VALUE
     * 逻辑：
     * 1.如果要移除的Value就是第一个，则第一个Value就会该value的next值。
     */
    public void removeValve(Valve valve) {

        Valve current;
        if(first == valve) {
            first = first.getNext();
            current = null;
        } else {
            current = first;
        }
        while (current != null) {
            if (current.getNext() == valve) {
                current.setNext(valve.getNext());
                break;
            }
            current = current.getNext();
        }

        if (first == basic) first = null;

        if (valve instanceof Contained)
            ((Contained) valve).setContainer(null);

        // Stop this valve if necessary
        if (started) {
            if (valve instanceof Lifecycle) {
                try {
                    ((Lifecycle) valve).stop();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.removeValve: stop: ", e);
                }
            }
            // Unregister the removed valave
            unregisterValve(valve);
        }
    
    }


    public Valve getFirst() {
        if (first != null) {
            return first;
        } else {
            return basic;
        }
    }


}
