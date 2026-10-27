import assert from 'node:assert/strict';
import { test } from 'node:test';
import { N3Index, Transport } from '../src/index.ts';
import { fakeFetch, type FakeReply } from './fake-fetch.ts';

const ADDR = 'Nf4kn2P7X9bdwvu4KXfrGW4aZALBeKYH2n';
const ROOT = 'https://idx.test';

interface FeedRow { block_index: number; event_key: string; kind: string; direction: string; timestamp_ms: number; txid: string }

const feed = (n: number): FeedRow[] => Array.from({ length: n }, (_, i) => ({
  block_index: 1000 + Math.floor(i / 3), event_key: `tx:${i}/${i % 3}&x`, kind: i % 2 ? 'nep17_transfer' : 'transaction',
  direction: 'sent', timestamp_ms: 1_600_000_000_000 + i, txid: `0x${i.toString(16).padStart(4, '0')}`,
}));

interface ActivityRequest { block: number; key: string; limit: number }

function activityServer(rows: FeedRow[], misbehave: (r: ActivityRequest, remaining: number) => boolean = () => false) {
  const requests: ActivityRequest[] = [];
  const f = fakeFetch((url): FakeReply => {
    const q = new URL(url).searchParams;
    const r = { block: Number(q.get('after_block')), key: q.get('after_key') ?? '', limit: Number(q.get('limit')) };
    requests.push(r);
    const from = r.block < 0 ? 0 : rows.findIndex(e => e.block_index === r.block && e.event_key === r.key) + 1;
    const start = misbehave(r, rows.length - from) ? 0 : from;
    const page = rows.slice(start, start + r.limit), last = page[page.length - 1];
    const hasMore = start + r.limit < rows.length;
    return { json: { data: page, paging: { has_more: hasMore, next_cursor: hasMore && last ? { block_index: last.block_index, event_key: last.event_key } : undefined } } };
  });
  const index = new N3Index(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), { baseUrl: ROOT });
  return { index, requests, calls: f.calls };
}

const keysOf = (events: { txid: string }[]) => events.map(e => e.txid);

test('activity follows the cursor page by page until has_more is false', async () => {
  const rows = feed(450), s = activityServer(rows), heard: number[] = [];
  const out = await s.index.activity(ADDR, { onPage: n => heard.push(n) });
  assert.equal(out.complete, true);
  assert.deepEqual(keysOf(out.events), rows.map(r => r.txid));
  assert.deepEqual(heard, [200, 400, 450]);
  assert.deepEqual(s.requests.map(r => r.limit), [200, 200, 200]);
  assert.deepEqual(s.requests[1], { block: rows[199].block_index, key: rows[199].event_key, limit: 200 });
});

test('activity requests the documented URL and escapes the cursor key', async () => {
  const s = activityServer(feed(201));
  await s.index.activity(ADDR);
  assert.equal(s.calls[0].url, `${ROOT}/v1/networks/mainnet/accounts/${ADDR}/activity?after_block=-1&after_key=&limit=200`);
  assert.match(s.calls[1].url, /after_key=tx%3A199%2F1%26x&limit=200$/);
});

test('activity maps rows to camel-cased events', async () => {
  const s = activityServer([{ block_index: 5, event_key: 'k', kind: 'nep17_transfer', direction: 'received', timestamp_ms: 9, txid: '0x1', contract_hash: '0xc', from_address: 'A', to_address: 'B', amount_raw: '12', symbol: 'GAS' } as FeedRow]);
  const out = await s.index.activity(ADDR);
  assert.deepEqual(out.events, [{ kind: 'nep17_transfer', direction: 'received', timestampMs: 9, blockIndex: 5, txid: '0x1', contract: '0xc', from: 'A', to: 'B', amountRaw: '12', symbol: 'GAS' }]);
});

