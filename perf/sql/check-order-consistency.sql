-- 写链路压测后对账（demo_perf）
-- 用法：mysql -uroot -p123456 demo_perf < perf/sql/check-order-consistency.sql

USE demo_perf;

-- A. 当前商品剩余库存
SELECT id AS product_id, stock AS remaining_stock
FROM product
WHERE id = 1;

-- B. 最终创建的订单总数
SELECT COUNT(*) AS total_orders
FROM orders;

-- C. 订单明细总购买件数（用于与库存变化核对）
SELECT COALESCE(SUM(quantity), 0) AS total_bought_quantity
FROM order_item
WHERE product_id = 1;

-- D. 重复 userId 检查（命中“一人一单”时应该主要看到 count=1，多轮压测可观测是否有异常）
SELECT user_id, COUNT(*) AS order_count
FROM orders
GROUP BY user_id
HAVING COUNT(*) > 1
ORDER BY order_count DESC, user_id ASC
LIMIT 20;

-- E. 是否超卖（若 oversold = 1 说明发生超卖）
-- 这里假设 reset-order-bench.sql 初始 stock=20000。
SELECT
    CASE
        WHEN (20000 - (SELECT stock FROM product WHERE id = 1))
             < (SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE product_id = 1)
        THEN 1 ELSE 0
    END AS oversold;
