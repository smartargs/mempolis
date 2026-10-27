import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import { bytesToHex, sha256 } from '../src/index.ts';

const text = (s: string) => new TextEncoder().encode(s);
const digest = (data: Uint8Array) => bytesToHex(sha256(data));

test('NIST vector: the empty message', () => {
  assert.equal(digest(new Uint8Array(0)), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
});

test('NIST vector: "abc"', () => {
  assert.equal(digest(text('abc')), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});

test('NIST vector: the 448-bit message, whose padding spills into a second block', () => {
  assert.equal(digest(text('abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq')), '248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1');
});

test('NIST vector: the 896-bit two-block message', () => {
  const msg = 'abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu';
  assert.equal(digest(text(msg)), 'cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1');
});

test('NIST vector: one million "a"', () => {
  assert.equal(digest(new Uint8Array(1_000_000).fill(0x61)), 'cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0');
});

test('matches node:crypto for every length around the block and length-field boundaries', () => {
  for (let len = 0; len <= 200; len++) {
    const data = Uint8Array.from({ length: len }, (_, i) => (i * 131 + len * 7) & 0xff);
    assert.equal(digest(data), createHash('sha256').update(data).digest('hex'), `length ${len}`);
  }
});

test('hashes a subarray view by its own bytes, not the whole buffer behind it', () => {
  const whole = text('xxabcxx');
  assert.equal(digest(whole.subarray(2, 5)), digest(text('abc')));
});

test('leaves its input untouched', () => {
  const data = text('abc'), copy = data.slice();
  sha256(data);
  assert.deepEqual(data, copy);
});
