import type { Transport } from './transport.ts';
import type { AccountIdentity, AccountSummary, ActivityEvent, ActivityFeed, Holder, IndexStatus, Page, TokenBalance, Transfer, Voter } from './types.ts';

interface Wrapped<T> { data: T; paging?: { has_more?: boolean; total?: number; next_cursor?: { block_index: number; event_key: string } }; meta?: { total?: number } }
type Row = Record<string, any>;

const PAGE = 200;

const toTransfer = (r: Row): Transfer => ({
  txid: r.txid, blockIndex: r.block_index, timestampMs: r.timestamp_ms, contract: r.contract_hash,
  from: r.from_address ?? '', to: r.to_address ?? '', amountRaw: String(r.amount_raw ?? '0'),
});

const toEvent = (r: Row): ActivityEvent => ({
  kind: r.kind, direction: r.direction, timestampMs: r.timestamp_ms, blockIndex: r.block_index, txid: r.txid,
  contract: r.contract_hash, from: r.from_address, to: r.to_address, amountRaw: r.amount_raw, symbol: r.symbol,
});

/** Read access to the N3Index REST API (`/v1/networks/{network}` and the PostgREST tables). */
export class N3Index {
  private readonly http: Transport;
  private readonly base: string;
  private readonly root: string;
  private readonly network: string;

  constructor(http: Transport, o: { baseUrl?: string; network?: string } = {}) {
    this.http = http;
    this.root = o.baseUrl ?? 'https://api.n3index.dev';
    this.network = o.network ?? 'mainnet';
    this.base = `${this.root}/v1/networks/${this.network}`;
  }

  /** Indexer freshness. Not cached unless `cache` is true. */
  async status(o: { cache?: boolean } = {}): Promise<IndexStatus> {
    const r = await this.http.get<Wrapped<Row>>(`${this.base}/status`, { cache: o.cache ?? false });
    const d = r!.data;
    return { lastIndexedBlock: d.last_indexed_block, chainTipBlock: d.chain_tip_block, updatedAtMs: Date.parse(d.updated_at) };
  }

  /** Account overview, or `null` when the indexer has never seen the address. */
  async accountSummary(address: string): Promise<AccountSummary | null> {
    const r = await this.http.get<Wrapped<Row>>(`${this.base}/accounts/${address}`);
    const d = r?.data;
    if (!d) return null;
    return {
      address: d.address, txSent: d.tx_sent, txSigned: d.tx_signed, nep17SentRaw: d.nep17_sent_raw, nep17ReceivedRaw: d.nep17_received_raw,
      nep11SentEvents: d.nep11_sent_events, nep11ReceivedEvents: d.nep11_received_events, contractsInteracted: d.contracts_interacted,
      firstTxMs: d.first_tx_ms, lastTxMs: d.last_tx_ms,
    };
  }

  /** Identity labels and behaviour signals from the indexer's address intelligence. */
  async accountIdentity(address: string): Promise<AccountIdentity | null> {
    const r = await this.http.get<Wrapped<Row>>(`${this.base}/accounts/${address}/intelligence?sample=20&limit=4`);
    const d = r?.data;
    if (!d) return null;
    const id = d.identity ?? {};
    return {
      category: id.category ?? 'unknown', status: id.status ?? 'unlabeled', name: id.entity_name || '', tags: id.tags ?? [],
      confidence: id.confidence ?? 0, signals: (d.signals ?? []).map((s: Row) => s.code),
    };
  }

