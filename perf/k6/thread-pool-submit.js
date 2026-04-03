import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 20,
    duration: '20s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<5000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TASK_COUNT = __ENV.TASK_COUNT || '20';
const SLEEP_MILLIS = __ENV.SLEEP_MILLIS || '1000';

export default function () {
    const url = `${BASE_URL}/lab/thread-pool/submit?taskCount=${TASK_COUNT}&sleepMillis=${SLEEP_MILLIS}`;
    const res = http.post(url, null);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.2);
}