test('a page that restarts from the beginning is fetched again with half the limit, then the limit recovers', async () => {
  const rows = feed(700);
  const s = activityServer(rows, (r, remaining) => r.block >= 0 && remaining > 400 && r.limit > 100);
  const out = await s.index.activity(ADDR);
  assert.equal(out.complete, true);
  assert.deepEqual(keysOf(out.events), rows.map(r => r.txid));
  assert.deepEqual(s.requests.map(r => r.limit), [200, 200, 100, 200, 200]);
});

test('near the end of a feed the limit keeps halving until the page fits', async () => {
  const rows = feed(450);
  const s = activityServer(rows, (r, remaining) => r.block >= 0 && r.limit > remaining);
  const out = await s.index.activity(ADDR);
  assert.equal(out.complete, true);
  assert.deepEqual(keysOf(out.events), rows.map(r => r.txid));
  assert.deepEqual(s.requests.map(r => r.limit), [200, 200, 200, 100, 50]);
});

test('a feed that keeps restarting down to limit 1 is returned as incomplete, with what was read so far', async () => {
  const rows = feed(450);
  const s = activityServer(rows, r => r.block >= 0);
  const out = await s.index.activity(ADDR);
  assert.equal(out.complete, false);
  assert.deepEqual(keysOf(out.events), rows.slice(0, 200).map(r => r.txid));
  assert.deepEqual(s.requests.map(r => r.limit), [200, 200, 100, 50, 25, 12, 6, 3, 1]);
});

test('rows older than the cursor block count as a restart even when their keys are new', async () => {
  const rows = feed(300);
  let served = false;
  const f = fakeFetch((url): FakeReply => {
    const q = new URL(url).searchParams;
    if (q.get('after_block') === '-1') return { json: { data: rows.slice(0, 200), paging: { has_more: true, next_cursor: { block_index: rows[199].block_index, event_key: rows[199].event_key } } } };
    if (!served) { served = true; return { json: { data: [{ ...rows[0], event_key: 'fresh' }], paging: { has_more: false } } }; }
    return { json: { data: rows.slice(200), paging: { has_more: false } } };
  });
  const out = await new N3Index(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), { baseUrl: ROOT }).activity(ADDR);
  assert.equal(out.events.length, 300);
  assert.deepEqual(f.calls.map(c => new URL(c.url).searchParams.get('limit')), ['200', '200', '100']);
});

test('maxPages cuts the feed and marks it incomplete; refetched pages do not count against it', async () => {
  const rows = feed(450);
  assert.deepEqual(await activityServer(rows).index.activity(ADDR, { maxPages: 2 }).then(o => [o.events.length, o.complete]), [400, false]);
  const flaky = activityServer(rows, (r, remaining) => r.block >= 0 && r.limit > remaining);
  assert.deepEqual(await flaky.index.activity(ADDR, { maxPages: 3 }).then(o => [o.events.length, o.complete]), [450, true]);
});

test('has_more without a cursor ends the feed as complete', async () => {
  const f = fakeFetch(() => ({ json: { data: feed(3), paging: { has_more: true } } }));
  const out = await new N3Index(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), { baseUrl: ROOT }).activity(ADDR);
  assert.deepEqual([out.events.length, out.complete, f.calls.length], [3, true, 1]);
});

test('an unknown account (404) is an empty, complete feed', async () => {
  const f = fakeFetch(() => ({ status: 404 }));
  const out = await new N3Index(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), { baseUrl: ROOT }).activity(ADDR);
  assert.deepEqual(out, { events: [], complete: true });
});

function indexWith(route: (url: string) => FakeReply, network?: string) {
  const f = fakeFetch(route);
  return { index: new N3Index(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), { baseUrl: ROOT, network }), calls: f.calls };
}

