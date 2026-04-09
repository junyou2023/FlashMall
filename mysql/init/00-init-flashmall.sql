-- ==============================================================
-- FlashMall Docker MySQL 初始化脚本（首次初始化数据目录时执行）
--
-- 设计目标：
-- 1) 与当前分支 dev/perf profile 对齐（demo_dev/demo_perf）
-- 2) 表结构严格来自当前 MyBatis mapper 使用的字段
-- 3) 提供最小可运行种子数据，开箱即可联调
-- ============================================================== 

-- ------------------------------
-- 第一步：创建数据库
-- ------------------------------
CREATE DATABASE IF NOT EXISTS demo_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS demo_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- prod 仅做本地占位（真实生产建议独立实例+权限）
CREATE DATABASE IF NOT EXISTS demo_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- ------------------------------
-- 第二步：初始化 dev
-- ------------------------------
USE demo_dev;

-- product: 对应 ProductMapper.xml 的 SELECT/UPDATE 字段（id/name/price/stock）
CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  price DOUBLE NOT NULL,
  stock INT NOT NULL,
  KEY idx_product_name (name)
) ENGINE=InnoDB;

-- orders: 对应 OrderMapper.xml 的 INSERT/SELECT 字段
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  total_price DOUBLE NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_orders_request_id (request_id),
  KEY idx_orders_user_id (user_id),
  KEY idx_orders_created_at (created_at)
) ENGINE=InnoDB;

-- order_item: 对应 OrderItemMapper.xml 的 INSERT/SELECT 字段
CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  price DOUBLE NOT NULL,
  KEY idx_order_item_order_id (order_id),
  KEY idx_order_item_product_id (product_id)
) ENGINE=InnoDB;

-- payment_order: 支付一期的本地支付聚合表（订单后置支付，不污染 orders 主表）
CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  out_trade_no VARCHAR(64) NOT NULL,
  amount DOUBLE NOT NULL,
  currency VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  stripe_checkout_session_id VARCHAR(128) NULL,
  stripe_payment_intent_id VARCHAR(128) NULL,
  checkout_url VARCHAR(1000) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_payment_order_out_trade_no (out_trade_no),
  UNIQUE KEY uk_payment_order_idempotency_key (idempotency_key),
  KEY idx_payment_order_order_id (order_id),
  KEY idx_payment_order_user_id (user_id),
  KEY idx_payment_order_provider_status (provider, status)
) ENGINE=InnoDB;

-- dev 最小种子数据：至少确保 productId=1 可被读链路/写链路直接使用
INSERT INTO product (id, name, price, stock)
VALUES
  (1, 'seed-product-dev', 99.00, 20000),
  (2, 'keyboard-dev', 199.00, 5000)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  stock = VALUES(stock);

-- ------------------------------
-- 第三步：初始化 perf
-- ------------------------------
USE demo_perf;

-- schema 与 dev 同构，避免“压测的是另一套结构”
CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  price DOUBLE NOT NULL,
  stock INT NOT NULL,
  KEY idx_product_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  total_price DOUBLE NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_orders_request_id (request_id),
  KEY idx_orders_user_id (user_id),
  KEY idx_orders_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  price DOUBLE NOT NULL,
  KEY idx_order_item_order_id (order_id),
  KEY idx_order_item_product_id (product_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  out_trade_no VARCHAR(64) NOT NULL,
  amount DOUBLE NOT NULL,
  currency VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  stripe_checkout_session_id VARCHAR(128) NULL,
  stripe_payment_intent_id VARCHAR(128) NULL,
  checkout_url VARCHAR(1000) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_payment_order_out_trade_no (out_trade_no),
  UNIQUE KEY uk_payment_order_idempotency_key (idempotency_key),
  KEY idx_payment_order_order_id (order_id),
  KEY idx_payment_order_user_id (user_id),
  KEY idx_payment_order_provider_status (provider, status)
) ENGINE=InnoDB;

-- perf 种子库存更高，避免基础烟测时很快耗尽库存
INSERT INTO product (id, name, price, stock)
VALUES
  (1, 'seed-product-perf', 99.00, 500000),
  (2, 'keyboard-perf', 199.00, 200000)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price = VALUES(price),
  stock = VALUES(stock);
