# JMeter 可视化压测指南（与现有 k6 资产并行）

> 【定位先说清】
> - JMeter：可视化配置线程组、请求参数、快速在 GUI 里调压和排查。
> - k6：脚本化、可版本化、适合 CI/复现实验。
> - 项目内部指标：仍由 `/ops/**`、`/dashboard/**`、`/lab/**`（以及可选 Actuator）提供。
>
> 所以这次新增 JMeter 是“可视化补充”，不是替代现有 k6。

---

## 1. 本轮新增的 JMeter 测试计划

目录：`perf/jmeter/`

1. `product-detail-read.jmx`
   - 链路：`GET /products/{id}`
   - 目的：读链路吞吐/RT 可视化调参

2. `order-create-write.jmx`
   - 链路：`POST /orders/create`
   - 目的：写链路入口发压，配合 `/ops/write-chain/stats` 看内部计数器变化

3. `lab-thread-pool-submit.jmx`
   - 链路：`POST /lab/thread-pool/submit`
   - 目的：线程池实验田（`cacheTaskExecutor`）可视化发压
   - 注意：仅 `perf` profile 暴露

4. `dashboard-home-query.jmx`
   - 链路：`GET /dashboard/home`
   - 目的：正式聚合查询线程池（`queryTaskExecutor`）发压与降级观察

---

## 2. 在 JMeter GUI 打开 `.jmx`

1. 启动 JMeter（建议 5.6+）
2. `File -> Open` 选择 `perf/jmeter/*.jmx`
3. 左侧树形结构中可看到：
   - Test Plan（全局变量）
   - Thread Group（并发参数）
   - HTTP Request（接口路径/参数/Body）
4. 点击顶部绿色三角执行，红色方块停止

---

## 3. 如何修改线程数、Ramp-Up、循环次数

每个 `.jmx` 都给了两层入口：

### 3.1 GUI 直接改（最直观）
- 在 `Thread Group` 里改：
  - Number of Threads (users)
  - Ramp-up period (seconds)
  - Loop Count

### 3.2 命令行参数覆盖（便于批量跑）
测试计划支持 `__P` 覆盖，例如：

```bash
jmeter -n -t perf/jmeter/product-detail-read.jmx -Jusers=120 -Jramp=20 -JloopCount=100
```

---

## 4. GUI 看哪些报表

推荐至少加 3 个 Listener：

1. **Summary Report**
   - 看整体吞吐、平均 RT、错误率
2. **Aggregate Report**
   - 看 P90/P95/P99 更方便
3. **View Results Tree**
   - 看单请求报文和响应（调试期用）
   - 高压正式跑时建议关闭，避免吃内存

---

## 5. 非 GUI 模式运行并生成报告

### 5.1 直接跑 + 产出 `.jtl`
```bash
jmeter -n \
  -t perf/jmeter/order-create-write.jmx \
  -Jusers=80 -Jramp=20 -JloopCount=30 \
  -l perf/reports/order-create.jtl
```

### 5.2 基于 `.jtl` 生成 HTML 报告
```bash
jmeter -g perf/reports/order-create.jtl -o perf/reports/order-create-html
```

---

## 6. 四条链路分别怎么压

## A) `/products/{id}` 读链路
- 文件：`product-detail-read.jmx`
- 重点：`users/ramp/loopCount`
- 对照指标：
  - JMeter：吞吐、RT、错误率
  - 应用内：`/ops/overview`、`/dashboard/query-pool/stats`（若还同时压 dashboard）

## B) `/orders/create` 写链路
- 文件：`order-create-write.jmx`
- 请求体已内置：`userId/productId/quantity/requestId`
- 对照指标：
  - JMeter：写入口 RT、成功率
  - 应用内：`/ops/write-chain/stats`、`/ops/overview`
  - MQ 管理台：ready/unacked

## C) `/lab/thread-pool/submit` 实验链路
- 文件：`lab-thread-pool-submit.jmx`
- 重点参数：`taskCount/sleepMillis`（单次请求提交多少异步任务）
- 对照指标：
  - JMeter：提交接口 RT
  - 应用内：`/lab/thread-pool/stats`、`/ops/overview` 中 cacheTaskExecutor/labCounters

## D) `/dashboard/home` 聚合查询线程池场景
- 文件：`dashboard-home-query.jmx`
- 重点：`users/ramp/loopCount`
- 对照指标：
  - JMeter：RT 分位、错误率
  - 应用内：`/dashboard/query-pool/stats`、`/ops/query-thread-pool/stats`、`/ops/overview`
  - 关注：`degradeCount/timeoutCount` 是否上升

---

## 7. 与现有 k6 / notes / sql / setup 的关系

1. `perf/k6/*.js` 仍是主力“可版本化压测资产”
2. `perf/jmeter/*.jmx` 是 GUI 辅助资产，方便临场演示、快速调参
3. `perf/sql/*.sql` 继续用于准备数据与压测后对账
4. `perf/setup/init-isolated-resources.sh` 继续用于隔离环境初始化
5. 推荐实践：
   - 先用 JMeter GUI 把参数调顺（找拐点）
   - 再把最终参数固化到 k6 脚本或命令，做可复现实验

---

## 8. 当前边界（避免误解）

- JMeter **不会自动显示你的应用内部计数器**。
- JMeter 负责“发流量 + 看请求层指标”；
  应用内部指标必须看：
  - `/ops/**`
  - `/dashboard/**`
  - `/lab/**`
  - （可选）Actuator + Prometheus + Grafana。

