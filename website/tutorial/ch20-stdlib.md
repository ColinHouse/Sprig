# 20. 标准库实用篇

程序不能只会算数和操作自己的数据，它还得和外面的世界打交道：读配置、存结果、解析别人发来的 JSON、算两个日期的间隔、在文本里找符合模式的部分。这些活不用自己写——随编译器一起发货的**标准库**里都有。

标准库是以 `@std/` 开头的模块，和平常一样导入：`import "@std/files.spr" as files`。前面的章节里你已经用过其中一些：`@std/text`（第 4 章）、`@std/lists`（第 8 章）、`@std/sets`（第 9 章）、`@std/process` 和 `@std/random`（第 10 章）、`@std/nulls`（第 13 章）、`@std/math`（第 17 章）、`@std/test`（第 19 章）。

这一章补齐四组做真实程序离不开的模块：文件、JSON、日期和时间、正则。

这一章你会学到：

- `@std/files`：读、写、原子替换、列目录、临时文件；每个失败都抛 `Error`；
- `@std/json` 和 `@std/json_codec`：解析与生成 JSON，按名字和类型取字段，错误消息带着路径；
- `@std/dates` 和 `@std/time`：日期就是 ISO 文本，时间戳就是毫秒数；
- `@std/regex`：查找、替换、整体匹配、分组、切分。

## 20.1 文件：@std/files

为什么需要它：变量和列表活在内存里，程序一结束就没了。要留住结果、读别人的数据，就得写文件、读文件。

最小的例子在一个临时文件上走完整个来回：

<<< @/snippets/book/ch20_files.spr

```text
true
3
first
second
false
```

逐行读：

- `files.temp_file()` 在操作系统的临时目录里新建一个空文件，返回它的绝对路径。它**不会**被自动删除，用完了要自己 `remove_file`。
- `files.write_utf8(path, "first draft\n")` 把文本按 UTF-8 写进文件。文件已存在就原地覆盖——写到一半时，别的程序可能读到一个不完整的文件。
- `files.atomic_write_utf8(path, "first\nsecond\n")` 也是写，但先写一个临时邻居，成功后再整体改名替换。读者要么看到旧内容，要么看到新内容，不会看到一半。要保证"别人随时来读都读到完整内容"，就改用它。
- `files.exists(path)` 打印 `true`：文件在。
- `files.read_lines(path)` 按行读回来，返回 `List[String]`，行尾的换行符被去掉。`"first\nsecond\n"` 分成 `["first", "second", ""]`：最后的换行符留下一个空行，所以 `.size()` 是 `3`。
- `files.read_utf8(path).trim()` 把整个文件读成一个字符串，`trim()` 去掉末尾换行，打印 `first` 和 `second`。
- `files.remove_file(path)` 删除文件；删完再 `exists`，得到 `false`。

文件操作的失败是常态——路径不存在、权限不够、磁盘满了。`@std/files` 里会失败的操作（读、写、删、建目录、列目录……）签名里都写着 `throws Error`，失败时抛一个消息为 `cannot 操作 文件: 原因` 的 `Error`：

<<< @/snippets/book/ch20_files_error.spr

```text
cannot read no-such-file.txt: no such file
```

用第 14 章的 `try`/`catch` 接住，`problem.message` 就是这行消息。读一个不存在的文件不会给你空字符串或者 `null`；"读不到"必须显式处理。

其他常用函数：`is_file` / `is_directory` 看路径是什么；`make_directory` 建目录（连同缺失的上级）；`copy_file` 和 `move` 复制、移动（都不覆盖已有文件）；`file_name` / `parent` / `join` / `normalize` / `absolute` 摆弄路径文本；`list` 和 `walk` 列目录；`temp_file` 拿临时文件。注意 `files.parent(...)` 可能没有父目录，它返回 `String?`。

### 列目录

想知道一个目录里有什么，用 `list`；要递归整个目录树，用 `walk`：

<<< @/snippets/book/ch20_files_list.spr

```text
2
a.txt
notes.txt
0
```

逐行读：

- `files.temp_file()` 先给一个临时文件；`files.parent(anchor)` 取它所在的目录。`parent` 的类型是 `String?`，所以先 `if parent == null: throw`，让后面的 `parent` 收窄成 `String`（第 13 章）。
- `files.join(parent, "sprig-book-" + files.file_name(anchor))` 用路径规则把目录和名字拼起来。目录名里带上临时文件的名字，每次运行都不重样。`files.remove_file(anchor)` 把只用来取名字的小文件删掉。
- `files.make_directory(dir)` 建出目录，然后往里写 `a.txt` 和 `notes.txt`。
- `files.list(dir)` 返回**排好序的**目录快照，元素是绝对路径。打印绝对路径会带上你机器上的目录名，不可移植，所以用 `file_name` 取出最后一段：`a.txt`、`notes.txt`。
- `files.walk(dir)` 递归列出目录下所有**文件**的绝对路径。这个目录里只有两个文件，所以又是这两行；`walk` 不列目录本身。
- 最后删掉两个文件再数一次 `list`，得到 `0`。

