# 最小可视化监控接入骨架（Actuator + Prometheus + Grafana）

> 【为什么有这份说明】
> 当前项目已经有最小可观测性（`/ops/**`、`/dashboard/**`、`/lab/**`）。
> 这份文档是“下一步”骨架：把指标接到标准平台，得到历史曲线与图表。

---

## 1. 边界先明确

1. **不会替代现有最小观测接口**
   - `/ops/write-chain/stats` 仍是业务语义计数器主入口
   - `/ops/overview` 仍是一屏总览入口
2. **Actuator/Micrometer 解决的是通用指标暴露**
   - JVM、HTTP、线程等标准指标
   - Prometheus 可定时抓取并留存
   - Grafana 可做趋势图与面板
3. **JMeter 仍只负责压测发流量和请求侧指标**

---

## 2. 项目内已补的最小配置

- 依赖：
  - `spring-boot-starter-actuator`
  - `micrometer-registry-prometheus`
- perf profile 额外暴露：
  - `/actuator/health`
  - `/actuator/info`
  - `/actuator/metrics`
  - `/actuator/prometheus`

本地快速验证：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | head
```

---

## 3. Docker Compose（可选）

文件：`perf/setup/monitoring-compose.yml`

启动：

```bash
docker compose -f perf/setup/monitoring-compose.yml up -d
```

访问：
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`（默认 `admin/admin`）

---

## 4. 典型查询与面板建议

1. 写链路高并发时：
   - `rate(http_server_requests_seconds_count{uri="/orders/create"}[1m])`
2. 聚合查询压力时：
   - `rate(http_server_requests_seconds_count{uri="/dashboard/home"}[1m])`
3. 与业务语义口径联合看：
   - 浏览器开 `/ops/overview`
   - 对照 Grafana 曲线看趋势

---

## 5. 当前边界

- 当前还没把 `WriteChainMetricsService` 的自定义计数器全部映射成 Micrometer meter（可作为下一阶段）。
- 当前目标是“最小可运行骨架 + 可视化入口”，不是一次性上完整 APM 平台。

