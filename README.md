# FlashMall 本地 Docker 联调环境（dev/perf 隔离版）

> 目标：**只安装 Docker Desktop**，即可一键拉起 MySQL / Redis / RabbitMQ，然后在 IDEA 直接启动 Spring Boot 完成联调与压测准备。

---

## 1. 当前分支环境说明

当前分支：`codex/enhance-project-for-jmeter-visualization`

项目是 Spring Boot + MyBatis + MySQL + Redis + RabbitMQ，已具备多 profile：

- `application.yml`：默认 `dev`
- `application-dev.yml`
- `application-perf.yml`
- `application-prod.yml`

并且已经有 perf 资产：

- `perf/jmeter/*.jmx`
- `perf/k6/*.js`
- `perf/notes/*`
- `perf/setup/monitoring-compose.yml`

本次 Docker 方案只负责**本地基础依赖环境**，不破坏现有压测结构。

---

## 2. 为什么需要 Docker

在本分支中，dev/perf 对 MySQL、Redis、RabbitMQ 都有明确隔离要求。手工安装和配置成本高、容易漏配（尤其是 RabbitMQ vhost/权限）。

引入根目录 `docker-compose.yml` 后：

- 一条命令拉起依赖
- 自动创建 `demo_dev/demo_perf` 库和表
- 自动创建 RabbitMQ `vhost` 与队列命名空间
- 本地 `localhost` 端口直连，IDEA 启动方式不变

---

## 3. dev / perf / prod 区别（与当前分支对齐）

| profile | MySQL | Redis DB | RabbitMQ vhost | MQ 命名空间 |
|---|---|---|---|---|
| dev | `demo_dev` | `1` | `/flashmall-dev` | `order.exchange.dev` / `order.create.queue.dev` / `order.create.dev` |
| perf | `demo_perf` | `2` | `/flashmall-perf` | `order.exchange.perf` / `order.create.queue.perf` / `order.create.perf` |
| prod | 环境变量注入 | 环境变量注入 | 环境变量注入 | 环境变量注入 |

---

## 4. 新增目录与文件说明

```text
.
├─ docker-compose.yml                    # 本地依赖环境编排（MySQL/Redis/RabbitMQ）
├─ .env.example                          # 本地端口/密码覆盖示例
├─ Makefile                              # 一键 up/down/ps/logs/reset
├─ mysql/
│  └─ init/
│     └─ 00-init-flashmall.sql          # 初始化库表与最小种子数据
└─ rabbitmq/
   ├─ rabbitmq.conf                      # 管理插件定义加载配置
   └─ definitions.json                   # vhost/权限/交换机/队列/绑定预置
```

---


## 4.1 注释约定说明（本次补充）

本次新增文件都补了说明性注释（用途/关键参数/风险点）：

- `.env.example`：每个变量用途、与 profile 对齐关系
- `docker-compose.yml`：每个服务的职责、卷、健康检查意义
- `mysql/init/00-init-flashmall.sql`：按步骤解释建库建表与种子数据
- `rabbitmq/rabbitmq.conf`：每行配置作用
- `Makefile`：每条命令的行为

> `rabbitmq/definitions.json` 因为 JSON 语法不支持注释，注释说明放在同目录 `rabbitmq/README.md`。

---

## 5. 首次启动步骤

1) 安装 Docker Desktop 并确保已启动。

2) 进入项目根目录：

```bash
cd FlashMall
```

3) 复制环境变量模板（可选）：

```bash
cp .env.example .env
```

> 不复制也能跑，Compose 会使用默认值。

4) 一键启动依赖：

```bash
docker compose up -d
```

---

## 6. 一键启动命令

```bash
docker compose up -d
```

或使用 Makefile：

```bash
make env-up
```

---

## 7. 一键停止命令

```bash
docker compose down
```

如需清空卷（重置数据）：

```bash
docker compose down -v
```

或：

```bash
make env-down
make env-reset
```

---

## 8. 容器状态检查命令

```bash
docker compose ps
docker compose logs -f --tail=150
```

或：

```bash
make env-ps
make env-logs
```

---

## 9. 如何在 IDEA 中启动 Spring Boot

不需要容器化应用本身，保持本地 Java 启动：

- 主类：`com.example.demo.DemoApplication`
- JDK：17
- Active Profiles：`dev` 或 `perf`

等价命令行：

```bash
# dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# perf
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
```

---

## 10. 如何切换 dev / perf profile

### 方式 A：IDEA Run Configuration

`Active profiles` 填：

- `dev`（默认）
- `perf`