目录本身没有删——`@std/files` 只删文件，不删目录；它在操作系统的临时目录里，由系统清理。

### 故意写错：忘了写 throws

`read_lines` 会抛 `Error`，调用它的函数必须在签名上承认这一点，否则编译器拒绝：

<<< @/snippets/book/ch20_files_throws.spr

```text
SPR-FLOW-THROWS [FLOW] main.spr:5:12: Call may throw Error; declare 'throws Error' or handle it with try/catch
```

`SPR-FLOW-THROWS`：第 5 行第 12 列调用了一个可能抛错的东西，而 `first_line` 既没声明 `throws Error`，也没用 `try` 包住调用。修法有两个：给 `first_line` 的签名加上 `throws Error`（它的调用者接着也要处理或声明），或者在调用处 `try` / `catch`。

::: tip 学过其他语言？

- 没有文件句柄要开和关：这些函数一次读写整份文件，路径就是个 `String`。
- 失败既不是返回值（C 的 `-1`、Go 的 `err`），也不是悄悄往外传播的异常：它抛 `Error`，你必须处理或声明。
- 路径函数是纯文本运算：`join`、`normalize` 不碰磁盘，也不解析软链接。

:::

## 20.2 JSON：@std/json 和 @std/json_codec

程序之间交换数据最常用的文本格式是 JSON。标准库把它拆成两层：`@std/json` 管"JSON 文本 ↔ JSON 值"，`@std/json_codec` 管"从 JSON 值里按名字和类型取字段"。

<<< @/snippets/book/ch20_json.spr

```text
sprig
12
[small, typed]
null
{"name":"sprig","stars":12,"tags":["small","typed"]}
```

逐行读：

- `json.parse(doc)` 把文本解析成一个 `json.Value`。JSON 的每种东西对应一个 case：`Null`、`Boolean`、`Number`、`Text`、`Array`、`Object`。数字保存的是**原始文本**（`Number(text: String)`），不会先转成 `Float` 丢精度；对象里重复的键会被拒绝，不是"最后一个赢"。
- `codec.root(...)` 要求文档本身就是个对象，返回一个带着**路径**的 reader，根路径是 `$`。文档是数组时用 `codec.root_array`，元素路径是 `$[0]`、`$[1]`……
- `codec.required_string(root, "name")` 取 `name` 字段，要求它是字符串，打印 `sprig`；`required_int(root, "stars")` 取整数，打印 `12`。
- `codec.required_string_array(root, "tags")` 取字符串数组；列表打印成 `[small, typed]`。
- `codec.optional_string(root, "license")` 取可有可无的字段：缺失或为 JSON `null` 时返回 `String?` 的 `null`，这里打印 `null`。
- `json.stringify(json.parse(doc))` 把值写回**紧凑** JSON，没有任何多余空格；`stringify` 也会检查手工拼出来的对象有没有重复的键。

字段缺失、是 `null`、或者类型不对，`required_*` 都抛 `Error`，消息里带着字段在文档里的路径：

<<< @/snippets/book/ch20_json_error.spr

```text
$.stars: expected integer, found string
```

`$.stars` 是路径，`expected integer, found string` 说清两边。注意 "integer" 比"数字"严格：JSON 的 `12.0` 和 `1e3` 都是数字，但不是整数，`required_int` 会拒绝；这种情况用 `required_number_text`（原文）或 `required_decimal`（精确小数）。

`json_codec` 的取字段函数是成对的：`required_*` 要求字段在且类型对，`optional_*` 允许缺失（结果是 `T?`），`as_*` 给数组元素这类"已经拿到值"的场景。要拒绝不认识的字段（读配置时很有用），用 `reject_unknown_fields(root, ["name", "stars"])`。

### 故意写错：optional 的结果不能当普通值用

`optional_int` 找不到字段时返回 `null`，类型是 `Int?`，不能直接赋给 `Int`：

