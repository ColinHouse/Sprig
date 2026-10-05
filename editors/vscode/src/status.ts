import * as vscode from 'vscode';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { projectRoot } from './compiler';
import type { Queries, Runner } from './queries';

/** Language status item: compiler version and, inside a project, the lock state. */
export function registerStatus(context: vscode.ExtensionContext, queries: Queries, run: Runner): { refresh(): Promise<void> } {
  const item = vscode.languages.createLanguageStatusItem('sprig.status', { language: 'sprig' });
  item.name = 'Sprig';
  item.text = 'Sprig';
  item.command = { title: 'Actions', command: 'sprig.showActions' };
  context.subscriptions.push(item);
  let generation = 0;

  async function refresh(): Promise<void> {
    const ticket = ++generation, doc = vscode.window.activeTextEditor?.document;
    item.busy = false;
    if (!doc || doc.languageId !== 'sprig' || doc.uri.scheme !== 'file') return;
    if (!vscode.workspace.isTrusted) {
      item.text = 'Sprig'; item.detail = 'Restricted Mode: compiler features are off';
      item.severity = vscode.LanguageStatusSeverity.Information;
      return;
    }
    item.busy = true;
    try {
      const root = projectRoot(doc.uri.fsPath), home = await queries.home(root);
      let detail = home ? `compiler ${home.compilerVersion ?? 'unknown'}` : 'compiler not found; set sprig.compilerPath';
      let severity = home ? vscode.LanguageStatusSeverity.Information : vscode.LanguageStatusSeverity.Error;
      if (home && fs.existsSync(path.join(root, 'sprig.toml'))) {
        const project = await run(['project', '--json'], root).catch(() => undefined);
        const lock = project?.exitCode === 0 ? project.project?.lockStatus : undefined;
        if (lock) detail += ` · lock ${lock}`;
        if (lock && lock !== 'current') severity = vscode.LanguageStatusSeverity.Warning;
      }
      if (ticket !== generation) return;
      item.text = home?.compilerVersion ? `Sprig ${home.compilerVersion}` : 'Sprig';
      item.detail = detail;
      item.severity = severity;
    } finally {
      if (ticket === generation) item.busy = false;
    }
  }

  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(() => void refresh()),
    vscode.workspace.onDidSaveTextDocument(doc => { if (/sprig\.(toml|lock)$/.test(doc.fileName)) void refresh(); }),
    vscode.workspace.onDidGrantWorkspaceTrust(() => void refresh()),
  );
  void refresh();
  return { refresh };
}
