import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 混合流量压测：读 + 写 + 聚合查询
 *
 * 目标：
 * - 模拟更接近真实业务的混合流量
 * - 观察写链路和查询线程池链路互相影响
 */
export const options = {
    scenarios: {
        write_orders: {
            executor: 'constant-arrival-rate',
            exec: 'writeOrders',
            rate: Number(__ENV.WRITE_RATE || 40),
            timeUnit: '1s',
            duration: __ENV.DURATION || '60s',
            preAllocatedVUs: Number(__ENV.WRITE_PRE_VUS || 50),
            maxVUs: Number(__ENV.WRITE_MAX_VUS || 150),
        },
        read_products: {
            executor: 'constant-vus',
            exec: 'readProducts',
            vus: Number(__ENV.READ_VUS || 60),
            duration: __ENV.DURATION || '60s',
        },
        query_dashboard: {
            executor: 'constant-vus',
            exec: 'queryDashboard',
            vus: Number(__ENV.DASHBOARD_VUS || 40),
            duration: __ENV.DURATION || '60s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const USER_ID_BASE = Number(__ENV.USER_ID_BASE || 100000);
const USER_POOL = Number(__ENV.USER_POOL || 50000);

function randomUserId() {
    return USER_ID_BASE + Math.floor(Math.random() * USER_POOL);
}

function requestId() {
    return `mix-${__VU}-${__ITER}-${Date.now()}`;
}

export function writeOrders() {
    const payload = JSON.stringify({
        userId: randomUserId(),
        productId: PRODUCT_ID,
        quantity: 1,
        requestId: requestId(),
    });
    const res = http.post(`${BASE_URL}/orders/create`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'write status 200': (r) => r.status === 200 });
    sleep(0.03);
}

export function readProducts() {
    const res = http.get(`${BASE_URL}/products/${PRODUCT_ID}?userId=${randomUserId()}`);
    check(res, { 'read status 200': (r) => r.status === 200 });
    sleep(0.02);
}

export function queryDashboard() {
    const res = http.get(`${BASE_URL}/dashboard/home?productId=${PRODUCT_ID}&userId=${randomUserId()}`);
    check(res, { 'dashboard status 200': (r) => r.status === 200 });
    sleep(0.01);
}
