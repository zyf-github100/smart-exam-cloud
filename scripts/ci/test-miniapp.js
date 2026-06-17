#!/usr/bin/env node

const assert = require('node:assert/strict');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..', '..');
const miniappRoot = path.join(repoRoot, 'smart-exam-miniapp');
const requestModulePath = path.join(miniappRoot, 'services', 'request.js');
const apiModulePath = path.join(miniappRoot, 'services', 'api.js');
const loginPagePath = path.join(miniappRoot, 'pages', 'login', 'index.js');

let failures = 0;

function resetModule(modulePath) {
  delete require.cache[require.resolve(modulePath)];
}

function restoreGlobals(snapshot) {
  for (const [key, value] of Object.entries(snapshot)) {
    if (value === undefined) {
      delete global[key];
    } else {
      global[key] = value;
    }
  }
}

async function runTest(label, fn) {
  const snapshot = {
    Page: global.Page,
    getApp: global.getApp,
    getCurrentPages: global.getCurrentPages,
    setTimeout: global.setTimeout,
    wx: global.wx,
  };

  try {
    resetModule(requestModulePath);
    resetModule(apiModulePath);
    resetModule(loginPagePath);
    await fn();
    console.log(`[miniapp-test] PASS ${label}`);
  } catch (error) {
    failures += 1;
    console.error(`[miniapp-test] FAIL ${label}`);
    console.error(error && error.stack ? error.stack : error);
  } finally {
    restoreGlobals(snapshot);
  }
}

function loadLoginPage() {
  let definition = null;
  global.Page = (options) => {
    definition = options;
  };
  require(loginPagePath);
  assert.ok(definition, 'login page definition should be captured');
  return definition;
}

function createPageInstance(definition) {
  const instance = {
    data: { ...(definition.data || {}) },
    setData(update) {
      this.data = { ...this.data, ...update };
    },
  };

  for (const [key, value] of Object.entries(definition)) {
    if (key === 'data') {
      continue;
    }
    instance[key] = typeof value === 'function' ? value.bind(instance) : value;
  }

  return instance;
}

async function main() {
  await runTest('request adds auth header and resolves business payload', async () => {
    let requestOptions = null;
    global.getApp = () => ({
      getSession() {
        return { token: 'token-123' };
      },
    });
    global.wx = {
      request(options) {
        requestOptions = options;
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: { id: 'student001' },
          },
        });
      },
    };

    const { request } = require(requestModulePath);
    const result = await request({ url: '/users/me' });

    assert.deepEqual(result, { id: 'student001' });
    assert.equal(
      requestOptions.url,
      'http://127.0.0.1:9000/api/v1/users/me',
    );
    assert.equal(requestOptions.header.Authorization, 'Bearer token-123');
  });

  await runTest('request clears session and redirects on 401', async () => {
    let redirectedTo = '';
    let clearSessionCalls = 0;

    global.getApp = () => ({
      getSession() {
        return { token: 'expired-token' };
      },
      clearSession() {
        clearSessionCalls += 1;
      },
    });
    global.getCurrentPages = () => [{ route: 'pages/exams/index' }];
    global.wx = {
      reLaunch({ url }) {
        redirectedTo = url;
      },
      request(options) {
        options.success({
          statusCode: 401,
          data: { message: 'expired' },
        });
      },
    };

    const { request } = require(requestModulePath);
    await assert.rejects(() => request({ url: '/exams/students/me' }));
    assert.equal(clearSessionCalls, 1);
    assert.equal(redirectedTo, '/pages/login/index');
  });

  await runTest('login page blocks empty credentials before API call', async () => {
    const api = require(apiModulePath);
    let loginCalls = 0;
    let lastToast = null;

    api.login = async () => {
      loginCalls += 1;
      return {};
    };
    global.getApp = () => ({
      isLoggedIn() {
        return false;
      },
    });
    global.wx = {
      showToast(options) {
        lastToast = options;
      },
    };

    const page = createPageInstance(loadLoginPage());
    await page.submitLogin();

    assert.equal(loginCalls, 0);
    assert.ok(lastToast);
    assert.equal(lastToast.icon, 'none');
  });

  await runTest('login page stores student session and navigates to exams', async () => {
    const api = require(apiModulePath);
    const setSessionCalls = [];
    const toastTitles = [];
    let relaunchUrl = '';

    api.login = async ({ username, password }) => ({
      token: `${username}:${password}`,
      user: {
        id: 'u-1',
        role: 'student',
      },
    });
    api.getMe = async () => ({
      id: 'u-1',
      role: 'STUDENT',
      profile: {
        nickname: 'mock-user',
      },
    });

    global.getApp = () => ({
      setSession(session) {
        setSessionCalls.push(session);
      },
      clearSession() {
        throw new Error('clearSession should not be called for student login');
      },
    });
    global.setTimeout = (callback) => {
      callback();
      return 1;
    };
    global.wx = {
      showToast({ title }) {
        toastTitles.push(title);
      },
      reLaunch({ url }) {
        relaunchUrl = url;
      },
    };

    const page = createPageInstance(loadLoginPage());
    page.setData({ username: 'student001', password: '123456' });
    await page.submitLogin();

    assert.equal(page.data.loading, false);
    assert.equal(setSessionCalls.length, 2);
    assert.equal(setSessionCalls.at(-1).user.role, 'STUDENT');
    assert.equal(setSessionCalls.at(-1).user.nickname, 'mock-user');
    assert.equal(relaunchUrl, '/pages/exams/index');
    assert.ok(toastTitles.length > 0);
  });

  await runTest('login page rejects non-student roles', async () => {
    const api = require(apiModulePath);
    let clearSessionCalls = 0;
    let relaunchUrl = '';
    let lastToast = null;

    api.login = async () => ({
      token: 'teacher-token',
      user: {
        id: 't-1',
        role: 'TEACHER',
      },
    });
    api.getMe = async () => ({
      id: 't-1',
      role: 'TEACHER',
      profile: {
        nickname: 'teacher-user',
      },
    });

    global.getApp = () => ({
      setSession() {},
      clearSession() {
        clearSessionCalls += 1;
      },
    });
    global.setTimeout = () => 1;
    global.wx = {
      showToast(options) {
        lastToast = options;
      },
      reLaunch({ url }) {
        relaunchUrl = url;
      },
    };

    const page = createPageInstance(loadLoginPage());
    page.setData({ username: 'teacher001', password: '123456' });
    await page.submitLogin();

    assert.equal(clearSessionCalls, 1);
    assert.equal(relaunchUrl, '');
    assert.ok(lastToast);
    assert.equal(lastToast.icon, 'none');
  });

  if (failures > 0) {
    console.error(`[miniapp-test] ${failures} test(s) failed.`);
    process.exit(1);
  }

  console.log('[miniapp-test] All tests passed.');
}

main().catch((error) => {
  console.error('[miniapp-test] Unhandled error');
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
