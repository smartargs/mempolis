import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createN3Client, HttpError, NATIVE, NeoRpc, nnsNamesOf, NNS_CONTRACT, Transport } from '../src/index.ts';
import { fakeFetch, type FakeReply } from './fake-fetch.ts';

const ADDR = 'Nf4kn2P7X9bdwvu4KXfrGW4aZALBeKYH2n';
const RPC = 'https://rpc.test';
const KEY = '02' + 'ab'.repeat(32);

function rpcWith(route: (body: { method: string; params: unknown[] }) => FakeReply) {
  const f = fakeFetch((_url, call) => route(JSON.parse(call.body!)));
  return { rpc: new NeoRpc(new Transport({ fetch: f.fetch, spacingMs: 0, retries: 0 }), RPC), calls: f.calls };
}

test('a JSON-RPC error becomes an HttpError naming the method', async () => {
  const s = rpcWith(() => ({ json: { jsonrpc: '2.0', id: 1, error: { code: -32601, message: 'Method not found', data: 'nope' } } }));
  await assert.rejects(s.rpc.call('missing', []), (e: unknown) => e instanceof HttpError && e.status === 200 && !e.retryable && /missing: Method not found nope/.test(e.message));
});

test('a reply without a result is an error too', async () => {
  const s = rpcWith(() => ({ json: { jsonrpc: '2.0', id: 1 } }));
  await assert.rejects(s.rpc.call('x', []), /x: no result/);
});

test('the vote of an account is read from getAccountState as a hex public key', async () => {
  const s = rpcWith(() => ({ json: { result: { stack: [{ type: 'Struct', value: [{ type: 'Integer', value: '12' }, { type: 'Integer', value: '345' }, { type: 'ByteString', value: Buffer.from(KEY, 'hex').toString('base64') }] }] } } }));
  assert.deepEqual(await s.rpc.neoAccountState(ADDR), { balance: '12', balanceHeight: 345, voteTo: KEY });
  const sent = JSON.parse(s.calls[0].body!);
  assert.deepEqual(sent.params, [NATIVE.neo, 'getAccountState', [{ type: 'Hash160', value: '0xbb19cfc864b73159277e1fd39694b3fd5fc613d2' }]]);
});

test('an account that never held NEO has no state, and one that does not vote has no vote', async () => {
  assert.equal(await rpcWith(() => ({ json: { result: { stack: [{ type: 'Any' }] } } })).rpc.neoAccountState(ADDR), null);
  const novote = rpcWith(() => ({ json: { result: { stack: [{ type: 'Struct', value: [{ type: 'Integer', value: '1' }, { type: 'Integer', value: '2' }, { type: 'Any' }] }] } } }));
  assert.equal((await novote.rpc.neoAccountState(ADDR))?.voteTo, null);
});

test('an invalid address is refused before any request', async () => {
  const s = rpcWith(() => ({ json: {} }));
  await assert.rejects(s.rpc.neoAccountState('not-an-address'), /invalid address/);
  assert.equal(s.calls.length, 0);
});

test('NEP-11 holdings drop tokens with amount 0 and contracts left empty', async () => {
  const s = rpcWith(() => ({ json: { result: { balance: [
    { assethash: '0xa', symbol: 'A', tokens: [{ tokenid: 't1', amount: '1' }, { tokenid: 't2', amount: '0' }] },
    { assethash: '0xb', tokens: [{ tokenid: 't3', amount: '0' }] },
  ] } } }));
  assert.deepEqual(await s.rpc.nep11Balances(ADDR), [{ contract: '0xa', symbol: 'A', tokens: [{ tokenId: 't1', amount: '1' }] }]);
});

test('NEP-17 transfers merge sent and received oldest first, with the counterparty on the right side', async () => {
  const s = rpcWith(() => ({ json: { result: { sent: [{ txhash: 's', blockindex: 2, timestamp: 20, assethash: '0xg', transferaddress: 'B', amount: '3' }], received: [{ txhash: 'r', blockindex: 1, timestamp: 10, assethash: '0xg', transferaddress: null, amount: '5' }] } } }));
  const out = await s.rpc.nep17Transfers(ADDR, 0, 100);
  assert.deepEqual(out.map(t => [t.txid, t.from, t.to]), [['r', '', ADDR], ['s', ADDR, 'B']]);
});

test('NNS names decode from hex and base64 token ids and ignore other contracts', () => {
  const name = 'flamingo.neo';
  assert.deepEqual(nnsNamesOf([
    { contract: NNS_CONTRACT, symbol: 'NNS', tokens: [{ tokenId: Buffer.from(name).toString('hex'), amount: '1' }, { tokenId: Buffer.from('a.neo').toString('base64'), amount: '1' }] },
    { contract: '0xother', symbol: 'X', tokens: [{ tokenId: Buffer.from('b.neo').toString('hex'), amount: '1' }] },
  ]), [name, 'a.neo']);
});

test('the client sums stats over both endpoints', async () => {
  const f = fakeFetch(url => (url.startsWith(RPC) ? { json: { result: { stack: [{ type: 'Any' }] } } } : { status: 404 }));
  const client = createN3Client({ fetch: f.fetch, spacingMs: 0, retries: 0, rpcUrl: RPC, indexUrl: 'https://idx.test' });
  assert.equal(await client.voteOf(ADDR), null);
  assert.equal(await client.index.accountSummary(ADDR), null);
  assert.deepEqual(client.stats(), { requests: 2, cacheHits: 0, retries: 0, failures: 0 });
});
