import * as vscode from 'vscode';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import type { CompilerDiagnostic, CompilerResult } from './compiler';
import { mapRange } from './diagnostics';
import { TestRecord, canonical, outcomes, testLabel } from './testResults';

export type TestRunner = (args: string[], cwd: string, signal: AbortSignal) => Promise<CompilerResult>;
export interface Testing { refresh(): Promise<void>; runProject(root: string): Promise<CompilerResult | undefined> }

const EXCLUDE = '{**/node_modules/**,**/sprig-build/**,**/build/**,**/.git/**}';
const list = (items: vscode.TestItemCollection) => { const out: vscode.TestItem[] = []; items.forEach(item => out.push(item)); return out; };

async function message(d: CompilerDiagnostic, fallback: vscode.Uri): Promise<vscode.TestMessage> {
  const result = new vscode.TestMessage(`${d.code}: ${d.message}${d.hint ? `\n${d.hint}` : ''}`);
  const uri = d.uri ? vscode.Uri.parse(d.uri) : fallback;
  if (d.range) {
    let range = d.range;
    try { range = mapRange(d.range, await fs.readFile(uri.fsPath, 'utf8')); } catch { /* keep compiler columns */ }
    result.location = new vscode.Location(uri, new vscode.Range(range.start.line, range.start.character, range.end.line, range.end.character));
  }
  return result;
}

/** Testing view for `tests/**` programs in every Sprig project of the workspace. */
export function registerTesting(context: vscode.ExtensionContext, runner: TestRunner): Testing {
  const controller = vscode.tests.createTestController('sprig', 'Sprig');
  context.subscriptions.push(controller);

  async function refresh(): Promise<void> {
    const projects: vscode.TestItem[] = [];
    for (const manifest of await vscode.workspace.findFiles('**/sprig.toml', EXCLUDE, 200)) {
      const root = canonical(path.dirname(manifest.fsPath));
      const project = controller.createTestItem(root, path.basename(root), vscode.Uri.file(root));
      const files = await vscode.workspace.findFiles(new vscode.RelativePattern(root, 'tests/**/*.spr'), EXCLUDE, 2000);
      for (const file of files.sort((a, b) => a.fsPath.localeCompare(b.fsPath))) {
        const id = canonical(file.fsPath), label = testLabel(root, id);
        const item = controller.createTestItem(id, label, file);
        if (label.startsWith('compile_fail/')) item.description = 'expected compile failure';
        project.children.add(item);
      }
      if (project.children.size) projects.push(project);
    }
    controller.items.replace(projects);
  }

  async function report(run: vscode.TestRun, item: vscode.TestItem, test: TestRecord | undefined): Promise<void> {
    if (!test) { run.skipped(item); return; }
    const output = (test.programOutput ?? '') + (test.programErrorOutput ?? '');
    if (output) run.appendOutput(output.replace(/\r?\n/g, '\r\n'), undefined, item);
    if (test.status === 'passed') { run.passed(item); return; }
    const messages = await Promise.all((test.diagnostics ?? []).map(d => message(d, item.uri!)));
    if (test.failure) messages.unshift(new vscode.TestMessage(test.failure));
    if (!messages.length) messages.push(new vscode.TestMessage('Test failed.'));
    run.failed(item, messages);
  }

  async function execute(request: vscode.TestRunRequest, token: vscode.CancellationToken): Promise<CompilerResult[]> {
    const run = controller.createTestRun(request), results: CompilerResult[] = [];
    const excluded = new Set((request.exclude ?? []).map(item => item.id));
    const abort = new AbortController(), cancel = token.onCancellationRequested(() => abort.abort());
    try {
      for (const item of request.include ?? list(controller.items)) {
        if (token.isCancellationRequested) break;
        if (excluded.has(item.id)) continue;
        const files = item.parent ? [item] : list(item.children).filter(file => !excluded.has(file.id));
        if (!vscode.workspace.isTrusted) { for (const file of files) run.skipped(file); continue; }
        for (const file of files) run.started(file);
        try {
          const result = await runner(item.parent ? ['test', item.id, '--json'] : ['test', '--json'], (item.parent ?? item).id, abort.signal);
          results.push(result);
          const map = outcomes(result);
          for (const file of files) await report(run, file, map.get(file.id));
        } catch (error) {
          const failure = new vscode.TestMessage(error instanceof Error ? error.message : String(error));
          for (const file of files) run.errored(file, failure);
        }
      }
    } finally {
      cancel.dispose();
      run.end();
    }
    return results;
  }

  controller.createRunProfile('Run', vscode.TestRunProfileKind.Run, (request, token) => { void execute(request, token); }, true);
  controller.resolveHandler = async item => { if (!item) await refresh(); };
  const watcher = vscode.workspace.createFileSystemWatcher('**/{sprig.toml,*.spr}');
  let timer: NodeJS.Timeout | undefined;
  const later = () => { clearTimeout(timer); timer = setTimeout(() => void refresh(), 500); };
  context.subscriptions.push(watcher, watcher.onDidCreate(later), watcher.onDidDelete(later), { dispose: () => clearTimeout(timer) });
  void refresh();

  return {
    refresh,
    async runProject(root: string) {
      await refresh();
      const project = list(controller.items).find(item => item.id === canonical(root));
      if (!project) return undefined;
      const source = new vscode.CancellationTokenSource();
      try { return (await execute(new vscode.TestRunRequest([project]), source.token))[0]; }
      finally { source.dispose(); }
    },
  };
}
