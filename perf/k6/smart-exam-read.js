import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = String(__ENV.BASE_URL || 'http://8.148.181.9').replace(/\/$/, '');
const API_BASE = `${BASE_URL}/api/v1`;
const ADMIN_VUS = Number(__ENV.ADMIN_VUS || 6);
const TEACHER_VUS = Number(__ENV.TEACHER_VUS || 12);
const TEST_DURATION = __ENV.TEST_DURATION || '1m';

const loginSuccessRate = new Rate('login_success_rate');
const apiSuccessRate = new Rate('api_success_rate');

const LOGIN_USERS = [
  { username: 'admin', password: '123456' },
  { username: 'teacher001', password: '123456' },
  { username: 'student001', password: '123456' },
];

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    login_smoke: {
      executor: 'per-vu-iterations',
      vus: LOGIN_USERS.length,
      iterations: 1,
      maxDuration: '1m',
      startTime: '10s',
      exec: 'loginSmoke',
      tags: { flow: 'login' },
    },
    admin_read: {
      executor: 'constant-vus',
      vus: ADMIN_VUS,
      duration: TEST_DURATION,
      startTime: '5s',
      exec: 'adminRead',
      tags: { flow: 'admin-read' },
    },
    teacher_read: {
      executor: 'constant-vus',
      vus: TEACHER_VUS,
      duration: TEST_DURATION,
      startTime: '5s',
      exec: 'teacherRead',
      tags: { flow: 'teacher-read' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1200', 'p(99)<2500'],
    login_success_rate: ['rate>0.99'],
    api_success_rate: ['rate>0.99'],
    'http_req_duration{name:auth_login}': ['p(95)<800'],
    'http_req_duration{name:admin_users}': ['p(95)<1200'],
    'http_req_duration{name:question_list}': ['p(95)<2500'],
  },
};

function jsonHeaders(token, name) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return {
    headers,
    responseType: 'text',
    tags: { name },
  };
}

function login(username, password) {
  const response = http.post(
    `${API_BASE}/auth/login`,
    JSON.stringify({ username, password }),
    jsonHeaders('', 'auth_login')
  );

  const ok = check(response, {
    'login status is 200': (r) => r.status === 200,
    'login body is present': (r) => Boolean(r.body),
  });

  if (!ok) {
    loginSuccessRate.add(false);
    return null;
  }

  let payload;
  try {
    payload = JSON.parse(response.body);
  } catch (_) {
    loginSuccessRate.add(false);
    return null;
  }

  const token = payload?.data?.token || '';
  const success = payload?.code === 0 && token.length > 0;
  loginSuccessRate.add(success);
  return success ? token : null;
}

function apiGet(url, token, name) {
  const response = http.get(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    tags: { name },
  });

  const success = response.status === 200;
  apiSuccessRate.add(success);
  check(response, {
    [`${name} status is 200`]: (r) => r.status === 200,
  });
  return response;
}

function randomInt(max) {
  return Math.floor(Math.random() * max);
}

function think() {
  sleep(0.2 + Math.random() * 0.4);
}

export function setup() {
  const adminToken = login('admin', '123456');
  const teacherToken = login('teacher001', '123456');

  if (!adminToken || !teacherToken) {
    throw new Error('Failed to create setup tokens');
  }

  return {
    adminToken,
    teacherToken,
  };
}

export function loginSmoke() {
  const user = LOGIN_USERS[(__VU - 1) % LOGIN_USERS.length] || LOGIN_USERS[0];
  const token = login(user.username, user.password);
  if (!token) {
    throw new Error(`Login smoke failed for ${user.username}`);
  }
  think();
}

export function adminRead(data) {
  const token = data.adminToken;
  const roll = Math.random();

  if (roll < 0.2) {
    apiGet(`${API_BASE}/users/me`, token, 'user_me_admin');
  } else if (roll < 0.45) {
    apiGet(`${API_BASE}/admin/overview`, token, 'admin_overview');
  } else if (roll < 0.85) {
    const page = 1 + randomInt(3);
    apiGet(`${API_BASE}/admin/users?page=${page}&size=20`, token, 'admin_users');
  } else if (roll < 0.93) {
    apiGet(`${API_BASE}/admin/roles`, token, 'admin_roles');
  } else {
    apiGet(`${API_BASE}/admin/permissions`, token, 'admin_permissions');
  }

  think();
}

export function teacherRead(data) {
  const token = data.teacherToken;
  const roll = Math.random();

  if (roll < 0.15) {
    apiGet(`${API_BASE}/users/me`, token, 'user_me_teacher');
  } else if (roll < 0.8) {
    apiGet(`${API_BASE}/questions`, token, 'question_list');
  } else {
    const page = 1 + randomInt(3);
    apiGet(`${API_BASE}/papers?page=${page}&size=20`, token, 'paper_list');
  }

  think();
}
