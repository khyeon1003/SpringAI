import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const getPath = __ENV.GET_PATH || '/actuator/health';
const thinkTime = Number(__ENV.THINK_TIME || 1);
const getFailures = new Rate('get_failures');

export const options = {
  scenarios: {
    get_endpoint: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP_UP || '10s', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.HOLD || '30s', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.RAMP_DOWN || '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    get_failures: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}${getPath}`, {
    headers: { Accept: 'application/json' },
    tags: { name: 'get_endpoint' },
    timeout: __ENV.REQUEST_TIMEOUT || '5s',
  });

  const passed = check(response, {
    'status is 2xx': (res) => res.status >= 200 && res.status < 300,
    'response body exists': (res) => Boolean(res.body && res.body.length > 0),
  });
  getFailures.add(!passed);
  sleep(thinkTime);
}
