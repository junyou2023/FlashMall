-- 写链路压测前重置（demo_perf）
-- 用法：mysql -uroot -p123456 < perf/sql/reset-order-bench.sql

CREATE DATABASE IF NOT EXISTS demo_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE demo_perf;

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
    created_at DATETIME NOT NULL,
    KEY idx_orders_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DOUBLE NOT NULL,
    KEY idx_order_item_order_id (order_id),
    KEY idx_order_item_product_id (product_id)
);

-- 压测商品（id=1），每轮实验前重置库存
INSERT INTO product (id, name, price, stock)
VALUES (1, 'order-bench-product', 199.0, 20000)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    stock = VALUES(stock);

DELETE FROM order_item WHERE product_id = 1;
DELETE FROM orders WHERE id > 0;
