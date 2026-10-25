import { diskCache, type JsonCache } from './cache.ts';
import { N3Index } from './n3index.ts';
import { NeoRpc } from './rpc.ts';
import { Transport, type TransportOptions, type TransportStats } from './transport.ts';
import { NNS_CONTRACT } from './types.ts';

/** Options for `createN3Client`. */
export interface N3ClientOptions extends Omit<TransportOptions, 'cache'> {
  /** Directory for the on-disk JSON cache. */
  cacheDir?: string;
  /** A cache to use instead of `cacheDir`; `null` disables caching. */
  cache?: JsonCache | null;
  network?: string;
  indexUrl?: string;
  rpcUrl?: string;
}

/** The N3Index API and a neo-go RPC node behind one cache, each with its own rate limit. */
export interface N3Client {
  index: N3Index;
  rpc: NeoRpc;
  /** The public key of the candidate `address` votes for, or `null`. */
  voteOf(address: string): Promise<string | null>;
  /** The `.neo` names `address` owns (NNS NEP-11 tokens). */
  nnsNames(address: string): Promise<string[]>;
  /** Network requests, cache hits, retries and failures so far, summed over both endpoints. */
  stats(): TransportStats;
}

function decodeName(tokenId: string): string {
  const bytes = /^[0-9a-f]+$/i.test(tokenId) && tokenId.length % 2 === 0 ? Buffer.from(tokenId, 'hex') : Buffer.from(tokenId, 'base64');
  return bytes.toString('utf8');
}

/** Create a client for one network. Pass `cacheDir` to keep every response on disk. */
export function createN3Client(o: N3ClientOptions = {}): N3Client {
  const cache = o.cache !== undefined ? o.cache : o.cacheDir ? diskCache(o.cacheDir) : null;
  const indexHttp = new Transport({ ...o, cache }), rpcHttp = new Transport({ ...o, cache });
  const index = new N3Index(indexHttp, { baseUrl: o.indexUrl, network: o.network });
  const rpc = new NeoRpc(rpcHttp, o.rpcUrl);
  return {
    index,
    rpc,
    voteOf: async address => (await rpc.neoAccountState(address))?.voteTo ?? null,
    nnsNames: async address => (await rpc.nep11Balances(address))
      .filter(h => h.contract === NNS_CONTRACT)
      .flatMap(h => h.tokens.map(t => decodeName(t.tokenId))),
    stats: () => {
      const a = indexHttp.stats, b = rpcHttp.stats;
      return { requests: a.requests + b.requests, cacheHits: a.cacheHits + b.cacheHits, retries: a.retries + b.retries, failures: a.failures + b.failures };
    },
  };
}
