import assert from 'node:assert/strict';
import { mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';
import { memoryCache, storageCache, type KeyValueStorage } from '../src/index.ts';
import { diskCache } from '../src/node.ts';

function mapStorage(): KeyValueStorage & { items: Map<string, string> } {
  const items = new Map<string, string>();
  return { items, getItem: k => items.get(k) ?? null, setItem: (k, v) => { items.set(k, v); } };
}

const dirs: string[] = [];
const scratch = () => { const d = mkdtempSync(join(tmpdir(), 'n3-cache-')); dirs.push(d); return d; };
after(() => { for (const d of dirs) rmSync(d, { recursive: true, force: true }); });

test('the memory cache tells a stored null apart from a miss', () => {
  const c = memoryCache();
  assert.equal(c.get('k'), undefined);
  c.set('k', null);
  assert.deepEqual(c.get('k'), { value: null });
  c.set('k', { a: 1 });
  assert.deepEqual(c.get('k'), { value: { a: 1 } });
});

test('separate memory caches do not share entries', () => {
  const a = memoryCache(), b = memoryCache();
  a.set('k', 1);
  assert.equal(b.get('k'), undefined);
});

test('the storage cache keeps JSON under its prefix and round-trips null', () => {
  const s = mapStorage(), c = storageCache(s, 'x:');
  c.set('k', { n: [1, 'two', null] });
  c.set('gone', null);
  assert.deepEqual([...s.items.keys()], ['x:k', 'x:gone']);
  assert.deepEqual(c.get('k'), { value: { n: [1, 'two', null] } });
  assert.deepEqual(c.get('gone'), { value: null });
  assert.equal(c.get('missing'), undefined);
});

test('the storage cache uses the n3: prefix by default', () => {
  const s = mapStorage();
  storageCache(s).set('k', 1);
  assert.deepEqual([...s.items.keys()], ['n3:k']);
});

test('the storage cache treats corrupt entries and failing storage as misses and never throws', () => {
  const s = mapStorage();
  s.items.set('n3:bad', '{not json');
  assert.equal(storageCache(s).get('bad'), undefined);
  const full: KeyValueStorage = { getItem: () => { throw new Error('denied'); }, setItem: () => { throw new Error('QuotaExceededError'); } };
  const c = storageCache(full);
  assert.doesNotThrow(() => c.set('k', 'v'));
  assert.equal(c.get('k'), undefined);
});

test('the disk cache round-trips values, null included, across instances', () => {
  const dir = scratch();
  diskCache(dir).set('GET https://x/a', { a: 1 });
  diskCache(dir).set('GET https://x/404', null);
  const again = diskCache(dir);
  assert.deepEqual(again.get('GET https://x/a'), { value: { a: 1 } });
  assert.deepEqual(again.get('GET https://x/404'), { value: null });
  assert.equal(again.get('GET https://x/b'), undefined);
  assert.equal(readdirSync(dir).filter(f => f.endsWith('.tmp')).length, 0);
});

test('the disk cache creates nested folders and treats unreadable files as misses', () => {
  const dir = join(scratch(), 'a', 'b');
  const c = diskCache(dir);
  c.set('k', 1);
  const [file] = readdirSync(dir);
  writeFileSync(join(dir, file), '{oops');
  assert.equal(c.get('k'), undefined);
});

test('the disk cache refuses an entry stored under another key', () => {
  const dir = scratch(), c = diskCache(dir);
  c.set('k', 1);
  const [file] = readdirSync(dir);
  writeFileSync(join(dir, file), JSON.stringify({ key: 'other', value: 2 }));
  assert.equal(c.get('k'), undefined);
});
