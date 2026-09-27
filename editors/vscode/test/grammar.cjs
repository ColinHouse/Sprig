const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const tm = require('vscode-textmate');
const onig = require('vscode-oniguruma');
const root = path.resolve(__dirname, '../../..');
let grammar;
async function load() {
  if (!grammar) {
    await onig.loadWASM(fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm')));
    const registry = new tm.Registry({onigLib: Promise.resolve({createOnigScanner:p=>new onig.OnigScanner(p),createOnigString:s=>new onig.OnigString(s)}), loadGrammar:async()=>JSON.parse(fs.readFileSync(path.join(__dirname,'../syntaxes/sprig.tmLanguage.json'),'utf8'))});
    grammar = await registry.loadGrammar('source.sprig');
  }
  return grammar;
}
async function scope(line, word, occurrence=0) {
 const g=await load(); const tokens=g.tokenizeLine(line, tm.INITIAL).tokens;
 let start=-1; for(let i=0;i<=occurrence;i++) start=line.indexOf(word,start+1);
 assert.ok(start>=0); return tokens.find(t=>t.startIndex<=start && t.endIndex>start).scopes.join(' ');
}
test('grammar exists and covers every actual lexer keyword, without partial identifiers',async()=>{
 assert.ok(fs.existsSync(path.join(__dirname,'../syntaxes/sprig.tmLanguage.json')), 'Sprig TextMate grammar must exist');
 const lexer=fs.readFileSync(path.join(root,'grammar/SprigLexer.g4'),'utf8');
 const keywords=[...lexer.matchAll(/^[A-Z_]+: '([a-z]+)';$/gm)].map(m=>m[1]);
 for(const k of keywords){ assert.match(await scope(k,k), /keyword|storage|constant.language/,k); assert.doesNotMatch(await scope(k+'_value',k), /keyword|storage|constant.language/,k+' boundary'); }
});
test('strings isolate hashes/keywords, escapes and invalid escapes; comments isolate numbers',async()=>{
 assert.match(await scope('let x = "# match 12 \\n" # tail','match'),/string.quoted.double/);
 assert.match(await scope('let x = "# match 12 \\n" # tail','\\n'),/constant.character.escape/);
 assert.match(await scope('let x = "\\q"','\\q'),/invalid.illegal.escape/);
 assert.match(await scope('# let x = 3.14','3.14'),/comment.line.number-sign/);
 const g=await load(); const broken=g.tokenizeLine('"unclosed',tm.INITIAL);
 assert.doesNotMatch(g.tokenizeLine('let next = 1',broken.ruleStack).tokens[0].scopes.join(' '),/string/);
});
test('actual numeric formats, generic/nullable types and declaration/call names',async()=>{
 for(const n of ['0','9223372036854775807','0.1','1e-10','2.53E+12']) assert.match(await scope('let x = '+n,n),/constant.numeric/);
 assert.match(await scope('func evaluate(x: List[Expr]?) -> Int:','evaluate'),/entity.name.function/);
 assert.match(await scope('variant Expr:','Expr'),/entity.name.type/);
 assert.match(await scope('let y = evaluate(x)','evaluate'),/entity.name.function/);
 assert.match(await scope('let x: MutableMap[String, Float] = {}','Float'),/support.type/);
 assert.match(await scope('case Expr.Literal as lit:','case'),/keyword/);
 assert.match(await scope('import "@std/json.spr" as json','@std'),/string/);
 assert.doesNotMatch(await scope('let name = 123abc','123'),/constant.numeric/);
});
test('all repository Sprig files tokenize and strings/comments do not leak across lines',async()=>{
 const g=await load(); let count=0;
 function walk(dir){ for(const ent of fs.readdirSync(dir,{withFileTypes:true})){ const f=path.join(dir,ent.name); if(ent.isDirectory()) walk(f); else if(f.endsWith('.spr')) {count++;let state=tm.INITIAL; for(const line of fs.readFileSync(f,'utf8').split(/\r\n|\n|\r/)){ const r=g.tokenizeLine(line,state);assert.ok(!r.stoppedEarly,f);state=r.ruleStack; }}}}
 for(const d of ['std','examples','tests/runtime'])walk(path.join(root,d)); assert.ok(count>30);
});

test('source function types and lambda syntax retain keyword/type/operator scopes',async()=>{
 for(const line of ['let handler: fn(String) -> Bool = predicate', 'let nullable: (fn(Int) -> Int)? = null', 'let values: List[fn() -> Int] = []']) {
  assert.match(await scope(line,'fn'),/storage.type/);
  assert.match(await scope(line,'->'),/keyword.operator/);
 }
 assert.match(await scope('let handler: fn(String) -> Bool = predicate','String'),/support.type/);
 assert.match(await scope('let handler = fn(x: Int) => x + 1','=>'),/keyword.operator/);
 assert.doesNotMatch(await scope('let fn_handler = 1','fn'),/storage.type/);
});

test("contextual export highlights facades without reserving old identifiers", async()=>{
 assert.match(await scope("export app.Request", "export"), /keyword.control/);
 assert.doesNotMatch(await scope("let export = 1", "export"), /keyword/);
});