<<< @/snippets/book/ch20_json_optional.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:6:18: Nullable value is not assignable to Int (initializer); check for null first (expected Int, actual Int?)
```

修法还是第 13 章那套：先判 `null`，或者用 `nulls.or_else` / `nulls.require`。别为了过编译随手改成 `required_int`——"可以没有"和"必须有"是不同的意思，读代码的人靠这个区分。

::: tip 学过其他语言？

- 数字不经过 `Double`：`Value.Number` 保存原文，`required_decimal` 给你精确的 `Decimal`。这是给金额准备的。
- 取值不是"先拿 `Any` 再强转"：`required_int` 在类型层面保证结果就是 `Int`，失败抛错。
- 想处理任意形状的 JSON，可以 `match` `json.Value` 的 case（第 12 章）；形状固定就用 codec。
- 构造 JSON 用 `codec.text` / `int` / `number` / `bool` / `member` / `object` / `array` 拼，再 `json.stringify`；不要手拼字符串。

:::

## 20.3 日期和时间：@std/dates 和 @std/time

记录什么时候创建的、算还有几天到期、比较先后——都要日期。Sprig 不发明新的日期类型：**日期就是一个 ISO 文本**，例如 `2026-10-05`；时间戳是一个 `Int`，表示 1970-01-01T00:00:00Z 以来经过的毫秒数，也可以转成带时区的 ISO-8601 文本。

<<< @/snippets/book/ch20_dates.spr

```text
2026-10-05
2026-10-15
14
1
false
1970-01-01T00:00:00Z
1970-01-01T00:00:01.500Z
1791450930000
```

逐行读：

- `dates.parse("2026-10-05")` 检查文本是不是真实存在的日历日期（ISO 形式），返回它，打印 `2026-10-05`。
- `dates.plus_days(start, 10)`：10 天以后，`2026-10-15`；第二个参数给负数就往回走。
- `dates.days_between(start, "2026-10-19")`：从第一个日期到第二个相差 `14` 天；第二个更早会得到负数。
- `dates.day_of_week(start)`：`1` 是星期一，`7` 是星期日。2026-10-05 是星期一。
- `dates.is_valid("2026-02-30")`：二月没有 30 日，返回 `false`。`is_valid` 只回答"是不是合法日期"，不抛错。
- `time.format_utc(0)`：毫秒 `0` 就是 `1970-01-01T00:00:00Z`；`1500` 是同一时刻 1.5 秒后，打印 `1970-01-01T00:00:01.500Z`。
- `time.parse_utc("2026-10-08T17:15:30+08:00")`：带时区的文本换成毫秒数 `1791450930000`。`+08:00` 是东八区，同一瞬间在 UTC 是 09:15:30。

`@std/dates` 还有 `today_utc()`（今天）、`year` / `month` / `day`。`@std/time` 还有 `utc_now()`（现在）、`epoch_millis()`（现在的毫秒数）、`sleep(毫秒)`（暂停）、`monotonic_nanos()`（只用来量经过的时间，不受系统时钟调整影响）。

`parse` 不做猜测：不合法的日期在**运行时**抛错，而不是悄悄给你一个别的日期。

<<< @/snippets/book/ch20_dates_error.spr

```text
not a date: 2026-02-30
```

### 故意写错：天数不是文本

`plus_days` 的第二个参数是 `Int`。用 `"3"` 表示三天，编译器直接拒绝：

<<< @/snippets/book/ch20_dates_type.spr

```text
SPR-TYPE-MISMATCH [TYPE] main.spr:4:37: Type mismatch in argument 2 of plus_days (expected Int, actual String)
```

`SPR-TYPE-MISMATCH` 说清是第几个参数、期望什么、给了什么。Sprig 不会把文本自动变成数字；真要解析用户输入，用第 4 章的 `"3".toIntOrNull()`。

::: tip 学过其他语言？

- 没有日期对象：日期就是 `String`，`dates` 的函数就是纯函数。ISO 文本按字典序比较就等于按时间先后比较，`"2026-10-05" < "2026-10-06"` 是 `true`。
- `dates` 是"日历日"，`time` 是"时刻"。没有时区数据库、没有时长类型、没有格式化模式串。
- 要更复杂的日期运算（时区、周期、格式化），第 21 章起直接调用 Java 的 `java.time`。

:::

## 20.4 正则：@std/regex

文本里常常要"找像电话号码的一段""把所有数字换成 `#`""检查整个字符串符不符合格式"。正则表达式就是描述这种模式的迷你语言。

<<< @/snippets/book/ch20_regex.spr

```text
[a1, b22, c333]
a# b# c#
true
[a, 1]
[a1, b22, c333]
true
```

逐行读：

