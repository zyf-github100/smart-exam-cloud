#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const repoRoot = path.resolve(__dirname, '..', '..');
const miniappRoot = path.join(repoRoot, 'smart-exam-miniapp');
const errors = [];

function toRepoPath(filePath) {
  return path.relative(repoRoot, filePath).replace(/\\/g, '/');
}

function addError(message) {
  errors.push(message);
}

function walkFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const entryPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkFiles(entryPath));
    } else if (entry.isFile()) {
      files.push(entryPath);
    }
  }

  return files.sort();
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    addError(`${toRepoPath(filePath)} is not valid JSON: ${error.message}`);
    return null;
  }
}

function checkJavaScriptSyntax(jsFiles) {
  for (const filePath of jsFiles) {
    const result = spawnSync(process.execPath, ['--check', filePath], {
      encoding: 'utf8',
      stdio: 'pipe',
    });

    if (result.status !== 0) {
      const output = [result.stdout, result.stderr].filter(Boolean).join('\n').trim();
      addError(`${toRepoPath(filePath)} failed JS syntax check${output ? `:\n${output}` : ''}`);
    }
  }
}

function checkAppPages(appConfig) {
  if (!appConfig || !Array.isArray(appConfig.pages) || appConfig.pages.length === 0) {
    addError('smart-exam-miniapp/app.json must declare a non-empty pages array');
    return 0;
  }

  for (const page of appConfig.pages) {
    if (typeof page !== 'string' || page.trim() === '') {
      addError('smart-exam-miniapp/app.json contains an invalid page path');
      continue;
    }

    const normalizedPage = page.replace(/^\/+/, '');
    const pageBase = path.resolve(miniappRoot, ...normalizedPage.split('/'));
    if (!pageBase.startsWith(miniappRoot + path.sep)) {
      addError(`Page path escapes miniapp root: ${page}`);
      continue;
    }

    for (const extension of ['.js', '.json', '.wxml', '.wxss']) {
      const expectedFile = `${pageBase}${extension}`;
      if (!fs.existsSync(expectedFile)) {
        addError(`Page file is missing: ${toRepoPath(expectedFile)}`);
      }
    }
  }

  return appConfig.pages.length;
}

if (!fs.existsSync(miniappRoot)) {
  console.error('[miniapp] smart-exam-miniapp directory was not found');
  process.exit(1);
}

const allFiles = walkFiles(miniappRoot);
const jsFiles = allFiles.filter((filePath) => filePath.endsWith('.js'));
const jsonFiles = allFiles.filter((filePath) => filePath.endsWith('.json'));

checkJavaScriptSyntax(jsFiles);
for (const filePath of jsonFiles) {
  readJson(filePath);
}

const appConfig = readJson(path.join(miniappRoot, 'app.json'));
const pageCount = checkAppPages(appConfig);

if (errors.length > 0) {
  console.error(`[miniapp] Static check failed with ${errors.length} issue(s):`);
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(`[miniapp] Static check passed: ${jsFiles.length} JS file(s), ${jsonFiles.length} JSON file(s), ${pageCount} page(s).`);
