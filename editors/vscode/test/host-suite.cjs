const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vscode=require('vscode');
exports.run=async()=>{
 const codeOf=d=>typeof d.code==='object'?d.code.value:d.code;
 const sleep=ms=>new Promise(r=>setTimeout(r,ms));
 const root=process.env.SPRIG_TEST_ROOT;const folder=vscode.workspace.workspaceFolders[0].uri.fsPath;
 const ext=vscode.extensions.getExtension('ColinHouse.sprig-language');assert.ok(ext,'extension must be installed/registered');await ext.activate();
 assert.equal(ext.exports.languageServer(),'off','no language server without trust, or before it is enabled');
 const config=vscode.workspace.getConfiguration('sprig');await config.update('compilerPath',path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig'),vscode.ConfigurationTarget.Workspace);
 await config.update('checkOnSave',false,vscode.ConfigurationTarget.Workspace);
 if(process.env.SPRIG_TEST_RESTRICTED==='1') {
  assert.equal(vscode.workspace.isTrusted,false,'restricted workspace');
  const file=path.join(folder,'restricted.spr');fs.writeFileSync(file,'print("safe")\n');
  const doc=await vscode.workspace.openTextDocument(file);await vscode.window.showTextDocument(doc);assert.equal(doc.languageId,'sprig');
  assert.equal(await vscode.commands.executeCommand('sprig.check'),undefined);
  assert.equal(await vscode.commands.executeCommand('sprig.run'),undefined);
  const terminalCount=vscode.window.terminals.length;assert.equal(await vscode.commands.executeCommand('sprig.runInTerminal'),undefined);assert.equal(vscode.window.terminals.length,terminalCount);
  assert.equal(await vscode.commands.executeCommand('sprig.showGeneratedJava'),undefined);
  assert.equal(vscode.languages.getDiagnostics(doc.uri).length,0);
  const outlineFile=path.join(folder,'restricted outline.spr');fs.writeFileSync(outlineFile,'import java.lang.Math as Math\nclass Hero:\n    let name: String\nprint(Math.max(1, 2))\n');
  const outlineDoc=await vscode.workspace.openTextDocument(outlineFile);
  assert.deepEqual((await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider',outlineDoc.uri)).map(s=>s.name),['Hero']);
  assert.equal((await vscode.commands.executeCommand('vscode.executeHoverProvider',outlineDoc.uri,new vscode.Position(3,12))).length,0,'no compiler hover without trust');
  const restrictedEdits=await vscode.commands.executeCommand('vscode.executeFormatDocumentProvider',outlineDoc.uri,{tabSize:4,insertSpaces:true});
  assert.ok(!restrictedEdits||restrictedEdits.length===0,'no formatting without trust');
  assert.equal(await vscode.commands.executeCommand('sprig.runTests'),undefined);
  console.log('Restricted Host passed: highlighting/language registration available; compiler commands blocked.');return;
 }
 const file=path.join(folder,'hello 中文.spr');fs.writeFileSync(file,'let bad: Int = "wrong"\n');
 let doc=await vscode.workspace.openTextDocument(file);await vscode.window.showTextDocument(doc);assert.equal(doc.languageId,'sprig');
 let checked=await vscode.commands.executeCommand('sprig.check');assert.equal(checked.exitCode,1);assert.ok(vscode.languages.getDiagnostics(doc.uri).some(d=>codeOf(d)==='SPR-TYPE-ASSIGN'));
 const linked=vscode.languages.getDiagnostics(doc.uri).find(d=>codeOf(d)==='SPR-TYPE-ASSIGN');
 assert.ok(String(linked.code.target).endsWith('/reference/tooling/diagnostic-codes'),'diagnostic code links to the docs');
 const actions=await vscode.commands.executeCommand('vscode.executeCodeActionProvider',doc.uri,linked.range);
 assert.ok(actions.some(a=>a.title==='Explain SPR-TYPE-ASSIGN'));assert.ok(actions.some(a=>a.title==='Show sprig help types'));
 const edit=new vscode.WorkspaceEdit();edit.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'import "@std/text.spr" as text\nprint(text.trim("  hello extension host  "))\n');await vscode.workspace.applyEdit(edit);await doc.save();
 await vscode.commands.executeCommand('sprig.check');assert.equal(vscode.languages.getDiagnostics(doc.uri).length,0);
 const ran=await vscode.commands.executeCommand('sprig.run');assert.equal(ran.exitCode,0);assert.equal(ran.programOutput.trim(),'hello extension host');
 const built=await vscode.commands.executeCommand('sprig.showGeneratedJava');assert.equal(built.javacInvoked,false);assert.ok(built.javaSources.length);assert.equal(vscode.window.activeTextEditor.document.languageId,'java');assert.ok(vscode.window.activeTextEditor.document.getText().includes('hello extension host'));
 // On-save uses actual compiler feedback without invoking the manual command.
 await config.update('checkOnSave',true,vscode.ConfigurationTarget.Workspace);
 await vscode.window.showTextDocument(doc);
 const bad=new vscode.WorkspaceEdit();bad.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'let wrong: Int = "bad"\n');await vscode.workspace.applyEdit(bad);await doc.save();
 for(let i=0;i<100 && !vscode.languages.getDiagnostics(doc.uri).length;i++)await new Promise(r=>setTimeout(r,100));
 assert.ok(vscode.languages.getDiagnostics(doc.uri).some(d=>codeOf(d)==='SPR-TYPE-ASSIGN'),'on-save diagnostics');
 const repaired=new vscode.WorkspaceEdit();repaired.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'print("repaired save")\n');await vscode.workspace.applyEdit(repaired);await doc.save();
 for(let i=0;i<100 && vscode.languages.getDiagnostics(doc.uri).length;i++)await new Promise(r=>setTimeout(r,100));
 assert.equal(vscode.languages.getDiagnostics(doc.uri).length,0,'on-save repair clears errors');
 // Restore standalone error to check isolation when another project is checked.
 const isolated=new vscode.WorkspaceEdit();isolated.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'let wrong: Int = "bad"\n');await vscode.workspace.applyEdit(isolated);await doc.save();await vscode.commands.executeCommand('sprig.check');
 // Project root discovery and diagnostics from imported modules.
 const project=path.join(folder,'nested project');fs.mkdirSync(path.join(project,'src'),{recursive:true});
 fs.writeFileSync(path.join(project,'sprig.toml'),'[project]\nname = "editor-test"\nversion = "0.1.0"\nlanguage = "0.8"\nsource = "src"\nentry = "src/main.spr"\n');
 const child=path.join(project,'src','child.spr');fs.writeFileSync(child,'let x: Int = "bad"\n');
 const main=path.join(project,'src','main.spr');fs.writeFileSync(main,'import "child.spr" as child\nprint("project")\n');
 const adapter=require(path.join(ext.extensionPath,'out/compiler.js'));const locked=await adapter.invoke(path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig'),['resolve','--json'],project);assert.equal(locked.json.exitCode,0);
 const mainDoc=await vscode.workspace.openTextDocument(main);await vscode.window.showTextDocument(mainDoc);const projectChecked=await vscode.commands.executeCommand('sprig.check');assert.equal(projectChecked.exitCode,1);
 for(let i=0;i<50 && !vscode.languages.getDiagnostics(vscode.Uri.file(child)).some(d=>codeOf(d)==='SPR-TYPE-ASSIGN');i++)await new Promise(r=>setTimeout(r,100));
 assert.ok(vscode.languages.getDiagnostics(vscode.Uri.file(child)).some(d=>codeOf(d)==='SPR-TYPE-ASSIGN'),JSON.stringify({expectedUri:vscode.Uri.file(child).toString(),result:projectChecked,actual:vscode.languages.getDiagnostics().map(([uri,ds])=>[uri.toString(),ds.map(d=>({code:codeOf(d),message:d.message}))])}));
 assert.ok(vscode.languages.getDiagnostics(doc.uri).length,'independent project diagnostics retained');
 const unused=path.join(project,'src','unused.spr');fs.writeFileSync(unused,'print(\"unused\")\n');await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(unused));
 assert.equal((await vscode.commands.executeCommand('sprig.check')).exitCode,1,'entry graph failure must survive a clean unused-file check');
 // Language features from lexical outline plus compiler queries.
 await config.update('checkOnSave',false,vscode.ConfigurationTarget.Workspace);
 const lang=path.join(folder,'language features');fs.mkdirSync(lang,{recursive:true});
 fs.writeFileSync(path.join(lang,'helper.spr'),'func twice(x: Int) -> Int:\n    return x * 2\n');
 const featureFile=path.join(lang,'features.spr');
 fs.writeFileSync(featureFile,'import java.lang.Math as Math\nimport "./helper.spr" as helper\n\nclass Hero:\n    let name: String\n    var health: Int = 100\n\nfunc describe(hero: Hero) -> String:\n    return hero.name\n\nlet total = Math.max(1, 2)\nprint(helper.twice(total))\n');
 const featureDoc=await vscode.workspace.openTextDocument(featureFile);await vscode.window.showTextDocument(featureDoc);
 const symbols=await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider',featureDoc.uri);
 assert.deepEqual(symbols.map(s=>s.name),['Hero','describe','total']);assert.deepEqual(symbols[0].children.map(s=>s.name),['name','health']);
 const hoverAt=async(line,character)=>((await vscode.commands.executeCommand('vscode.executeHoverProvider',featureDoc.uri,new vscode.Position(line,character)))??[]).flatMap(h=>h.contents.map(c=>typeof c==='string'?c:c.value)).join('\n');
 assert.ok((await hoverAt(10,18)).includes('max(Int, Int) -> Int'),'Java member hover');
 assert.ok((await hoverAt(11,14)).includes('func twice(x: Int) -> Int'),'module member hover');
 assert.ok((await hoverAt(3,2)).includes('sprig help classes'),'keyword hover');
 assert.ok((await hoverAt(7,6)).includes('func describe(hero: Hero) -> String'),'own declaration hover');
 const draft=path.join(lang,'draft.spr');fs.writeFileSync(draft,'import java.lang.Math as Math\nimport "./helper.spr" as helper\nMath.\nhelper.\n');
 const draftDoc=await vscode.workspace.openTextDocument(draft);
 const complete=async(line,character)=>(await vscode.commands.executeCommand('vscode.executeCompletionItemProvider',draftDoc.uri,new vscode.Position(line,character),'.')).items.map(i=>typeof i.label==='string'?i.label:i.label.label);
 const mathItems=await complete(2,5);assert.ok(mathItems.includes('max')&&mathItems.includes('abs'),'Java static completion');
 assert.ok((await complete(3,7)).includes('twice'),'module completion');
 const definitionAt=async(line,character)=>(await vscode.commands.executeCommand('vscode.executeDefinitionProvider',featureDoc.uri,new vscode.Position(line,character))).map(l=>l.uri?{uri:l.uri,range:l.range}:{uri:l.targetUri,range:l.targetRange});
 const toHelper=await definitionAt(11,14);assert.equal(toHelper[0].uri.fsPath,path.join(lang,'helper.spr'));assert.equal(toHelper[0].range.start.line,0);
 assert.equal((await definitionAt(1,10))[0].uri.fsPath,path.join(lang,'helper.spr'));
 assert.equal((await definitionAt(7,22))[0].range.start.line,3);
 const messy=path.join(lang,'messy.spr');fs.writeFileSync(messy,'func add(a:Int,b:Int)->Int:\n  return a+b\n');
 const messyDoc=await vscode.workspace.openTextDocument(messy);
 const edits=await vscode.commands.executeCommand('vscode.executeFormatDocumentProvider',messyDoc.uri,{tabSize:4,insertSpaces:true});
 const formatting=new vscode.WorkspaceEdit();formatting.set(messyDoc.uri,edits);await vscode.workspace.applyEdit(formatting);
 assert.equal(messyDoc.getText(),'func add(a: Int, b: Int) -> Int:\n    return a + b\n');
 // Test panel: discovery and a real `sprig test` run through the controller.
 const tested=path.join(folder,'tested project');fs.mkdirSync(path.join(tested,'src'),{recursive:true});fs.mkdirSync(path.join(tested,'tests','compile_fail'),{recursive:true});
 fs.writeFileSync(path.join(tested,'sprig.toml'),'[project]\nname = "tested"\nversion = "0.1.0"\nlanguage = "0.8"\n');
 fs.writeFileSync(path.join(tested,'src','main.spr'),'print("main")\n');
 fs.writeFileSync(path.join(tested,'tests','pass.spr'),'print("ok")\n');
 fs.writeFileSync(path.join(tested,'tests','fail.spr'),'throw Error("boom")\n');
 fs.writeFileSync(path.join(tested,'tests','compile_fail','wrong.spr'),'let x: Int = "s"\n');
 fs.writeFileSync(path.join(tested,'tests','compile_fail','wrong.expect.toml'),'codes = ["SPR-TYPE-ASSIGN"]\nexact = true\n');
 assert.equal((await adapter.invoke(path.join(root,'bin',process.platform==='win32'?'sprig.cmd':'sprig'),['resolve','--json'],tested)).json.exitCode,0);
 await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(path.join(tested,'src','main.spr')));
 const testResult=await vscode.commands.executeCommand('sprig.runTests');
 assert.deepEqual(testResult.summary,{total:3,passed:2,failed:1});
 // Project commands: create, resolve and the registered command set.
 const created=await vscode.commands.executeCommand('sprig.newProject',folder,'created app');
 assert.equal(created,path.join(folder,'created app'));assert.ok(fs.existsSync(path.join(created,'src','main.spr')));assert.ok(fs.existsSync(path.join(created,'sprig.lock')));
 const resolved=await vscode.commands.executeCommand('sprig.resolveDependencies');assert.equal(resolved.exitCode,0);
 const registered=await vscode.commands.getCommands(true);
 for(const id of ['sprig.newProject','sprig.resolveDependencies','sprig.runTests','sprig.openDocumentation','sprig.showActions','sprig.showHelp','sprig.restartLanguageServer'])assert.ok(registered.includes(id),id);
 // Language server: unsaved edits are checked, locals have types, and navigation crosses modules.
 const until=async(check,what)=>{for(let i=0;i<600;i++){const value=await check();if(value)return value;await sleep(100);}assert.fail(what);};
 const serverState=async state=>until(()=>ext.exports.languageServer()===state,`language server ${state}, not ${ext.exports.languageServer()}`);
 await config.update('languageServer.enabled',true,vscode.ConfigurationTarget.Workspace);await serverState('running');
 const served=path.join(folder,'language server');fs.mkdirSync(served,{recursive:true});
 fs.writeFileSync(path.join(served,'shapes.spr'),'class Box:\n    let width: Int\n\nfunc area(box: Box) -> Int:\n    return box.width * box.width\n');
 const liveFile=path.join(served,'live.spr');
 fs.writeFileSync(liveFile,'import "./shapes.spr" as shapes\n\nfunc report(size: Int) -> String:\n    let box = shapes.Box(width=size)\n    let label = "area " + shapes.area(box).toString()\n    return label\n\nprint(report(3))\n');
 const live=await vscode.workspace.openTextDocument(liveFile);await vscode.window.showTextDocument(live);
 const typo=new vscode.WorkspaceEdit();typo.insert(live.uri,new vscode.Position(live.lineCount-1,0),'let wrong: Int = "bad"\n');await vscode.workspace.applyEdit(typo);assert.ok(live.isDirty);
 const typed=await until(()=>vscode.languages.getDiagnostics(live.uri).find(d=>codeOf(d)==='SPR-TYPE-ASSIGN'),'diagnostics for an unsaved edit');
 assert.equal(typed.source,'Sprig');assert.ok(String(typed.code.target).endsWith('/reference/tooling/diagnostic-codes'),'server codes link to the docs too');
 const fixes=await vscode.commands.executeCommand('vscode.executeCodeActionProvider',live.uri,typed.range);
 assert.ok(fixes.some(a=>a.title==='Explain SPR-TYPE-ASSIGN')&&fixes.some(a=>a.title==='Show sprig help types'),JSON.stringify(fixes.map(a=>a.title)));
 const untypo=new vscode.WorkspaceEdit();untypo.delete(live.uri,new vscode.Range(live.lineCount-2,0,live.lineCount-1,0));await vscode.workspace.applyEdit(untypo);
 await until(()=>vscode.languages.getDiagnostics(live.uri).length===0,'an unsaved repair clears the diagnostics');await live.save();
 const hoverIn=async(doc,line,character)=>((await vscode.commands.executeCommand('vscode.executeHoverProvider',doc.uri,new vscode.Position(line,character)))??[]).flatMap(h=>h.contents.map(c=>typeof c==='string'?c:c.value));
 assert.ok((await hoverIn(live,3,9)).some(text=>text.includes('let box: Box')),'type of a local from the server');
 const keyword=await hoverIn(live,2,1);assert.equal(keyword.length,1,'one hover: the server says nothing about keywords');assert.ok(keyword[0].includes('sprig help functions'));
 const dot=new vscode.WorkspaceEdit();dot.insert(live.uri,new vscode.Position(6,0),'    box.\n');await vscode.workspace.applyEdit(dot);
 const members=(await vscode.commands.executeCommand('vscode.executeCompletionItemProvider',live.uri,new vscode.Position(6,8),'.')).items.map(i=>typeof i.label==='string'?i.label:i.label.label);
 assert.ok(members.includes('width'),'members of a local variable: '+JSON.stringify(members));
 const undot=new vscode.WorkspaceEdit();undot.delete(live.uri,new vscode.Range(6,0,7,0));await vscode.workspace.applyEdit(undot);await live.save();
 const located=(await vscode.commands.executeCommand('vscode.executeDefinitionProvider',live.uri,new vscode.Position(4,34))).map(l=>l.uri?{uri:l.uri,range:l.range}:{uri:l.targetUri,range:l.targetRange});
 assert.equal(located.length,1);assert.equal(located[0].uri.fsPath,path.join(served,'shapes.spr'));assert.equal(located[0].range.start.line,3);
 const references=await vscode.commands.executeCommand('vscode.executeReferenceProvider',live.uri,new vscode.Position(4,34));
 assert.deepEqual(references.map(l=>path.basename(l.uri.fsPath)).sort(),['live.spr','shapes.spr']);
 const renamed=await vscode.commands.executeCommand('vscode.executeDocumentRenameProvider',live.uri,new vscode.Position(5,12),'text');
 assert.equal(renamed.entries().find(([uri])=>uri.fsPath===live.uri.fsPath)?.[1].length,2,'rename a local and its use');
 assert.deepEqual((await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider',live.uri)).map(s=>s.name),['report'],'one outline provider at a time');
 const messyFile=path.join(served,'messy.spr');fs.writeFileSync(messyFile,'func add(a:Int,b:Int)->Int:\n  return a+b\n');
 const messyServed=await vscode.workspace.openTextDocument(messyFile);
 const serverEdits=await vscode.commands.executeCommand('vscode.executeFormatDocumentProvider',messyServed.uri,{tabSize:4,insertSpaces:true});
 const reformat=new vscode.WorkspaceEdit();reformat.set(messyServed.uri,serverEdits);await vscode.workspace.applyEdit(reformat);
 assert.equal(messyServed.getText(),'func add(a: Int, b: Int) -> Int:\n    return a + b\n','formatting through the server');await messyServed.save();
 // A manual check keeps reporting closed files, but the server alone reports open ones.
 const saveBad=new vscode.WorkspaceEdit();saveBad.insert(live.uri,new vscode.Position(live.lineCount-1,0),'let wrong: Int = "bad"\n');await vscode.workspace.applyEdit(saveBad);await live.save();
 await until(()=>vscode.languages.getDiagnostics(live.uri).some(d=>codeOf(d)==='SPR-TYPE-ASSIGN'),'server diagnostics after save');
 await vscode.window.showTextDocument(live);assert.equal((await vscode.commands.executeCommand('sprig.check')).exitCode,1);
 assert.equal(vscode.languages.getDiagnostics(live.uri).filter(d=>codeOf(d)==='SPR-TYPE-ASSIGN').length,1,'no duplicate from the CLI check');
 assert.equal(await vscode.commands.executeCommand('sprig.restartLanguageServer'),'running');
 await config.update('languageServer.enabled',false,vscode.ConfigurationTarget.Workspace);await serverState('off');
 assert.deepEqual((await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider',live.uri)).map(s=>s.name),['report','wrong'],'the lexical outline, with top-level bindings, returns');
 // Actual integrated terminal invokes normal Run without the finite JSON adapter.
 await config.update('checkOnSave',false,vscode.ConfigurationTarget.Workspace);
 const terminalSource=path.join(folder,'terminal 中文 $;.spr'), marker=path.join(folder,'terminal-marker.txt');
 fs.writeFileSync(terminalSource,'import "@std/files.spr" as files\nfiles.write_utf8('+JSON.stringify(marker)+', "terminal execution")\n');
 await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(terminalSource));
 const terminal=await vscode.commands.executeCommand('sprig.runInTerminal');assert.ok(terminal,'integrated terminal created');
 try {
  const readMarker=()=>{try{return fs.readFileSync(marker,'utf8')}catch{return null}};
  let content=readMarker();
  for(let i=0;i<100 && content!=='terminal execution';i++){await new Promise(r=>setTimeout(r,100));content=readMarker();}
  assert.equal(content,'terminal execution','actual terminal JVM must execute the saved source');
 } finally {terminal.dispose();}
 // Rendered pages open beside the editor and may take focus some time later,
 // depending on the VS Code version, so they run last: no later step needs the editor.
 const page=await vscode.commands.executeCommand('sprig.explainDiagnostic','SPR-TYPE-ASSIGN');assert.match(page,/^# SPR-TYPE-ASSIGN\n/);
 const topic=await vscode.commands.executeCommand('sprig.showHelp','nullability');assert.match(topic,/`sprig help nullability`/);
 console.log('Extension Host passed: registered language, check/error repair, actual JVM Run, Java-only view and save diagnostics.');
};
