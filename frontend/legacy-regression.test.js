import test from 'node:test';
import assert from 'node:assert/strict';
import {createApi} from './api.js';

test('legacy API preserves error codes and actionable status messages', async () => {
  for (const [status, message] of [[403, '권한'], [413, '크기'], [429, '요청이 많'], [502, '백엔드'], [500, '완료하지']]) {
    const api = createApi(async () => new Response(JSON.stringify({success: false, error: {code: 'TEST_ERROR'}}), {status}));
    await assert.rejects(api.request('/artworks'), error => error.code === 'TEST_ERROR' && error.message.includes(message));
  }
});

test('legacy API rejects stale responses after session clear', async () => {
  let resolve;
  const api = createApi(() => new Promise(done => { resolve = done; }));
  const pending = api.request('/artworks');
  api.clear();
  resolve(new Response(JSON.stringify({success: true, data: {secret: 'old-session'}})));
  await assert.rejects(pending, /세션이 변경/);
});

test('legacy API preserves binary image responses', async () => {
  const api = createApi(async () => new Response('image-bytes', {headers: {'Content-Type': 'image/png'}}));
  const blob = await api.request('/preview', {blob: true});
  assert.equal(blob.type, 'image/png');
  assert.equal(await blob.text(), 'image-bytes');
});

test('legacy API handles malformed JSON without exposing parser errors', async () => {
  const api = createApi(async () => new Response('not-json', {status: 502}));
  await assert.rejects(api.request('/artworks'), /백엔드/);
});