- `regex.find_all("[a-z][0-9]+", text)`：找"一个小写字母后跟一串数字"的片段，按出现顺序返回所有匹配，打印 `[a1, b22, c333]`。
- `regex.replace_all("[0-9]+", text, "#")`：每一段数字整体换成 `#`，得到 `a# b# c#`。
- `regex.matches(...)`：问"**整段**文本是不是恰好符合模式"，是就 `true`。和 `find_all` 的区别是它要求从头到尾匹配。
- `regex.find_groups("([a-z])([0-9]+)", text)`：括号是**捕获组**；返回第一个匹配里的各组 `[a, 1]`。没匹配时返回 `null`，组没参与匹配的位置是 `null`。所有匹配的所有组用 `find_all_groups`。
- `regex.split("\\s+", text)`：按空白切开，得到 `[a1, b22, c333]`。模式里的 `\\s`：`\\` 是字符串转义（第 4 章），正则引擎收到的是 `\s`。
- `regex.find("z+", text)`：找"一个或多个 z"；文本里没有，返回 `null`，`== null` 打印 `true`。

### 故意写错：find 的结果不能直接当 String 用

<<< @/snippets/book/ch20_regex_null.spr

```text
SPR-TYPE-NULLABLE [TYPE] main.spr:4:21: Nullable value is not assignable to String (initializer); check for null first (expected String, actual String?)
```

`SPR-TYPE-NULLABLE` 又一次出现：`find` 可能找不到，结果是 `String?`。要么先判 `null`，要么换用 `find_all`——找不到时它是空列表，不是 `null`。

::: tip 学过其他语言？

- 模式语法就是 Java 的 `java.util.regex`：`\d`、`\w`、`(?<name>...)`、量词都一样。
- "没找到"不是异常也不是空串，而是 `String?`，编译器逼你处理。
- 每次调用都会重新编译模式；放在循环里之前先想想。要复用编译结果，可以先用 Java 的 `Pattern`（第 21 章）。

:::

## 本章小结

- 标准库用 `@std/名字.spr` 导入，不需要声明依赖。
- `@std/files`：`read_utf8` / `read_lines` 读，`write_utf8` 写，`atomic_write_utf8` 原子替换，`list` / `walk` 列目录，`temp_file` 临时文件；失败抛 `Error`，函数里调用要声明 `throws`。
- `@std/json` 管文本和值；`@std/json_codec` 的 `required_*` / `optional_*` 按类型取字段，错误带 `$` 路径；`optional_*` 的结果是 `T?`。
- 日期是 ISO 文本，时间戳是毫秒；`dates.parse` / `plus_days` / `days_between` / `is_valid` / `day_of_week`，`time.format_utc` / `parse_utc` / `utc_now` / `sleep`。
- `@std/regex`：`find`（`String?`）、`find_all`、`replace_all`、`matches`（整段）、`find_groups`、`split`。

## 动手练习

### 练习 1：找出所有数字

从文本 `"I have 3 cats, 12 dogs and 1 fish"` 里找出所有数字，打印这个列表和数字的个数。

提示：`[0-9]+` 表示"一个或多个数字"；`find_all` 返回 `List[String]`，列表有 `.size()`。

::: details 参考答案

<<< @/snippets/book/ch20_ex1.spr

```text
[3, 12, 1]
3
```

:::

### 练习 2：把 JSON 数组里的金额加起来

下面这段 JSON 是一个数组，每条有一个 `amount`。把所有 `amount` 加起来并打印总和。

```text
[{"amount": 12}, {"amount": 30}, {"amount": 5}]
```

提示：`codec.root_array(...)` 给每个元素一个 reader，`codec.required_int(row, "amount")` 取整数。

::: details 参考答案

<<< @/snippets/book/ch20_ex2.spr

```text
47
```

:::

### 练习 3：日期加减

从 `"2026-10-08"` 出发，打印 30 天后的日期，以及到年底（`"2026-12-31"`）还有多少天。

提示：`plus_days(date, 天数)` 和 `days_between(start, end)` 都直接收 ISO 文本。

::: details 参考答案

<<< @/snippets/book/ch20_ex3.spr

```text
2026-11-07
84
```

:::

### 练习 4：读回带空行的文件

把 `"one\n\ntwo\n"` 写进一个临时文件，再按行读回来：打印行数，并判断第二行是不是空行。最后删掉文件。

提示：`write_utf8` 里写 `\n` 就是换行；`read_lines` 的元素不带换行符。注意末尾那个换行符也会产生一个空行。

::: details 参考答案

<<< @/snippets/book/ch20_ex4.spr

```text
4
true
```

:::

下一章：[调用 Java](/tutorial/ch21-java)。
