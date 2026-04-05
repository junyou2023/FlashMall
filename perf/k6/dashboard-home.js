import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 聚合查询（正式业务线程池场景）压测脚本
 *
 * 目标：
 * 1) 压 /dashboard/home 并发拆分读任务链路
 * 2) 配合 /dashboard/query-pool/stats 观察 activeCount / queueSize / degrade
 */
export const options = {
    scenarios: {
        dashboard_home: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 120),
            timeUnit: '1s',
            duration: __ENV.DURATION || '60s',
            preAllocatedVUs: Number(__ENV.PRE_VUS || 80),
            maxVUs: Number(__ENV.MAX_VUS || 300),
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.03'],
        http_req_duration: ['p(95)<250', 'p(99)<450'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const USER_ID_BASE = Number(__ENV.USER_ID_BASE || 20000);
const USER_POOL = Number(__ENV.USER_POOL || 10000);

function randomUserId() {
    return USER_ID_BASE + Math.floor(Math.random() * USER_POOL);
}

export default function () {
    const url = `${BASE_URL}/dashboard/home?productId=${PRODUCT_ID}&userId=${randomUserId()}`;
    const res = http.get(url);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'contains dashboard payload': (r) => r.body && r.body.includes('dashboard'),
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.01));
}
