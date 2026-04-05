package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 *
 * 原版本这个类只有两件事：
 * 1. 标记为配置类（@Configuration）
 * 2. 开启 Spring 的 @Async 能力（@EnableAsync）
 *
 * 也就是说，原来只是“能异步”，
 * 但没有真正定义你自己的线程池。
 *
 * =========================================================
 * 【本轮改动 1】
 * 从“只开启 @Async”升级为“显式定义业务线程池”
 * =========================================================
 *
 * 为什么要改？
 * 因为默认异步执行器不可控：
 * - 不方便观察线程数、队列长度
 * - 不方便验证拒绝策略
 * - 不方便做线程池专项压测
 *
 * 所以这一轮开始，我们给缓存类异步任务单独定义一个线程池：
 * cacheTaskExecutor
 *
 * 注意：
 * 当前这个线程池还不是“整个系统主链路线程池治理”。
 * 它现在主要服务的是：
 * - 延迟双删的第二次删缓存
 *
 * 所以它当前的定位应该是：
 * “线程池治理的第一块实验田”
 * 而不是：
 * “已经完成高并发主链路线程池治理”
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * =========================================================
     * 【本轮改动 2】
     * 线程池参数从“写死在代码里”变成“从配置文件读取”
     * =========================================================
     *
     * 为什么要这样改？
     * 因为线程池参数本质上属于“运行时调优参数”，
     * 不应该每次都改 Java 代码再重新编译。
     *
     * 当前这样做以后，你就能在 application.yml 里快速调：
     * - corePoolSize
     * - maxPoolSize
     * - queueCapacity
     * - keepAliveSeconds
     * - threadNamePrefix
     *
     * 这一步的价值是：
     * 让线程池从“写着玩的 demo 配置”
     * 变成“可调、可实验、可压测的配置项”。
     */
    @Value("${app.async.cache.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.async.cache.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${app.async.cache.queue-capacity:50}")
    private int queueCapacity;

    @Value("${app.async.cache.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${app.async.cache.thread-name-prefix:cache-delete-}")
    private String threadNamePrefix;

    @Value("${app.async.query.core-pool-size:8}")
    private int queryCorePoolSize;

    @Value("${app.async.query.max-pool-size:16}")
    private int queryMaxPoolSize;

    @Value("${app.async.query.queue-capacity:200}")
    private int queryQueueCapacity;

    @Value("${app.async.query.keep-alive-seconds:60}")
    private int queryKeepAliveSeconds;

    @Value("${app.async.query.thread-name-prefix:biz-query-}")
    private String queryThreadNamePrefix;

    /**
     * 缓存异步任务线程池
     *
     * =========================================================
     * 【本轮改动 3】
     * 新增一个名字明确的业务线程池：cacheTaskExecutor
     * =========================================================
     *
     * 为什么要单独起这个名字？
     * 因为后面 @Async("cacheTaskExecutor") 会显式指定：
     * 某些异步任务必须跑在这个线程池里，
     * 而不是跑在 Spring 默认异步执行器里。
     *
     * 当前这个线程池先只服务一个真实任务：
     * - AsyncDeleteService 里的延迟双删第二次删缓存
     *
     * 你现在可以把它理解成：
     * “专门处理缓存类异步任务的后厨”
     */
    @Bean("cacheTaskExecutor")
    public ThreadPoolTaskExecutor cacheTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        /**
         * 核心线程数
         *
         * 含义：
         * 平时常驻的工作线程数。
         *
         * 可以理解成：
         * “线程池平时的基本兵力”
         *
         * 当前配置小，是因为你还在本地实验阶段，
         * 主要目的不是追求吞吐极限，
         * 而是先观察线程池在不同压力下的行为。
         */
        executor.setCorePoolSize(corePoolSize);

        /**
         * 最大线程数
         *
         * 含义：
         * 当核心线程都忙、队列也开始吃满之后，
         * 线程池最多还能再扩到多少线程。
         *
         * 你可以把它理解成：
         * “高峰时最多能临时再拉多少人顶上去”
         */
        executor.setMaxPoolSize(maxPoolSize);

        /**
         * 队列容量
         *
         * 含义：
         * 当核心线程忙不过来时，新任务先进入队列等待。
         *
         * 这也是后面压测时最值得观察的指标之一：
         * - queueSize
         * - remainingCapacity
         *
         * 因为线程池是否开始堆积，最先通常反映在队列上。
         */
        executor.setQueueCapacity(queueCapacity);

        /**
         * 非核心线程空闲回收时间
         *
         * 含义：
         * 超过核心线程数的那些“临时扩出来的线程”，
         * 如果空闲达到 keepAliveSeconds，就会被回收。
         *
         * 这样做的意义是：
         * 高峰过后，线程池不会一直维持在最大线程数。
         */
        executor.setKeepAliveSeconds(keepAliveSeconds);

        /**
         * 线程名前缀
         *
         * 作用非常实际：
         * 方便你在日志里一眼看出：
         * “这是不是 cacheTaskExecutor 的线程”
         *
         * 比如后面你压测时如果看到：
         * cache-delete-xxx
         * 说明任务确实在线程池线程里跑。
         *
         * 如果以后你看到 http-nio-8080-exec-xxx 这种请求线程名也开始跑任务，
         * 那就说明 CallerRunsPolicy 很可能已经触发了。
         */
        executor.setThreadNamePrefix(threadNamePrefix);

        /**
         * =========================================================
         * 【本轮改动 4】
         * 显式指定拒绝策略：CallerRunsPolicy
         * =========================================================
         *
         * 这是这一轮最关键的改动之一。
         *
         * 含义：
         * 当出现下面这个场景时：
         * - 核心线程满
         * - 队列满
         * - 最大线程也满
         *
         * 那么新提交的任务不会直接丢弃，
         * 而是由“提交任务的那个线程”自己执行。
         *
         * 为什么当前阶段选它？
         * 因为它最适合你现在做实验：
         *
         * 1. 不会悄悄丢任务
         * 2. 能把压力反推回调用方
         * 3. 很容易在压测里观察到“系统变慢但不直接丢任务”的现象
         *
         * 你后面回答面试官：
         * “如果线程池处理不完怎么办？”
         * 现在就可以基于这个策略，讲出真实实验结果。
         *
         * 但注意：
         * 当前这个策略只是在“缓存异步任务线程池”里做实验，
         * 还不能上升成：
         * “我已经治理了订单主链路线程池”
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        /**
         * 应用关闭时，尽量等待已提交任务执行完
         *
         * 这两个配置的作用是：
         * 避免应用停机时，线程池里还有任务没执行完就被强行中断。
         *
         * 当前阶段虽然只是实验线程池，
         * 但这两个参数属于比较好的工程习惯。
         */
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);

        /**
         * 初始化线程池
         *
         * 到这里，这个线程池就真正可用了。
         */
        executor.initialize();
        return executor;
    }

    /**
     * =========================================================
     * 【本轮改动 5】
     * 新增正式业务聚合查询线程池：queryTaskExecutor
     * =========================================================
     *
     * 【为什么改】
     * 当前 cacheTaskExecutor 的真实定位是：
     * - 真实业务：延迟双删第二次删缓存
     * - 实验用途：线程池行为观察
     *
     * 但如果要更接近“高并发主链路线程池治理”，
     * 更典型的真实场景是：聚合查询接口把多个读任务并发拆分执行。
     *
     * 所以这一轮新增 queryTaskExecutor，专门承接：
     * - /dashboard/home 这类正式业务聚合查询
     * - CompletableFuture 并发拆分任务
     *
     * 这样你后续回答“线程池治理主链路”时：
     * 可以清晰区分两个线程池的角色，不会把实验链路和业务主链路混在一起。
     */
    @Bean("queryTaskExecutor")
    public ThreadPoolTaskExecutor queryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(queryCorePoolSize);
        executor.setMaxPoolSize(queryMaxPoolSize);
        executor.setQueueCapacity(queryQueueCapacity);
        executor.setKeepAliveSeconds(queryKeepAliveSeconds);
        executor.setThreadNamePrefix(queryThreadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
