import * as fs from 'node:fs';
import * as path from 'node:path';
import type { CompilerDiagnostic, CompilerResult } from './compiler';

export interface TestRecord {
  name: string; path: string; mode: string; status: string; failure?: string;
  diagnostics?: CompilerDiagnostic[]; programOutput?: string; programErrorOutput?: string;
}

/** Resolves symlinks (such as /var → /private/var) so compiler and editor paths compare equal. */
export function canonical(file: string): string {
  try { return fs.realpathSync.native(file); } catch { return path.resolve(file); }
}

/** Per-file outcomes of `sprig test --json`, keyed by canonical path. */
export function outcomes(result: CompilerResult): Map<string, TestRecord> {
  const map = new Map<string, TestRecord>();
  for (const test of (result.tests as TestRecord[] | undefined) ?? []) map.set(canonical(test.path), test);
  return map;
}

/** A test file's name relative to the project's tests directory, with forward slashes. */
export function testLabel(root: string, file: string): string {
  return path.relative(path.join(root, 'tests'), file).split(path.sep).join('/');
}
