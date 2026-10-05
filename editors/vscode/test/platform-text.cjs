// Non-ASCII text the platform can pass to the compiler on its command line.
// java.exe reads its command line in the Windows ANSI code page, so on
// Windows fixtures use a sample that code page carries (ASCII when unknown).
const {execFileSync}=require('node:child_process');
const SAMPLES={874:'สวัสดี',932:'テスト',936:'中文',949:'테스트',950:'中文',1250:'café',1251:'Привет',
  1252:'café',1253:'Ωμέγα',1254:'café',1255:'שלום',1256:'مرحبا',1257:'café',1258:'café'};
function ansiCodePage(){
  const out=execFileSync('reg',['query','HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage','/v','ACP'],{encoding:'utf8'});
  return Number(/ACP\s+REG_SZ\s+(\d+)/.exec(out)?.[1]);
}
exports.nonAscii=process.platform==='win32'?(SAMPLES[ansiCodePage()]??''):'中文';
