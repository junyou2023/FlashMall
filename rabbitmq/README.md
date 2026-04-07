# rabbitmq/definitions.json 字段说明（逐段注释版）

> 说明：`definitions.json` 是标准 JSON，JSON 语法本身不支持注释。  
> 为避免破坏 RabbitMQ 导入，这里提供同目录注释文档。

## 顶层结构

- `vhosts`: 预创建虚拟主机
- `permissions`: 用户在各 vhost 的读写配置权限
- `exchanges`: 交换机定义
- `queues`: 队列定义
- `bindings`: 交换机到队列的绑定规则

## 当前项目对应关系

### 1) vhosts
- `/flashmall-dev`: 对应 `application-dev.yml` 的 `spring.rabbitmq.virtual-host`
- `/flashmall-perf`: 对应 `application-perf.yml` 的 `spring.rabbitmq.virtual-host`

### 2) exchanges / queues / bindings
- dev:
  - exchange: `order.exchange.dev`
  - queue: `order.create.queue.dev`
  - routing key: `order.create.dev`
- perf:
  - exchange: `order.exchange.perf`
  - queue: `order.create.queue.perf`
  - routing key: `order.create.perf`

这些命名与 profile 里 `app.mq.order.*` 保持一致。

### 3) permissions
当前用 `guest` 给 `/`、`/flashmall-dev`、`/flashmall-perf` 都开 `configure/write/read` 全权限，
目的是让本地联调零阻塞。生产环境应改为最小权限专用账号。
