import type { JsonCache } from './cache.ts';
import { N3Index } from './n3index.ts';
import { nnsNamesOf } from './nns.ts';
import { NeoRpc } from './rpc.ts';
import { Transport, type TransportOptions, type TransportStats } from './transport.ts';

/** Options for `createN3Client`. */
export interface N3ClientOptions extends Omit<TransportOptions, 'cache'> {
  /** Where responses are kept, e.g. `diskCache` from `@mempolis/n3-client/node` or `storageCache(sessionStorage)`; omit or `null` for none. */
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

/** Create a client for one network. Runs in Node and the browser; the caller picks the cache. */
export function createN3Client(o: N3ClientOptions = {}): N3Client {
  const cache = o.cache ?? null;
  const indexHttp = new Transport({ ...o, cache }), rpcHttp = new Transport({ ...o, cache });
  const index = new N3Index(indexHttp, { baseUrl: o.indexUrl, network: o.network });
  const rpc = new NeoRpc(rpcHttp, o.rpcUrl);
  return {
    index,
    rpc,
    voteOf: async address => (await rpc.neoAccountState(address))?.voteTo ?? null,
    nnsNames: async address => nnsNamesOf(await rpc.nep11Balances(address)),
    stats: () => {
      const a = indexHttp.stats, b = rpcHttp.stats;
      return { requests: a.requests + b.requests, cacheHits: a.cacheHits + b.cacheHits, retries: a.retries + b.retries, failures: a.failures + b.failures };
    },
  };
}
