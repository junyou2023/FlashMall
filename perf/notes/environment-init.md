# dev/perf 资源初始化与运行最小说明

## 1) 一次性初始化（推荐）

```bash
bash perf/setup/init-isolated-resources.sh
```

这个脚本会创建并初始化：
- MySQL：`demo_dev`、`demo_perf`
- 表：`product`、`orders`、`order_item`
- 种子商品：`product.id=1`

## 2) Redis 隔离

配置已在 profile 中固定：
- dev: `spring.data.redis.database=1`
- perf: `spring.data.redis.database=2`

可手动验证：

```bash
redis-cli -n 1 INFO keyspace
redis-cli -n 2 INFO keyspace
```

## 3) RabbitMQ 隔离

配置已在 profile 中固定：
- dev: `virtual-host=/flashmall-dev` + `order.exchange.dev / order.create.queue.dev / order.create.dev`
- perf: `virtual-host=/flashmall-perf` + `order.exchange.perf / order.create.queue.perf / order.create.perf`

最小手工命令（RabbitMQ 已安装时）：

```bash
rabbitmqctl add_vhost /flashmall-dev
rabbitmqctl add_vhost /flashmall-perf
rabbitmqctl set_permissions -p /flashmall-dev guest ".*" ".*" ".*"
rabbitmqctl set_permissions -p /flashmall-perf guest ".*" ".*" ".*"
```

> 交换机/队列绑定由 Spring Boot 启动时自动声明（见 `RabbitMQConfig`）。

## 4) 启动方式

```bash
# dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# perf
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf
```

## 5) 本轮新增观测接口（perf/dev 都可用）

- 写链路统计：`GET /ops/write-chain/stats`
- 聚合查询线程池统计：`GET /dashboard/query-pool/stats`
- 聚合查询入口：`GET /dashboard/home?productId=1&userId=1001`

## 6) 压测脚本补充

- 读链路：`perf/k6/product-detail.js`
- 写链路：`perf/k6/order-create.js`
- 线程池实验链路：`perf/k6/thread-pool-submit.js`（仅 perf profile）
- 正式聚合查询线程池链路：`perf/k6/dashboard-home.js`
- 混合流量（可选）：`perf/k6/mixed-flow.js`
