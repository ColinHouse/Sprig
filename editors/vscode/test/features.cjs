const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const compiler=path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig');
const {invoke}=require('../out/compiler.js');
const run=async(args,cwd)=>(await invoke(compiler,args,cwd)).json;
const {nonAscii}=require('./platform-text.cjs');
function fixture(t){const dir=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),`Sprig ${nonAscii} $; `)));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}

test('formatSource returns canonical text and refuses broken source',async()=>{
 const {formatSource}=require('../out/format.js');
 assert.equal(await formatSource('func add(a:Int,b:Int)->Int:\n  return a+b\nprint("中文 "+add(1,2).toString())\n',run),
  'func add(a: Int, b: Int) -> Int:\n    return a + b\nprint("中文 " + add(1, 2).toString())\n');
 assert.equal(await formatSource('func broken(:\n',run),undefined);
});
test('queries cache lookups, find the bundled std and degrade to undefined',async()=>{
 const {Queries}=require('../out/queries.js');let calls=0;
 const q=new Queries((args,cwd)=>{calls++;return run(args,cwd);});
 assert.equal((await q.help('match',root)).topic,'match');await q.help('match',root);assert.equal(calls,1);
 assert.equal((await q.javaClass('java.lang.Math',root)).className,'java.lang.Math');
 assert.equal((await q.module(path.join(root,'website/snippets/classes.spr'),root)).declarations[0].name,'Hero');
 const home=await q.home(root);assert.ok(fs.existsSync(path.join(home.compilerHome,'std','text.spr')));
 assert.equal(await q.javaClass('com.example.Missing',root),undefined);
 assert.equal(await q.module(path.join(root,'no such file.spr'),root),undefined);
});
// ${1:default} and ${1|a,b|} define a placeholder; $1 and ${1} mirror it.
function expand(body){
 const values={};
 let text=body.replace(/\$\{(\d+)\|([^|]*)\|\}/g,(_,n,choices)=>values[n]=choices.split(',')[0]);
 for(let i=0;i<5;i++)text=text.replace(/\$\{(\d+):((?:[^{}]|\{[^{}]*\})*)\}/g,(_,n,value)=>values[n]??=value);
 return text.replace(/\$\{(\d+)\}|\$(\d+)/g,(_,a,b)=>values[a??b]??'');
}
test('every snippet expands to Sprig that parses',async t=>{
 const snippets=JSON.parse(fs.readFileSync(path.join(__dirname,'../snippets/sprig.json'),'utf8'));
 assert.ok(Object.keys(snippets).length>=16);
 const dir=fixture(t);
 for(const [name,snippet] of Object.entries(snippets)){
  const text=expand(snippet.body.join('\n'))+'\n';
  const file=path.join(dir,name.replace(/\W+/g,'_')+'.spr');fs.writeFileSync(file,text);
  const result=await run(['check',file,'--syntax-only','--json'],dir);
  assert.equal(result.exitCode,0,`${name}:\n${text}\n${JSON.stringify(result.diagnostics)}`);
 }
});
test('test outcomes map pass, runtime failure and compile-fail fixtures by file',async t=>{
 const {canonical,outcomes,testLabel}=require('../out/testResults.js');
 const dir=fixture(t);
 // Look up files the way testing.ts names test items. A Windows temp directory can be
 // spelled with 8.3 short names (C:\Users\RUNNER~1), which only the native realpath expands.
 const item=(...parts)=>canonical(path.join(dir,...parts));
 fs.writeFileSync(path.join(dir,'sprig.toml'),'[project]\nname = "outcomes"\nversion = "0.1.0"\nlanguage = "0.8"\n');
 fs.mkdirSync(path.join(dir,'src'));fs.writeFileSync(path.join(dir,'src','main.spr'),'print("main")\n');
 fs.mkdirSync(path.join(dir,'tests','compile_fail'),{recursive:true});
 fs.writeFileSync(path.join(dir,'tests','pass.spr'),'print("ok")\n');
 fs.writeFileSync(path.join(dir,'tests','fail.spr'),'print("before")\nthrow Error("boom")\n');
 fs.writeFileSync(path.join(dir,'tests','compile_fail','wrong.spr'),'let x: Int = "s"\n');
 fs.writeFileSync(path.join(dir,'tests','compile_fail','wrong.expect.toml'),'codes = ["SPR-TYPE-ASSIGN"]\nexact = true\n');
 assert.equal((await run(['resolve','--json'],dir)).exitCode,0);
 const all=await run(['test','--json'],dir);assert.equal(all.exitCode,1);
 const map=outcomes(all);
 assert.equal(map.get(item('tests','pass.spr')).status,'passed');
 const fail=map.get(item('tests','fail.spr'));
 assert.equal(fail.status,'failed');assert.equal(fail.programOutput.replace(/\r\n/g,'\n'),'before\n');
 assert.equal(fail.diagnostics[0].code,'SPR-RUNTIME-ERROR');assert.equal(fail.diagnostics[0].range.start.line,1);
 assert.equal(map.get(item('tests','compile_fail','wrong.spr')).status,'passed');
 const single=await run(['test',path.join(dir,'tests','pass.spr'),'--json'],dir);
 assert.deepEqual([...outcomes(single).keys()],[item('tests','pass.spr')]);
 assert.equal(testLabel(dir,path.join(dir,'tests','compile_fail','wrong.spr')),'compile_fail/wrong.spr');
});
