# 读链路压测记录模板（/products/{id}）

## 1. 环境启动

```bash
# 1) 启动 perf profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=perf

# 2) 准备读压测数据
mysql -uroot -p123456 < perf/sql/prepare-read-bench.sql
```

## 2. 冷缓存压测

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e PRODUCT_ID=1 \
  -e COLD_CACHE=true \
  -e VUS=80 \
  -e DURATION=60s \
  perf/k6/product-detail.js
```

记录：
- p95:
- p99:
- error_rate:
- 日志中 readSource=db-fallback 的比例:

## 3. 热缓存压测

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e PRODUCT_ID=1 \
  -e COLD_CACHE=false \
  -e VUS=80 \
  -e DURATION=60s \
  perf/k6/product-detail.js
```

记录：
- p95:
- p99:
- error_rate:
- 日志中 readSource=cache-hit 的比例:

## 4. Explain 留档

```bash
mysql -uroot -p123456 demo_perf < perf/sql/explain-product.sql
```

把 explain 输出贴到这里：

```text
(粘贴 explain 结果)
```

## 5. 最小结论

- 冷缓存与热缓存 p95 差异：
- 读链路瓶颈初判（Redis / DB / 应用线程）：
- 下一步动作：
