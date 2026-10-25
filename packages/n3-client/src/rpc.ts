import { addressToScriptHash } from './address.ts';
import { base64ToBytes, bytesToHex } from './bytes.ts';
import { HttpError } from './http.ts';
import type { Transport } from './transport.ts';
import { NATIVE, type Candidate, type NeoAccountState, type Nep11Holding, type TokenBalance, type Transfer } from './types.ts';

type Row = Record<string, any>;
interface RpcReply<T> { result?: T; error?: { code: number; message: string; data?: string } }
interface StackItem { type: string; value?: any }

function stackHex(item: StackItem | undefined): string | null {
  if (!item || item.type === 'Any' || item.value == null) return null;
  return bytesToHex(base64ToBytes(String(item.value)));
}

/** JSON-RPC access to a Neo N3 node (neo-go method set). */
export class NeoRpc {
  private readonly http: Transport;
  private readonly url: string;

  constructor(http: Transport, url = 'https://rpc10.n3.nspcc.ru:10331') {
    this.http = http;
    this.url = url;
  }

  /** Call `method` with `params`; cached like any other response unless `cache` is false. */
  async call<T>(method: string, params: unknown[], o: { cache?: boolean } = {}): Promise<T> {
    const reply = await this.http.post<RpcReply<T>>(this.url, { jsonrpc: '2.0', id: 1, method, params }, o);
    if (!reply || reply.error || reply.result === undefined) throw new HttpError(this.url, 200, `${method}: ${reply?.error?.message ?? 'no result'} ${reply?.error?.data ?? ''}`.trim());
    return reply.result;
  }

  /** Unclaimed GAS of an address, in raw units (8 decimals). */
  async unclaimedGas(address: string): Promise<string> {
    return String((await this.call<Row>('getunclaimedgas', [address])).unclaimed ?? '0');
  }

  /** NEP-17 balances as the node tracks them. */
  async nep17Balances(address: string): Promise<TokenBalance[]> {
    const r = await this.call<Row>('getnep17balances', [address]);
    return (r.balance ?? []).map((b: Row) => ({ contract: b.assethash, amountRaw: String(b.amount) }));
  }

  /** NEP-11 tokens held, per contract; contracts with no tokens left are dropped. */
  async nep11Balances(address: string): Promise<Nep11Holding[]> {
    const r = await this.call<Row>('getnep11balances', [address]);
    return (r.balance ?? [])
      .map((b: Row) => ({ contract: b.assethash, symbol: b.symbol ?? '', tokens: (b.tokens ?? []).map((t: Row) => ({ tokenId: t.tokenid, amount: String(t.amount) })).filter((t: { amount: string }) => t.amount !== '0') }))
      .filter((h: Nep11Holding) => h.tokens.length > 0);
  }

  /** NEP-17 transfers between two timestamps (milliseconds), from the node's own transfer log. */
  async nep17Transfers(address: string, fromMs: number, toMs: number): Promise<Transfer[]> {
    const r = await this.call<Row>('getnep17transfers', [address, fromMs, toMs]);
    const row = (t: Row, sent: boolean): Transfer => ({
      txid: t.txhash, blockIndex: t.blockindex, timestampMs: t.timestamp, contract: t.assethash,
      from: sent ? address : t.transferaddress ?? '', to: sent ? t.transferaddress ?? '' : address, amountRaw: String(t.amount),
    });
    return [...(r.sent ?? []).map((t: Row) => row(t, true)), ...(r.received ?? []).map((t: Row) => row(t, false))].sort((a, b) => a.timestampMs - b.timestampMs);
  }

  /** NEO account state including the candidate the address votes for, or `null` if it never held NEO. */
  async neoAccountState(address: string): Promise<NeoAccountState | null> {
    const hash = addressToScriptHash(address);
    if (!hash) throw new HttpError(this.url, 0, 'invalid address');
    const r = await this.call<Row>('invokefunction', [NATIVE.neo, 'getAccountState', [{ type: 'Hash160', value: hash }]]);
    const item: StackItem | undefined = r.stack?.[0];
    if (!item || item.type !== 'Struct') return null;
    const [balance, height, voteTo] = item.value as StackItem[];
    return { balance: String(balance.value ?? '0'), balanceHeight: Number(height.value ?? 0), voteTo: stackHex(voteTo) };
  }

  /** Every registered council candidate. */
  async candidates(): Promise<Candidate[]> {
    const r = await this.call<Row[]>('getcandidates', []);
    return r.map(c => ({ publicKey: c.publickey, votes: String(c.votes), active: !!c.active }));
  }
}
