/** Wrapper that lets a cache tell a stored `null` apart from a miss. */
export interface CacheHit { value: unknown }

/** A store for JSON responses, keyed by request. */
export interface JsonCache {
  get(key: string): CacheHit | undefined;
  set(key: string, value: unknown): void;
}

/** A cache that lives only as long as the process or page. */
export function memoryCache(): JsonCache {
  const entries = new Map<string, unknown>();
  return {
    get: key => (entries.has(key) ? { value: entries.get(key) } : undefined),
    set: (key, value) => { entries.set(key, value); },
  };
}

/** The part of the Web Storage API a `storageCache` needs, e.g. `sessionStorage`. */
export interface KeyValueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/** A cache in Web Storage under `prefix`. Entries that do not fit are skipped, never thrown. */
export function storageCache(storage: KeyValueStorage, prefix = 'n3:'): JsonCache {
  return {
    get(key) {
      try {
        const text = storage.getItem(prefix + key);
        return text === null ? undefined : { value: JSON.parse(text) };
      } catch {
        return undefined;
      }
    },
    set(key, value) {
      try {
        storage.setItem(prefix + key, JSON.stringify(value));
      } catch {
        return;
      }
    },
  };
}
