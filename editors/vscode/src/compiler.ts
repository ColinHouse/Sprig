import { ChildProcess, spawn } from 'node:child_process';
import * as fs from 'node:fs';
import * as path from 'node:path';

export interface Point { line: number; character: number }
export interface SourceRange { start: Point; end: Point }
export interface CompilerDiagnostic {
  code: string; severity: string; message: string; uri: string | null;
  range: SourceRange | null; expectedType?: string; actualType?: string; hint?: string;
  relatedHelp?: string;
}
export interface CompilerResult {
  schemaVersion: number; exitCode: number; diagnostics?: CompilerDiagnostic[];
  programOutput?: string; javaSources?: string[]; javacInvoked?: boolean;
  project?: { root: string; entry: string; name?: string; lockStatus?: string };
  [key: string]: unknown;
}
export interface Invocation { json: CompilerResult; stderr: string }
export interface Captured { code: number | null; stdout: string; stderr: string }
export interface RunOptions { signal?: AbortSignal; timeoutMs?: number; maxOutput?: number }

// Query commands print a JSON document without exitCode when they succeed.
const QUERIES = ['capabilities', 'explain', 'api', 'help', 'doctor'];

export function projectRoot(file: string): string {
  for (let dir = path.dirname(file); ; dir = path.dirname(dir)) {
    if (fs.existsSync(path.join(dir, 'sprig.toml'))) return dir;
    if (path.dirname(dir) === dir) return path.dirname(file);
  }
}

/** A configured launcher wins; otherwise PATH, then a built source ancestor. */
export function resolveCompiler(configured: string, cwd: string): string {
  if (configured.trim()) return path.resolve(cwd, configured.trim());
  const name = process.platform === 'win32' ? 'sprig.cmd' : 'sprig';
  for (const dir of (process.env.PATH ?? '').split(path.delimiter)) {
    if (!dir) continue;
    const candidate = path.join(dir, name);
    if (fs.existsSync(candidate)) return candidate;
  }
  for (let dir = cwd; ; dir = path.dirname(dir)) {
    const candidate = path.join(dir, 'bin', name);
    if (fs.existsSync(candidate)) return candidate;
    if (dir === path.dirname(dir)) break;
  }
  throw new Error('Sprig compiler not found. Install the SDK and set sprig.compilerPath to its bin/sprig launcher.');
}

/**
 * Shared direct-process plan for finite JSON commands and integrated terminals.
 * On Windows the JVM is started with the classpath bin/sprig.cmd would use, so
 * no argument passes through cmd.exe quoting.
 */
export function compilerCommand(executable: string, args: string[],
  platform: NodeJS.Platform = process.platform): { command: string; args: string[] } {
  if (platform === 'win32' && /\.(cmd|bat)$/i.test(executable)) {
    const home = path.dirname(path.dirname(executable));
    let classpath: string[];
    if (fs.existsSync(path.join(home, 'lib', 'sprig-compiler.jar'))) {
      classpath = [path.join(home, 'lib', '*')]; // extracted release SDK
    } else if (fs.existsSync(path.join(home, 'build', 'sprig-compiler.jar'))) {
      classpath = [path.join(home, 'build', 'sprig-compiler.jar'), // source checkout build
        path.join(home, 'build', 'deps', 'antlr-4.13.2-complete.jar'), path.join(home, 'build', 'deps', 'resolver', '*')];
    } else {
      throw new Error('Windows preview requires the SDK bin/sprig.cmd launcher; arbitrary batch wrappers are unsupported.');
    }
    return {command: 'java', args: ['-Dfile.encoding=UTF-8', '-cp', classpath.join(';'),
      `-Dsprig.home=${home}`, 'sprig.compiler.cli.Main', ...args]};
  }
  return {command: executable, args};
}

/**
 * Windows has no process groups: ending the compiler JVM alone would leave the
 * program JVM it started running, so stop the whole tree.
 */
function killTree(child: ChildProcess): void {
  spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], {windowsHide: true, stdio: 'ignore'})
    .on('error', () => { try { child.kill(); } catch { /* exited */ } });
}

/** Runs one compiler process with the shared cancel, timeout and output limits. */
export function capture(executable: string, args: string[], cwd: string, options: RunOptions = {}): Promise<Captured> {
  if (options.signal?.aborted) return Promise.reject(new Error('Sprig command canceled.'));
  let invocation: ReturnType<typeof compilerCommand>;
  try { invocation = compilerCommand(executable, args); }
  catch (error) { return Promise.reject(error); }
  return new Promise((resolve, reject) => {
    const child = spawn(invocation.command, invocation.args, {cwd, shell: false, windowsHide: true, detached: process.platform !== 'win32'});
    child.stdin.end(); // Finite non-interactive Run: never wait for hidden input.
    let stdout = '', stderr = '', size = 0, failure: Error | undefined;
    const stop = (error: Error) => {
      failure ??= error;
      if (child.pid) {
        try { if (process.platform === 'win32') killTree(child); else process.kill(-child.pid, 'SIGKILL'); } catch { /* exited */ }
      }
    };
    const abort = () => stop(new Error('Sprig command canceled.'));
    options.signal?.addEventListener('abort', abort, {once:true});
    const timer = setTimeout(() => stop(new Error('Sprig command timed out. Adjust sprig.commandTimeoutSeconds if needed.')), options.timeoutMs ?? 120000);
    child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
    child.stdout.on('data', (s: string) => {stdout += s; size += Buffer.byteLength(s); if(size > (options.maxOutput ?? 8*1024*1024)) stop(new Error('Sprig output exceeded the 8 MB limit.'));});
    child.stderr.on('data', (s: string) => {stderr += s; size += Buffer.byteLength(s); if(size > (options.maxOutput ?? 8*1024*1024)) stop(new Error('Sprig output exceeded the 8 MB limit.'));});
    const cleanup = () => {clearTimeout(timer); options.signal?.removeEventListener('abort', abort);};
    child.on('error', error => {cleanup(); reject(new Error(`Cannot start Sprig compiler (${executable}): ${error.message}`));});
    child.on('close', (code, signal) => {
      cleanup();
      if(failure) return reject(failure);
      if(signal) return reject(new Error(`Sprig compiler terminated by ${signal}.`));
      resolve({code, stdout, stderr});
    });
    // Handle an abort racing with spawn/listener registration.
    if(options.signal?.aborted) abort();
  });
}

export async function invoke(executable: string, args: string[], cwd: string, options: RunOptions = {}): Promise<Invocation> {
  const {code, stdout, stderr} = await capture(executable, args, cwd, options);
  let json: CompilerResult;
  try { json = JSON.parse(stdout); } catch {
    throw new Error(`Sprig did not return JSON (exit ${code}). ${stderr || stdout}`.slice(0,2000));
  }
  if(!json || typeof json !== 'object' || Array.isArray(json)) throw new Error('Invalid Sprig JSON response: expected an object.');
  if(json.exitCode === undefined && QUERIES.includes(args[0]) && Number.isInteger(code)) json.exitCode = code!;
  if(json.schemaVersion !== 1 || !Number.isInteger(json.exitCode) || json.exitCode !== code) {
    throw new Error('Unsupported or inconsistent Sprig JSON response; compiler 0.4.0-alpha.1+ is required.');
  }
  if(json.diagnostics !== undefined && !Array.isArray(json.diagnostics)) throw new Error('Invalid Sprig diagnostics response.');
  return {json, stderr};
}
