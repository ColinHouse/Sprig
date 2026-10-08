"""Expression match tests assert front-end errors and independent runtime effects."""
import pathlib, os, subprocess, tempfile, json
ROOT=pathlib.Path(__file__).resolve().parents[2]
def invoke(*args):
    return subprocess.run([str(ROOT/'bin'/('sprig.cmd' if os.name=='nt' else 'sprig')),*map(str,args)],capture_output=True,text=True,timeout=60)
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
        nullable_context = 'import java.lang.System as System\n' + HEADER + '''let absent: Int? = match Flag.On:
    case Flag.On:
        null
    case Flag.Off:
        null
print(absent)
let host: String? = match Flag.On:
    case Flag.On:
        System.getProperty("java.version")
    case Flag.Off:
        null
if host != null:
    print(host.length() > 0)
class Holder:
    let value: Int = match Flag.On:
        case Flag.On:
            5
        case Flag.Off:
            6
print(Holder().value)
let payload: Maybe[Int] = Maybe[Int].Some(value=4)
class BoundDefault:
    let value: Int = match payload:
        case Maybe.Some as value:
            value.value
        case Maybe.None:
            0
print(BoundDefault().value)
'''
        p.write_text(nullable_context)
        result=invoke('run',p)
        assert result.returncode==0 and result.stdout=='null\ntrue\n5\n4\n',result.stdout+result.stderr
        assert invoke('fmt',p).returncode==0
        formatted=invoke('run',p)
        assert (formatted.returncode,formatted.stdout,formatted.stderr)==(result.returncode,result.stdout,result.stderr)
        boxed_yields = HEADER + '''let absent_int: Int? = null
let absent_small: Int32? = null
let absent_flag: Bool? = null
let absent_float: Float? = null
let absent_float32: Float32? = null
let absent_text: String? = null
func null_int(flag: Flag) -> Int?:
    return match flag:
        case Flag.On:
            absent_int
        case Flag.Off:
            42
func null_int32(flag: Flag) -> Int32?:
    return match flag:
        case Flag.On:
            absent_small
        case Flag.Off:
            7
func null_bool(flag: Flag) -> Bool?:
    return match flag:
        case Flag.On:
            absent_flag
        case Flag.Off:
            true
func null_float(flag: Flag) -> Float?:
    return match flag:
        case Flag.On:
            absent_float
        case Flag.Off:
            1.5
func null_float32(flag: Flag) -> Float32?:
    return match flag:
        case Flag.On:
            absent_float32
        case Flag.Off:
            2.5
func text(yes: Bool) -> String?:
    if yes:
        return "text"
    return null
func null_string(flag: Flag) -> String?:
    return match flag:
        case Flag.On:
            absent_text
        case Flag.Off:
            text(true)
func null_calc(flag: Flag) -> Int?:
    let result: Int? = match flag:
        case Flag.On:
            absent_int
        case Flag.Off:
            1 + 1
    return result
print(null_int(Flag.On))
print(null_int(Flag.Off))
print(null_int32(Flag.On))
print(null_int32(Flag.Off))
print(null_bool(Flag.On))
print(null_bool(Flag.Off))
print(null_float(Flag.On))
print(null_float(Flag.Off))
print(null_float32(Flag.On))
print(null_float32(Flag.Off))
print(null_string(Flag.On))
print(null_string(Flag.Off))
print(null_calc(Flag.On))
print(null_calc(Flag.Off))
'''
        p.write_text(boxed_yields)
        result=invoke('run',p)
        assert result.returncode==0 and result.stdout==('null\n42\nnull\n7\nnull\ntrue\nnull\n1.5\n'
            'null\n2.5\nnull\ntext\nnull\n2\n'),result.stdout+result.stderr
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
            ('SPR-TYPE-CAPTURE','func capture(flag: Flag) -> Int:\n    var n = 0\n    let values = [10, 20]\n    let f = fn() => match flag:\n        case Flag.On:\n            values[n]\n        case Flag.Off:\n            0\n    n = 1\n    return f()\nprint(capture(Flag.On))\n'),
            ('SPR-NAME-UNRESOLVED','let field: Int = 99\nclass InvalidDefault:\n    let field: Int = match Flag.On:\n        case Flag.On:\n            field\n        case Flag.Off:\n            0\nprint(InvalidDefault().field)\n'),
            ('SPR-TYPE-NULL','let x: String = match Flag.On:\n    case Flag.On:\n        "value"\n    case Flag.Off:\n        null\n'),
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
