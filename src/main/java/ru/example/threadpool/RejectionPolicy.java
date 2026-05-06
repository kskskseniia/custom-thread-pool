package ru.example.threadpool;

public interface RejectionPolicy {

    void reject(Runnable task);
}