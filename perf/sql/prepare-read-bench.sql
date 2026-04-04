-- 读链路压测预置数据（demo_perf）
-- 用法：mysql -uroot -p123456 < perf/sql/prepare-read-bench.sql

CREATE DATABASE IF NOT EXISTS demo_perf CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE demo_perf;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    price DOUBLE NOT NULL,
    stock INT NOT NULL
);

-- 准备一个固定压测商品（id=1）
INSERT INTO product (id, name, price, stock)
VALUES (1, 'read-bench-product', 99.0, 100000)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    stock = VALUES(stock);
