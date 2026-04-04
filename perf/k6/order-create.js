import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 写链路正式压测脚本（多用户并发下单）
 *
 * 关键点：
 * - 每次请求显式传 userId/requestId，避免旧版 userId=1 导致数据失真
 * - 用不同 userId 模拟真实并发用户
 */
export const options = {
    scenarios: {
        order_burst: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 100),
            timeUnit: '1s',
            duration: __ENV.DURATION || '60s',
            preAllocatedVUs: Number(__ENV.PRE_VUS || 100),
            maxVUs: Number(__ENV.MAX_VUS || 300),
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.02'],
        http_req_duration: ['p(95)<200', 'p(99)<350'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const QUANTITY = Number(__ENV.QUANTITY || 1);
const USER_POOL = Number(__ENV.USER_POOL || 20000);
const USER_ID_BASE = Number(__ENV.USER_ID_BASE || 100000);

function randomUserId() {
    return USER_ID_BASE + Math.floor(Math.random() * USER_POOL);
}

function requestId() {
    return `k6-${__VU}-${__ITER}-${Date.now()}`;
}

export default function () {
    const payload = JSON.stringify({
        userId: randomUserId(),
        productId: PRODUCT_ID,
        quantity: QUANTITY,
        requestId: requestId(),
    });

    const res = http.post(`${BASE_URL}/orders/create`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'accepted text returned': (r) => r.body && r.body.includes('已受理'),
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.02));
}
