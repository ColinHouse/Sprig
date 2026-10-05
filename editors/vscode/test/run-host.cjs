const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const {runTests,downloadAndUnzipVSCode}=require('@vscode/test-electron');
(async()=>{
 const temp=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),'sprig-vscode-host-')));const workspace=path.join(temp,'workspace');fs.mkdirSync(workspace);fs.mkdirSync(path.join(workspace,'.vscode'));
 const restricted=process.env.SPRIG_TEST_RESTRICTED==='1';
 const extensionPath=process.env.SPRIG_EXTENSION_PATH || path.resolve(__dirname,'..');
 const profile=path.join(temp,'profile');fs.mkdirSync(path.join(profile,'User'),{recursive:true});
 // The trusted suite starts with CLI queries and turns the language server on later; Restricted Mode keeps the default, which trust still blocks.
 fs.writeFileSync(path.join(profile,'User/settings.json'),JSON.stringify({'security.workspace.trust.enabled':restricted,'security.workspace.trust.startupPrompt':'never','sprig.checkOnSave':false,...(restricted?{}:{'sprig.languageServer.enabled':false})}));
 let executable=process.env.VSCODE_EXECUTABLE_PATH || await downloadAndUnzipVSCode({version:process.env.VSCODE_VERSION||'stable'});
 // Recent macOS builds renamed Electron to Code; the official runner still returns the old name.
 if(process.platform==='darwin' && !fs.existsSync(executable)){const candidate=path.join(path.dirname(executable),'Code');if(fs.existsSync(candidate))executable=candidate;}
 try{if(restricted){
 await new Promise((resolve,reject)=>{const child=require('node:child_process').spawn(executable,[workspace,'--no-sandbox','--disable-gpu','--disable-extensions','--skip-welcome','--skip-release-notes','--user-data-dir',profile,'--extensionDevelopmentPath='+extensionPath,'--extensionTestsPath='+path.join(__dirname,'host-suite.cjs')],{env:{...process.env,SPRIG_TEST_ROOT:path.resolve(__dirname,'../../..'),SPRIG_TEST_RESTRICTED:'1'},stdio:'inherit'});const timer=setTimeout(()=>{child.kill();reject(new Error('Restricted Host timed out'));},90000);child.on('error',reject);child.on('exit',code=>{clearTimeout(timer);code===0?resolve():reject(new Error('Restricted Host exit '+code));});});
 }else await runTests({version:process.env.VSCODE_VERSION||'stable',extensionDevelopmentPath:extensionPath,extensionTestsPath:path.join(__dirname,'host-suite.cjs'),vscodeExecutablePath:executable,
 extensionTestsEnv:{SPRIG_TEST_ROOT:path.resolve(__dirname,'../../..'),SPRIG_TEST_RESTRICTED:restricted?'1':'0'},launchArgs:[workspace,'--disable-gpu','--disable-extensions',...(restricted?[]:['--disable-workspace-trust']),'--skip-welcome','--skip-release-notes','--user-data-dir',profile]});}
 // On Windows, VS Code helper processes can hold the profile for a moment after the window exits.
 finally{fs.rmSync(temp,{recursive:true,force:true,maxRetries:10,retryDelay:200});}
})().catch(e=>{console.error(e);process.exit(1)});
