import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

/** Wrapper that lets a cache tell a stored `null` apart from a miss. */
export interface CacheHit { value: unknown }

/** A store for JSON responses, keyed by request. */
export interface JsonCache {
  get(key: string): CacheHit | undefined;
  set(key: string, value: unknown): void;
}

function fileName(key: string): string {
  return `${createHash('sha256').update(key).digest('hex').slice(0, 40)}.json`;
}

/** A cache that keeps every response as a JSON file under `dir`, forever. Delete the folder to refresh. */
export function diskCache(dir: string): JsonCache {
  mkdirSync(dir, { recursive: true });
  return {
    get(key) {
      try {
        const stored = JSON.parse(readFileSync(join(dir, fileName(key)), 'utf8')) as { key: string; value: unknown };
        return stored.key === key ? { value: stored.value } : undefined;
      } catch {
        return undefined;
      }
    },
    set(key, value) {
      const path = join(dir, fileName(key)), tmp = `${path}.tmp`;
      writeFileSync(tmp, JSON.stringify({ key, value }));
      renameSync(tmp, path);
    },
  };
}

/** A cache that lives only as long as the process. */
export function memoryCache(): JsonCache {
  const entries = new Map<string, unknown>();
  return {
    get: key => (entries.has(key) ? { value: entries.get(key) } : undefined),
    set: (key, value) => { entries.set(key, value); },
  };
}
