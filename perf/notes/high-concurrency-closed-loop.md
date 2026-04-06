# FlashMall 准生产高并发模拟闭环（本轮）

> 这不是“生产容量承诺”，而是“准生产高并发模拟闭环”。
> 目标是：链路完整、脚本可跑、指标可看、口径可讲。

---

## 1. 当前链路分层（先讲清边界）

### A) 真实下单写链路（主链路）
1. `POST /orders/create`
2. `OrderGatewayService` 做 requestId 幂等 + Redis 预扣库存 + 标记 PENDING + 发 MQ
3. `OrderMessageConsumer` 手动 ACK 模式消费
4. `OrderConsumerService` MySQL 扣库存 + 创建订单 + 事务后善后
5. 失败时补偿：回补 Redis 库存、清理幂等和订单状态

### B) 线程池实验链路（实验田）
- `/lab/thread-pool/*`
- 线程池：`cacheTaskExecutor`
- 目标：观察 activeCount/queueSize/callerRunsTriggered
- 边界：它不是订单入口/MQ消费/聚合查询主链路线程池治理

### C) 正式业务聚合查询链路（本轮新增）
- `GET /dashboard/home`
- `DashboardAggregationService` 使用 `CompletableFuture + queryTaskExecutor`
- 并发执行 4 类读任务（DB 统计 + Redis 读取）
- 支持 timeout fallback（降级）并暴露 degrade/timeout 指标

---

## 2. 本轮新增的最小可观测性入口

## 总览入口（本轮新增）
- `GET /ops/overview`
- 作用：一屏聚合查看写链路计数器、queryTaskExecutor、cacheTaskExecutor/labCounters、MQ 口径提示。
- 关系：先看总览，再按需深入 `/ops/write-chain/stats`、`/dashboard/query-pool/stats`、`/lab/thread-pool/stats`。


## 写链路统计
- `GET /ops/write-chain/stats`
- 重点看：
  - `redisPreDeductSuccess / Insufficient / NotPreheated`
  - `mqSendSuccess / mqSendFail / mqConfirmFail / mqReturnFail`
  - `consumeSuccess / consumeNackRequeue / consumeRejectDiscard`

## 聚合查询线程池统计
- `GET /dashboard/query-pool/stats`
- 重点看：
  - `activeCount`
  - `queueSize`
  - `degradeCount`
  - `timeoutCount`

## MQ 堆积口径说明
- `ready`：还在队列里、等消费者拿
- `unacked`：消费者已取走但还没 ACK
- `backlog`：可用 `ready + unacked` 粗看
- 当前项目通过 `/ops/write-chain/stats` 给出最小提示，精确口径以 RabbitMQ 管理台为准。

---

## 3. 压测脚本矩阵（4 类）

1) 读链路压测：`perf/k6/product-detail.js`
2) 写链路压测：`perf/k6/order-create.js`
3) 线程池实验压测：`perf/k6/thread-pool-submit.js`
4) 聚合查询线程池主链路压测：`perf/k6/dashboard-home.js`

可选混合流量：`perf/k6/mixed-flow.js`

---


## 3.5 本轮新增：JMeter GUI 可视化压测补充

- 测试计划目录：`perf/jmeter/`
  - `product-detail-read.jmx`
  - `order-create-write.jmx`
  - `lab-thread-pool-submit.jmx`
  - `dashboard-home-query.jmx`
- 这 4 个计划分别对应读链路、写链路、实验线程池链路、正式聚合查询线程池链路。
- 边界不变：
  - JMeter 负责可视化发流量与请求侧统计
  - 应用内部指标继续通过 `/ops/**`、`/dashboard/**`、`/lab/**` 查看

## 4. 推荐执行顺序（perf 环境）

### Step 1: 启动
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
```

### Step 2: 初始化库存
```bash
curl -X POST 'http://localhost:8080/redis/stock/preheat/all'
```

### Step 3: 写链路压测
```bash
k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_ID=1 -e RATE=200 -e DURATION=120s perf/k6/order-create.js
```

### Step 4: 读链路压测
```bash
k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_ID=1 -e VUS=120 -e DURATION=120s perf/k6/product-detail.js
```

### Step 5: 线程池实验压测
```bash
k6 run -e BASE_URL=http://localhost:8080 -e TASK_COUNT=120 -e SLEEP_MILLIS=1200 perf/k6/thread-pool-submit.js
```

### Step 6: 聚合查询线程池压测
```bash
k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_ID=1 -e RATE=180 -e DURATION=120s perf/k6/dashboard-home.js
```

### Step 7: 混合流量（可选）
```bash
k6 run -e BASE_URL=http://localhost:8080 -e DURATION=180s -e WRITE_RATE=60 -e READ_VUS=90 -e DASHBOARD_VUS=60 perf/k6/mixed-flow.js
```

### Step 8: 对账
```bash
mysql -uroot -p123456 demo_perf < perf/sql/check-order-consistency.sql
```

---

## 5. 压测时“先看哪里”

### 写链路先看
1. `/ops/write-chain/stats` 的 `redisPreDeduct*`（入口库存判定是否扛住）
2. `mqSend*`（消息发送是否稳定）
3. `consume*`（消费是否堆积重投）
4. RabbitMQ 管理台 ready/unacked 是否持续抬升

### 聚合查询线程池先看
1. `/dashboard/query-pool/stats` 的 `activeCount/queueSize`
2. `degradeCount/timeoutCount` 是否明显上升
3. 访问日志 `MIN-OBS` 中 `/dashboard/home` 的 `costMs` 变化

---

## 6. 当前可讲点 / 边界

## 能真实讲
- 订单写链路完成了“入口预扣 + MQ 异步 + 手动ACK消费 + 对账脚本”的闭环。
- 实验线程池和正式业务聚合查询线程池已经分层，不再混为一谈。
- 聚合查询场景可以真实演示：排队、超时、降级、RT 抬升。

## 不能硬吹
- 当前指标是单实例内存计数器，不是集群级全量指标平台。
- 没有 DLQ/延迟重试编排和自动告警，只是最小可观测性。
- 这是“准生产模拟”，不是“生产容量认证结果”。
