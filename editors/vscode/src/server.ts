import * as vscode from 'vscode';
import {
  CloseAction, CloseHandlerResult, ErrorAction, ExecutableOptions, LanguageClient, RevealOutputChannelOn,
} from 'vscode-languageclient/node';
import { compilerCommand, invoke, projectRoot, resolveCompiler, supportsLanguageServer } from './compiler';

/** Why this window does or does not have a running `sprig lsp`. */
export type ServerState = 'off' | 'starting' | 'running' | 'unsupported' | 'failed';

const RESTARTS = 4;
const RESTART_WINDOW_MS = 3 * 60 * 1000;

const sprigFile = (doc: vscode.TextDocument) => doc.languageId === 'sprig' && doc.uri.scheme === 'file';

/** Short text for the language status item. */
export function stateLabel(state: ServerState): string {
  return {
    off: 'language server off', starting: 'starting language server', running: 'language server',
    unsupported: 'no language server in this compiler', failed: 'language server stopped',
  }[state];
}

/** What to tell someone who asked for the server and did not get it. */
export function stateAdvice(state: ServerState): string {
  return {
    off: 'The Sprig language server is off. It runs in a trusted workspace with a Sprig file open, while sprig.languageServer.enabled is on.',
    starting: 'The Sprig language server is starting.',
    running: 'The Sprig language server is running.',
    unsupported: 'This Sprig compiler has no language server (sprig lsp). Install a newer SDK or build the compiler from source; until then, Sprig features use separate compiler commands.',
    failed: 'The Sprig language server did not start or kept stopping. See the Sprig Language Server output.',
  }[state];
}

/**
 * One `sprig lsp` per window, started with the compiler of the active Sprig
 * file or the first workspace folder. While it runs it answers diagnostics as
 * you type, hover, completion, navigation, references, rename and formatting.
 */
export class LanguageServer implements vscode.Disposable {
  private client: LanguageClient | undefined;
  private current: ServerState = 'off';
  private ticket = 0;
  private waiting = false;
  private crashes: number[] = [];
  private readonly changed = new vscode.EventEmitter<ServerState>();
  private readonly channel = vscode.window.createOutputChannel('Sprig Language Server', { log: true });
  private readonly subscriptions: vscode.Disposable[];
  readonly onDidChangeState = this.changed.event;

  /** `inspect` sees every published diagnostic before VS Code shows it. */
  constructor(private readonly inspect: (diagnostic: vscode.Diagnostic) => void) {
    this.subscriptions = [
      vscode.workspace.onDidGrantWorkspaceTrust(() => void this.start()),
      vscode.workspace.onDidOpenTextDocument(doc => { if (this.waiting && sprigFile(doc)) void this.start(); }),
      vscode.workspace.onDidChangeConfiguration(event => {
        if (event.affectsConfiguration('sprig.languageServer') || event.affectsConfiguration('sprig.compilerPath')) void this.start();
      }),
    ];
  }

  get state(): ServerState { return this.current; }

