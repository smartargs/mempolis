import { bytesToHex, hexToBytes } from './bytes.ts';
import { sha256 } from './sha256.ts';

const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const ADDRESS_VERSION = 0x35;

function base58Decode(text: string): Uint8Array | null {
  let n = 0n;
  for (const c of text) {
    const digit = ALPHABET.indexOf(c);
    if (digit < 0) return null;
    n = n * 58n + BigInt(digit);
  }
  const hex = n === 0n ? '' : n.toString(16);
  const bytes = hexToBytes(hex.length % 2 ? `0${hex}` : hex);
  let zeros = 0;
  while (text[zeros] === '1') zeros++;
  return new Uint8Array([...new Uint8Array(zeros), ...bytes]);
}

function base58Encode(bytes: Uint8Array): string {
  let n = 0n;
  for (const b of bytes) n = n * 256n + BigInt(b);
  let out = '';
  while (n > 0n) { out = ALPHABET[Number(n % 58n)] + out; n /= 58n; }
  let zeros = 0;
  while (bytes[zeros] === 0) zeros++;
  return '1'.repeat(zeros) + out;
}

/** Whether `text` is a valid Neo N3 address (version byte and checksum). */
export function isAddress(text: string): boolean {
  return addressToScriptHash(text) !== null;
}

/** The `0x`-prefixed script hash (big-endian, as explorers show it) of a Neo N3 address, or `null` if invalid. */
export function addressToScriptHash(address: string): string | null {
  const raw = base58Decode(address);
  if (!raw || raw.length !== 25 || raw[0] !== ADDRESS_VERSION) return null;
  const check = sha256(sha256(raw.subarray(0, 21))).subarray(0, 4);
  if (!check.every((v, i) => v === raw[21 + i])) return null;
  return `0x${bytesToHex(raw.slice(1, 21).reverse())}`;
}

/** The Neo N3 address of a `0x`-prefixed (big-endian) script hash, or `null` if it is not 20 bytes of hex. */
export function scriptHashToAddress(scriptHash: string): string | null {
  const hex = scriptHash.replace(/^0x/i, '');
  if (!/^[0-9a-fA-F]{40}$/.test(hex)) return null;
  const body = new Uint8Array([ADDRESS_VERSION, ...hexToBytes(hex).reverse()]);
  return base58Encode(new Uint8Array([...body, ...sha256(sha256(body)).subarray(0, 4)]));
}
