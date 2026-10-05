const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const Module=require('node:module');

function harness(t, options={}) {
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'Sprig terminal 中文 $; '));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 fs.writeFileSync(path.join(dir,'sprig.toml'),'[project]\nname="terminal-test"\n');
 fs.mkdirSync(path.join(dir,'src'));
 const file=path.join(dir,'src','server $; 中文.spr');fs.writeFileSync(file,'print("server")\n');
 const doc={languageId:'sprig',uri:{scheme:'file',fsPath:file},isDirty:!!options.dirty,
  save:async()=>{doc.saveCalls++;if(options.saveResult===false)return false;doc.isDirty=false;return true;},saveCalls:0};
 const callbacks=new Map(), terminals=[], warnings=[];
 const disposable={dispose(){}};
 const compiler=path.join(dir,'SDK space $;','bin','sprig');
 const fakes={
  window:{activeTextEditor:{document:doc},createOutputChannel:()=>disposable,
   showWarningMessage:message=>{warnings.push(message);},showErrorMessage:message=>{throw Error(message);},
   createTerminal:settings=>{const terminal={settings,shown:false,show(){this.shown=true;}};terminals.push(terminal);return terminal;}},
  workspace:{isTrusted:options.trusted!==false,textDocuments:[doc],
   getConfiguration:()=>({get:(name,fallback)=>name==='compilerPath'?compiler:fallback}),
   onDidSaveTextDocument:()=>disposable,onDidChangeTextDocument:()=>disposable},
  languages:{createDiagnosticCollection:()=>disposable,registerCodeActionsProvider:()=>disposable},
  commands:{registerCommand:(name,callback)=>{callbacks.set(name,callback);return disposable;}},
  CodeActionKind:{QuickFix:'quickfix'}
 };
 // Editor features unrelated to the terminal register through inert stand-ins.
 const inert=()=>new Proxy(function(){},{get:(_,key)=>key===Symbol.iterator?function*(){}:key==='then'||typeof key==='symbol'?undefined:inert(),set:()=>true,apply:()=>inert(),construct:()=>inert()});
 const open=object=>new Proxy(object,{get:(target,key)=>key in target||typeof key==='symbol'?target[key]:inert()});
 const vscode=open({...fakes,window:open(fakes.window),workspace:open(fakes.workspace),languages:open(fakes.languages),commands:open(fakes.commands)});
 if(options.peerDirty)vscode.workspace.textDocuments.push({languageId:'sprig',uri:{scheme:'file',fsPath:path.join(dir,'src','peer.spr')},isDirty:true});
 const load=Module._load;
 Module._load=function(name,...rest){return name==='vscode'?vscode:load.call(this,name,...rest);};
 const modulePath=require.resolve('../out/extension.js');delete require.cache[modulePath];
 try{require(modulePath).activate({subscriptions:[],globalStorageUri:{fsPath:path.join(dir,'global')}});}
 finally{Module._load=load;}
 return {doc,callbacks,terminals,warnings,compiler,dir,vscode};
}

test('terminal command launches normal Run with argument arrays, project cwd and no JSON buffering',async t=>{
 const h=harness(t), command=h.callbacks.get('sprig.runInTerminal');
 assert.equal(typeof command,'function','Sprig: Run in Terminal must be registered');
 await command();assert.equal(h.terminals.length,1);
 const terminal=h.terminals[0];
 assert.equal(terminal.settings.shellPath,h.compiler);
 assert.deepEqual(terminal.settings.shellArgs,['run',h.doc.uri.fsPath]);
 assert.equal(terminal.settings.cwd,h.dir);
 assert.equal(terminal.shown,true);
 assert.equal(terminal.settings.shellArgs.includes('--json'),false);
 assert.equal(h.doc.saveCalls,0);
});
test('untrusted workspaces cannot create terminal compiler processes',async t=>{
 const h=harness(t,{trusted:false});
 const command=h.callbacks.get('sprig.runInTerminal');assert.equal(typeof command,'function');
 await command();assert.equal(h.terminals.length,0);assert.match(h.warnings.join(' '),/Trust/);
});
test('terminal run saves the active document and rejects other dirty project files',async t=>{
 const h=harness(t,{dirty:true});await h.callbacks.get('sprig.runInTerminal')();
 assert.equal(h.doc.saveCalls,1);assert.equal(h.terminals.length,1);
 const failed=harness(t,{dirty:true,saveResult:false});await failed.callbacks.get('sprig.runInTerminal')();assert.equal(failed.terminals.length,0);
 const peer=harness(t,{peerDirty:true});await peer.callbacks.get('sprig.runInTerminal')();assert.equal(peer.terminals.length,0);assert.match(peer.warnings.join(' '),/Save/);
});
test('terminal command requires a local saved Sprig document',async t=>{
 const h=harness(t);h.doc.uri.scheme='untitled';await h.callbacks.get('sprig.runInTerminal')();assert.equal(h.terminals.length,0);
 h.vscode.window.activeTextEditor=undefined;await h.callbacks.get('sprig.runInTerminal')();assert.equal(h.terminals.length,0);
});
