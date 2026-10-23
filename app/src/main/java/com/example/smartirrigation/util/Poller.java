package com.example.smartirrigation.util;

import android.os.Handler;
import android.os.Looper;

/**
 * 轮询器（Handler 封装）：首页 5 秒、命令状态 2.5 秒等场景使用。
 * 设计意图：onPause() 中调用 stop() 取消，避免 Activity 泄漏；stop() 后正在执行的任务不会再次投递。
 */
public class Poller {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable task;
    private boolean running;

    /**
     * @param intervalMs 轮询间隔（毫秒），首次立即执行一次
     */
    public void start(final long intervalMs, final Runnable task) {
        stop();
        this.task = new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                task.run();
                handler.postDelayed(this, intervalMs);
            }
        };
        running = true;
        handler.post(this.task);
    }

    public void stop() {
        running = false;
        if (task != null) {
            handler.removeCallbacks(task);
        }
    }

    public boolean isRunning() {
        return running;
    }
}
