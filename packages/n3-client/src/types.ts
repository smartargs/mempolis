/** Well-known Neo N3 native contract hashes. */
export const NATIVE = {
  neo: '0xef4073a0f2b305a38ec4050e4d3d28bc40ea63f5',
  gas: '0xd2a4cff31913016155e38e474a2c06d08be276cf',
  contractManagement: '0xfffdc93764dbaddd97c48f252a53ea4643faa3fd',
  oracle: '0xfe924b7cfe89ddd271abaf7210a80a7e11178758',
} as const;

/** The Neo Name Service (.neo) NEP-11 contract on mainnet. */
export const NNS_CONTRACT = '0x50ac1c37690cc2cfc594472833cf57505d5f46de';

/** Indexer freshness. */
export interface IndexStatus { lastIndexedBlock: number; chainTipBlock: number; updatedAtMs: number }

/** Account overview from the indexer. Raw amounts are integer strings in token units before decimals. */
export interface AccountSummary {
  address: string;
  txSent: number;
  txSigned: number;
  nep17SentRaw: string;
  nep17ReceivedRaw: string;
  nep11SentEvents: number;
  nep11ReceivedEvents: number;
  contractsInteracted: number;
  firstTxMs: number;
  lastTxMs: number;
}

/** What the indexer knows about who runs an address. */
export interface AccountIdentity {
  category: string;
  status: string;
  name: string;
  tags: string[];
  confidence: number;
  /** Behaviour signal codes, e.g. `recurrent-counterparty`. */
  signals: string[];
}

/** A token balance in raw units. */
export interface TokenBalance { contract: string; amountRaw: string }

/** One NEP-17 or NEP-11 transfer. `from` is empty for mints, `to` for burns. */
export interface Transfer {
  txid: string;
  blockIndex: number;
  timestampMs: number;
  contract: string;
  from: string;
  to: string;
  amountRaw: string;
}

/** Kinds of entries in an account's activity feed. */
export type ActivityKind = 'transaction' | 'nep17_transfer' | 'nep11_transfer';

/** One entry of an account's oldest-first activity feed. */
export interface ActivityEvent {
  kind: ActivityKind | string;
  direction: 'sent' | 'received' | 'self' | string;
  timestampMs: number;
  blockIndex: number;
  txid: string;
  contract?: string;
  from?: string;
  to?: string;
  amountRaw?: string;
  symbol?: string;
}

/** A whole activity feed; `complete` is false when it was cut at `maxPages`. */
export interface ActivityFeed { events: ActivityEvent[]; complete: boolean }

/** A transaction an account sent or signed. */
export interface AccountTransaction { txid: string; blockIndex: number; timestampMs: number; sender: string }

/** A holder of a token, by balance. */
export interface Holder { address: string; balanceRaw: string }

/** A page of results with the total when the API reports one. */
export interface Page<T> { items: T[]; total?: number }

/** An account voting for a candidate, by script hash, with its NEO weight. */
export interface Voter { scriptHash: string; votes: string }

/** A council candidate as reported by the RPC node. */
export interface Candidate { publicKey: string; votes: string; active: boolean }

/** NEO account state: balance, the block of its last change and the candidate it votes for. */
export interface NeoAccountState { balance: string; balanceHeight: number; voteTo: string | null }

/** NEP-11 tokens held under one contract. */
export interface Nep11Holding { contract: string; symbol: string; tokens: { tokenId: string; amount: string }[] }

/** A Neo VM stack item as the node serialises it to JSON. */
export interface StackItemJson { type: string; value?: unknown }

/** One contract notification (event) emitted while a transaction ran. */
export interface ContractNotification {
  txid: string;
  blockIndex: number;
  timestampMs: number;
  contract: string;
  event: string;
  /** The event's arguments; byte strings are base64. */
  state: StackItemJson[];
}
