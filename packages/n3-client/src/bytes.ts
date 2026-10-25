/** Lower-case hex of `bytes`. */
export const bytesToHex = (bytes: Uint8Array) => Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');

/** Bytes of an even-length hex string (no `0x`). */
export function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length >> 1);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

/** Bytes of a base64 string. */
export function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  return Uint8Array.from(bin, c => c.charCodeAt(0));
}

/** UTF-8 text of `bytes`. */
export const utf8 = (bytes: Uint8Array) => new TextDecoder().decode(bytes);

/** The `0x`-prefixed big-endian script hash of a base64 little-endian hash, as notifications carry them. */
export const base64ToScriptHash = (b64: string) => `0x${bytesToHex(base64ToBytes(b64).reverse())}`;