test('transfers page by offset and stop at the first page that reaches back past sinceMs', async () => {
  const at = (i: number) => ({ txid: `t${i}`, block_index: 1000 - i, timestamp_ms: 10_000 - i, contract_hash: '0xc', from_address: null, to_address: 'B', amount_raw: 5 });
  const all = Array.from({ length: 450 }, (_, i) => at(i));
  const s = indexWith(url => {
    const offset = Number(new URL(url).searchParams.get('offset'));
    return { json: { data: all.slice(offset, offset + 200) } };
  });
  const out = await s.index.transfers(ADDR, { sinceMs: 10_000 - 250, standard: 'nep17' });
  assert.equal(out.length, 251);
  assert.deepEqual(out[0], { txid: 't0', blockIndex: 1000, timestampMs: 10_000, contract: '0xc', from: '', to: 'B', amountRaw: '5' });
  assert.deepEqual(s.calls.map(c => new URL(c.url).search), ['?limit=200&offset=0&standard=nep17', '?limit=200&offset=200&standard=nep17']);
});

test('notifications dedupe txids, batch 40 per query, honour maxQueries and come back oldest first', async () => {
  const txids = Array.from({ length: 90 }, (_, i) => `0x${i}`);
  const s = indexWith(url => {
    const ids = decodeURIComponent(url).match(/txid=in\.\(([^)]*)\)/)![1].split(',');
    return { json: ids.map(t => ({ txid: t, block_index: 500 - Number(t.slice(2)), notification_index: 1, block_time_ms: 1, contract_hash: '0xc', event_name: 'Transfer', state_json: { value: [{ type: 'Integer', value: '1' }] } })) };
  }, 'testnet');
  const full = await s.index.notifications([...txids, ...txids.slice(0, 10)], { events: ['Transfer', 'Mint'] });
  assert.equal(s.calls.length, 3);
  assert.ok(s.calls.every(c => c.url.startsWith(`${ROOT}/rest/v1/contract_notifications?network=eq.testnet&`) && c.url.includes('&event_name=in.(Transfer,Mint)&')));
  assert.equal(full.complete, true);
  assert.equal(full.items.length, 90);
  assert.deepEqual(full.items.slice(0, 2).map(n => n.txid), ['0x89', '0x88']);
  assert.deepEqual(full.items[0].state, [{ type: 'Integer', value: '1' }]);
  const capped = await s.index.notifications(txids, { maxQueries: 1 });
  assert.deepEqual([capped.items.length, capped.complete], [40, false]);
});

test('a batch that fills the 1000-row limit is not reported as complete', async () => {
  const s = indexWith(() => ({ json: Array.from({ length: 1000 }, (_, i) => ({ txid: '0x1', block_index: 1, notification_index: i, block_time_ms: 1, contract_hash: '0xc', event_name: 'Transfer' })) }));
  const out = await s.index.notifications(['0x1']);
  assert.equal(out.complete, false);
});

test('notifications of no transactions make no request', async () => {
  const s = indexWith(() => ({ json: [] }));
  assert.deepEqual(await s.index.notifications([]), { items: [], complete: true });
  assert.equal(s.calls.length, 0);
});

test('status parses freshness and is not cached by default', async () => {
  const s = indexWith(() => ({ json: { data: { last_indexed_block: 9, chain_tip_block: 10, updated_at: '2026-01-01T00:00:00Z' } } }));
  assert.deepEqual(await s.index.status(), { lastIndexedBlock: 9, chainTipBlock: 10, updatedAtMs: Date.UTC(2026, 0, 1) });
  assert.equal(s.calls[0].url, `${ROOT}/v1/networks/mainnet/status`);
});

test('an account the indexer never saw has no summary or identity', async () => {
  const s = indexWith(() => ({ status: 404 }));
  assert.equal(await s.index.accountSummary(ADDR), null);
  assert.equal(await s.index.accountIdentity(ADDR), null);
  assert.deepEqual(await s.index.balances(ADDR), []);
  assert.equal(await s.index.isContract('0x01'), false);
});

test('identity falls back to unknown and unlabeled when the indexer has no label', async () => {
  const s = indexWith(() => ({ json: { data: { signals: [{ code: 'recurrent-counterparty' }] } } }));
  assert.deepEqual(await s.index.accountIdentity(ADDR), { category: 'unknown', status: 'unlabeled', name: '', tags: [], confidence: 0, signals: ['recurrent-counterparty'] });
});
