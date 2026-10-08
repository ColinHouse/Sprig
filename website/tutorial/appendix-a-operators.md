# 附录 A. 运算符与优先级

本附录是速查表：把 Sprig 的全部运算符放在一起，给出优先级、结合性和每种运算的真实行为。文中每个程序都来自仓库里的 snippet，输出由编译器生成。

## A.1 运算符一览

**赋值**（只能写在语句开头，右边是表达式）：

| 运算符 | 写法 | 说明 |
|---|---|---|
| `=` | `total = 0` | 赋值；目标必须是 `var` 变量、`var` 字段或可变集合的元素 |
| `+=` | `total += 1` | 加后再赋值 |
| `-=` | `total -= 1` | 减后再赋值 |
| `*=` | `total *= 2` | 乘后再赋值 |
| `/=` | `ratio /= 2.0` | 除后再赋值；整数仍不允许 `/` |

**逻辑**：`and`、`or`、`not`，操作数必须是 `Bool`。

**比较**：`==`、`!=`、`<`、`<=`、`>`、`>=`、`in`。

**算术**：二元的 `+`、`-`、`*`、`/`、`%`，前缀的 `+`、`-`。

**后缀**：`.成员`、`(参数)`、`[下标]`，以及泛型用的 `[类型参数]`。

**其他符号**：`:`（块、类型标注、map 字面量）、`,`、`->`（函数返回值）、`=>`（lambda 体）、`?`（可空类型后缀）、`#`（注释）。

## A.2 优先级与结合性

从最松（先算的在后）到最紧：

| 级别 | 运算符 | 结合性 | 例子与结果 |
|---|---|---|---|
| 1 | `or` | 左 | `true or false and false` → `true` |
| 2 | `and` | 左 | |
| 3 | `not`（前缀） | 右 | `not 1 == 2` → `not (1 == 2)` → `true` |
| 4 | `==` `!=` `<` `<=` `>` `>=` `in` | 不可连写 | `1 + 1 in [2]` → `(1 + 1) in [2]` → `true` |
| 5 | `+` `-`（二元） | 左 | `10 - 2 - 3` → `5` |
| 6 | `*` `/` `%` | 左 | `1 + 2 * 3` → `7` |
| 7 | `+` `-`（一元） | 前缀 | `-2 * 3` → `-6` |
| 8 | `.成员` `(参数)` `[下标]` | 左 | `items.size()`、`text[0]` |
| 9 | 字面量、`(...)`、`[...]`、`{...}` | — | `(true or false) and false` → `false` |

赋值不是表达式，是整个语句，不能连写：

<<< @/snippets/book/appendix_a_assign_chain.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:4:7: Expected the end of the line, found '='
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

比较也不能连写（下面的"故意写错"一节里有完整的报错）。

## A.3 优先级演示

<<< @/snippets/book/appendix_a_precedence.spr

```text
7
7
5
2
-6
3
true
true
false
true
true
true
```

逐行说明：

- `1 + 2 * 3` 是 `1 + (2 * 3)`；`2 * 3 + 1` 说明乘除级别都高于加减。
- `10 - 2 - 3` 是 `(10 - 2) - 3`，同级从左往右。
- `-2 * 3` 先取负再相乘；`- -3` 就是 3。
- `not 1 == 2` 里 `not` 比 `==` 松，所以先比较、再取反。
- `true or false and false` 里 `and` 比 `or` 紧；加括号后 `(true or false) and false` 才是 `false`。
- `1 + 1 in [2]` 里 `+` 比 `in` 紧；`"a" in "abc"` 在字符串里找子串；`"a" in {"a": 1}` 在 map 里找键。

## A.4 运算符的行为

<<< @/snippets/book/appendix_a_semantics.spr

```text
false
true
called true
true
-1
1
3 then 12
18
3.5
```

逐行说明：

- `false and loud_true()` 和 `true or loud_false()` 没有打印 `called ...`，说明 `and`/`or` 短路：右边不需要求值时不调用。
- `true and loud_true()` 才调用了右边。
- `-7 % 3` 是 `-1`，`7 % -3` 是 `1`：`%` 结果的符号跟着被除数，和 Java 相同，和 Python 相反。
- `1 + 2 + " then " + 1 + 2` 打印 `3 then 12`：前两个 `Int` 先相加，从遇到文字开始才拼接。
- `total += 3`、`-=`、`*=` 依次得到 18；`ratio /= 2.0` 得到 `3.5`（`/=` 可以用于浮点数）。
- `var` 才能被赋值；`let` 绑定一次（见第 3 章）。

