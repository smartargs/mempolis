import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import type { JsonCache } from './cache.ts';

function fileName(key: string): string {
  return `${createHash('sha256').update(key).digest('hex').slice(0, 40)}.json`;
}

/** A cache that keeps every response as a JSON file under `dir`, forever. Delete the folder to refresh. Node only. */
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
