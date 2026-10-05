const {test}=require('node:test');
const assert=require('node:assert/strict');
const {scan,referenceAt,inCode,codeOnly,findDecl}=require('../out/symbols.js');

const source=[
 'import "./util.spr" as util',
 'import "@std/text.spr" as text',
 'import "@math/vector.spr"',
 'import java.time.LocalDate as Date',
 'import java.util.ArrayList',
 '',
 '# class Commented:',
 'let greeting = "class Fake: # not a comment"',
 'var count: Int = 0',
 '',
 'generic T:',
 '    class Box:',
 '        let value: T',
 '        var label: String = "box"',
 '',
 '        func describe() -> String:',
 '            let inner = 1',
 '            return label',
 '',
 'variant Shape:',
 '    Circle(radius: Float)',
 '    Square:',
 '        side: Float',
 '    Empty',
 '',
 'enum Mode:',
 '    Fast',
 '    Careful',
 '',
 'func area(shape: Shape,',
 '        scale: Float) -> Float:',
 '    match shape:',
 '        case Shape.Circle as c:',
 '            return c.radius * scale',
 '    return 0.0',
 '',
 'if count > 0:',
 '    let hidden = 2',
 'print(area(Shape.Empty, 1.0))',
].join('\n');
const lines=source.split('\n');

test('imports: aliases, kinds and spec positions',()=>{
 const {imports}=scan(source);
 assert.deepEqual(imports.map(i=>[i.alias,i.kind,i.spec]),[
  ['util','file','./util.spr'],['text','std','@std/text.spr'],['vector','package','@math/vector.spr'],
  ['Date','java','java.time.LocalDate'],['ArrayList','java','java.util.ArrayList']]);
 assert.equal(lines[0].slice(imports[0].start,imports[0].end),'./util.spr');
 assert.equal(lines[3].slice(imports[3].start,imports[3].end),'java.time.LocalDate');
});
test('top-level declarations skip comments, strings and nested bodies',()=>{
 const outline=scan(source);
 assert.deepEqual(outline.declarations.map(d=>[d.kind,d.name]),[['let','greeting'],['var','count'],['class','Box'],['variant','Shape'],['enum','Mode'],['func','area']]);
 const box=findDecl(outline,'Box');
 assert.deepEqual(box.generics,['T']);
 assert.deepEqual(box.children.map(c=>[c.kind,c.name,c.detail]),[['field','value','T'],['field','label','String'],['method','describe','func describe() -> String']]);
 assert.deepEqual(findDecl(outline,'Shape').children.map(c=>[c.name,c.detail]),[['Circle','(radius: Float)'],['Square',''],['Empty','']]);
 assert.deepEqual(findDecl(outline,'Mode').children.map(c=>c.name),['Fast','Careful']);
 assert.equal(findDecl(outline,'count').detail,'Int');
 assert.equal(findDecl(outline,'hidden'),undefined);
});
test('declaration ranges cover their bodies and point at the name',()=>{
 const outline=scan(source),area=findDecl(outline,'area');
 assert.equal(lines[area.line].slice(area.start,area.end),'area');
 assert.equal(lines[area.endLine].trim(),'return 0.0');
 assert.equal(lines[findDecl(outline,'Box').endLine].trim(),'return label');
 assert.equal(lines[findDecl(outline,'Shape').endLine].trim(),'Empty');
 const member=findDecl(outline,'Square','Shape');
 assert.equal(lines[member.line].slice(member.start,member.end),'Square');
 const box=findDecl(outline,'Box');assert.equal(lines[box.line].slice(box.start,box.end),'Box');
});
test('references: qualifier, partial word, strings, comments and chains',()=>{
 assert.deepEqual(referenceAt('print(util.twi',14),{word:'twi',start:11,end:14,qualifier:'util'});
 assert.deepEqual(referenceAt('let d = Date.',13),{word:'',start:13,end:13,qualifier:'Date'});
 assert.deepEqual(referenceAt('Math.max(1, 2)',6),{word:'max',start:5,end:8,qualifier:'Math'});
 assert.deepEqual(referenceAt('class Hero:',2),{word:'class',start:0,end:5,qualifier:undefined});
 assert.equal(referenceAt('print("Math.',12),undefined);
 assert.equal(referenceAt('x = 1 # Math.',13),undefined);
 assert.equal(referenceAt('a.b.',4),undefined);
 assert.equal(referenceAt('let x = 3.14',12),undefined);
 assert.equal(inCode('say("a\\"#b") # c',9),false);
 assert.equal(inCode('say("a") # c',8),true);
 assert.equal(codeOnly('x = "a#b" # c'),'x = "   " ');
});
