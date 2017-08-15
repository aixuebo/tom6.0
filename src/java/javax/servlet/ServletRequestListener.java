/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package javax.servlet;

import java.util.EventListener;

    /**
     * A ServletRequestListener can be implemented by the developer
     * interested in being notified of requests coming in and out of
     * scope in a web component. A request is defined as coming into
     * scope when it is about to enter the first servlet or filter
     * in each web application, as going out of scope when it exits
     * the last servlet or the first filter in the chain.
     *
     * @since Servlet 2.4
     * 
     * 应用项目Context下每一个请求进来后,只要请求的资源合法,都会走该监听器
     * 因此该监听器可以监听该项目的所有合法请求,比如请求的url等信息
     */


public interface ServletRequestListener extends EventListener {

    /** The request is about to go out of scope of the web application. 
     * 表示该web应用的一个请求走了
     **/
    public void requestDestroyed ( ServletRequestEvent sre );

    /** The request is about to come into scope of the web application. 
     * 表示该web应用的一个请求来了
     **/
    public void requestInitialized ( ServletRequestEvent sre );
}
