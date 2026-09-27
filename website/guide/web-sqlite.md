# Web 与 SQLite 开发里程碑

当前源码包含 `libraries/sprig-web`、`libraries/sprig-sqlite`，以及
`examples/mini_web`、`examples/sqlite`、`examples/ledger`。
这是当前开发成果，不是新发布版本或生产框架。

## 运行账本后端

从已构建的仓库根目录开始：

```bash
cd examples/ledger
../../bin/sprig resolve
../../bin/sprig check --offline
../../bin/sprig run --offline -- ledger.sqlite 8080
```

访问 `http://localhost:8080/docs` 使用 Swagger UI，或读取 `/openapi.json`。
Swagger 使用固定版本的 CDN 资源，需要联网；依赖解析并缓存之后，后端本身
可以离线运行。服务仅绑定本机回环地址，Ctrl+C 停止。
VS Code 的 **Sprig: Run in Terminal** 支持持续运行；JSON run 仍等待结束后
输出完整诊断结果。

端点包括账户和分类的 GET/POST、交易的 GET/POST/DELETE，以及
`/api/statistics/monthly?month=2026-09`。金额使用有符号 Int 最小货币单位。
项目 README 给出了请求字段、持久化和验证规则。

## 公共 API 使用普通 Sprig 类型

处理函数类型是 `fn(web.Request) -> web.Response`。App 的 get/post/delete
注册回调，route 接收具名 Route 构造值。没有装饰器或 Java 注解。
文本和 JSON 响应采用模块函数 `web.text(body,status)` 与
`web.json_response(value,status)`，没有 `Response.text` 静态方法语法。

Request 的 path_param/query/header 返回 String?，区分缺少值与空字符串。
body 是 UTF-8 文本，json() 返回封闭 JSON variant；格式错误返回400，路由
不存在返回404，未处理应用错误返回受控500。json.find_member 的 Missing、
Found(value)、NotObject 区分缺失、JSON null 和错误对象类型。

OpenAPI 使用普通 Schema/FieldSchema/QueryParameter 元数据，不反射任意类；
实际请求仍由处理函数验证。重复查询参数和等价路径模板在注册阶段被拒绝。
仓库 `libraries/sprig-web/README.md` 与 `api.json` 提供明确签名。

## JVM 适配器的边界

SQLite 包通过已有 Maven Resolver 和 schema-3 lock 解析固定的
`org.xerial:sqlite-jdbc:3.46.1.0`。SQL 留在 Sprig，用户值使用具备类型的
Integer/Text/Boolean/Null 参数。结果是脱离 JDBC 资源的快照，连接、语句和
结果集均被关闭；失败的批处理或 RETURNING 快照会回滚。
没有 ORM、Java 数组语法、隐式 REAL 到 Int 转换、BLOB 或 Decimal 绑定。

这是同步单用户例子，尚无身份认证、会话、迁移、连接池、async 或公开部署
契约。请求体与结果快照会载入内存；类型安全不保证业务规则或数值稳定性。

```bash
python3 tests/callables/check_callables.py
python3 tests/web/check_web.py
python3 tests/sqlite/check_sqlite.py
./scripts/verify.sh
```

这些测试使用真实本机 HTTP、临时 SQLite 文件、重启持久化和结构化 OpenAPI
断言。实际验收记录位于 `docs/milestones/WEB_SQLITE_ENGINEERING_REPORT.md`。
源码函数类型见[语言导览](/guide/language-tour)，JVM 边界见
[JVM 互操作](/guide/jvm-interop)。
