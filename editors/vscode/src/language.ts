import * as vscode from 'vscode';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { projectRoot } from './compiler';
import { formatSource } from './format';
import { JavaApi, JavaMember, ModuleApi, declarationMarkdown, helpMarkdown, javaMarkdown, summary, variableMarkdown } from './markdown';
import { Queries, Runner } from './queries';
import { BUILTIN_TYPES, Decl, HELP_TOPICS, ImportDecl, KEYWORDS, Outline, findDecl, inCode, referenceAt, scan } from './symbols';

const SELECTOR: vscode.DocumentSelector = { language: 'sprig' };
const EXCLUDE = '{**/node_modules/**,**/sprig-build/**,**/build/**,**/.git/**}';
const SYMBOLS: Record<string, vscode.SymbolKind> = {
  func: vscode.SymbolKind.Function, class: vscode.SymbolKind.Class, variant: vscode.SymbolKind.Enum,
  enum: vscode.SymbolKind.Enum, field: vscode.SymbolKind.Field, method: vscode.SymbolKind.Method,
  case: vscode.SymbolKind.EnumMember, let: vscode.SymbolKind.Constant, var: vscode.SymbolKind.Variable,
};
const ITEMS: Record<string, vscode.CompletionItemKind> = {
  func: vscode.CompletionItemKind.Function, function: vscode.CompletionItemKind.Function,
  class: vscode.CompletionItemKind.Class, variant: vscode.CompletionItemKind.Enum, enum: vscode.CompletionItemKind.Enum,
  field: vscode.CompletionItemKind.Field, method: vscode.CompletionItemKind.Method, case: vscode.CompletionItemKind.EnumMember,
  let: vscode.CompletionItemKind.Constant, var: vscode.CompletionItemKind.Variable,
};

interface Member { name: string; kind: string; detail: string; markdown(): string }

function javaMembers(api: JavaApi, statics: boolean): Member[] {
  const byName = new Map<string, JavaMember[]>();
  for (const m of [...api.staticMethods ?? [], ...api.fields ?? [], ...api.instanceMethods ?? []]) {
    if (!!m.static !== statics || m.usableFromSprig === false) continue;
    byName.set(m.name, [...byName.get(m.name) ?? [], m]);
  }
  return [...byName].map(([name, overloads]) => ({
    name, kind: overloads[0].sprigSignature ? 'method' : 'field',
    detail: overloads[0].sprigSignature ?? overloads[0].sprigType ?? '',
    markdown: () => javaMarkdown(api.className, overloads),
  }));
}

function moduleMembers(api: ModuleApi): Member[] {
  return [
    ...(api.declarations ?? []).map(d => ({ name: d.name, kind: d.kind, detail: summary(d), markdown: () => declarationMarkdown(d) })),
    ...(api.variables ?? []).map(v => ({ name: v.name, kind: v.mutable ? 'var' : 'let', detail: v.type, markdown: () => variableMarkdown(v.name, v.type, v.mutable) })),
  ];
}

/** Switches the compiler-backed editor features between `sprig lsp` and separate CLI queries. */
export interface LanguageFeatures { useServer(active: boolean): void }

