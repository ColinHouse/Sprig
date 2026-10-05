import * as vscode from 'vscode';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { createHash } from 'node:crypto';
import { CompilerResult, capture, compilerCommand, invoke, projectRoot, resolveCompiler } from './compiler';
import { mapRange } from './diagnostics';
import { DIAGNOSTIC_DOCS_URL, DOCS_URL, ExplainJson, HelpJson, explainMarkdown, helpMarkdown } from './markdown';
import { registerLanguageFeatures } from './language';
import { Queries } from './queries';
import { registerStatus } from './status';
import { registerTesting } from './testing';

const HELP_TOPICS_LIST = ['language','types','strings','functions','classes','variants','match','nullability','errors','collections',
  'numerics','modules','jvm','conform','generics','projects','dependencies','agents','upgrade','fmt','testing','wrap'];

/** The plain code of a diagnostic, whether or not it carries a documentation link. */
export const codeOf = (code: vscode.Diagnostic['code']): string | undefined =>
  code === undefined ? undefined : typeof code === 'object' ? String(code.value) : String(code);

export function activate(context: vscode.ExtensionContext): void {
  const output = vscode.window.createOutputChannel('Sprig');
  const diagnostics = vscode.languages.createDiagnosticCollection('sprig');
  const jobs = new Map<string, AbortController>();
  const groups = new Map<string, Map<string, vscode.Diagnostic[]>>();
  const helpTopics = new Map<string, string>();
  const saved = (doc: vscode.TextDocument) => doc.languageId === 'sprig' && doc.uri.scheme === 'file';
  const settings = (uri: vscode.Uri) => vscode.workspace.getConfiguration('sprig', uri);
  context.subscriptions.push(output, diagnostics, {dispose: () => {for(const job of jobs.values()) job.abort();}});

  const publish = async (key: string, results: CompilerResult[], fallback: vscode.Uri, owner: AbortController) => {
    const entries = new Map<string, vscode.Diagnostic[]>();
    for(const result of results) for(const d of result.diagnostics ?? []) {
      const uri = d.uri ? vscode.Uri.parse(d.uri) : fallback;
      let range = new vscode.Range(0,0,0,0);
      if(d.range) {
        // Prefer disk contents: compiler checked saved bytes, not dirty buffers.
        try {
          const text = await fs.readFile(uri.fsPath,'utf8');
          const mapped = mapRange(d.range,text);
          range = new vscode.Range(mapped.start.line,mapped.start.character,mapped.end.line,mapped.end.character);
        } catch { range = new vscode.Range(d.range.start.line,d.range.start.character,d.range.end.line,d.range.end.character); }
      }
      let message = d.message;
      if(d.expectedType || d.actualType) message += `\nExpected: ${d.expectedType ?? '?'}; actual: ${d.actualType ?? '?'}.`;
      if(d.hint) message += `\n${d.hint}`;
      const item = new vscode.Diagnostic(range,message,d.severity==='warning' ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Error);
      item.source = 'Sprig'; item.code = {value: d.code, target: vscode.Uri.parse(DIAGNOSTIC_DOCS_URL)};
      if(d.relatedHelp) helpTopics.set(d.code, d.relatedHelp);
      const list = entries.get(uri.toString()) ?? [];
      if(!list.some(x=>codeOf(x.code)===d.code && x.message===item.message && x.range.isEqual(item.range))) list.push(item);
      entries.set(uri.toString(),list);
    }
    if(owner.signal.aborted || jobs.get(key)!==owner) return;
    groups.set(key,entries);
    diagnostics.clear();
    const merged = new Map<string,vscode.Diagnostic[]>();
    for(const group of groups.values()) for(const [uri,items] of group) merged.set(uri,[...(merged.get(uri)??[]),...items]);
    for(const [uri,items] of merged) diagnostics.set(vscode.Uri.parse(uri),items);
  };

  async function executionDirectory(doc: vscode.TextDocument, automatic=false): Promise<string | undefined> {
    if(!vscode.workspace.isTrusted) {
      if(!automatic) void vscode.window.showWarningMessage('Trust this workspace to invoke the Sprig compiler. Syntax highlighting remains available.');
      return;
    }
    if(!saved(doc)) {if(!automatic) void vscode.window.showWarningMessage('Open and save a local .spr file first.');return;}
    if(!automatic && doc.isDirty && !await doc.save()) return;
    // Do not check a graph against unsaved Sprig files in that project.
    const cwd = projectRoot(doc.uri.fsPath);
    if(vscode.workspace.textDocuments.some(d=>saved(d) && d.isDirty && projectRoot(d.uri.fsPath)===cwd)) {
      if(!automatic) void vscode.window.showWarningMessage('Save the Sprig files in this project before checking or running.');
      return;
    }
    return cwd;
  }

  async function execute(command: string, doc: vscode.TextDocument, automatic=false): Promise<CompilerResult | undefined> {
    const cwd = await executionDirectory(doc, automatic);
    if(!cwd) return;
    const controller = new AbortController(); jobs.get(cwd)?.abort(); jobs.set(cwd,controller);
    const config = settings(doc.uri);
    const timeoutMs = Math.max(1,Math.min(3600,config.get<number>('commandTimeoutSeconds',120)))*1000;
    const call = async (args: string[]) => {
      const result = await invoke(resolveCompiler(config.get<string>('compilerPath',''),cwd),args,cwd,{signal:controller.signal,timeoutMs});
      if(result.stderr) output.appendLine(result.stderr);
      return result.json;
    };
    const work = async () => {
      const results: CompilerResult[] = [];
      let args: string[];
      if(command==='check') {
        // Include entry graph plus current file; unused modules still receive feedback.
        const project = await fs.stat(path.join(cwd,'sprig.toml')).catch(()=>undefined);
        if(project) {
          const metadata = await call(['project','--json']);
          if(metadata.exitCode!==0) {results.push(metadata);await publish(cwd,results,doc.uri,controller);return metadata;}
          if(metadata.project?.entry) {
            const entry = path.resolve(cwd,metadata.project.entry);
            if(entry !== doc.uri.fsPath) results.push(await call(['check',entry,'--json']));
          }
        }
        args = ['check',doc.uri.fsPath,'--json'];
      } else if(command==='java' || command==='build') {
        const id = createHash('sha256').update(doc.uri.toString()).digest('hex').slice(0,16);
        const dest = path.join(context.globalStorageUri.fsPath,'generated',id);
        await fs.mkdir(dest,{recursive:true});
        args = ['build',doc.uri.fsPath,'-d',dest,'--json'];
        if(command==='java') args.push('--emit-java-only');
      } else args = ['run',doc.uri.fsPath,'--json'];
      output.appendLine(`Sprig ${command}: ${doc.uri.fsPath}`);
      let result = await call(args); results.push(result);
      if(command==='check' && results.some(r=>r.exitCode!==0)) result={...result,exitCode:1,diagnostics:results.flatMap(r=>r.diagnostics??[])};
      if(controller.signal.aborted || jobs.get(cwd)!==controller) return;
      await publish(cwd,results,doc.uri,controller);
      if(controller.signal.aborted || jobs.get(cwd)!==controller) return;
      if(command==='run' && result.programOutput!==undefined) {output.append(result.programOutput);output.show(true);}
      if(!automatic) output.appendLine(`Exit ${result.exitCode}`);
      if(result.exitCode!==0 && !automatic) {output.show(true);void vscode.commands.executeCommand('workbench.actions.view.problems');}
      if((command==='java'||command==='build') && result.exitCode===0) {
        output.appendLine(`Java sources: ${(result.javaSources??[]).join(', ')}`);
        if(command==='java' && result.javaSources?.length) {
          const file = result.javaSources.find(f=>path.basename(f)===`${String(result.mainClass).split('.').pop()}.java`) ?? result.javaSources[0];
          await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(vscode.Uri.file(file)),{viewColumn:vscode.ViewColumn.Beside,preview:true});
        }
      }
      return result;
    };
    try {
      if(automatic) return await work();
      return await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:`Sprig: ${command==='java'?'Show Generated Java':command}`,cancellable:true},async(_,token)=>{
        const subscription = token.onCancellationRequested(()=>controller.abort());
        try {return await work();} finally {subscription.dispose();}
      });
    } catch(error) {
      if(!controller.signal.aborted) {
        const message = error instanceof Error ? error.message : String(error);output.appendLine(message);
        if(!automatic) void vscode.window.showErrorMessage(message);
      }
      return;
    } finally {if(jobs.get(cwd)===controller) jobs.delete(cwd);}
  }

  // Read-only compiler queries for editor features; never run without workspace trust.
  const query = async (args: string[], cwd: string, root = cwd): Promise<CompilerResult> => {
    if(!vscode.workspace.isTrusted) throw new Error('Workspace is not trusted.');
    const executable = resolveCompiler(settings(vscode.Uri.file(root)).get<string>('compilerPath',''), root);
    const result = await invoke(executable, args, cwd, {timeoutMs: 30000});
    if(result.stderr) output.appendLine(result.stderr);
    return result.json;
  };
  const queries = new Queries(query);
  registerLanguageFeatures(context, queries, query);
  context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(event => {
    if(event.affectsConfiguration('sprig')) queries.clear();
  }));
  const testing = registerTesting(context, async (args, cwd, signal) => {
    const config = settings(vscode.Uri.file(cwd));
    const executable = resolveCompiler(config.get<string>('compilerPath',''), cwd);
    const timeoutMs = Math.max(1, Math.min(7200, config.get<number>('testTimeoutSeconds', 600))) * 1000;
    const result = await invoke(executable, args, cwd, {signal, timeoutMs});
    if(result.stderr) output.appendLine(result.stderr);
    return result.json;
  });
  context.subscriptions.push(vscode.commands.registerCommand('sprig.runTests', async () => {
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to run Sprig tests.');return;}
    const doc = vscode.window.activeTextEditor?.document;
    const root = doc?.uri.scheme==='file' ? projectRoot(doc.uri.fsPath) : vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    if(!root) {void vscode.window.showWarningMessage('Open a file in a Sprig project first.');return;}
    for(const d of vscode.workspace.textDocuments) if(saved(d) && d.isDirty && projectRoot(d.uri.fsPath)===root) await d.save();
    const result = await testing.runProject(root);
    if(!result) {void vscode.window.showInformationMessage('No Sprig tests found under tests/.');return;}
    const summary = result.summary as {total:number; passed:number} | undefined;
    if(summary) vscode.window.setStatusBarMessage(`Sprig tests: ${summary.passed}/${summary.total} passed`, 5000);
    return result;
  }));
  const status = registerStatus(context, queries, query);
  context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(event => {
    if(event.affectsConfiguration('sprig')) void status.refresh();
  }));
  /** Runs a project command with the user's command time limit. */
  const project = async (args: string[], root: string): Promise<CompilerResult> => {
    const config = settings(vscode.Uri.file(root));
    const timeoutMs = Math.max(1,Math.min(3600,config.get<number>('commandTimeoutSeconds',120)))*1000;
    const result = await invoke(resolveCompiler(config.get<string>('compilerPath',''), root), args, root, {timeoutMs});
    if(result.stderr) output.appendLine(result.stderr);
    return result.json;
  };
  context.subscriptions.push(vscode.commands.registerCommand('sprig.resolveDependencies', async () => {
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to resolve Sprig dependencies.');return;}
    const doc = vscode.window.activeTextEditor?.document;
    if(!doc || doc.uri.scheme!=='file') {void vscode.window.showWarningMessage('Open a file in a Sprig project first.');return;}
    const root = projectRoot(doc.uri.fsPath);
    try {
      const result = await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Sprig: Resolve Dependencies'},()=>project(['resolve','--json'],root));
      queries.clear(); void status.refresh();
      if(result.exitCode===0) void vscode.window.showInformationMessage('Sprig: sprig.lock is up to date.');
      else void vscode.window.showErrorMessage(`Sprig: ${(result.diagnostics??[]).map(d=>d.message).join('; ') || 'resolve failed'}`);
      return result;
    } catch(e) {void vscode.window.showErrorMessage(e instanceof Error ? e.message : String(e));return;}
  }));
  context.subscriptions.push(vscode.commands.registerCommand('sprig.newProject', async (parent?: string, name?: string) => {
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to create a Sprig project.');return;}
    parent ??= (await vscode.window.showOpenDialog({canSelectFolders:true,canSelectFiles:false,canSelectMany:false,openLabel:'Create Sprig project here'}))?.[0]?.fsPath;
    if(!parent) return;
    name ??= await vscode.window.showInputBox({prompt:'Project folder name',value:'my-sprig-app',
      validateInput:value=>value.trim()===value && value && !/[\\/:*?"<>|]/.test(value) && value!=='.' && value!=='..' ? undefined : 'Enter a folder name without / \\ : * ? " < > |.'});
    if(!name) return;
    const target = path.join(parent, name);
    try {
      const executable = resolveCompiler(settings(vscode.Uri.file(parent)).get<string>('compilerPath',''), parent);
      const init = await capture(executable, ['init', target], parent, {timeoutMs: 60000});
      if(init.code!==0) throw new Error((init.stderr || init.stdout).trim() || `sprig init exited with ${init.code}`);
      // An explicit create: also write the empty lock so the new project checks immediately.
      const resolved = await project(['resolve','--json'], target);
      if(resolved.exitCode!==0) throw new Error((resolved.diagnostics??[]).map(d=>d.message).join('; ') || 'sprig resolve failed');
      await vscode.window.showTextDocument(vscode.Uri.file(path.join(target,'src','main.spr')));
      void vscode.window.showInformationMessage(`Created Sprig project ${name}.`, 'Open Folder').then(choice => {
        if(choice) void vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.file(target), {forceNewWindow: true});
      });
      return target;
    } catch(e) {void vscode.window.showErrorMessage(e instanceof Error ? e.message : String(e));return;}
  }));
  context.subscriptions.push(vscode.commands.registerCommand('sprig.openDocumentation', () =>
    vscode.env.openExternal(vscode.Uri.parse(vscode.env.language.toLowerCase().startsWith('zh') ? DOCS_URL : DOCS_URL + 'en/'))));
  context.subscriptions.push(vscode.commands.registerCommand('sprig.showActions', async () => {
    const actions = [['Check','sprig.check'],['Run','sprig.run'],['Run in Terminal','sprig.runInTerminal'],['Run Tests','sprig.runTests'],
      ['Format Document','editor.action.formatDocument'],['Resolve Dependencies','sprig.resolveDependencies'],['Show Generated Java','sprig.showGeneratedJava'],
      ['Show Help Topic','sprig.showHelp'],['Explain Diagnostic','sprig.explainDiagnostic'],['Show Capabilities','sprig.showCapabilities'],
      ['New Project','sprig.newProject'],['Open Documentation','sprig.openDocumentation']];
    const picked = await vscode.window.showQuickPick(actions.map(([label, command]) => ({label, command})), {placeHolder: 'Sprig'});
    return picked && vscode.commands.executeCommand(picked.command);
  }));

  for(const [name,command] of [['check','check'],['run','run'],['build','build'],['showGeneratedJava','java']]) {
    context.subscriptions.push(vscode.commands.registerCommand(`sprig.${name}`,()=>{
      const doc = vscode.window.activeTextEditor?.document;
      if(!doc) {void vscode.window.showWarningMessage('Open a .spr file first.');return;}
      return execute(command,doc);
    }));
  }
  context.subscriptions.push(vscode.commands.registerCommand('sprig.runInTerminal',async()=>{
    const doc = vscode.window.activeTextEditor?.document;
    if(!doc) {void vscode.window.showWarningMessage('Open a .spr file first.');return;}
    const cwd = await executionDirectory(doc);
    if(!cwd) return;
    try {
      const executable = resolveCompiler(settings(doc.uri).get<string>('compilerPath',''),cwd);
      const command = compilerCommand(executable,['run',doc.uri.fsPath]);
      // Launch a process directly: paths and file names never become shell command text.
      const terminal = vscode.window.createTerminal({name:`Sprig: ${path.basename(doc.uri.fsPath)}`,
        cwd, shellPath:command.command, shellArgs:command.args});
      terminal.show();
      return terminal;
    } catch(error) {
      void vscode.window.showErrorMessage(error instanceof Error ? error.message : String(error));
      return;
    }
  }));
  context.subscriptions.push(vscode.commands.registerCommand('sprig.showCapabilities',async()=>{
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to query the Sprig compiler.');return;}
    try {
      const doc = vscode.window.activeTextEditor?.document;
      const cwd = doc?.uri.scheme==='file'?projectRoot(doc.uri.fsPath):vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
      if(!cwd) {void vscode.window.showWarningMessage('Open a local file or workspace first.');return;}
      const executable = resolveCompiler(settings(doc?.uri??vscode.Uri.file(cwd)).get<string>('compilerPath',''),cwd);
      const result = await invoke(executable,['capabilities','--json'],cwd);
      output.appendLine(JSON.stringify(result.json,null,2));output.show(true);
    } catch(e) {void vscode.window.showErrorMessage(String(e));}
  }));
  // Rendered explanations and help open as read-only Markdown pages.
  const pages = new Map<string, string>(), changed = new vscode.EventEmitter<vscode.Uri>();
  context.subscriptions.push(changed, vscode.workspace.registerTextDocumentContentProvider('sprig-doc', {
    onDidChange: changed.event, provideTextDocumentContent: uri => pages.get(uri.path) ?? '',
  }));
  async function showPage(name: string, markdown: string): Promise<string> {
    const uri = vscode.Uri.from({scheme: 'sprig-doc', path: `/${name}.md`});
    pages.set(uri.path, markdown); changed.fire(uri);
    try { await vscode.commands.executeCommand('markdown.showPreviewToSide', uri); }
    catch { await vscode.window.showTextDocument(uri, {viewColumn: vscode.ViewColumn.Beside, preview: true}); }
    return markdown;
  }
  const queryRoot = () => {
    const doc = vscode.window.activeTextEditor?.document;
    return doc?.uri.scheme==='file' ? projectRoot(doc.uri.fsPath) : vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? process.cwd();
  };
  context.subscriptions.push(vscode.commands.registerCommand('sprig.explainDiagnostic',async(code?: string)=>{
    if(!code) code=await vscode.window.showInputBox({prompt:'Sprig diagnostic code',placeHolder:'SPR-TYPE-ASSIGN'});
    if(!code || !/^SPR-[A-Z0-9-]+$/.test(code)) return;
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to query the Sprig compiler.');return;}
    try { return await showPage(code, explainMarkdown(await query(['explain',code,'--json'],queryRoot()) as unknown as ExplainJson)); }
    catch(e) {void vscode.window.showErrorMessage(String(e));return;}
  }));
  context.subscriptions.push(vscode.commands.registerCommand('sprig.showHelp',async(topic?: string)=>{
    topic ??= await vscode.window.showQuickPick(HELP_TOPICS_LIST,{placeHolder:'Sprig help topic'});
    if(!topic) return;
    if(!vscode.workspace.isTrusted) {void vscode.window.showWarningMessage('Trust this workspace to query the Sprig compiler.');return;}
    try { return await showPage(`help-${topic}`, helpMarkdown(await query(['help',topic,'--json'],queryRoot()) as unknown as HelpJson)); }
    catch(e) {void vscode.window.showErrorMessage(String(e));return;}
  }));
  context.subscriptions.push(vscode.languages.registerCodeActionsProvider('sprig',{
    provideCodeActions(_doc,_range,ctx) {
      return ctx.diagnostics.filter(d=>d.source==='Sprig' && codeOf(d.code)).flatMap(d=>{
        const code=codeOf(d.code)!, explain=new vscode.CodeAction(`Explain ${code}`,vscode.CodeActionKind.QuickFix);
        explain.command={command:'sprig.explainDiagnostic',title:explain.title,arguments:[code]};explain.diagnostics=[d];
        const topic=helpTopics.get(code);
        if(!topic) return [explain];
        const help=new vscode.CodeAction(`Show sprig help ${topic}`,vscode.CodeActionKind.QuickFix);
        help.command={command:'sprig.showHelp',title:help.title,arguments:[topic]};help.diagnostics=[d];
        return [explain,help];
      });
    }
  },{providedCodeActionKinds:[vscode.CodeActionKind.QuickFix]}));
  context.subscriptions.push(vscode.workspace.onDidSaveTextDocument(doc=>{
    if(saved(doc)&&settings(doc.uri).get<boolean>('checkOnSave',true)) void execute('check',doc,true);
  }));
  context.subscriptions.push(vscode.workspace.onDidChangeTextDocument(event=>{
    if(saved(event.document)) jobs.get(projectRoot(event.document.uri.fsPath))?.abort();
  }));
}
