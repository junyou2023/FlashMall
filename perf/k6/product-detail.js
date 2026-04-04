import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 商品详情读链路压测脚本（冷缓存 / 热缓存）
 *
 * 用法示例：
 * 1) 冷缓存：
 *    k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_ID=1 -e USER_ID_BASE=1000 -e COLD_CACHE=true perf/k6/product-detail.js
 * 2) 热缓存：
 *    k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_ID=1 -e USER_ID_BASE=1000 -e COLD_CACHE=false perf/k6/product-detail.js
 */
export const options = {
    scenarios: {
        steady_read: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 80),
            duration: __ENV.DURATION || '60s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<120', 'p(99)<200'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';
const USER_ID_BASE = Number(__ENV.USER_ID_BASE || 1000);
const COLD_CACHE = (__ENV.COLD_CACHE || 'false').toLowerCase() === 'true';

export function setup() {
    // 冷缓存模式下先删一次缓存，逼出首轮 DB fallback。
    if (COLD_CACHE) {
        http.post(`${BASE_URL}/products/${PRODUCT_ID}/evict`, null);
    }
}

export default function () {
    const userId = USER_ID_BASE + __VU;
    const url = `${BASE_URL}/products/${PRODUCT_ID}?userId=${userId}`;
    const res = http.get(url);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'body is json-ish': (r) => r.body && (r.body.includes('id') || r.body === 'null'),
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.05));
}
