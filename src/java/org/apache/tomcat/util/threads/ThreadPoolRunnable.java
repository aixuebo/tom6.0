/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.threads;


/** Implemented if you want to run a piece of code inside a thread pool.
 */
public interface ThreadPoolRunnable {
    // XXX use notes or a hashtable-like
    // Important: ThreadData in JDK1.2 is implemented as a Hashtable( Thread -> object ),
    // expensive.
    
    /** Called when this object is first loaded in the thread pool.
     *  Important: all workers in a pool must be of the same type,
     *  otherwise the mechanism becomes more complex.
     *  当这个对象第一次被加载到线程池中的时候被调用。
     *  注意：在一个线程池中的所有的工人，必须有相同的类型，否则这个机制就会变得复杂了
     */
    public Object[] getInitData();

    /** This method will be executed in one of the pool's threads. The
     *  thread will be returned to the pool.
     *  这个方法将会从线程池中得到一个线程去执行。执行后返回到线程池中。
     */
    public void runIt(Object thData[]);

}