各类型的细节：

| 运算符 | 操作数 | 行为 |
|---|---|---|
| `+` | 一侧是 `String` | 把另一侧变成它的文字并拼接，不必先 `toString()` |
| `+ - *` | 两个同家族的数值 | `Int`、`Int32`、`Float`、`Float32`、`Decimal`、`BigInt` 都可以；结果还是同一家族 |
| `%` | 两个同家族的数值 | `Int`、`Int32`、`Float`、`Float32`、`BigInt`；`Decimal` 不允许 |
| `/` | 两个 `Float` 或两个 `Float32` | 小数除法 |
| `/` | 两个 `Int`/`Int32`/`BigInt` | **不允许**，见"故意写错" |
| `/` | 两个 `Decimal` | **不允许**，用 `a.divide(b, 小数位数, 舍入模式)` |
| `==` `!=` | 数字、`Bool`、`String`、enum、variant、列表、map | 按值比较 |
| `==` `!=` | 类对象 | 按同一性比较：同一个对象才相等，字段相同也不算（见第 11 章） |
| `< <= > >=` | 数字、`String` | `String` 按 UTF-16 码元顺序；数字之间不隐式混用 `Int` 和 `Float` |
| `in` | `List`、`Map`、`String` | 列表找元素、map 找键、字符串找子串；用 `==` 判定 |
| `and` `or` `not` | `Bool` | 短路；没有"非零即真" |
| `+ -` | 一个数字 | 取正、取负 |

两个可空的标量可以直接用 `==` 比较，两个 `null` 相等；但 `+` 不能连接可能为 `null` 的值，要先检查（见第 13 章）。

## A.5 不存在的运算符

| 你可能想写 | Sprig 的实际情况 | 替代写法 |
|---|---|---|
| `x++`、`x--` | 字法层面拒绝 | `x += 1`、`x -= 1` |
| `a ** b` | 没有幂运算符 | `Float.sqrt`，或通过 Java 的 `Math.pow`（第 21 章） |
| `<<` `>>` `&` `|` `^` `~` | 没有位运算 | 用 `@std/math` 的 `floor_div` 等；位运算走 Java（第 21 章） |
| `&&` `||` `!` | 语法错误 | `and`、`or`、`not` |
| `a ? b : c`、`a if c else b` | 没有三元表达式 | if 表达式（第 5 章） |
| `x ?? y`、`x?.y` | 语法错误 | `if x != null:`，或 `@std/nulls` 的 `or_else`、`require`（第 13 章） |
| `1 < x < 3` | 比较不能连写 | `1 < x and x < 3` |
| `===`、`!==` | 语法错误 | `==`、`!=`（本来就是严格比较，没有隐式转换） |

## A.6 故意写错

把比较串起来写，编译器在读到这里时就停下：

<<< @/snippets/book/appendix_a_chained_comparison.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:2:13: missing ')' (unexpected '<')
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

写 `++`，报错直接给出改法：

<<< @/snippets/book/appendix_a_increment.spr

```text
SPR-SYNTAX-ERROR [SYNTAX] main.spr:3:8: Sprig has no ++ or -- operator
  hint: Write 'count += 1' or 'count -= 1'.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

整数除法即使商是整数也被拒绝，因为 `/` 的意思必须是"真正的除法"：

<<< @/snippets/book/appendix_a_int_division.spr

```text
SPR-NUM-DIVISION [TYPE] main.spr:2:7: Integer / would truncate; use a.divTrunc(b), or convert both operands explicitly (expected explicit division, actual Int / Int)
  hint: Write 7.divTrunc(2) to drop the remainder on purpose, or 7.toFloatExact() / 2.toFloatExact() for a Float result. text.fixed(value, decimals) from @std/text.spr prints a Float with that many decimals.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

## 相关章节

- [第 3 章：值、变量和算术](/tutorial/ch03-values)：`let`/`var`、数值家族、`Bool`。
- [第 4 章：文字](/tutorial/ch04-text)：`+` 拼接和字符串方法。
- [第 5 章：做决定：if](/tutorial/ch05-if)：条件、if 表达式。
- [第 13 章：可空值](/tutorial/ch13-nullable)：`== null` 和收窄。
- [第 15 章：函数作为值](/tutorial/ch15-functions-as-values)：`=>` 和函数类型。
- [附录 B：报错码速查](/tutorial/appendix-b-error-codes)：本页出现的错误码。
