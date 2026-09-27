"""Expression match tests assert front-end errors and independent runtime effects."""
import pathlib, subprocess, tempfile, json
ROOT=pathlib.Path(__file__).resolve().parents[2]
def invoke(*args):
    return subprocess.run([str(ROOT/'bin/sprig'),*map(str,args)],capture_output=True,text=True,timeout=60)
HEADER='''enum Flag:
    On
    Off
generic T:
    variant Maybe:
        Some(value: T)
        None
'''
def main():
    with tempfile.TemporaryDirectory(prefix='sprig-match-expr-') as d:
        p=pathlib.Path(d)/'main.spr'
        positive=HEADER+'''var calls = 0
func make() -> Flag:
    calls += 1
    return Flag.On
func selected() -> Int:
    calls += 10
    return 42
func forbidden() -> Int:
    calls += 100
    return 0
func choose(flag: Flag) -> Int:
    return match flag:
        case Flag.On:
            selected()
        case Flag.Off:
            forbidden()
let answer = match make():
    case Flag.On:
        selected()
    case Flag.Off:
        forbidden()
print(answer)
print(calls)
print(choose(Flag.On))
print(calls)
let m: Maybe[Int] = Maybe[Int].Some(value=7)
let value = match m:
    case Maybe.Some as some:
        some.value
    case Maybe.None:
        0
print(value)
let nullable: String? = match Flag.On:
    case Flag.On:
        "ok"
    case Flag.Off:
        null
print(nullable)
let inferred = match Flag.Off:
    case Flag.On:
        null
    case Flag.Off:
        "fine"
print(inferred)
let small: Int32 = match Flag.On:
    case Flag.On:
        1
    case Flag.Off:
        2
print(small)
'''
        p.write_text(positive)
        result=invoke('run',p)
        assert result.returncode==0 and result.stdout=='42\n11\n42\n21\n7\nok\nfine\n1\n',result.stdout+result.stderr
        advanced=HEADER+'''generic T:
    func unwrap(value: Maybe[T], fallback: T) -> T:
        return match value:
            case Maybe.Some as some:
                some.value
            case Maybe.None:
                fallback
let nullable: Maybe[String?] = Maybe[String?].Some(value=null)
let result: String? = match nullable:
    case Maybe.Some as some:
        some.value
    case Maybe.None:
        "none"
print(result)
print(unwrap[Int](Maybe[Int].None, 9))
print(unwrap[Int32](Maybe[Int32].Some(value=11), 12))
print(unwrap[Bool](Maybe[Bool].None, true))
print(unwrap[String](Maybe[String].Some(value="generic"), "fallback"))
let nested = match Flag.On:
    case Flag.On:
        match Flag.Off:
            case Flag.On:
                1
            case Flag.Off:
                2
    case Flag.Off:
        3
print(nested)
let mapped: fn(Flag) -> Int = fn(flag: Flag) => match flag:
    case Flag.On:
        10
    case Flag.Off:
        20
print(mapped(Flag.Off))
func failing() -> Int throws Error:
    throw Error("selected")
func effect(flag: Flag) -> Int throws Error:
    return match flag:
        case Flag.On:
            3
        case Flag.Off:
            failing()
print(effect(Flag.On))
try:
    print(effect(Flag.Off))
catch error: Error:
    print(error.message)
func via_statement(flag: Flag) -> Int:
    var result = 0
    match flag:
        case Flag.On:
            result = 10
        case Flag.Off:
            result = 20
    return result
print(via_statement(Flag.Off) == mapped(Flag.Off))
'''
        p.write_text(advanced)
        result=invoke('run',p)
        assert result.returncode==0 and result.stdout=='null\n9\n11\ntrue\ngeneric\n2\n20\n3\nselected\ntrue\n',result.stdout+result.stderr
        p.write_text(advanced.replace('func effect(flag: Flag) -> Int throws Error:', 'func effect(flag: Flag) -> Int:'))
        result=invoke('check',p,'--json')
        assert result.returncode!=0 and 'SPR-FLOW-THROWS' in result.stdout,result.stdout+result.stderr
        negatives=[
            ('SPR-MATCH-NONEXHAUSTIVE','let x = match Flag.On:\n    case Flag.On:\n        1\n'),
            ('SPR-MATCH-DUPLICATE','let x = match Flag.On:\n    case Flag.On:\n        1\n    case Flag.On:\n        2\n    case Flag.Off:\n        3\n'),
            ('SPR-MATCH-RESULT','let x = match Flag.On:\n    case Flag.On:\n        1\n    case Flag.Off:\n        "bad"\n'),
            ('SPR-MATCH-INFERENCE','let x = match Flag.On:\n    case Flag.On:\n        null\n    case Flag.Off:\n        null\n'),
            ('SPR-TYPE-UNIT','let x = match Flag.On:\n    case Flag.On:\n        print(1)\n    case Flag.Off:\n        print(2)\n'),
            ('SPR-SYNTAX-ERROR','let x = match Flag.On:\n    case Flag.On:\n        let n = 1\n        n\n    case Flag.Off:\n        2\n'),
            ('SPR-SYNTAX-ERROR','let x = match Flag.On:\n    case Flag.On:\n        1\n        2\n    case Flag.Off:\n        3\n'),
            ('SPR-MATCH-SCRUTINEE','let x = match 1:\n    case Flag.On:\n        1\n    case Flag.Off:\n        2\n'),
            ('SPR-MATCH-ENUM-BINDER','let x = match Flag.On:\n    case Flag.On as on:\n        1\n    case Flag.Off:\n        2\n'),
            ('SPR-NAME-UNRESOLVED','let m: Maybe[Int] = Maybe[Int].Some(value=1)\nlet x = match m:\n    case Maybe.Some as some:\n        some.value\n    case Maybe.None:\n        0\nprint(some)\n'),
        ]
        negatives += [
            ('SPR-MATCH-WRONG-TYPE','enum Other:\n    On\n    Off\nlet x = match Flag.On:\n    case Other.On:\n        1\n    case Flag.Off:\n        2\n'),
            ('SPR-MATCH-SCRUTINEE','let flag: Flag? = null\nlet x = match flag:\n    case Flag.On:\n        1\n    case Flag.Off:\n        2\n'),
            ('SPR-NAME-UNRESOLVED','let m: Maybe[Int] = Maybe[Int].None\nlet x = match m:\n    case Maybe.Some as some:\n        some.value\n    case Maybe.None:\n        some.value\n'),
            ('SPR-SYNTAX-ERROR','let x = match Flag.On:\n    case Flag.On:\n    case Flag.Off:\n        2\n'),
            ('SPR-MATCH-UNKNOWN-CASE','let x = match Flag.On:\n    case _:\n        1\n'),
            ('SPR-TYPE-CAPTURE','func capture(flag: Flag) -> Int:\n    var n = 1\n    let f = fn() => match flag:\n        case Flag.On:\n            n\n        case Flag.Off:\n            0\n    return f()\n'),
        ]
        for code,body in negatives:
            p.write_text(HEADER+body)
            result=invoke('check',p,'--json')
            assert result.returncode!=0 and code in result.stdout,(body,result.stdout,result.stderr)
            assert 'SPR-JVM-INTERNAL' not in result.stdout
        print('match expressions: runtime/evaluation counts, generic payload, contexts, null inference, strict results and negative cases passed')
if __name__=='__main__':main()
