package ru.example.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {

    private final String poolName;
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String threadName = poolName + "-worker-" + threadCounter.getAndIncrement();

        System.out.println("[ThreadFactory] Creating new thread: " + threadName);

        return new Thread(() -> {
            try {
                runnable.run();
            } finally {
                System.out.println("[ThreadFactory] Thread finished: " + threadName);
            }
        }, threadName);
    }
}