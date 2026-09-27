const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vscode=require('vscode');
exports.run=async()=>{
 const root=process.env.SPRIG_TEST_ROOT;const folder=vscode.workspace.workspaceFolders[0].uri.fsPath;
 const ext=vscode.extensions.getExtension('ColinHouse.sprig-language');assert.ok(ext,'extension must be installed/registered');await ext.activate();
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
  console.log('Restricted Host passed: highlighting/language registration available; compiler commands blocked.');return;
 }
 const file=path.join(folder,'hello 中文.spr');fs.writeFileSync(file,'let bad: Int = "wrong"\n');
 let doc=await vscode.workspace.openTextDocument(file);await vscode.window.showTextDocument(doc);assert.equal(doc.languageId,'sprig');
 let checked=await vscode.commands.executeCommand('sprig.check');assert.equal(checked.exitCode,1);assert.ok(vscode.languages.getDiagnostics(doc.uri).some(d=>d.code==='SPR-TYPE-ASSIGN'));
 const edit=new vscode.WorkspaceEdit();edit.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'import "@std/text.spr" as text\nprint(text.trim("  hello extension host  "))\n');await vscode.workspace.applyEdit(edit);await doc.save();
 await vscode.commands.executeCommand('sprig.check');assert.equal(vscode.languages.getDiagnostics(doc.uri).length,0);
 const ran=await vscode.commands.executeCommand('sprig.run');assert.equal(ran.exitCode,0);assert.equal(ran.programOutput.trim(),'hello extension host');
 const built=await vscode.commands.executeCommand('sprig.showGeneratedJava');assert.equal(built.javacInvoked,false);assert.ok(built.javaSources.length);assert.equal(vscode.window.activeTextEditor.document.languageId,'java');assert.ok(vscode.window.activeTextEditor.document.getText().includes('hello extension host'));
 // On-save uses actual compiler feedback without invoking the manual command.
 await config.update('checkOnSave',true,vscode.ConfigurationTarget.Workspace);
 await vscode.window.showTextDocument(doc);
 const bad=new vscode.WorkspaceEdit();bad.replace(doc.uri,new vscode.Range(0,0,doc.lineCount,0),'let wrong: Int = "bad"\n');await vscode.workspace.applyEdit(bad);await doc.save();
 for(let i=0;i<100 && !vscode.languages.getDiagnostics(doc.uri).length;i++)await new Promise(r=>setTimeout(r,100));
 assert.ok(vscode.languages.getDiagnostics(doc.uri).some(d=>d.code==='SPR-TYPE-ASSIGN'),'on-save diagnostics');
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
 for(let i=0;i<50 && !vscode.languages.getDiagnostics(vscode.Uri.file(child)).some(d=>d.code==='SPR-TYPE-ASSIGN');i++)await new Promise(r=>setTimeout(r,100));
 assert.ok(vscode.languages.getDiagnostics(vscode.Uri.file(child)).some(d=>d.code==='SPR-TYPE-ASSIGN'),JSON.stringify({expectedUri:vscode.Uri.file(child).toString(),result:projectChecked,actual:vscode.languages.getDiagnostics().map(([uri,ds])=>[uri.toString(),ds.map(d=>({code:d.code,message:d.message}))])}));
 assert.ok(vscode.languages.getDiagnostics(doc.uri).length,'independent project diagnostics retained');
 const unused=path.join(project,'src','unused.spr');fs.writeFileSync(unused,'print(\"unused\")\n');await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(unused));
 assert.equal((await vscode.commands.executeCommand('sprig.check')).exitCode,1,'entry graph failure must survive a clean unused-file check');
 // Actual integrated terminal invokes normal Run without the finite JSON adapter.
 await config.update('checkOnSave',false,vscode.ConfigurationTarget.Workspace);
 const terminalSource=path.join(folder,'terminal 中文 $;.spr'), marker=path.join(folder,'terminal-marker.txt');
 fs.writeFileSync(terminalSource,'import "@std/files.spr" as files\nfiles.write_utf8('+JSON.stringify(marker)+', "terminal execution")\n');
 await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(terminalSource));
 const terminal=await vscode.commands.executeCommand('sprig.runInTerminal');assert.ok(terminal,'integrated terminal created');
 try {
  for(let i=0;i<100 && !fs.existsSync(marker);i++)await new Promise(r=>setTimeout(r,100));
  assert.equal(fs.existsSync(marker),true,'actual terminal JVM must execute the saved source');
  assert.equal(fs.readFileSync(marker,'utf8'),'terminal execution');
 } finally {terminal.dispose();}
 console.log('Extension Host passed: registered language, check/error repair, actual JVM Run, Java-only view and save diagnostics.');
};
