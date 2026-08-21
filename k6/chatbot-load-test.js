import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const chatPath = __ENV.CHAT_PATH || '/api/v1/chat';
const authToken = __ENV.AUTH_TOKEN;
const thinkTime = Number(__ENV.THINK_TIME || 1);

const chatFailures = new Rate('chat_failures');
const chatDuration = new Trend('chat_duration', true);
const chatRequests = new Counter('chat_requests');

export const options = {
  scenarios: {
    chatbot: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP_UP || '30s', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.HOLD || '1m', target: Number(__ENV.VUS || 10) },
        { duration: __ENV.RAMP_DOWN || '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    chat_failures: ['rate<0.01'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
  },
};

export default function () {
  const uniqueId = `${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    userId: Number(__ENV.USER_ID || __VU),
    sessionId: `k6-${uniqueId}`,
    message: __ENV.MESSAGE || '졸업 요건을 알려줘',
  });

  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  const response = http.post(`${baseUrl}${chatPath}`, payload, {
    headers,
    tags: { name: 'chat' },
    timeout: __ENV.REQUEST_TIMEOUT || '30s',
  });

  chatRequests.add(1);
  chatDuration.add(response.timings.duration);

  const passed = check(response, {
    'status is 2xx': (res) => res.status >= 200 && res.status < 300,
    'response body exists': (res) => Boolean(res.body && res.body.length > 0),
  });
  chatFailures.add(!passed);

  sleep(thinkTime);
}
