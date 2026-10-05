const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const compiler=path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig');
const {invoke}=require('../out/compiler.js');
const md=require('../out/markdown.js');
const ask=async args=>(await invoke(compiler,args,root)).json;

test('help and explain render the compiler text as Markdown',async()=>{
 const help=await ask(['help','match','--json']),text=md.helpMarkdown(help);
 assert.match(text,/^\*\*match\*\* · `sprig help match`/);
 assert.ok(text.includes('```sprig\n'+help.syntax[0]));assert.ok(text.includes('- '+help.rules[0]));
 const explain=await ask(['explain','SPR-MATCH-NONEXHAUSTIVE','--json']),page=md.explainMarkdown(explain);
 assert.match(page,/^# SPR-MATCH-NONEXHAUSTIVE\n\n/);assert.ok(page.includes(explain.meaning));
 assert.ok(page.includes('## How to fix it'));assert.ok(page.includes(explain.goodExample));
 assert.match(md.explainMarkdown({code:'SPR-NOPE',known:false}),/does not know/);
});
test('declarations render as Sprig outlines from real api output',async()=>{
 const classes=await ask(['api',path.join(root,'website/snippets/classes.spr'),'--json']);
 assert.equal(md.declarationMarkdown(classes.declarations[0]),'```sprig\nclass Hero:\n    let name: String\n    var health: Int = …\n    func heal(amount: Int) -> Unit\n    func describe() -> String\n```');
 const generics=await ask(['api',path.join(root,'website/snippets/generics.spr'),'--json']);
 assert.equal(md.declarationMarkdown(generics.declarations[1]),'```sprig\ngeneric T:\n    func identity(value: T) -> T\n```');
 const errors=await ask(['api',path.join(root,'website/snippets/errors.spr'),'--json']);
 assert.equal(md.summary(errors.declarations[0]),'func checkout(quantity: Int) -> Int throws Error');
 const variants=await ask(['api',path.join(root,'website/snippets/variants.spr'),'--json']);
 assert.equal(md.declarationMarkdown(variants.declarations[0]),'```sprig\nvariant Expr:\n    Literal(value: Int)\n    Add(left: Expr, right: Expr)\n```');
 assert.equal(md.declarationMarkdown(variants.declarations[1]),'```sprig\nenum Mode:\n    Fast\n    Careful\n```');
 assert.equal(md.variableMarkdown('tree','Expr.Add',false),'```sprig\nlet tree: Expr.Add\n```');
});
test('Java members show Sprig signatures',async()=>{
 const math=await ask(['api','java.lang.Math','--member','max','--json']);
 const text=md.javaMarkdown(math.className,math.staticMethods);
 assert.ok(text.includes('max(Int, Int) -> Int'));assert.ok(text.includes('Java `java.lang.Math`'));
});
