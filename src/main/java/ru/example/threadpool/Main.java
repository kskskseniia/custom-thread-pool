package ru.example.threadpool;

import java.util.concurrent.Future;

public class Main {

    public static void main(String[] args) throws Exception {
        ThreadPoolConfig config = ThreadPoolConfig.load("thread-pool.properties");

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

        Thread.sleep(10000);

        System.out.println();
        System.out.println("=== Overload demo ===");

        for (int i = 1; i <= 40; i++) {
            pool.execute(new DemoTask("overload-task-" + i, 3000));
        }

        Thread.sleep(10000);

        pool.shutdown();

        Thread.sleep(15000);

        System.out.println("[Main] Application finished.");
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
}