  /** Starts the server if it is enabled, trusted and supported, replacing a running one. */
  async start(): Promise<ServerState> {
    const ticket = ++this.ticket;
    this.waiting = false;
    await this.halt();
    if (ticket !== this.ticket) return this.current;
    if (!vscode.workspace.getConfiguration('sprig').get<boolean>('languageServer.enabled', true) || !vscode.workspace.isTrusted) {
      return this.set('off');
    }
    const anchor = anchorOf();
    if (!anchor) { this.waiting = true; return this.set('off'); }
    let executable: string;
    try { executable = resolveCompiler(vscode.workspace.getConfiguration('sprig', anchor.uri).get<string>('compilerPath', ''), anchor.cwd); }
    catch (error) { this.channel.warn(String(error instanceof Error ? error.message : error)); return this.set('unsupported'); }
    this.set('starting');
    // Only the capability tells: a development build reports the same version as the release before it.
    const supported = await invoke(executable, ['capabilities', '--json'], anchor.cwd, { timeoutMs: 60000 })
      .then(result => supportsLanguageServer(result.json), () => false);
    if (ticket !== this.ticket) return this.current;
    if (!supported) {
      this.channel.info(`${executable} has no language server (sprig lsp); Sprig features use separate compiler commands.`);
      return this.set('unsupported');
    }
    const plan = compilerCommand(executable, ['lsp']);
    const options: ExecutableOptions & { windowsHide: boolean } = { cwd: anchor.cwd, windowsHide: true };
    const client: LanguageClient = new LanguageClient('sprig', 'Sprig Language Server', { command: plan.command, args: plan.args, options }, {
      documentSelector: [{ scheme: 'file', language: 'sprig' }],
      diagnosticCollectionName: 'sprig-lsp',
      outputChannel: this.channel,
      revealOutputChannelOn: RevealOutputChannelOn.Never,
      initializationFailedHandler: () => false,
      errorHandler: {
        error: () => ({ action: ErrorAction.Continue }),
        closed: () => this.closed(client),
      },
      middleware: {
        handleDiagnostics: (uri, diagnostics, next) => { diagnostics.forEach(this.inspect); next(uri, diagnostics); },
      },
    });
    this.client = client;
    this.crashes = [];
    try {
      await client.start();
    } catch (error) {
      if (this.client === client) this.client = undefined;
      void client.dispose().catch(() => undefined);
      if (ticket !== this.ticket) return this.current;
      this.channel.error(`The language server did not start: ${error instanceof Error ? error.message : String(error)}`);
      return this.set('failed');
    }
    if (ticket !== this.ticket || this.client !== client) return this.current;
    this.channel.info(`Started ${executable} lsp in ${anchor.cwd}.`);
    return this.set('running');
  }

  /** Stops the server; Sprig features fall back to separate compiler commands. */
  async stop(): Promise<void> {
    this.ticket++;
    this.waiting = false;
    await this.halt();
    this.set('off');
  }

  dispose(): void {
    for (const subscription of this.subscriptions) subscription.dispose();
    void this.stop().finally(() => { this.changed.dispose(); this.channel.dispose(); });
  }

  private async halt(): Promise<void> {
    const client = this.client;
    this.client = undefined;
    if (client) await client.dispose().catch(() => undefined);
  }

  private closed(client: LanguageClient): CloseHandlerResult {
    if (client !== this.client) return { action: CloseAction.DoNotRestart, handled: true };
    const now = Date.now();
    this.crashes = this.crashes.filter(time => now - time < RESTART_WINDOW_MS);
    this.crashes.push(now);
    if (this.crashes.length <= RESTARTS) {
      this.channel.warn('The language server stopped unexpectedly; restarting it.');
      return { action: CloseAction.Restart, handled: true };
    }
    this.channel.error(`The language server stopped ${this.crashes.length} times in 3 minutes. Sprig features use separate compiler commands; run "Sprig: Restart Language Server" to try again.`);
    this.client = undefined;
    setTimeout(() => void client.dispose().catch(() => undefined), 0);
    this.set('failed');
    return { action: CloseAction.DoNotRestart, handled: true };
  }

  private set(state: ServerState): ServerState {
    if (state !== this.current) { this.current = state; this.changed.fire(state); }
    return state;
  }
}

/** The file or folder whose settings choose the compiler, and the server's working directory. */
function anchorOf(): { uri: vscode.Uri; cwd: string } | undefined {
  const active = vscode.window.activeTextEditor?.document;
  const doc = active && sprigFile(active) ? active : vscode.workspace.textDocuments.find(sprigFile);
  if (doc) return { uri: doc.uri, cwd: projectRoot(doc.uri.fsPath) };
  const folder = vscode.workspace.workspaceFolders?.find(f => f.uri.scheme === 'file');
  return folder && { uri: folder.uri, cwd: folder.uri.fsPath };
}
