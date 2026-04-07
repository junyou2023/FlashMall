# FlashMall 隔离环境一站式运行手册（dev / perf / prod）

> 目标：你打开项目后，不需要猜命令，不需要反复翻文档，按本手册即可完成：
> 1) 环境准备  
> 2) 依赖启动  
> 3) Spring Boot 启动  
> 4) 连接校验  
> 5) 冒烟验证  
> 6) 压测执行  
> 7) 压测后核对与清理

---

## 1. 环境与隔离模型（先统一认知）

| profile | MySQL | Redis DB | RabbitMQ vhost | MQ 命名空间 |
|---|---|---|---|---|
| dev | `demo_dev` | `1` | `/flashmall-dev` | `order.exchange.dev` / `order.create.queue.dev` / `order.create.dev` |
| perf | `demo_perf` | `2` | `/flashmall-perf` | `order.exchange.perf` / `order.create.queue.perf` / `order.create.perf` |
| prod | `demo_prod`（默认占位，可被环境变量覆盖） | `0`（默认占位） | `/flashmall-prod`（默认占位） | `*.prod`（默认占位） |

> 注意：prod 仅给本地“配置演练”占位，不建议用本地单机容器模拟真实生产。

---

## 2. 前置检查（每次开工第一步）

### 2.1 检查 Docker（用于依赖环境）
```bash
docker --version
docker compose version
```
- 作用：确认 Docker CLI 和 compose 插件可用。

### 2.2 检查 Java / Maven
```bash
java -version
./mvnw -v
```
- 作用：确认 JDK 与 Maven Wrapper 可运行。
- 如果 `./mvnw` 失败，可临时用系统 Maven：
```bash
mvn -v
```

---

## 3. 启动依赖服务（MySQL / Redis / RabbitMQ）

### 3.1 启动
```bash
docker compose up -d
```
- 作用：后台启动三大依赖并加载初始化数据。

### 3.2 看状态
```bash
docker compose ps
docker compose logs -f --tail=150
```
- 作用：确认容器已 healthy；若失败直接看日志定位。

---

## 4. 连接校验（必须做，避免应用启动后才报错）

### 4.1 MySQL 校验
```bash
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SHOW DATABASES LIKE 'demo_%';"
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SELECT id,name,price,stock FROM demo_dev.product;"
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SELECT id,name,price,stock FROM demo_perf.product;"
```
- 作用：确认 `demo_dev` / `demo_perf` 库和种子数据已经可读。

### 4.2 Redis 校验
```bash
docker exec -it flashmall-redis redis-cli ping
docker exec -it flashmall-redis redis-cli -n 1 SET flashmall:dev:ping ok
docker exec -it flashmall-redis redis-cli -n 1 GET flashmall:dev:ping
docker exec -it flashmall-redis redis-cli -n 2 SET flashmall:perf:ping ok
docker exec -it flashmall-redis redis-cli -n 2 GET flashmall:perf:ping
```
- 作用：确认 Redis 服务在线，且 dev/perf DB 隔离有效。

### 4.3 RabbitMQ 校验
```bash
docker exec -it flashmall-rabbitmq rabbitmqctl list_vhosts
docker exec -it flashmall-rabbitmq rabbitmqctl list_queues -p /flashmall-dev
docker exec -it flashmall-rabbitmq rabbitmqctl list_queues -p /flashmall-perf
```
- 作用：确认 vhost 和队列已创建。
- 管理台：`http://localhost:15672`，`guest/guest`

---

## 5. 启动 Spring Boot（按环境分开）

> 建议一次只启动一个 profile，避免你把压测数据打进错误环境。

### 5.1 启动 dev
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
- 作用：连到 `demo_dev + Redis DB1 + /flashmall-dev`，用于日常联调。

### 5.2 启动 perf
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
```
- 作用：连到 `demo_perf + Redis DB2 + /flashmall-perf`，用于压测与实验。

### 5.3 启动 prod（本地演练模式）
```bash
DB_URL=jdbc:mysql://localhost:3306/demo_prod?useSSL=false\&serverTimezone=UTC \
DB_USERNAME=root \
DB_PASSWORD=123456 \
REDIS_HOST=localhost \
REDIS_PORT=6379 \
REDIS_DATABASE=0 \
RABBIT_HOST=localhost \
RABBIT_PORT=5672 \
RABBIT_USERNAME=guest \
RABBIT_PASSWORD=guest \
RABBIT_VHOST=/flashmall-prod \
ORDER_EXCHANGE=order.exchange.prod \
ORDER_QUEUE=order.create.queue.prod \
ORDER_ROUTING_KEY=order.create.prod \
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```
- 作用：验证 prod profile 的配置绑定与环境变量注入逻辑。
- 注意：本地 RabbitMQ 默认 definitions 未预置 `/flashmall-prod`，你需要自行创建该 vhost 与资源，或改为外部真实中间件。

---

## 6. 冒烟验证（应用起来后立刻执行）

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/products
curl "http://localhost:8080/products/1?userId=1001"
curl http://localhost:8080/ops/overview
curl "http://localhost:8080/dashboard/home?productId=1&userId=1001"
```

