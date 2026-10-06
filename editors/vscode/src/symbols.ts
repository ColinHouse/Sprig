/** Lexical outline of Sprig source: declaration names and positions, never types or meaning. */

export type DeclKind = 'func' | 'class' | 'variant' | 'enum' | 'field' | 'method' | 'case' | 'let' | 'var';
export interface Decl {
  kind: DeclKind; name: string; detail: string;
  line: number; start: number; end: number; endLine: number;
  generics: string[]; children: Decl[];
}
export type ImportKind = 'java' | 'file' | 'std' | 'package';
export interface ImportDecl { alias: string; spec: string; kind: ImportKind; line: number; start: number; end: number }
export interface Outline { imports: ImportDecl[]; declarations: Decl[] }
export interface Reference { word: string; start: number; end: number; qualifier?: string }

export const KEYWORDS = ['generic', 'class', 'enum', 'variant', 'conform', 'match', 'case', 'func', 'fn', 'var', 'let',
  'if', 'elif', 'else', 'while', 'for', 'in', 'return', 'break', 'continue', 'pass', 'import', 'as', 'try', 'catch',
  'finally', 'throw', 'throws', 'rethrows', 'requires', 'and', 'or', 'not', 'true', 'false', 'null'];
export const BUILTIN_TYPES = ['Int', 'Int32', 'Float', 'Float32', 'Bool', 'String', 'Unit', 'List', 'MutableList', 'Map', 'MutableMap', 'Error'];

/** `sprig help` topic for keywords and built-in type names. */
export const HELP_TOPICS: Record<string, string> = {
  match: 'match', case: 'match', variant: 'variants', enum: 'variants', class: 'classes',
  func: 'functions', fn: 'functions', return: 'functions', generic: 'generics', requires: 'generics',
  throws: 'errors', rethrows: 'errors', throw: 'errors', try: 'errors', catch: 'errors', finally: 'errors', Error: 'errors',
  import: 'modules', as: 'modules', null: 'nullability', conform: 'conform', let: 'types', var: 'types',
  Int: 'numerics', Int32: 'numerics', Float: 'numerics', Float32: 'numerics', String: 'strings',
  List: 'collections', MutableList: 'collections', Map: 'collections', MutableMap: 'collections',
};

const ID = '[A-Za-z_][A-Za-z_0-9]*';

/** Blanks string contents and drops a trailing comment, keeping every offset in place. */
export function codeOnly(line: string): string {
  let out = '', quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '\\' && i + 1 < line.length) { out += '  '; i++; }
      else if (c === '"') { quoted = false; out += c; }
      else out += ' ';
    } else if (c === '#') break;
    else { if (c === '"') quoted = true; out += c; }
  }
  return out;
}

/** False inside a string literal or a comment. */
export function inCode(line: string, character: number): boolean {
  const prefix = line.slice(0, character), code = codeOnly(prefix);
  if (code.length < prefix.length) return false;
  let quotes = 0;
  for (const c of code) if (c === '"') quotes++;
  return quotes % 2 === 0;
}

function depthAfter(code: string, depth: number): number {
  for (const c of code) {
    if (c === '(' || c === '[' || c === '{') depth++;
    else if ((c === ')' || c === ']' || c === '}') && depth > 0) depth--;
  }
  return depth;
}

