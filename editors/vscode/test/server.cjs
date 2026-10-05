const {test}=require('node:test');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const rpc=require('vscode-jsonrpc/node');
const root=path.resolve(__dirname,'../../..');
const compiler=path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig');
const {compilerCommand,invoke,supportsLanguageServer}=require('../out/compiler.js');

test('capabilities, not the version, tell whether the compiler has a language server',async()=>{
 const capabilities=(await invoke(compiler,['capabilities','--json'],root)).json;
 assert.equal(supportsLanguageServer(capabilities),true);
 assert.equal(supportsLanguageServer({...capabilities,features:{...capabilities.features,languageServer:false}}),false);
 // v0.5.0-beta.1 reports no languageServer feature; the extension then keeps its CLI queries.
 assert.equal(supportsLanguageServer({schemaVersion:1,exitCode:0,features:{formatter:true}}),false);
});

test('the server command the extension starts speaks LSP, including on Windows and outside the ANSI code page',async t=>{
 // The document path travels inside the protocol, never on a command line, so any Unicode works.
 const dir=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),'Sprig lsp 😀 中文 ')));
 const file=path.join(dir,'main.spr'),text='let total: Int = 1\nlet wrong: Int = "bad"\n';fs.writeFileSync(file,text);
 const plan=compilerCommand(compiler,['lsp']);
 const child=spawn(plan.command,plan.args,{cwd:dir,windowsHide:true});
 const exited=new Promise(resolve=>child.on('exit',code=>resolve(code)));
 let stderr='';child.stderr.on('data',chunk=>{stderr+=chunk;});
 const connection=rpc.createMessageConnection(new rpc.StreamMessageReader(child.stdout),new rpc.StreamMessageWriter(child.stdin));
 t.after(()=>{
  connection.dispose();
  if(child.exitCode===null && child.signalCode===null){
   if(process.platform==='win32') spawn('taskkill',['/pid',String(child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});
   else child.kill('SIGKILL');
  }
  fs.rmSync(dir,{recursive:true,force:true});
 });
 const uri=pathToFileURL(file).href;
 const published=new Promise(resolve=>connection.onNotification('textDocument/publishDiagnostics',params=>{if(params.uri===uri && params.diagnostics.length) resolve(params.diagnostics);}));
 connection.listen();
 const {capabilities,serverInfo}=await connection.sendRequest('initialize',{processId:process.pid,rootUri:pathToFileURL(dir).href,capabilities:{}});
 assert.equal(serverInfo.name,'sprig');
 assert.ok(capabilities.hoverProvider && capabilities.definitionProvider && capabilities.referencesProvider && capabilities.documentFormattingProvider);
 connection.sendNotification('initialized',{});
 connection.sendNotification('textDocument/didOpen',{textDocument:{uri,languageId:'sprig',version:1,text}});
 const [diagnostic]=await published;
 assert.equal(diagnostic.code,'SPR-TYPE-ASSIGN');assert.equal(diagnostic.range.start.line,1);
 assert.deepEqual(diagnostic.data,{relatedHelp:'types'},'the help topic the extension offers in the lightbulb');
 const hover=await connection.sendRequest('textDocument/hover',{textDocument:{uri},position:{line:0,character:5}});
 assert.match(hover.contents.value,/let total: Int/);
 await connection.sendRequest('shutdown');
 connection.sendNotification('exit');
 assert.equal(await exited,0,stderr);
});
