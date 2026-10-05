import * as fs from 'node:fs';
import * as path from 'node:path';
import type { CompilerResult } from './compiler';
import type { HelpJson, JavaApi, ModuleApi } from './markdown';

/** Runs a read-only compiler query in `cwd`; `root` selects the configured compiler when they differ. */
export type Runner = (args: string[], cwd: string, root?: string) => Promise<CompilerResult>;
export interface Home { compilerHome: string; compilerVersion?: string }

/** Cached compiler lookups for hover, completion and navigation; failures resolve to undefined. */
export class Queries {
  private readonly cache = new Map<string, Promise<unknown>>();
  constructor(private readonly run: Runner) {}

  clear(): void { this.cache.clear(); }

  private ask<T>(key: string, args: string[], cwd: string, valid: (json: CompilerResult) => boolean): Promise<T | undefined> {
    const hit = this.cache.get(key);
    if (hit) return hit as Promise<T | undefined>;
    if (this.cache.size > 300) this.cache.clear();
    const pending = this.run(args, cwd).then(json => json.exitCode === 0 && valid(json) ? json as unknown as T : undefined, () => undefined);
    this.cache.set(key, pending);
    // Retry failed lookups after a pause instead of spawning a compiler on every keystroke.
    void pending.then(value => { if (value === undefined) setTimeout(() => this.cache.delete(key), 5000).unref?.(); });
    return pending;
  }

  help(topic: string, cwd: string) {
    return this.ask<HelpJson>(`help ${topic}`, ['help', topic, '--json'], cwd, json => typeof json.topic === 'string');
  }
  javaClass(name: string, cwd: string) {
    return this.ask<JavaApi>(`java ${cwd} ${name}`, ['api', name, '--json'], cwd, json => typeof json.className === 'string');
  }
  /** A .spr file, keyed by its modification time so saved edits are picked up. */
  module(file: string, cwd: string) {
    const stamp = mtime(file);
    if (stamp === undefined) return Promise.resolve(undefined);
    return this.ask<ModuleApi>(`module ${cwd} ${file} ${stamp}`, ['api', file, '--json'], cwd, json => json.kind === 'sprig-module');
  }
  /** `@dependency/module.spr` inside a project, keyed by the lock file. */
  dependency(spec: string, root: string) {
    const key = `dependency ${root} ${spec} ${mtime(path.join(root, 'sprig.lock')) ?? 0}`;
    return this.ask<ModuleApi>(key, ['api', spec, '--json'], root, json => json.kind === 'sprig-module');
  }
  home(cwd: string) {
    return this.ask<Home>(`doctor ${cwd}`, ['doctor', '--json'], cwd, json => typeof json.compilerHome === 'string');
  }
}

function mtime(file: string): number | undefined {
  try { return fs.statSync(file).mtimeMs; } catch { return undefined; }
}