### 方式 B：命令行

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
```

---

## 11. 启动后连通性验证

> 以下命令均可直接复制执行。

### 11.1 MySQL 连通

```bash
# 查看数据库是否存在
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SHOW DATABASES LIKE 'demo_%';"

# 查看 dev 商品数据
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SELECT id,name,price,stock FROM demo_dev.product;"

# 查看 perf 商品数据
docker exec -it flashmall-mysql mysql -uroot -p123456 -e "SELECT id,name,price,stock FROM demo_perf.product;"
```

### 11.2 Redis 连通

```bash
# 验证 Redis 服务
docker exec -it flashmall-redis redis-cli ping

# 写入并读取 dev 库（db=1）
docker exec -it flashmall-redis redis-cli -n 1 SET flashmall:dev:ping ok

docker exec -it flashmall-redis redis-cli -n 1 GET flashmall:dev:ping

# 写入并读取 perf 库（db=2）
docker exec -it flashmall-redis redis-cli -n 2 SET flashmall:perf:ping ok

docker exec -it flashmall-redis redis-cli -n 2 GET flashmall:perf:ping
```

### 11.3 RabbitMQ 连通

```bash
# 查看 vhost
docker exec -it flashmall-rabbitmq rabbitmqctl list_vhosts

# 查看 dev vhost 队列
docker exec -it flashmall-rabbitmq rabbitmqctl list_queues -p /flashmall-dev

# 查看 perf vhost 队列
docker exec -it flashmall-rabbitmq rabbitmqctl list_queues -p /flashmall-perf
```

管理台：

- URL: `http://localhost:15672`
- 用户名：`guest`
- 密码：`guest`

### 11.4 Spring Boot 连通

应用启动后访问：

```bash
curl http://localhost:8080/products
curl "http://localhost:8080/products/1?userId=1001"
curl http://localhost:8080/ops/overview
curl http://localhost:8080/actuator/health
```

写链路最小验证：

```bash
curl -X POST http://localhost:8080/orders/create \
  -H "Content-Type: application/json" \
  -d '{"userId":1001,"productId":1,"quantity":1,"requestId":"readme-smoke-001"}'
```

---

## 12. JMeter / k6 与 perf profile 的配合

1) 启动依赖：`docker compose up -d`
2) 用 `perf` profile 启动应用
3) 执行压测：

```bash
# k6 示例
k6 run perf/k6/order-create.js -e BASE_URL=http://localhost:8080

# JMeter GUI 打开
perf/jmeter/order-create-write.jmx
```

4) 对照内部观测：

- `GET /ops/write-chain/stats`
- `GET /ops/overview`
- `GET /dashboard/query-pool/stats`

> 监控栈（Prometheus/Grafana）仍使用 `perf/setup/monitoring-compose.yml`，与根目录 `docker-compose.yml` 职责不同：
>
> - 根目录 compose：基础中间件（MySQL/Redis/RabbitMQ）
> - perf/setup compose：观测栈（Prometheus/Grafana）

---

## 13. 常见故障排查

### 13.1 端口占用

现象：`docker compose up -d` 报 3306/6379/5672/15672 被占用。

处理：

```bash
# 查看占用（Linux/macOS）
lsof -i :3306
lsof -i :6379
lsof -i :5672
lsof -i :15672

# 或修改 .env 自定义映射端口
```

### 13.2 容器已启动但项目连不上

检查项：

1. 是否用了正确 profile（dev/perf）
2. `application-*.yml` 连接地址是否仍为 localhost
3. 容器健康状态：`docker compose ps`
4. MySQL 用户密码是否被 `.env` 改过

### 13.3 MySQL 初始化脚本未生效

根因：`docker-entrypoint-initdb.d` 仅在**数据目录首次初始化**时执行。

处理：

```bash
docker compose down -v
docker compose up -d
```

### 13.4 RabbitMQ vhost 不存在

先确认 definitions 是否加载：

```bash
docker exec -it flashmall-rabbitmq rabbitmqctl list_vhosts
```

若缺失，重建容器并清卷：

```bash
docker compose down -v
docker compose up -d
```

### 13.5 Redis database 选择不对

确认 profile：

- dev 应使用 `database=1`
- perf 应使用 `database=2`

可用 `redis-cli -n 1/2 DBSIZE` 交叉检查。

---

## 14. 一条龙验收路径（复制仓库后）

```bash
cp .env.example .env
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

然后访问：

- `http://localhost:8080/products/1?userId=1001`
- `http://localhost:8080/ops/overview`
- `http://localhost:15672`

即完成“Docker 一键起环境 + IDEA 运行应用 + 本地联调可用”。
