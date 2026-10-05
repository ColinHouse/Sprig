const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const compiler=path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig');
function api(){assert.ok(fs.existsSync(path.join(__dirname,'../out/compiler.js')),'CLI adapter must be implemented');return require('../out/compiler.js');}
const {nonAscii}=require('./platform-text.cjs');
function fixture(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),`Sprig ${nonAscii} $; `));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));return dir;}
test('CLI check, real JVM run and Java-only output with adversarial filename',async t=>{
 const a=api(),dir=fixture(t),file=path.join(dir,`hello $; ${nonAscii}.spr`);fs.writeFileSync(file,'import "@std/text.spr" as text\nprint(text.trim("  hello VS Code  "))\n');
 assert.equal((await a.invoke(compiler,['check',file,'--json'],dir)).json.exitCode,0);
 const run=await a.invoke(compiler,['run',file,'--json'],dir);assert.equal(run.json.exitCode,0);assert.equal(run.json.programOutput.trim(),'hello VS Code');
 const out=path.join(dir,'generated Java');const built=await a.invoke(compiler,['build',file,'--emit-java-only','-d',out,'--json'],dir);
 assert.equal(built.json.exitCode,0);assert.equal(built.json.javacInvoked,false);assert.ok(built.json.javaSources.length);for(const f of built.json.javaSources)assert.ok(fs.existsSync(f));assert.ok(!fs.existsSync(path.join(out,'classes')));
});
test('static failures return diagnostics instead of transport failure; compiler absent is actionable',async t=>{
 const a=api(),dir=fixture(t),file=path.join(dir,'bad.spr');fs.writeFileSync(file,'let wrong: Int = "bad"\n');
 const r=await a.invoke(compiler,['check',file,'--json'],dir);assert.equal(r.json.exitCode,1);assert.ok(r.json.diagnostics.some(d=>d.actualType==='String' && d.expectedType==='Int'));
 await assert.rejects(a.invoke(path.join(dir,'no compiler'),['check',file,'--json'],dir),/compiler|ENOENT/i);
});
test('nearest project root and source SDK discovery, configured path is authoritative',t=>{
 const a=api(),dir=fixture(t);fs.mkdirSync(path.join(dir,'nested','src'),{recursive:true});fs.writeFileSync(path.join(dir,'sprig.toml'),'');fs.writeFileSync(path.join(dir,'nested','sprig.toml'),'');
 assert.equal(a.projectRoot(path.join(dir,'nested','src','main.spr')),path.join(dir,'nested'));
 assert.equal(a.resolveCompiler(compiler,dir),compiler);
 assert.equal(a.resolveCompiler('missing',dir),path.join(dir,'missing'));
 const savedPath=process.env.PATH;try{process.env.PATH='';assert.equal(a.resolveCompiler('',path.join(root,'examples')),compiler);}finally{process.env.PATH=savedPath;}
});
test('cancel aborts a real process, timeout terminates it, malformed output is diagnosed',async t=>{
 if(process.platform==='win32')return t.skip('POSIX shell fixture; Windows is preview');
 const a=api(),dir=fixture(t);const slow=path.join(dir,'slow');fs.writeFileSync(slow,'#!/bin/sh\nsleep 30\n',{mode:0o755});
 const abort=new AbortController(); const p=a.invoke(slow,[],dir,{signal:abort.signal});setTimeout(()=>abort.abort(),50);await assert.rejects(p,/cancel|abort/i);
 await assert.rejects(a.invoke(slow,[],dir,{timeoutMs:50}),/timed out/i);
 const bad=path.join(dir,'bad');fs.writeFileSync(bad,'#!/bin/sh\nprintf "not json"\n',{mode:0o755});await assert.rejects(a.invoke(bad,[],dir),/JSON/);
});
test('Windows cancel stops the compiler JVM and the program JVM it started',async t=>{
 if(process.platform!=='win32')return t.skip('POSIX process groups are covered above');
 const a=api(),dir=fixture(t),file=path.join(dir,'beat.spr'),marker=path.join(dir,'beats.txt');
 fs.writeFileSync(file,'import "@std/files.spr" as files\nimport "@std/process.spr" as process\n\nlet marker = process.arguments().get(0)\nvar beats = 0\nwhile true:\n    beats += 1\n    files.write_utf8(marker, beats.toString())\n');
 const abort=new AbortController(),run=a.capture(compiler,['run',file,'--',marker],dir,{signal:abort.signal});
 const read=()=>{try{return fs.readFileSync(marker,'utf8');}catch{return null;}},pause=ms=>new Promise(r=>setTimeout(r,ms));
 for(let i=0;i<600&&read()===null;i++)await pause(100);
 assert.notEqual(read(),null,'the program never started');
 abort.abort();await assert.rejects(run,/cancel/i);
 await pause(1000);const stopped=read();await pause(1500);
 assert.equal(read(),stopped,'the program JVM kept running after cancel');
});
test('real Unicode prefix diagnostic points to the ASCII failing operand in UTF-16',async t=>{
 const a=api(),dir=fixture(t),file=path.join(dir,'unicode.spr');const source='let bad = ["😀", missing]\n';fs.writeFileSync(file,source);
 const r=await a.invoke(compiler,['check',file,'--json'],dir);assert.ok(r.json.diagnostics.length);
 const d=r.json.diagnostics.find(d=>d.range.start.character>=source.indexOf(','));assert.ok(d,JSON.stringify(r.json));
 const mapping=require('../out/diagnostics.js');const range=mapping.mapRange(d.range,source);assert.equal(range.start.character,source.indexOf('missing'));assert.equal(range.end.character,source.indexOf('missing')+7);
});
test('tool queries use their actual protocol: capabilities and explain have no exitCode',async()=>{
 const a=api();for(const args of [['capabilities','--json'],['explain','SPR-TYPE-ASSIGN','--json']]){const r=await a.invoke(compiler,args,root);assert.equal(r.json.exitCode,0);assert.equal(r.json.schemaVersion,1);}
 const caps=await a.invoke(compiler,['capabilities','--json'],root);assert.equal(caps.json.features.emitJavaOnly,true);
});
test('valid JSON with a wrong protocol shape produces a controlled adapter error',async t=>{
 if(process.platform==='win32')return t.skip('POSIX executable fixture');const a=api(),dir=fixture(t),file=path.join(dir,'null-json');fs.writeFileSync(file,"#!/bin/sh\nprintf 'null'\n",{mode:0o755});await assert.rejects(a.invoke(file,[],dir),/JSON|response/i);
});

