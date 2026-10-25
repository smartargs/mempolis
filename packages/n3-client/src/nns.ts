import { base64ToBytes, hexToBytes, utf8 } from './bytes.ts';
import { NNS_CONTRACT, type Nep11Holding } from './types.ts';

function decodeName(tokenId: string): string {
  const isHex = /^[0-9a-f]+$/i.test(tokenId) && tokenId.length % 2 === 0;
  return utf8(isHex ? hexToBytes(tokenId) : base64ToBytes(tokenId));
}

/** The `.neo` names among NEP-11 holdings (NNS tokens). */
export function nnsNamesOf(holdings: Nep11Holding[]): string[] {
  return holdings.filter(h => h.contract === NNS_CONTRACT).flatMap(h => h.tokens.map(t => decodeName(t.tokenId)));
}