  /** Current NEP-17 balances. */
  async balances(address: string): Promise<TokenBalance[]> {
    const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/accounts/${address}/balances`);
    return (r?.data ?? []).map(b => ({ contract: b.contract_hash, amountRaw: String(b.balance_raw) }));
  }

  /** Token transfers, newest first, back to `sinceMs` (all when omitted), at most `maxPages` pages of 200. */
  async transfers(address: string, o: { sinceMs?: number; standard?: 'nep17' | 'nep11'; maxPages?: number } = {}): Promise<Transfer[]> {
    const out: Transfer[] = [], since = o.sinceMs ?? -Infinity, std = o.standard ? `&standard=${o.standard}` : '';
    for (let page = 0; page < (o.maxPages ?? 50); page++) {
      const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/accounts/${address}/transfers?limit=200&offset=${page * 200}${std}`);
      const rows = (r?.data ?? []).map(toTransfer);
      for (const t of rows) if (t.timestampMs >= since) out.push(t);
      if (rows.length < 200 || rows[rows.length - 1].timestampMs < since) break;
    }
    return out;
  }

  /**
   * The full oldest-first activity feed (transactions plus NEP-17/NEP-11 transfers), up to `maxPages` pages.
   * The API sometimes restarts from the beginning instead of following the cursor near the end of a feed;
   * such pages are detected and fetched again with a smaller limit.
   */
  async activity(address: string, o: { maxPages?: number } = {}): Promise<ActivityFeed> {
    const events: ActivityEvent[] = [], seen = new Set<string>();
    let block = -1, key = '', limit = PAGE, pages = 0;
    while (pages < (o.maxPages ?? 40)) {
      const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/accounts/${address}/activity?after_block=${block}&after_key=${encodeURIComponent(key)}&limit=${limit}`);
      const rows = r?.data ?? [];
      if (rows.some(e => e.block_index < block || seen.has(e.event_key))) {
        if (limit === 1) return { events, complete: false };
        limit = Math.max(1, limit >> 1);
        continue;
      }
      pages++;
      for (const e of rows) { seen.add(e.event_key); events.push(toEvent(e)); }
      const next = r?.paging?.next_cursor;
      if (!r?.paging?.has_more || !next) return { events, complete: true };
      block = next.block_index;
      key = next.event_key;
      limit = PAGE;
    }
    return { events, complete: false };
  }

  /** Top holders of a token by balance. */
  async tokenHolders(contract: string, o: { limit?: number; offset?: number } = {}): Promise<Page<Holder>> {
    const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/tokens/${contract}/holders?limit=${o.limit ?? 200}&offset=${o.offset ?? 0}`);
    return { items: (r?.data ?? []).map(h => ({ address: h.address, balanceRaw: String(h.balance_raw) })), total: r?.meta?.total ?? r?.paging?.total };
  }

  /** Current voters of a council candidate (33-byte public key in hex). */
  async voters(candidate: string, o: { limit?: number; offset?: number } = {}): Promise<Page<Voter>> {
    const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/governance/voters?candidate=${candidate}&limit=${o.limit ?? 200}&offset=${o.offset ?? 0}`);
    return { items: (r?.data ?? []).map(v => ({ scriptHash: v.script_hash, votes: String(v.votes) })), total: r?.paging?.total };
  }

  /** Committee and candidate metadata with display names and vote ranks. */
  async validators(): Promise<{ publicKey: string; name: string; rank: number; votes: string; active: boolean }[]> {
    const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/metadata/validators`);
    return (r?.data ?? []).map(v => ({ publicKey: v.public_key, name: v.display_name, rank: v.rank, votes: String(v.votes), active: !!v.active }));
  }

  /** Whether a script hash belongs to a deployed contract. */
  async isContract(scriptHash: string): Promise<boolean> {
    return (await this.http.get(`${this.base}/contracts/${scriptHash}`)) !== null;
  }

  /** Registered NNS domains with the address their record resolves to. */
  async nnsDomains(o: { limit?: number; offset?: number } = {}): Promise<{ domain: string; address: string }[]> {
    const r = await this.http.get<Wrapped<Row[]>>(`${this.base}/nns/domains?limit=${o.limit ?? 200}&offset=${o.offset ?? 0}`);
    return (r?.data ?? []).map(d => ({ domain: d.domain, address: d.address }));
  }

  /** Latest NEP-17 transfers across the chain, newest first, optionally for one token. Not cached unless `cache` is true. */
  async recentTransfers(o: { contract?: string; limit?: number; cache?: boolean } = {}): Promise<Transfer[]> {
    const filter = o.contract ? `&contract_hash=eq.${o.contract}` : '';
    const r = await this.http.get<Row[]>(`${this.root}/rest/v1/nep17_transfers?network=eq.${this.network}&order=block_index.desc&limit=${o.limit ?? 200}${filter}`, { cache: o.cache ?? false });
    return (r ?? []).map(toTransfer);
  }
}