export function registerLanguageFeatures(context: vscode.ExtensionContext, queries: Queries, run: Runner): LanguageFeatures {
  const outlines = new Map<string, { version: number; outline: Outline }>();
  const outlineOf = (doc: vscode.TextDocument): Outline => {
    const key = doc.uri.toString(), hit = outlines.get(key);
    if (hit && hit.version === doc.version) return hit.outline;
    const outline = scan(doc.getText());
    outlines.set(key, { version: doc.version, outline });
    return outline;
  };
  const files = new Map<string, { mtime: number; outline: Outline }>();
  const outlineOfFile = async (file: string): Promise<Outline | undefined> => {
    try {
      const { mtimeMs } = await fs.stat(file), hit = files.get(file);
      if (hit && hit.mtime === mtimeMs) return hit.outline;
      const outline = scan(await fs.readFile(file, 'utf8'));
      files.set(file, { mtime: mtimeMs, outline });
      return outline;
    } catch { return undefined; }
  };
  const trusted = () => vscode.workspace.isTrusted;
  const local = (doc: vscode.TextDocument) => doc.uri.scheme === 'file';
  const rootOf = (doc: vscode.TextDocument) => projectRoot(doc.uri.fsPath);
  const at = (uri: vscode.Uri, d: Decl) => new vscode.Location(uri, new vscode.Range(d.line, d.start, d.line, d.end));

  /** Absolute path of an imported Sprig module, when it can be located. */
  async function moduleFile(doc: vscode.TextDocument, imp: ImportDecl): Promise<string | undefined> {
    if (imp.kind === 'file') return path.resolve(path.dirname(doc.uri.fsPath), imp.spec);
    if (!trusted()) return undefined;
    if (imp.kind === 'std') {
      const home = await queries.home(rootOf(doc));
      return home && path.join(home.compilerHome, 'std', imp.spec.slice('@std/'.length));
    }
    if (imp.kind === 'package') return (await queries.dependency(imp.spec, rootOf(doc)))?.path;
    return undefined;
  }
  async function moduleApi(doc: vscode.TextDocument, imp: ImportDecl): Promise<ModuleApi | undefined> {
    if (imp.kind === 'package') return queries.dependency(imp.spec, rootOf(doc));
    const file = await moduleFile(doc, imp);
    return file ? queries.module(file, rootOf(doc)) : undefined;
  }

  /** What `qualifier.` offers in this document; the compiler supplies everything but local enum cases. */
  async function members(doc: vscode.TextDocument, qualifier: string): Promise<Member[]> {
    const outline = outlineOf(doc), owner = findDecl(outline, qualifier);
    if (owner && (owner.kind === 'enum' || owner.kind === 'variant')) {
      return owner.children.map(c => ({ name: c.name, kind: 'case', detail: `${qualifier}.${c.name}${c.detail}`,
        markdown: () => '```sprig\n' + `${owner.kind} ${owner.name}:\n    ${c.name}${c.detail}` + '\n```' }));
    }
    if (!trusted() || !local(doc)) return [];
    const imp = outline.imports.find(i => i.alias === qualifier);
    if (imp?.kind === 'java') {
      const api = await queries.javaClass(imp.spec, rootOf(doc));
      return api ? javaMembers(api, true) : [];
    }
    if (imp) {
      const api = await moduleApi(doc, imp);
      return api ? moduleMembers(api) : [];
    }
    // A top-level variable: the compiler reports its type for the saved file.
    const self = await queries.module(doc.uri.fsPath, rootOf(doc));
    const variable = self?.variables?.find(v => v.name === qualifier);
    if (!variable) return [];
    const type = variable.type.replace(/\?$/, '').replace(/\[.*$/, '');
    const own = self!.declarations?.find(d => d.kind === 'class' && d.name === type);
    if (own) return moduleMembers({ kind: 'sprig-module', declarations: own.methods, variables: (own.fields ?? []).map(f => ({ name: f.name, type: f.type, mutable: f.mutable })) });
    const java = outline.imports.find(i => i.kind === 'java' && (i.alias === type || i.spec.endsWith('.' + type)));
    const api = java && await queries.javaClass(java.spec, rootOf(doc));
    return api ? javaMembers(api, false) : [];
  }

  let server = false;
  const show = (markdown: string | undefined, range: vscode.Range) => markdown ? new vscode.Hover(new vscode.MarkdownString(markdown), range) : undefined;

  // Always registered: the language server has no workspace symbols, and says nothing about keywords.
  context.subscriptions.push(
    vscode.workspace.onDidCloseTextDocument(doc => outlines.delete(doc.uri.toString())),

    vscode.languages.registerWorkspaceSymbolProvider({
      async provideWorkspaceSymbols(query) {
        const needle = query.toLowerCase(), found: vscode.SymbolInformation[] = [];
        for (const uri of await vscode.workspace.findFiles('**/*.spr', EXCLUDE, 2000)) {
          const outline = await outlineOfFile(uri.fsPath);
          const visit = (d: Decl, container: string) => {
            if (d.name.toLowerCase().includes(needle)) found.push(new vscode.SymbolInformation(d.name, SYMBOLS[d.kind], container, at(uri, d)));
            for (const child of d.children) visit(child, d.name);
          };
          for (const d of outline?.declarations ?? []) visit(d, '');
          if (found.length >= 500) break;
        }
        return found;
      },
    }),

    // `sprig help` for keywords; for built-in types too, unless the language server describes them.
    vscode.languages.registerHoverProvider(SELECTOR, {
      async provideHover(doc, position) {
        const ref = referenceAt(doc.lineAt(position.line).text, position.character);
        if (!ref?.word || ref.qualifier) return undefined;
        const topic = HELP_TOPICS[ref.word];
        if (!topic || !(KEYWORDS.includes(ref.word) || (!server && BUILTIN_TYPES.includes(ref.word)))) return undefined;
        if (findDecl(outlineOf(doc), ref.word) || !trusted() || !local(doc)) return undefined;
        const help = await queries.help(topic, rootOf(doc));
        return show(help && helpMarkdown(help), new vscode.Range(position.line, ref.start, position.line, ref.end));
      },
    }),
  );

  // Without the language server: the lexical outline plus compiler queries on saved files.
  const cliFeatures = () => vscode.Disposable.from(
    vscode.languages.registerDocumentSymbolProvider(SELECTOR, {
      provideDocumentSymbols(doc) {
        const symbol = (d: Decl): vscode.DocumentSymbol => {
          const end = doc.lineAt(Math.min(d.endLine, doc.lineCount - 1)).range.end;
          const item = new vscode.DocumentSymbol(d.name, d.detail, SYMBOLS[d.kind],
            new vscode.Range(d.line, 0, end.line, end.character), new vscode.Range(d.line, d.start, d.line, d.end));
          item.children = d.children.map(symbol);
          return item;
        };
        return outlineOf(doc).declarations.map(symbol);
      },
    }),

    vscode.languages.registerHoverProvider(SELECTOR, {
      async provideHover(doc, position) {
        const ref = referenceAt(doc.lineAt(position.line).text, position.character);
        if (!ref?.word) return undefined;
        const range = new vscode.Range(position.line, ref.start, position.line, ref.end);
        if (ref.qualifier) return show((await members(doc, ref.qualifier)).find(m => m.name === ref.word)?.markdown(), range);
        const outline = outlineOf(doc), decl = findDecl(outline, ref.word);
        // Keywords and built-in types: the help hover above.
        if (HELP_TOPICS[ref.word] && !decl && (KEYWORDS.includes(ref.word) || BUILTIN_TYPES.includes(ref.word))) return undefined;
        const imp = outline.imports.find(i => i.alias === ref.word);
        if (imp) return show('```sprig\n' + `import ${imp.kind === 'java' ? imp.spec : `"${imp.spec}"`} as ${imp.alias}` + '\n```', range);
        if (!decl) return undefined;
        if (trusted() && local(doc)) {
          const self = await queries.module(doc.uri.fsPath, rootOf(doc));
          const api = self?.declarations?.find(d => d.name === decl.name);
          if (api) return show(declarationMarkdown(api), range);
          const variable = self?.variables?.find(v => v.name === decl.name);
          if (variable) return show(variableMarkdown(variable.name, variable.type, variable.mutable), range);
        }
        return show('```sprig\n' + doc.lineAt(decl.line).text.trim() + '\n```', range);
      },
    }),

    vscode.languages.registerCompletionItemProvider(SELECTOR, {
      async provideCompletionItems(doc, position) {
        const line = doc.lineAt(position.line).text;
        if (!inCode(line, position.character)) return undefined;
        const ref = referenceAt(line, position.character);
        if (ref?.qualifier) {
          return (await members(doc, ref.qualifier)).map(m => {
            const item = new vscode.CompletionItem(m.name, ITEMS[m.kind] ?? vscode.CompletionItemKind.Property);
            item.detail = m.detail;
            item.documentation = new vscode.MarkdownString(m.markdown());
            return item;
          });
        }
        const outline = outlineOf(doc), items: vscode.CompletionItem[] = [];
        for (const word of KEYWORDS) items.push(new vscode.CompletionItem(word, vscode.CompletionItemKind.Keyword));
        for (const type of BUILTIN_TYPES) items.push(new vscode.CompletionItem(type, vscode.CompletionItemKind.Struct));
        for (const imp of outline.imports) {
          const item = new vscode.CompletionItem(imp.alias, imp.kind === 'java' ? vscode.CompletionItemKind.Class : vscode.CompletionItemKind.Module);
          item.detail = imp.spec;
          items.push(item);
        }
        for (const d of outline.declarations) {
          const item = new vscode.CompletionItem(d.name, ITEMS[d.kind]);
          item.detail = d.detail;
          items.push(item);
        }
        return items;
      },
    }, '.'),

    vscode.languages.registerDefinitionProvider(SELECTOR, {
      async provideDefinition(doc, position) {
        const outline = outlineOf(doc);
        const imp = outline.imports.find(i => i.line === position.line && position.character >= i.start && position.character <= i.end);
        if (imp) {
          const file = local(doc) ? await moduleFile(doc, imp) : undefined;
          return file ? new vscode.Location(vscode.Uri.file(file), new vscode.Position(0, 0)) : undefined;
        }
        const ref = referenceAt(doc.lineAt(position.line).text, position.character);
        if (!ref?.word) return undefined;
        if (!ref.qualifier) {
          const own = findDecl(outline, ref.word);
          return own && at(doc.uri, own);
        }
        const member = findDecl(outline, ref.word, ref.qualifier);
        if (member) return at(doc.uri, member);
        const target = outline.imports.find(i => i.alias === ref.qualifier && i.kind !== 'java');
        const file = target && local(doc) ? await moduleFile(doc, target) : undefined;
        const found = file && findDecl(await outlineOfFile(file) ?? { imports: [], declarations: [] }, ref.word);
        return file && found ? at(vscode.Uri.file(file), found) : undefined;
      },
    }),

    vscode.languages.registerDocumentFormattingEditProvider(SELECTOR, {
      async provideDocumentFormattingEdits(doc) {
        if (!trusted() || !local(doc)) return [];
        const text = doc.getText();
        const formatted = await formatSource(text, (args, cwd) => run(args, cwd, rootOf(doc)));
        if (formatted === undefined) {
          vscode.window.setStatusBarMessage('Sprig: fix the syntax errors before formatting.', 5000);
          return [];
        }
        return formatted === text ? [] : [vscode.TextEdit.replace(new vscode.Range(doc.positionAt(0), doc.positionAt(text.length)), formatted)];
      },
    }),
  );

  // Two providers of the same feature would duplicate results (and make VS Code ask for a default formatter).
  let cli: vscode.Disposable | undefined = cliFeatures();
  context.subscriptions.push({ dispose: () => cli?.dispose() });
  return {
    useServer(active: boolean) {
      server = active;
      if (active) { cli?.dispose(); cli = undefined; }
      else cli ??= cliFeatures();
    },
  };
}
