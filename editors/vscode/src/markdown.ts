/** Renders compiler JSON (help, explain, api) as Markdown for hovers and previews. */

export const DOCS_URL = 'https://colinhouse.github.io/Sprig/';
export const DIAGNOSTIC_DOCS_URL = DOCS_URL + 'en/reference/tooling/diagnostic-codes';

export interface HelpJson { topic: string; syntax?: string[]; rules?: string[]; examples?: string[] }
export interface ExplainJson {
  code: string; known?: boolean; meaning?: string; whyMatters?: string; confusedWith?: string[];
  commonCauses?: string[]; safeFixes?: string[]; badExample?: string | null; goodExample?: string | null;
  relatedCodes?: string[]; relatedHelp?: string;
}
export interface ApiField { name: string; type: string; mutable?: boolean; required?: boolean }
export interface ApiCase { name: string; fields?: ApiField[] }
export interface ApiDecl {
  kind: string; name: string; genericParameters?: string[]; parameters?: { name: string; type: string }[];
  result?: string; throws?: string[]; fields?: ApiField[]; methods?: ApiDecl[]; cases?: (string | ApiCase)[];
}
export interface ModuleApi {
  kind: string; path?: string; imports?: { spec: string; alias: string; kind: string }[];
  variables?: { name: string; type: string; mutable?: boolean }[]; declarations?: ApiDecl[];
}
export interface JavaMember {
  name: string; sprigSignature?: string; sprigType?: string; javaSignature?: string;
  static?: boolean; usableFromSprig?: boolean; unusableReason?: string | null;
}
export interface JavaApi {
  className: string; constructors?: JavaMember[]; staticMethods?: JavaMember[];
  instanceMethods?: JavaMember[]; fields?: JavaMember[];
}

const block = (lines: string[]) => '```sprig\n' + lines.join('\n') + '\n```';
const bullets = (items: string[]) => items.map(item => `- ${item}`).join('\n');
const field = (f: ApiField) => `${f.mutable ? 'var' : 'let'} ${f.name}: ${f.type}${f.required === false ? ' = …' : ''}`;
const fn = (d: ApiDecl) => `func ${d.name}(${(d.parameters ?? []).map(p => `${p.name}: ${p.type}`).join(', ')}) -> ${d.result ?? 'Unit'}`
  + (d.throws?.length ? ` throws ${d.throws.join(', ')}` : '');

export function helpMarkdown(help: HelpJson): string {
  const out = [`**${help.topic}** · \`sprig help ${help.topic}\``];
  if (help.syntax?.length) out.push(block(help.syntax));
  if (help.rules?.length) out.push(bullets(help.rules));
  return out.join('\n\n');
}

export function explainMarkdown(e: ExplainJson): string {
  if (e.known === false) return `# ${e.code}\n\nThis compiler does not know \`${e.code}\`. Run \`sprig codes\` for the list.\n`;
  const out = [`# ${e.code}`];
  if (e.meaning) out.push(e.meaning);
  if (e.whyMatters) out.push(`**Why it matters:** ${e.whyMatters}`);
  if (e.commonCauses?.length) out.push('## Common causes', bullets(e.commonCauses));
  if (e.safeFixes?.length) out.push('## How to fix it', bullets(e.safeFixes));
  if (e.badExample) out.push('## Wrong', block([e.badExample]));
  if (e.goodExample) out.push('## Right', block([e.goodExample]));
  if (e.confusedWith?.length) out.push('## Not to be confused with', bullets(e.confusedWith));
  if (e.relatedCodes?.length) out.push(`Related codes: ${e.relatedCodes.map(c => `\`${c}\``).join(', ')}`);
  if (e.relatedHelp) out.push(`More: \`sprig help ${e.relatedHelp}\``);
  return out.join('\n\n') + '\n';
}

/** One-line summary used as completion detail. */
export function summary(d: ApiDecl): string {
  return d.kind === 'function' ? fn(d) : `${d.kind} ${d.name}`;
}

/** Sprig-looking outline of a declaration exactly as the compiler reports it. */
export function declarationMarkdown(d: ApiDecl): string {
  const lines: string[] = [];
  const pad = d.genericParameters?.length ? '    ' : '';
  if (pad) lines.push(`generic ${d.genericParameters!.join(', ')}:`);
  if (d.kind === 'function') lines.push(pad + fn(d));
  else {
    lines.push(`${pad}${d.kind} ${d.name}:`);
    for (const f of d.fields ?? []) lines.push(`${pad}    ${field(f)}`);
    for (const m of d.methods ?? []) lines.push(`${pad}    ${fn(m)}`);
    for (const c of d.cases ?? []) {
      if (typeof c === 'string') lines.push(`${pad}    ${c}`);
      else lines.push(`${pad}    ${c.name}${c.fields?.length ? `(${c.fields.map(f => `${f.name}: ${f.type}`).join(', ')})` : ''}`);
    }
  }
  return block(lines);
}

export function variableMarkdown(name: string, type: string, mutable?: boolean): string {
  return block([`${mutable ? 'var' : 'let'} ${name}: ${type}`]);
}

/** Every Sprig-usable overload of one Java member. */
export function javaMarkdown(className: string, members: JavaMember[]): string {
  const usable = members.filter(m => m.usableFromSprig !== false);
  const out = usable.length ? [block(usable.map(m => m.sprigSignature ?? `${m.name}: ${m.sprigType ?? '?'}`))] : [];
  out.push(`Java \`${className}\``);
  const blocked = members.filter(m => m.usableFromSprig === false);
  if (blocked.length) out.push(`${blocked.length} overload(s) not usable from Sprig: ${blocked[0].unusableReason ?? 'see sprig api'}`);
  return out.join('\n\n');
}