function importOf(line: string, n: number): ImportDecl | undefined {
  const code = codeOnly(line);
  const quoted = /^import\s+"/.exec(code);
  if (quoted) {
    const start = quoted[0].length, close = line.indexOf('"', start);
    if (close < 0) return undefined;
    const spec = line.slice(start, close);
    const alias = new RegExp(`^\\s+as\\s+(${ID})`).exec(code.slice(close + 1))?.[1]
      ?? spec.replace(/^.*\//, '').replace(/\.spr$/, '');
    const kind: ImportKind = spec.startsWith('@std/') ? 'std' : spec.startsWith('@') ? 'package' : 'file';
    return { alias, spec, kind, line: n, start, end: close };
  }
  const java = new RegExp(`^import\\s+(${ID}(?:\\.${ID})*)(?:\\s+as\\s+(${ID}))?`).exec(code);
  if (!java) return undefined;
  const start = code.indexOf(java[1], 'import'.length);
  return { alias: java[2] ?? java[1].split('.').pop()!, spec: java[1], kind: 'java', line: n, start, end: start + java[1].length };
}

function decl(kind: DeclKind, name: string, line: number, start: number, detail: string, generics: string[] = []): Decl {
  return { kind, name, detail, line, start, end: start + name.length, endLine: line, generics, children: [] };
}

interface Frame { indent: number; kind: 'class' | 'variant' | 'enum' | 'generic' | 'body'; decl?: Decl; members?: number; generics?: string[] }

/** Imports and declarations found by indentation; bodies of functions and statements are skipped. */
export function scan(text: string): Outline {
  const lines = text.split(/\r\n|\n|\r/);
  const imports: ImportDecl[] = [], declarations: Decl[] = [], stack: Frame[] = [];
  let depth = 0, last = 0;
  const close = (frame: Frame) => { if (frame.decl) frame.decl.endLine = Math.max(frame.decl.line, last); };
  for (let n = 0; n < lines.length; n++) {
    const code = codeOnly(lines[n]);
    const continued = depth > 0;
    depth = depthAfter(code, depth);
    if (!code.trim()) continue;
    if (continued) { last = n; continue; }
    const indent = code.length - code.trimStart().length, body = code.trim();
    while (stack.length && indent <= stack[stack.length - 1].indent) close(stack.pop()!);
    const parent = stack[stack.length - 1];
    const at = (name: string, after: number) => code.indexOf(name, indent + after);
    let m: RegExpExecArray | null;
    if (!parent || parent.kind === 'generic') {
      const generics = parent?.generics ?? [];
      if (!parent && /^import\s/.test(body)) {
        const imp = importOf(lines[n], n);
        if (imp) imports.push(imp);
      } else if ((m = new RegExp(`^generic\\s+(${ID}(?:\\s*,\\s*${ID})*)\\s*:`).exec(body))) {
        stack.push({ indent, kind: 'generic', generics: m[1].split(',').map(s => s.trim()) });
      } else if ((m = new RegExp(`^(class|variant|enum)\\s+(${ID})`).exec(body))) {
        const d = decl(m[1] as DeclKind, m[2], n, at(m[2], m[1].length), body.replace(/\s*:\s*$/, ''), generics);
        declarations.push(d);
        stack.push({ indent, kind: m[1] as Frame['kind'], decl: d });
      } else if ((m = new RegExp(`^func\\s+(${ID})`).exec(body))) {
        const d = decl('func', m[1], n, at(m[1], 4), body.replace(/\s*:\s*$/, ''), generics);
        declarations.push(d);
        stack.push({ indent, kind: 'body', decl: d });
      } else if (!parent && (m = new RegExp(`^(let|var)\\s+(${ID})`).exec(body))) {
        const type = new RegExp(`^(?:let|var)\\s+${ID}\\s*:\\s*([^=]+?)\\s*(?:=|$)`).exec(body)?.[1] ?? '';
        declarations.push(decl(m[1] as DeclKind, m[2], n, at(m[2], m[1].length), type));
        if (body.endsWith(':')) stack.push({ indent, kind: 'body' });
      } else if (body.endsWith(':')) stack.push({ indent, kind: 'body' });
    } else if (parent.kind !== 'body') {
      parent.members ??= indent;
      if (indent === parent.members) {
        if (parent.kind === 'class' && (m = new RegExp(`^(let|var)\\s+(${ID})\\s*:\\s*([^=]+?)\\s*(?:=|$)`).exec(body))) {
          parent.decl!.children.push(decl('field', m[2], n, at(m[2], m[1].length), m[3]));
        } else if (parent.kind === 'class' && (m = new RegExp(`^func\\s+(${ID})`).exec(body))) {
          const d = decl('method', m[1], n, at(m[1], 4), body.replace(/\s*:\s*$/, ''));
          parent.decl!.children.push(d);
          stack.push({ indent, kind: 'body', decl: d });
        } else if (parent.kind !== 'class' && (m = new RegExp(`^(${ID})`).exec(body)) && !KEYWORDS.includes(m[1])) {
          parent.decl!.children.push(decl('case', m[1], n, indent, body.slice(m[1].length).replace(/\s*:\s*$/, '')));
          if (body.endsWith(':')) stack.push({ indent, kind: 'body' });
        } else if (body.endsWith(':')) stack.push({ indent, kind: 'body' });
      }
    }
    last = n;
  }
  while (stack.length) close(stack.pop()!);
  return { imports, declarations };
}

/** The identifier under (or just before) the cursor, with a single `qualifier.` in front of it. */
export function referenceAt(line: string, character: number): Reference | undefined {
  if (!inCode(line, character)) return undefined;
  let start = character, end = character;
  while (start > 0 && /\w/.test(line[start - 1])) start--;
  while (end < line.length && /\w/.test(line[end])) end++;
  const word = line.slice(start, end);
  const qualifier = new RegExp(`(?:^|[^.\\w])(${ID})\\s*\\.\\s*$`).exec(line.slice(0, start))?.[1];
  if (!word && !qualifier) return undefined;
  if (/^\d/.test(word)) return undefined;
  return { word, start, end, qualifier };
}

/** Top-level declaration by name, or a member of the named top-level declaration. */
export function findDecl(outline: Outline, name: string, owner?: string): Decl | undefined {
  const scope = owner ? outline.declarations.find(d => d.name === owner)?.children ?? [] : outline.declarations;
  return scope.find(d => d.name === name);
}
