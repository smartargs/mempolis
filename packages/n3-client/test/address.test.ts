import assert from 'node:assert/strict';
import { test } from 'node:test';
import { addressToScriptHash, base64ToScriptHash, bytesToHex, hexToBytes, isAddress, NATIVE, scriptHashToAddress } from '../src/index.ts';

const WALLET = 'Nf4kn2P7X9bdwvu4KXfrGW4aZALBeKYH2n';
const WALLET_HASH = '0xbb19cfc864b73159277e1fd39694b3fd5fc613d2';

const VECTORS: [string, string][] = [
  ['0x0000000000000000000000000000000000000000', 'NKuyBkoGdZZSLyPbJEetheRhMjeznFZszf'],
  [NATIVE.neo, 'NiHURyS83nX2mpxtA7xq84cGxVbHojj5Wc'],
  ['0xffffffffffffffffffffffffffffffffffffffff', 'NjFaAs6ZLk2KAQXgKezDBmhUzEuwLsBcxQ'],
  [WALLET_HASH, WALLET],
];

test('script hashes encode to their known base58check addresses', () => {
  for (const [hash, address] of VECTORS) assert.equal(scriptHashToAddress(hash), address, hash);
});

test('addresses decode back to their big-endian script hash', () => {
  for (const [hash, address] of VECTORS) assert.equal(addressToScriptHash(address), hash, address);
});

test('a valid address is recognised', () => {
  for (const [, address] of VECTORS) assert.equal(isAddress(address), true, address);
});

test('any single changed character breaks the checksum', () => {
  for (let i = 1; i < WALLET.length; i++) {
    const c = WALLET[i] === 'a' ? 'b' : 'a';
    const mutated = WALLET.slice(0, i) + c + WALLET.slice(i + 1);
    assert.equal(isAddress(mutated), false, mutated);
  }
});

test('characters outside the base58 alphabet, wrong lengths and empty input are refused', () => {
  for (const bad of ['', 'N', `${WALLET}1`, WALLET.slice(0, -1), WALLET.replace('f', '0'), WALLET.replace('f', 'O'), WALLET.replace('f', 'I'), WALLET.replace('f', 'l'), ` ${WALLET}`, '1111111111111111111111111']) {
    assert.equal(addressToScriptHash(bad), null, JSON.stringify(bad));
  }
});

test('a Neo Legacy address (version 0x17, checksum intact) is refused', () => {
  assert.equal(isAddress('Ae9NtiVUkNckEpnHRXyGaJSg5MrzU1qQFX'), false);
});

test('script hashes are accepted with or without 0x in any case, and malformed ones give null', () => {
  const bare = WALLET_HASH.slice(2);
  assert.equal(scriptHashToAddress(bare), WALLET);
  assert.equal(scriptHashToAddress(`0X${bare.toUpperCase()}`), WALLET);
  for (const bad of ['', '0x', `0x${bare.slice(1)}`, `0x${bare}00`, `0x${bare.slice(1)}g`, `0x0x${bare}`]) assert.equal(scriptHashToAddress(bad), null, bad);
});

test('hex helpers round-trip and use lower case', () => {
  const bytes = Uint8Array.from([0, 1, 0x7f, 0x80, 0xab, 0xff]);
  assert.equal(bytesToHex(bytes), '00017f80abff');
  assert.deepEqual(hexToBytes('00017F80ABff'), bytes);
  assert.deepEqual(hexToBytes(''), new Uint8Array(0));
});

test('base64 little-endian hashes from notifications become big-endian script hashes', () => {
  const le = hexToBytes(WALLET_HASH.slice(2)).reverse();
  assert.equal(base64ToScriptHash(Buffer.from(le).toString('base64')), WALLET_HASH);
});