test('Windows terminal plan invokes the SDK JVM directly and rejects arbitrary batch wrappers',t=>{
 const a=api(),dir=fixture(t),launcher=path.join(dir,'bin','sprig.cmd');
 assert.throws(()=>a.compilerCommand(launcher,['run','server $; 中文.spr'],'win32'),/SDK/);
 fs.mkdirSync(path.join(dir,'build'),{recursive:true});fs.writeFileSync(path.join(dir,'build','sprig-compiler.jar'),'fixture');
 const command=a.compilerCommand(launcher,['run','server $; 中文.spr'],'win32');
 assert.equal(command.command,'java');assert.equal(command.args.includes('sprig.compiler.cli.Main'),true);
 assert.deepEqual(command.args.slice(-2),['run','server $; 中文.spr']);
 assert.equal(command.args[4].includes(';'),true);
 assert.equal(command.args.includes('--json'),false);
 // The same options and classpaths bin/sprig.cmd uses: a source build, then an extracted release SDK.
 assert.equal(command.args[1],'-XX:TieredStopAtLevel=1');
 assert.equal(a.compilerCommand(launcher,['lsp'],'win32').args[1],'-XX:+TieredCompilation','the language server keeps tiered compilation');
 assert.equal(command.args[3],[path.join(dir,'build','sprig-compiler.jar'),path.join(dir,'build','deps','antlr-4.13.2-complete.jar'),path.join(dir,'build','deps','resolver','*')].join(';'));
 const sdk=fixture(t);fs.mkdirSync(path.join(sdk,'lib'),{recursive:true});fs.writeFileSync(path.join(sdk,'lib','sprig-compiler.jar'),'fixture');
 const packaged=a.compilerCommand(path.join(sdk,'bin','sprig.cmd'),['check','--json'],'win32');
 assert.equal(packaged.args[3],path.join(sdk,'lib','*'));assert.equal(packaged.args[4],`-Dsprig.home=${sdk}`);
});
test('query commands without exitCode are accepted: api, help and doctor',async()=>{
 const a=api();
 const module=await a.invoke(compiler,['api',path.join(root,'website/snippets/classes.spr'),'--json'],root);assert.equal(module.json.exitCode,0);assert.equal(module.json.kind,'sprig-module');
 const help=await a.invoke(compiler,['help','match','--json'],root);assert.equal(help.json.exitCode,0);assert.equal(help.json.topic,'match');
 const doctor=await a.invoke(compiler,['doctor','--json'],root);assert.equal(doctor.json.exitCode,0);assert.equal(typeof doctor.json.compilerHome,'string');
 const missing=await a.invoke(compiler,['api','com.example.Missing','--json'],root);assert.equal(missing.json.exitCode,1);assert.equal(missing.json.diagnostics[0].code,'SPR-JVM-CLASS');
});
test('capture runs plain-text commands such as init with adversarial paths',async t=>{
 const a=api(),dir=fixture(t),target=path.join(dir,`new app $; ${nonAscii}`);
 const result=await a.capture(compiler,['init',target],dir);assert.equal(result.code,0);assert.match(result.stdout,/Created/);
 assert.ok(fs.existsSync(path.join(target,'src','main.spr')));
});
