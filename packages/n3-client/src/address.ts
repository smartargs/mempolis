import { createHash } from 'node:crypto';

const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const ADDRESS_VERSION = 0x35;

function base58Decode(text: string): Uint8Array | null {
  let n = 0n;
  for (const c of text) {
    const digit = ALPHABET.indexOf(c);
    if (digit < 0) return null;
    n = n * 58n + BigInt(digit);
  }
  const hex = n.toString(16);
  const bytes = Buffer.from(hex.length % 2 ? `0${hex}` : hex, 'hex');
  let zeros = 0;
  while (text[zeros] === '1') zeros++;
  return new Uint8Array([...new Uint8Array(zeros), ...bytes]);
}

const sha256 = (data: Uint8Array) => createHash('sha256').update(data).digest();

/** Whether `text` is a valid Neo N3 address (version byte and checksum). */
export function isAddress(text: string): boolean {
  return addressToScriptHash(text) !== null;
}

/** The `0x`-prefixed script hash (big-endian, as explorers show it) of a Neo N3 address, or `null` if invalid. */
export function addressToScriptHash(address: string): string | null {
  const raw = base58Decode(address);
  if (!raw || raw.length !== 25 || raw[0] !== ADDRESS_VERSION) return null;
  const body = raw.subarray(0, 21), check = sha256(sha256(body)).subarray(0, 4);
  if (!check.every((v, i) => v === raw[21 + i])) return null;
  return `0x${Buffer.from(raw.subarray(1, 21)).reverse().toString('hex')}`;
}
