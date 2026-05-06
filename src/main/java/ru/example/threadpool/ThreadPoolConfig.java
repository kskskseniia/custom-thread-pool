package ru.example.threadpool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ThreadPoolConfig {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final String poolName;

    private ThreadPoolConfig(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            String poolName
    ) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.poolName = poolName;
    }

    public static ThreadPoolConfig load(String fileName) {
        Properties properties = new Properties();

        try (InputStream inputStream = ThreadPoolConfig.class
                .getClassLoader()
                .getResourceAsStream(fileName)) {

            if (inputStream == null) {
                throw new IllegalStateException("Config file not found: " + fileName);
            }

            properties.load(inputStream);

            return new ThreadPoolConfig(
                    getInt(properties, "corePoolSize"),
                    getInt(properties, "maxPoolSize"),
                    getLong(properties, "keepAliveTime"),
                    TimeUnit.valueOf(getString(properties, "timeUnit")),
                    getInt(properties, "queueSize"),
                    getInt(properties, "minSpareThreads"),
                    getString(properties, "poolName")
            );

        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config file: " + fileName, e);
        }
    }

    private static String getString(Properties properties, String key) {
        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }

        return value.trim();
    }

    private static int getInt(Properties properties, String key) {
        return Integer.parseInt(getString(properties, key));
    }

    private static long getLong(Properties properties, String key) {
        return Long.parseLong(getString(properties, key));
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    public String getPoolName() {
        return poolName;
    }
}