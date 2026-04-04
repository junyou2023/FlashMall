# 写链路压测记录模板（/orders/create）

## 1. 环境启动

```bash
# 1) 启动 perf profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf

# 2) 重置写压测数据
mysql -uroot -p123456 < perf/sql/reset-order-bench.sql
```

> 注意：写链路依赖 Redis 预热和 RabbitMQ 队列可用；
> 如果 Redis 没预热库存，接口会出现“库存未预热”属于预期保护行为。

## 2. 执行压测

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e PRODUCT_ID=1 \
  -e QUANTITY=1 \
  -e RATE=100 \
  -e DURATION=60s \
  -e USER_POOL=20000 \
  perf/k6/order-create.js
```

记录：
- p95:
- p99:
- error_rate:
- 入口日志 redisPreDeduct=SUCCESS 比例:
- 入口日志 mqSend=SUCCESS 比例:
- 入口日志 asyncAccepted=true 比例:

## 3. 对账

```bash
mysql -uroot -p123456 demo_perf < perf/sql/check-order-consistency.sql
```

把对账结果贴到这里：

```text
(粘贴 SQL 结果)
```

重点关注：
- remaining_stock
- total_orders
- total_bought_quantity
- duplicate user_id（是否异常）
- oversold（必须是 0）

## 4. 最小结论

- 压测入口吞吐是否稳定：
- Redis 预扣 -> MQ -> MySQL 落单链路是否闭环：
- 是否出现超卖 / 重复下单：
- 下一步动作：
