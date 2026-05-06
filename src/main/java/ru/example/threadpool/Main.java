package ru.example.threadpool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final int BENCHMARK_TASKS = 30;
    private static final long BENCHMARK_TASK_TIME_MS = 1500;

    public static void main(String[] args) throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.load("thread-pool.properties");

        runDemo(config);

        System.out.println();
        System.out.println("=== Performance comparison ===");

        long customPoolTime = runCustomThreadPoolBenchmark(config);
        long standardPoolTime = runThreadPoolExecutorBenchmark(config);

        System.out.println();
        System.out.println("=== Benchmark result ===");
        System.out.println("[Benchmark] CustomThreadPool time: " + customPoolTime + " ms");
        System.out.println("[Benchmark] ThreadPoolExecutor time: " + standardPoolTime + " ms");

        if (customPoolTime > standardPoolTime) {
            System.out.println("[Benchmark] ThreadPoolExecutor was faster by "
                    + (customPoolTime - standardPoolTime) + " ms");
        } else if (customPoolTime < standardPoolTime) {
            System.out.println("[Benchmark] CustomThreadPool was faster by "
                    + (standardPoolTime - customPoolTime) + " ms");
        } else {
            System.out.println("[Benchmark] Both pools showed the same time.");
        }

        System.out.println("[Main] Application finished.");
    }

    private static void runDemo(ThreadPoolConfig config) throws Exception {
        CustomThreadPool pool = new CustomThreadPool(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                config.getTimeUnit(),
                config.getQueueSize(),
                config.getMinSpareThreads(),
                config.getPoolName(),
                task -> System.out.println("[Rejected] Task " + task + " was rejected due to overload!")
        );

        System.out.println("=== Normal load demo ===");

        for (int i = 1; i <= 8; i++) {
            pool.execute(new DemoTask("normal-task-" + i, 1500));
        }

        Future<String> future = pool.submit(() -> {
            Thread.sleep(1000);
            return "Result from Callable task";
        });

        System.out.println("[Main] Callable result: " + future.get());

        Thread.sleep(7000);

        System.out.println();
        System.out.println("=== Overload demo ===");

        for (int i = 1; i <= 40; i++) {
            pool.execute(new DemoTask("overload-task-" + i, 3000));
        }

        Thread.sleep(10000);

        pool.shutdown();

        Thread.sleep(15000);
    }

    private static long runCustomThreadPoolBenchmark(ThreadPoolConfig config) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(BENCHMARK_TASKS);
        AtomicInteger rejectedTasks = new AtomicInteger(0);

        CustomThreadPool pool = new CustomThreadPool(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                config.getTimeUnit(),
                config.getQueueSize(),
                config.getMinSpareThreads(),
                config.getPoolName() + "Benchmark",
                task -> {
                    rejectedTasks.incrementAndGet();
                    latch.countDown();
                }
        );

        System.out.println("[Benchmark] CustomThreadPool test started.");

        long startTime = System.nanoTime();

        for (int i = 1; i <= BENCHMARK_TASKS; i++) {
            pool.execute(new BenchmarkTask(
                    "custom-benchmark-task-" + i,
                    BENCHMARK_TASK_TIME_MS,
                    latch
            ));
        }

        latch.await();

        long endTime = System.nanoTime();

        pool.shutdown();

        long resultMillis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        System.out.println("[Benchmark] CustomThreadPool test finished.");
        System.out.println("[Benchmark] CustomThreadPool rejected tasks: " + rejectedTasks.get());

        Thread.sleep(config.getTimeUnit().toMillis(config.getKeepAliveTime()) + 500);

        return resultMillis;
    }

    private static long runThreadPoolExecutorBenchmark(ThreadPoolConfig config) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(BENCHMARK_TASKS);
        AtomicInteger rejectedTasks = new AtomicInteger(0);

        int queueCapacity = config.getQueueSize() * config.getMaxPoolSize();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTime(),
                config.getTimeUnit(),
                new ArrayBlockingQueue<>(queueCapacity),
                (task, executorService) -> {
                    rejectedTasks.incrementAndGet();
                    latch.countDown();
                }
        );

        System.out.println("[Benchmark] ThreadPoolExecutor test started.");

        long startTime = System.nanoTime();

        for (int i = 1; i <= BENCHMARK_TASKS; i++) {
            executor.execute(new BenchmarkTask(
                    "standard-benchmark-task-" + i,
                    BENCHMARK_TASK_TIME_MS,
                    latch
            ));
        }

        latch.await();

        long endTime = System.nanoTime();

        executor.shutdown();

        long resultMillis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        System.out.println("[Benchmark] ThreadPoolExecutor test finished.");
        System.out.println("[Benchmark] ThreadPoolExecutor rejected tasks: " + rejectedTasks.get());

        return resultMillis;
    }

    private static class DemoTask implements Runnable {

        private final String name;
        private final long workTimeMillis;

        public DemoTask(String name, long workTimeMillis) {
            this.name = name;
            this.workTimeMillis = workTimeMillis;
        }

        @Override
        public void run() {
            try {
                System.out.println("[Task] " + name + " started.");
                Thread.sleep(workTimeMillis);
                System.out.println("[Task] " + name + " finished.");
            } catch (InterruptedException e) {
                System.out.println("[Task] " + name + " interrupted.");
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class BenchmarkTask implements Runnable {

        private final String name;
        private final long workTimeMillis;
        private final CountDownLatch latch;

        public BenchmarkTask(String name, long workTimeMillis, CountDownLatch latch) {
            this.name = name;
            this.workTimeMillis = workTimeMillis;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(workTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }
}