#!/usr/bin/env bash
set -euo pipefail

# 最小资源初始化脚本（dev/perf）
# 作用：
# 1) 创建 MySQL 隔离库 demo_dev / demo_perf
# 2) 初始化基础表结构
# 3) 打印 Redis / RabbitMQ 需要的隔离参数提示

MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASSWORD=${MYSQL_PASSWORD:-123456}
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}

mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" <<'SQL'
CREATE DATABASE IF NOT EXISTS demo_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS demo_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
SQL

for DB in demo_dev demo_perf; do
  mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${DB}" <<'SQL'
CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  price DOUBLE NOT NULL,
  stock INT NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  total_price DOUBLE NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  price DOUBLE NOT NULL
);

INSERT INTO product (id, name, price, stock)
VALUES (1, CONCAT('seed-product-', DATABASE()), 99.0, 20000)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price), stock = VALUES(stock);
SQL
  echo "[OK] MySQL schema ensured for ${DB}"
done

cat <<'TXT'
[INFO] Redis 隔离约定：
  - dev -> database=1
  - perf -> database=2
  可用命令验证：
  redis-cli -n 1 DBSIZE
  redis-cli -n 2 DBSIZE

[INFO] RabbitMQ 隔离约定：
  - dev vhost: /flashmall-dev
    exchange: order.exchange.dev
    queue: order.create.queue.dev
    routing-key: order.create.dev
  - perf vhost: /flashmall-perf
    exchange: order.exchange.perf
    queue: order.create.queue.perf
    routing-key: order.create.perf

建议（有 rabbitmqadmin 时）：
  rabbitmqctl add_vhost /flashmall-dev
  rabbitmqctl add_vhost /flashmall-perf
  rabbitmqctl set_permissions -p /flashmall-dev guest ".*" ".*" ".*"
  rabbitmqctl set_permissions -p /flashmall-perf guest ".*" ".*" ".*"
TXT
