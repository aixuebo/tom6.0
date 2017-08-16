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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * This valve allows to detect requests that take a long time to process, which
 * might indicate that the thread that is processing it is stuck.
 * 这个value切面允许检测请求花费多长时间去处理,这个能检测到处理该请求的线程被卡住的可能
 */
public class StuckThreadDetectionValve extends ValveBase {

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(StuckThreadDetectionValve.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Keeps count of the number of stuck threads detected
     * 保持检测到的卡住的线程数
     */
    private final AtomicInteger stuckCount = new AtomicInteger(0);

    /**
     * In seconds. Default 600 (10 minutes).单位是s,默认10分钟
     */
    private int threshold = 600;

    /**
     * The only references we keep to actual running Thread objects are in
     * this Map (which is automatically cleaned in invoke()s finally clause).
     * That way, Threads can be GC'ed, eventhough the Valve still thinks they
     * are stuck (caused by a long monitor interval)
     * 表示活跃的线程集合
     * key是线程的唯一ID,value表示是此时线程正在处理哪个请求
     */
    private final ConcurrentHashMap<Long, MonitoredThread> activeThreads =
            new ConcurrentHashMap<Long, MonitoredThread>();
    /**
     * 记录卡住的线程集合
     * CompletedStuckThread对象表示每一个卡住的线程，在请求中卡住的时间是多少
     */
    private final Queue<CompletedStuckThread> completedStuckThreadsQueue =
            new ConcurrentLinkedQueue<CompletedStuckThread>();

    /**
     * Specify the threshold (in seconds) used when checking for stuck threads.
     * If &lt;=0, the detection is disabled. The default is 600 seconds.
     *
     * @param threshold
     *            The new threshold in seconds
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * @see #setThreshold(int)
     * @return The current threshold in seconds
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * 通知线程已经卡住了
     * @param monitoredThread 哪个线程
     * @param activeTime 处理一个url已经花费了多长时间
     * @param numStuckThreads 此时已经有多少个线程卡住了
     */
    private void notifyStuckThreadDetected(MonitoredThread monitoredThread,
        long activeTime, int numStuckThreads) {
        if (log.isWarnEnabled()) {
            String msg = sm.getString(
                "stuckThreadDetectionValve.notifyStuckThreadDetected",
                monitoredThread.getThread().getName(),
                Long.valueOf(activeTime),
                monitoredThread.getStartTime(),//该请求处理的开始时间
                Integer.valueOf(numStuckThreads),
                monitoredThread.getRequestUri(),//请求的url
                Integer.valueOf(threshold),//检测周期
                String.valueOf(monitoredThread.getThread().getId())//线程ID
                );
            // msg += "\n" + getStackTraceAsString(trace);
            Throwable th = new Throwable();
            th.setStackTrace(monitoredThread.getThread().getStackTrace());//设置此时线程的堆栈信息
            log.warn(msg, th);
        }
    }

    //通知已经有一个线程已经不卡了
    private void notifyStuckThreadCompleted(CompletedStuckThread thread,
            int numStuckThreads) {
        if (log.isWarnEnabled()) {
            String msg = sm.getString(
                "stuckThreadDetectionValve.notifyStuckThreadCompleted",
                thread.getName(),
                Long.valueOf(thread.getTotalActiveTime()),
                Integer.valueOf(numStuckThreads),
                String.valueOf(thread.getId()));
            // Since the "stuck thread notification" is warn, this should also
            // be warn
            log.warn(msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {
        if (threshold <= 0) {//小于0,表示不需要检测,因此直接执行下面的value即可
            // short-circuit if not monitoring stuck threads
            getNext().invoke(request, response);
            return;
        }

        // Save the thread/runnable
        // Keeping a reference to the thread object here does not prevent
        // GC'ing, as the reference is removed from the Map in the finally clause

        Long key = Long.valueOf(Thread.currentThread().getId());//当前运行的线程唯一ID
        //获取请求的url全路径
        StringBuffer requestUrl = request.getRequestURL();
        if(request.getQueryString()!=null) {
            requestUrl.append("?");
            requestUrl.append(request.getQueryString());
        }
        
        //为该线程处理该请求设置一个对象,缓存在内存
        MonitoredThread monitoredThread = new MonitoredThread(Thread.currentThread(),
            requestUrl.toString());
        activeThreads.put(key, monitoredThread);

        try {
            getNext().invoke(request, response);//真正执行逻辑
        } finally {
            activeThreads.remove(key);//执行完成后,将该线程关注的请求取消掉
            if (monitoredThread.markAsDone() == MonitoredThreadState.STUCK) {//说明此时状态虽然标记成完成,但是实际是卡住的
                completedStuckThreadsQueue.add(
                        new CompletedStuckThread(monitoredThread.getThread(),
                            monitoredThread.getActiveTimeInMillis()));
            }
        }
    }

    //定期执行的周期任务
    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        long thresholdInMillis = threshold * 1000;

        // Check monitored threads, being careful that the request might have
        // completed by the time we examine it
        for (MonitoredThread monitoredThread : activeThreads.values()) {//循环查看活跃的线程集合
            long activeTime = monitoredThread.getActiveTimeInMillis();//计算线程处理该url已经处理多久了

            if (activeTime >= thresholdInMillis && monitoredThread.markAsStuckIfStillRunning()) {//将处理该请求的线程状态从运行中 修改到卡住状态,因为该线程已经卡住很久了
                int numStuckThreads = stuckCount.incrementAndGet();//增加卡住的线程数
                notifyStuckThreadDetected(monitoredThread, activeTime, numStuckThreads);//通知此时线程已经卡住了
            }
        }
        // Check if any threads previously reported as stuck, have finished.测试已经报道的卡住的线程已经完成了
        for (CompletedStuckThread completedStuckThread = completedStuckThreadsQueue.poll();//循环每一个卡住的线程
            completedStuckThread != null; completedStuckThread = completedStuckThreadsQueue.poll()) {

            int numStuckThreads = stuckCount.decrementAndGet();//因为该卡住的线程已经完成了,因此删除一个卡住的数量
            notifyStuckThreadCompleted(completedStuckThread, numStuckThreads);//通知已经有一个线程已经不卡了
        }
    }

    //获取所有卡住的线程的id
    public long[] getStuckThreadIds() {
        List<Long> idList = new ArrayList<Long>();
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            if (monitoredThread.isMarkedAsStuck()) {//true表示该线程已经卡住了
                idList.add(Long.valueOf(monitoredThread.getThread().getId()));
            }
        }

        long[] result = new long[idList.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = idList.get(i).longValue();
        }
        return result;
    }

    //获取所有卡住的线程的name集合
    public String[] getStuckThreadNames() {
        List<String> nameList = new ArrayList<String>();
        for (MonitoredThread monitoredThread : activeThreads.values()) {
            if (monitoredThread.isMarkedAsStuck()) {//true表示该线程已经卡住了
                nameList.add(monitoredThread.getThread().getName());
            }
        }
        return nameList.toArray(new String[nameList.size()]);
    }

    //表示是此时线程正在处理哪个请求---此时请求线程的状态
    private static class MonitoredThread {

        /**
         * Reference to the thread to get a stack trace from background task
         */
        private final Thread thread;//当前处理请求的线程对象
        private final String requestUri;//当前线程正在处理的请求url
        private final long start;//开始运行该url的时间
        private final AtomicInteger state = new AtomicInteger(
            MonitoredThreadState.RUNNING.ordinal());//默认状态是运行中

        public MonitoredThread(Thread thread, String requestUri) {
            this.thread = thread;
            this.requestUri = requestUri;
            this.start = System.currentTimeMillis();
        }

        public Thread getThread() {
            return this.thread;
        }

        public String getRequestUri() {
            return requestUri;
        }

        //线程处理该url已经处理多久了
        public long getActiveTimeInMillis() {
            return System.currentTimeMillis() - start;
        }

        //返回开始时间
        public Date getStartTime() {
            return new Date(start);
        }

        //将处理该请求的线程状态从运行中 修改到卡住状态
        public boolean markAsStuckIfStillRunning() {
            return this.state.compareAndSet(MonitoredThreadState.RUNNING.ordinal(),
                MonitoredThreadState.STUCK.ordinal());
        }

        //标记该线程对该url请求已经完成---返回此时该线程对该请求的状态
        public MonitoredThreadState markAsDone() {
            int val = this.state.getAndSet(MonitoredThreadState.DONE.ordinal());//设置完成,但是返回此时应该的状态
            return MonitoredThreadState.values()[val];//找到状态对应的对象
        }

        //true表示该线程已经卡住了
        boolean isMarkedAsStuck() {
            return this.state.get() == MonitoredThreadState.STUCK.ordinal();
        }
    }

    //CompletedStuckThread对象表示每一个卡住的线程，在请求中卡住的时间是多少
    private static class CompletedStuckThread {

        private final String threadName;//线程的name
        private final long threadId;//线程的唯一ID
        private final long totalActiveTime;//该线程卡住的时间

        public CompletedStuckThread(Thread thread, long totalActiveTime) {
            this.threadName = thread.getName();
            this.threadId = thread.getId();
            this.totalActiveTime = totalActiveTime;
        }

        public String getName() {
            return this.threadName;
        }

        public long getId() {
            return this.threadId;
        }

        public long getTotalActiveTime() {
            return this.totalActiveTime;
        }
    }

    //检查的线程状态
    private enum MonitoredThreadState {
        RUNNING,//运行中 
        STUCK, //卡住了
        DONE;//完成
    }
}
