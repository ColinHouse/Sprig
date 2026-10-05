import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import type { CompilerResult } from './compiler';

/** Formats text with `sprig fmt` on a private temporary copy; undefined when the compiler rejects it. */
export async function formatSource(text: string, run: (args: string[], cwd: string) => Promise<CompilerResult>): Promise<string | undefined> {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'sprig-fmt-'));
  try {
    const file = path.join(dir, 'source.spr');
    await fs.writeFile(file, text, 'utf8');
    const result = await run(['fmt', file, '--json'], dir);
    return result.exitCode === 0 ? await fs.readFile(file, 'utf8') : undefined;
  } catch {
    return undefined;
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}
