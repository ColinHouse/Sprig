# Web 与 SQLite

SDK 里带着两个库：写 HTTP 服务的 `libraries/sprig-web`，和操作 SQLite 数据库的 `libraries/sprig-sqlite`。还有几个用到它们的示例项目：`examples/mini_web`、`examples/sqlite`、`examples/sqlite_migrations` 和 `examples/ledger`。

这些都是实验性的示例，用来展示 Sprig 能做到什么程度，不是可以直接用于生产环境的框架。

## 跑起记账后端

`examples/ledger` 是一个完整的记账后端，有 HTTP 接口和 SQLite 存储，还会自动生成接口文档。在已经构建好的 Sprig 仓库根目录下运行：

```bash
cd examples/ledger
../../bin/sprig resolve
../../bin/sprig check --offline
../../bin/sprig run --offline -- ledger.sqlite 8080
```

看到 `LEDGER_READY 8080`，服务就启动好了。打开 `http://localhost:8080/docs` 可以在 Swagger UI 里试接口，`/openapi.json` 是机器可读的接口描述。Swagger 页面要从 CDN 加载资源，所以需要联网；后端本身只监听本机地址，依赖解析并缓存好以后完全可以离线运行。按 Ctrl+C 停止。

在 VS Code 里，这种一直运行的程序要用 **Sprig: Run in Terminal** 来运行。

| 方法 | 路径 | 作用 |
|---|---|---|
| GET、POST | `/api/accounts` | 列出、新建账户 |
| GET、POST | `/api/categories` | 列出、新建分类 |
| GET、POST | `/api/transactions` | 列出、新建交易 |
| DELETE | `/api/transactions/{id}` | 删除一笔交易，删除成功返回 204，不存在返回 404 |
| GET | `/api/statistics/monthly?month=2026-09` | 某个月的交易笔数和收支统计 |

金额用整数表示，单位是最小的货币单位（比如「分」），可以是负数。请求字段、存储和校验规则见 [ledger 的 README（英文）](https://github.com/ColinHouse/Sprig/blob/main/examples/ledger/README.md)。

## 处理函数就是普通的 Sprig 函数

每个路由的处理函数，类型都是 `fn(web.Request) -> web.Response`。下面这几段摘自 `examples/mini_web/src/main.spr`：

```sprig
import "@web/web.spr" as web

let app = web.App(title="Mini Web", cors_origin="http://localhost:5173")

func root(req: web.Request) -> web.Response:
    return web.text("Hello, Sprig!", 200).with_header("X-Sprig", "mini-web")

func hello(req: web.Request) -> web.Response:
    let name = req.path_param("name")
    let greeting = req.query("greeting")
    if name != null:
        if greeting != null:
            return web.text(greeting + ", " + name + "!", 200)
        return web.text("Hello, " + name + "!", 200)
    return web.text("Missing name", 400)

app.get("/", fn(req: web.Request) => root(req))
```

- 用 `app.get`、`app.post`、`app.delete` 注册处理函数。如果还想生成接口文档，就用 `app.route` 传一个 `web.Route`，在里面写明摘要，以及参数、请求和响应的结构。没有装饰器，也没有 Java 注解。
- 响应用模块函数来构造：`web.text(内容, 状态码)` 和 `web.json_response(值, 状态码)`。没有 `Response.text` 这种静态方法的写法。
- `req.path_param`、`req.query`、`req.header` 都返回 `String?`，所以「没有这个参数」和「参数是空字符串」能区分开。
- `req.body` 是 UTF-8 文本，`req.json()` 把请求体解析成 JSON 值。要在 JSON 里找字段，用 `json.find_member`，它能区分键不存在、值是 JSON 的 `null`，以及要查的不是对象这几种情况，见[语言速查](/guide/language-tour)。
- 请求格式不对时返回 400，找不到路由时返回 404，处理函数里出了没处理的错误时，返回一个受控的 500。

## 接口文档

OpenAPI 文档来自你明确写出的 `Schema`、`FieldSchema`、`QueryParameter` 这些值，库不会去反射任意的类。这些描述只是文档，请求里的值仍然要在处理函数里自己校验。注册路由时，重复的查询参数和互相冲突的路径模板会直接报错。

完整的行为说明见 [`libraries/sprig-web/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-web/README.md)。运行 `sprig resolve` 之后，可以用 `sprig api @web/app.spr --json` 查看这个库所有接口的签名。

## SQL 写在明处

SQLite 库通过 Maven 拿到固定版本的 `org.xerial:sqlite-jdbc:3.46.1.0`，具体文件记录在锁文件里。

- SQL 语句就是你写的普通字符串，算作可信的应用代码。用户提供的值一律通过预编译语句的参数传进去，参数只有 `Integer`、`Text`、`Boolean`、`Null` 四种。这不是 SQL 沙箱。
- 查询返回的是带类型的结果快照，已经和数据库连接断开。连接、语句和结果集用完都会关闭。单次查询的结果最多 10,000 行，超过会报错；结果会整个读进内存。
- `Database.batch` 在一个事务里执行一组语句，任何一条失败就全部回滚。查询（包括 `INSERT ... RETURNING`）失败时，它写进去的数据也会回滚。
- 不支持你自己写 `BEGIN`、`COMMIT` 这类事务语句，事务只由 `batch` 管理。
- 没有 ORM，不会把 REAL 自动转成 Int，也不支持 BLOB 和 Decimal 参数。只支持普通的数据库文件，不支持 `:memory:` 内存数据库。

### 数据库迁移（实验性）

导入 `@sqlite/migrations.spr`，然后调用 `Migrations(database=database, directory="migrations").apply()`：

- 迁移文件命名为 `NNN_description.sql`，开头的三位编号不能重复。
- 文件按文件名顺序执行，执行过的文件名记在 `sprig_schema_migrations` 表里。如果一个已经执行过的文件排在某个还没执行的文件后面，迁移会停下来，不会乱序执行。
- 每个文件里的 SQL 和它的执行记录在同一个事务里提交。失败就一起回滚，修好以后可以再次执行。
- 已经执行过的文件不要再改：目前不会计算文件内容的校验值，改了也发现不了。迁移目录被当作可信的项目代码。

完整的例子见 [`examples/sqlite_migrations`](https://github.com/ColinHouse/Sprig/tree/main/examples/sqlite_migrations)，所有接口见 [`libraries/sprig-sqlite/README.md`（英文）](https://github.com/ColinHouse/Sprig/blob/main/libraries/sprig-sqlite/README.md)。

## 限制

这是一个同步、单用户的示例，没有登录认证、会话、连接池和异步处理，也没有考虑公网部署。请求体和查询结果都会整个读进内存。类型检查能保证类型正确，但保证不了业务规则和数值计算是对的。

## 怎么验证的

仓库里的测试会启动真正的本机 HTTP 服务，用临时的 SQLite 文件读写，检查重启之后数据还在不在，并核对 OpenAPI 文档的结构：

```bash
python3 tests/callables/check_callables.py
python3 tests/web/check_web.py
python3 tests/sqlite/check_sqlite.py
./scripts/verify.sh
```

开发过程的记录在 `docs/history/milestones/WEB_SQLITE_ENGINEERING_REPORT.md`。想写由多个文件组成、带选项解析的命令行工具，可以参考 [`examples/json_select`](https://github.com/ColinHouse/Sprig/tree/main/examples/json_select)。
