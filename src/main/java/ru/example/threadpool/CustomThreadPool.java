package ru.example.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final CustomThreadFactory threadFactory;
    private final RejectionPolicy rejectionPolicy;

    private final List<Worker> workers = new ArrayList<>();
    private final Object lock = new Object();

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private volatile boolean shutdown = false;

    public CustomThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            String poolName,
            RejectionPolicy rejectionPolicy
    ) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be greater than 0");
        }

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be greater than or equal to corePoolSize");
        }

        if (queueSize <= 0) {
            throw new IllegalArgumentException("queueSize must be greater than 0");
        }

        if (minSpareThreads < 0) {
            throw new IllegalArgumentException("minSpareThreads must be greater than or equal to 0");
        }

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.threadFactory = new CustomThreadFactory(poolName);
        this.rejectionPolicy = rejectionPolicy;

        synchronized (lock) {
            for (int i = 0; i < corePoolSize; i++) {
                addWorker();
            }
        }
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command is null");
        }

        synchronized (lock) {
            if (shutdown) {
                rejectionPolicy.reject(command);
                return;
            }

            boolean accepted = offerTask(command);

            if (!accepted && workers.size() < maxPoolSize) {
                Worker worker = addWorker();
                accepted = worker.offer(command);

                if (accepted) {
                    System.out.println("[Pool] Task accepted into new queue #" + worker.getId() + ": " + command);
                }
            }

            if (!accepted) {
                rejectionPolicy.reject(command);
                return;
            }

            ensureMinSpareThreads();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException("callable is null");
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);

        return futureTask;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            System.out.println("[Pool] Shutdown requested.");
            shutdown = true;
        }
    }

    @Override
    public void shutdownNow() {
        synchronized (lock) {
            System.out.println("[Pool] ShutdownNow requested.");
            shutdown = true;

            for (Worker worker : workers) {
                worker.stopNow();
                worker.clearQueue();
            }
        }
    }

    private boolean offerTask(Runnable command) {
        if (workers.isEmpty()) {
            return false;
        }

        int workersCount = workers.size();
        int startIndex = Math.abs(roundRobinCounter.getAndIncrement() % workersCount);

        for (int i = 0; i < workersCount; i++) {
            int index = (startIndex + i) % workersCount;
            Worker worker = workers.get(index);

            if (worker.offer(command)) {
                System.out.println("[Pool] Task accepted into queue #" + worker.getId() + ": " + command);
                return true;
            }
        }

        return false;
    }

    private Worker addWorker() {
        Worker worker = new Worker(workers.size() + 1);
        workers.add(worker);

        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread);
        thread.start();

        return worker;
    }

    private void ensureMinSpareThreads() {
        int spareThreads = countSpareThreads();

        while (spareThreads < minSpareThreads && workers.size() < maxPoolSize) {
            Worker worker = addWorker();
            System.out.println("[Pool] Added spare worker #" + worker.getId());
            spareThreads++;
        }
    }

    private int countSpareThreads() {
        int count = 0;

        for (Worker worker : workers) {
            if (worker.isSpare()) {
                count++;
            }
        }

        return count;
    }

    private class Worker implements Runnable {

        private final int id;
        private final BlockingQueue<Runnable> queue;

        private volatile boolean running = true;
        private volatile Thread thread;

        public Worker(int id) {
            this.id = id;
            this.queue = new ArrayBlockingQueue<>(queueSize);
        }

        public int getId() {
            return id;
        }

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        public boolean offer(Runnable task) {
            return queue.offer(task);
        }

        public boolean isSpare() {
            return queue.isEmpty();
        }

        public void stopNow() {
            running = false;

            if (thread != null) {
                thread.interrupt();
            }
        }

        public void clearQueue() {
            queue.clear();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    if (shutdown && queue.isEmpty()) {
                        break;
                    }

                    Runnable task = queue.poll(keepAliveTime, timeUnit);

                    if (task == null) {
                        synchronized (lock) {
                            if (workers.size() > corePoolSize) {
                                System.out.println("[Worker] " + Thread.currentThread().getName()
                                        + " idle timeout, stopping.");

                                workers.remove(this);
                                break;
                            }
                        }

                        continue;
                    }

                    if (shutdown) {
                        System.out.println("[Worker] " + Thread.currentThread().getName()
                                + " skipped task because pool is shutting down.");
                        continue;
                    }

                    System.out.println("[Worker] " + Thread.currentThread().getName()
                            + " executes " + task);

                    task.run();

                } catch (InterruptedException e) {
                    if (shutdown || !running) {
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("[Worker] " + Thread.currentThread().getName()
                            + " task failed: " + e.getMessage());
                }
            }

            System.out.println("[Worker] " + Thread.currentThread().getName() + " terminated.");
        }
    }
}