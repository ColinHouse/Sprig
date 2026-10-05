const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const compiler=path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig');
const {invoke}=require('../out/compiler.js');
const run=async(args,cwd)=>(await invoke(compiler,args,cwd)).json;
function fixture(t){const dir=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),'Sprig 功能 $; ')));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}

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
