# dev/perf 环境初始化与压测入口（索引版）

> 这份文档现在是“索引页”，完整一步到位手册请看：
> **`docs/isolated-environment-runbook.md`**

---

## 1) 你应该先看哪个文档？

- 想从 0 到 1 跑通（含启动、连通、冒烟、压测、排错）：  
  `docs/isolated-environment-runbook.md`
- 想用 JMeter GUI 调参：  
  `perf/notes/jmeter-guide.md`
- 想做高并发闭环方法论：  
  `perf/notes/high-concurrency-closed-loop.md`

---

## 2) 最小必跑命令（perf 场景）

```bash
# 1. 启动依赖
docker compose up -d

# 2. 启动 perf 应用
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf

# 3. 冒烟
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/dashboard/home?productId=1&userId=1001"

# 4. k6 压测（低并发起步）
k6 run perf/k6/product-detail.js
k6 run perf/k6/order-create.js
k6 run perf/k6/dashboard-home.js
```

---

## 3) perf 隔离要点（快速记忆）

- MySQL：`demo_perf`
- Redis：DB `2`
- RabbitMQ：`/flashmall-perf` + `order.exchange.perf` / `order.create.queue.perf` / `order.create.perf`

---

## 4) 观测接口（压测时同时看）

- `GET /ops/overview`
- `GET /ops/write-chain/stats`
- `GET /dashboard/query-pool/stats`
- `GET /dashboard/home?productId=1&userId=1001`
- `GET /actuator/prometheus`（perf profile）
