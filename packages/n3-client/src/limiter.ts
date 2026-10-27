import { wait } from './wait.ts';

/** Runs tasks with at most `concurrency` in flight and at least `spacingMs` between starts. */
export class Limiter {
  private active = 0;
  private nextStart = 0;
  private readonly queue: (() => void)[] = [];

  private readonly concurrency: number;
  private readonly spacingMs: number;

  constructor(concurrency: number, spacingMs: number) {
    this.concurrency = concurrency;
    this.spacingMs = spacingMs;
  }

  /** Run `task` once a slot is free. */
  async run<T>(task: () => Promise<T>): Promise<T> {
    await this.acquire();
    try {
      return await task();
    } finally {
      this.release();
    }
  }

  private async acquire() {
    if (this.active >= this.concurrency) await new Promise<void>(resolve => this.queue.push(resolve));
    else this.active++;
    const now = Date.now(), start = Math.max(now, this.nextStart);
    this.nextStart = start + this.spacingMs;
    if (start > now) await wait(start - now);
  }

  private release() {
    const next = this.queue.shift();
    if (next) next(); else this.active--;
  }
}
