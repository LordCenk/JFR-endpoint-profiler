// k6 load test for the JFR endpoint profiler demo app.
//
// Run from this directory (or point -e BASE_URL elsewhere):
//   k6 run load-test.js
//   k6 run -e BASE_URL=http://localhost:8080 -e DURATION=60s load-test.js
//
// Hits all four demo endpoints concurrently so the dashboard
// (http://localhost:8080/profiler/index.html) has something interesting to
// show: distinct endpoints with different latency profiles and different
// hot methods.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    fibonacci: {
      executor: 'constant-vus',
      exec: 'fibonacci',
      vus: 4,
      duration: __ENV.DURATION || '30s',
    },
    regex: {
      executor: 'constant-vus',
      exec: 'regex',
      vus: 4,
      duration: __ENV.DURATION || '30s',
    },
    serialization: {
      executor: 'constant-vus',
      exec: 'serialization',
      vus: 2,
      duration: __ENV.DURATION || '30s',
    },
    lockContention: {
      executor: 'constant-vus',
      exec: 'lockContention',
      vus: 8,
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function fibonacci() {
  const res = http.get(`${BASE_URL}/cpu/fibonacci?n=32`);
  check(res, { 'fibonacci 200': (r) => r.status === 200 });
  sleep(0.1);
}

export function regex() {
  const res = http.get(`${BASE_URL}/cpu/regex?iterations=20000`);
  check(res, { 'regex 200': (r) => r.status === 200 });
  sleep(0.1);
}

export function serialization() {
  const res = http.get(`${BASE_URL}/serialize/orders?count=5000`);
  check(res, { 'serialize 200': (r) => r.status === 200 });
  sleep(0.2);
}

export function lockContention() {
  const res = http.get(`${BASE_URL}/lock/increment?work=500000`);
  check(res, { 'lock 200': (r) => r.status === 200 });
  sleep(0.05);
}