- 作用说明：
  - `health`：看应用是否整体可用
  - `products`：验证读链路和 MySQL 基础查询
  - `ops/overview`：看内部观测聚合是否正常
  - `dashboard/home`：验证聚合查询线程池链路

---

## 7. 压测执行指南（从低到高，逐级放量）

## 7.1 k6（推荐脚本化）

### 读链路
```bash
k6 run perf/k6/product-detail.js
```

### 写链路
```bash
k6 run perf/k6/order-create.js
```

### 聚合查询线程池链路
```bash
k6 run perf/k6/dashboard-home.js
```

### 混合流量
```bash
k6 run perf/k6/mixed-flow.js
```

> 建议节奏：先 10~20 VUs 验证无错，再翻倍增压。

## 7.2 JMeter（推荐可视化调参）

```bash
jmeter -n -t perf/jmeter/product-detail-read.jmx -Jusers=20 -Jramp=10 -JloopCount=20
jmeter -n -t perf/jmeter/order-create-write.jmx -Jusers=20 -Jramp=10 -JloopCount=20
jmeter -n -t perf/jmeter/dashboard-home-query.jmx -Jusers=20 -Jramp=10 -JloopCount=20
```

- 作用：快速验证每条主链路在低并发下是否有错误/超时。
- 进一步 GUI 细节见：`perf/notes/jmeter-guide.md`

---

## 8. 压测期间必看指标（避免只看 RT）

1. 入口成功率 / RT / P95 / P99（k6/JMeter）
2. `/ops/write-chain/stats`（写链路内部状态）
3. `/dashboard/query-pool/stats`（线程池 active/queue/degrade）
4. RabbitMQ ready/unacked
5. MySQL 慢查询与连接数
6. Redis 命令耗时与 keyspace

---

## 9. 压测后核对（防“压测通过但数据错乱”）

```bash
# 示例：按你实际压测数据做 SQL 核对
mysql -h127.0.0.1 -P3306 -uroot -p123456 demo_perf < perf/sql/check-order-consistency.sql
```

- 作用：核对订单、库存、明细是否一致，避免只看 HTTP 200。

---

## 10. 常见报错与处理（速查）

### 10.1 `docker: command not found`
- 原因：本机未安装 Docker 或 PATH 未生效。
- 处理：安装 Docker Desktop，重启终端后重试。

### 10.2 `Could not transfer artifact ... 403`
- 原因：Maven 仓库访问被代理/网络策略拦截。
- 处理：配置企业 Maven 私服或放通 `repo.maven.apache.org`。

### 10.3 `Connection refused`（MySQL/Redis/RabbitMQ）
- 原因：依赖未启动、端口冲突、容器未 healthy。
- 处理：
```bash
docker compose ps
docker compose logs --tail=200 mysql
docker compose logs --tail=200 redis
docker compose logs --tail=200 rabbitmq
```

### 10.4 RabbitMQ 报 vhost 或队列不存在
- 原因：profile 与中间件资源不匹配。
- 处理：检查 profile 使用的 vhost/queue 命名；必要时在管理台补齐。

---

## 11. 结束与清理

```bash
docker compose down
```
- 作用：停止容器，保留数据卷。

```bash
docker compose down -v
```
- 作用：彻底重置环境（会清空 MySQL/Redis/RabbitMQ 数据）。

---

## 12. 推荐执行顺序（复制即可）

```bash
# 1) 依赖启动
docker compose up -d

# 2) 连接校验
docker compose ps
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SHOW DATABASES LIKE 'demo_%';"
docker exec -it flashmall-redis redis-cli ping
docker exec -it flashmall-rabbitmq rabbitmqctl list_vhosts

# 3) 启动应用（任选 dev/perf）
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf

# 4) 冒烟
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/dashboard/home?productId=1&userId=1001"

# 5) 低并发压测
k6 run perf/k6/product-detail.js
k6 run perf/k6/order-create.js
```

---

如果你希望再进一步“一键化”，下一步可以把本手册中的命令封装成 `make` 目标（如 `make doctor-dev`、`make smoke-perf`、`make load-perf`）。
