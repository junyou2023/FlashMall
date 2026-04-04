-- 商品详情链路 SQL explain 留档脚本
-- 用法：
-- mysql -uroot -p123456 demo_perf < perf/sql/explain-product.sql

USE demo_perf;

EXPLAIN FORMAT=TRADITIONAL
SELECT id, name, price, stock
FROM product
WHERE id = 1;
