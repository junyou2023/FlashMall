package com.example.demo.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 线程池实验服务
 *
 * 作用：
 * 专门用于向 cacheTaskExecutor 提交可控的模拟任务，
 * 方便你观察线程池在高并发下的行为。
 */
@Service
public class ThreadPoolLabService {

    /**
     * 提交一个模拟任务
     *
     * taskId：方便日志定位
     * sleepMillis：模拟任务耗时
     */
    @Async("cacheTaskExecutor")
    public void submitDummyTask(String taskId, long sleepMillis) {
        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        System.out.println("【线程池任务开始】taskId = " + taskId
                + "，thread = " + threadName
                + "，sleepMillis = " + sleepMillis);

        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("【线程池任务中断】taskId = " + taskId
                    + "，thread = " + threadName);
            return;
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("【线程池任务完成】taskId = " + taskId
                + "，thread = " + threadName
                + "，cost = " + cost + "ms");
    }
}