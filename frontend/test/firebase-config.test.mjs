import test from 'node:test';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { parseFirebaseConfig, validateFirebaseConfig } from '../src/services/firebaseConfig.js';

const valid = {
  apiKey: `AIza${'A'.repeat(35)}`,
  authDomain: 'sample-project.firebaseapp.com',
  projectId: 'sample-project',
  messagingSenderId: '123456789012',
  appId: '1:123456789012:web:abcdef012345',
};

test('accepts a structurally consistent Firebase web app configuration', () => {
  assert.equal(validateFirebaseConfig(valid), '');
});

test('diagnoses a malformed sender ID instead of passing it to Firebase Auth', () => {
  assert.match(validateFirebaseConfig({ ...valid, messagingSenderId: '123456789012"' }), /digits only/);
});

test('diagnoses a sender ID that does not match the Firebase app ID', () => {
  assert.match(validateFirebaseConfig({ ...valid, messagingSenderId: '210987654321' }), /do not match/);
});

test('reports missing public Firebase settings clearly', () => {
  assert.match(validateFirebaseConfig({}), /missing apiKey/);
});

test('parses Firebase config safely and rejects invalid JSON', () => {
  assert.deepEqual(parseFirebaseConfig('{"projectId":"sample-project"}'), { projectId: 'sample-project' });
  assert.deepEqual(parseFirebaseConfig('{invalid'), {});
});

test('desktop renderer CSP permits Firebase Auth network and Google popup resources', async () => {
  const html = await readFile(path.resolve('index.html'), 'utf8');
  const policy = html.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] || '';
  assert.match(policy, /script-src[^;]*https:\/\/apis\.google\.com/);
  assert.match(policy, /connect-src[^;]*https:\/\/identitytoolkit\.googleapis\.com/);
  assert.match(policy, /connect-src[^;]*https:\/\/securetoken\.googleapis\.com/);
  assert.match(policy, /frame-src[^;]*https:\/\/accounts\.google\.com/);
});
