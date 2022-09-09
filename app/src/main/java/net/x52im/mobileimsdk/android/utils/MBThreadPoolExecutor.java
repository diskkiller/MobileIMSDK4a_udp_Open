/*
 * Copyright (C) 2021  即时通讯网(52im.net) & Jack Jiang.
 * The MobileIMSDK_UDP (MobileIMSDK v6.x UDP版) Project. 
 * All rights reserved.
 * 
 * > Github地址：https://github.com/JackJiang2011/MobileIMSDK
 * > 文档地址：  http://www.52im.net/forum-89-1.html
 * > 技术社区：  http://www.52im.net/
 * > 技术交流群：215477170 (http://www.52im.net/topic-qqgroup.html)
 * > 作者公众号：“即时通讯技术圈】”，欢迎关注！
 * > 联系作者：  http://www.52im.net/thread-2792-1-1.html
 *  
 * "即时通讯网(52im.net) - 即时通讯开发者社区!" 推荐开源工程。
 * 
 * MBThreadPoolExecutor.java at 2021-7-2 22:38:44, code by Jack Jiang.
 */
package net.x52im.mobileimsdk.android.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MBThreadPoolExecutor {

    private static final String TAG = MBThreadPoolExecutor.class.getSimpleName();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE_TIME = 30L;
    private static final int WAIT_COUNT = 128;

    private static ThreadPoolExecutor pool = createThreadPoolExecutor();

    private static ThreadPoolExecutor createThreadPoolExecutor() {
        if (pool == null) {
            pool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(WAIT_COUNT),
                    new CThreadFactory("MBThreadPool", Thread.NORM_PRIORITY - 2),
                    new CHandlerException());
        }
        return pool;
    }

    /**
     * 线程工厂：
     * 用于创建自定义优先级，线程名称的守护线程
     */
    public static class CThreadFactory implements ThreadFactory {
        //是一个提供原子操作的Integer类，通过线程安全的方式操作加减。用于线程工厂中给线程命名
        private AtomicInteger counter = new AtomicInteger(1);//设置当前值
        private String prefix = "";
        private int priority = Thread.NORM_PRIORITY;

        public CThreadFactory(String prefix, int priority) {
            this.prefix = prefix;
            this.priority = priority;
        }

        public CThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        public Thread newThread(Runnable r) {
            /**
             *AtomicInteger i=new AtomicInteger(0);
             * int q=i.incrementAndGet();
             *
             * System.out.println(q);输出1
             *
             * 或
             *
             * int q=i.getAndIncrement();
             *
             * System.out.println(q);输出0
             * 类似于i++和++i的区别
             *
             */
            Thread executor = new Thread(r, prefix + " #" + counter.getAndIncrement());

            /**
             * 守护线程--也称“服务线程”，在没有用户线程可服务时会自动离开。主线程结束后守护线程也随之销毁
             *
             * 垃圾回收线程就是一个经典的守护线程，当我们的程序中不再有任何运行的Thread,程序就不会再产生垃圾，垃圾回收器也就无事可做，所以当垃圾回收线程是JVM上仅剩的线程时，垃圾回收线程会自动离开。它始终在低级别的状态中运行，用于实时监控和管理系统中的可回收资源。
             * 生命周期：守护进程（Daemon）是运行在后台的一种特殊进程。它独立于控制终端并且周期性地执行某种任务或等待处理某些发生的事件。也就是说守护线程不依赖于终端，但是依赖于系统，与系统“同生共死”。那Java的守护线程是什么样子的呢。当JVM中所有的线程都是守护线程的时候，JVM就可以退出了；如果还有一个或以上的非守护线程则JVM不会退出。
             */
            executor.setDaemon(true);

            /**
             * 线程优先级
             * 不设置优先级,默认为 5
             * 先设置,在启动!!!!
             *
             * 优先级只是相对的,大概率下是优先级高的先执行,不是一定先执行
             */
            executor.setPriority(priority);
            return executor;
        }
    }

    /**
     * 拒绝任务的处理程序
     * 抛出异常后，重新创建线程池
     */
    private static class CHandlerException extends ThreadPoolExecutor.AbortPolicy {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            Log.d(TAG, "rejectedExecution:" + r);
            Log.e(TAG, logAllThreadStackTrace().toString());
//          Tips.showForce("任务被拒绝", 5000);
            if (!pool.isShutdown()) {
                pool.shutdown();
                pool = null;
            }

            pool = createThreadPoolExecutor();
        }
    }

    private static ExecutorService jobsForUI = Executors.newFixedThreadPool(
            CORE_POOL_SIZE, new CThreadFactory("MBJobsForUI", Thread.NORM_PRIORITY - 1));

    public static void runInBackground(Runnable runnable) {
        if (pool == null) {
            createThreadPoolExecutor();
        }
        pool.execute(runnable);
    }

    private static Thread mainThread;
    private static Handler mainHandler;

    static {
        Looper mainLooper = Looper.getMainLooper();
        mainThread = mainLooper.getThread();
        mainHandler = new Handler(mainLooper);
    }

    public static boolean isOnMainThread() {
        return mainThread == Thread.currentThread();
    }

    public static void runOnMainThread(Runnable r) {
        if (isOnMainThread()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    public static void runOnMainThread(Runnable r, long delayMillis) {
        if (delayMillis <= 0) {
            runOnMainThread(r);
        } else {
            mainHandler.postDelayed(r, delayMillis);
        }
    }

    private static HashMap<Runnable, Runnable> mapToMainHandler = new HashMap<Runnable, Runnable>();

    public static void runInBackground(final Runnable runnable, long delayMillis) {
        if (delayMillis <= 0) {
            runInBackground(runnable);
        } else {
            Runnable mainRunnable = () -> {
                mapToMainHandler.remove(runnable);
                pool.execute(runnable);
            };

            //# Bug FIX: 20200716 by Jack Jiang
            if(mapToMainHandler.containsKey(runnable)) {
                Log.d(TAG, "该runnable（"+runnable+"）仍在mapToMainHandler中，表示它并未被执行，将" +
                        "先从mainHandler中移除，否则存在上次延迟执行并未完成，本次又再次提交延迟执行任务，失去了延迟执行的意义！");
                removeCallbackInBackground(runnable);
            }
            //# Bug FIX: END

            mapToMainHandler.put(runnable, mainRunnable);
            mainHandler.postDelayed(mainRunnable, delayMillis);
        }
    }

    public static void removeCallbackOnMainThread(Runnable r) {
        mainHandler.removeCallbacks(r);
    }

    public static void removeCallbackInBackground(Runnable runnable) {
        Runnable mainRunnable = mapToMainHandler.get(runnable);
        if (mainRunnable != null) {
            mainHandler.removeCallbacks(mainRunnable);
            pool.remove(mainRunnable); // add by jackjiang 20200718：记得尝试清除已加入线程队列但未开始执行的任务
        }
        else
            pool.remove(runnable);     // add by jackjiang 20200718：记得尝试清除已加入线程队列但未开始执行的任务
    }

    public static void logStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("getActiveCount=");
        sb.append(pool.getActiveCount());
        sb.append("\ngetTaskCount=");
        sb.append(pool.getTaskCount());
        sb.append("\ngetCompletedTaskCount=");
        sb.append(pool.getCompletedTaskCount());
        Log.d(TAG, sb.toString());
    }

    public static StringBuilder logAllThreadStackTrace() {
        StringBuilder builder = new StringBuilder();
        Map<Thread, StackTraceElement[]> liveThreads = Thread.getAllStackTraces();
        for (Iterator<Thread> i = liveThreads.keySet().iterator(); i.hasNext(); ) {
            Thread key = i.next();
            builder.append("Thread ").append(key.getName()).append("\n");
            StackTraceElement[] trace = liveThreads.get(key);
            for (int j = 0; j < trace.length; j++) {
                builder.append("\tat ").append(trace[j]).append("\n");
            }
        }
        return builder;
    }
}
