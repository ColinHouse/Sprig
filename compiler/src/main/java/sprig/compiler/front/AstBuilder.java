package sprig.compiler.front;
import sprig.compiler.parser.SprigLexer;
import sprig.compiler.parser.SprigParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Expr;
import sprig.compiler.ast.Module;
import sprig.compiler.ast.Stmt;
import sprig.compiler.ast.TypeRef;
import sprig.compiler.diag.Codes;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;

/** Builds the Sprig AST from an ANTLR parse tree. */
public final class AstBuilder {
    private final String uri;
    private final Diagnostics diagnostics;

    public AstBuilder(String uri, Diagnostics diagnostics) {
        this.uri = uri;
        this.diagnostics = diagnostics;
    }

    public Module build(Path path, SprigParser.ProgramContext program) {
        List<Decl.Import> imports = new ArrayList<>();
        for (SprigParser.ImportStatementContext ctx : program.importStatement()) {
            imports.add(buildImport(ctx));
        }
        Module module = new Module(path, uri, imports);
        boolean sawExport = false, sawBody = false;
        for (ParseTree child : program.children) {
            if (child instanceof SprigParser.ImportStatementContext ctx) {
                if (sawExport) diagnostics.add(Diagnostic.error(Codes.MODULE_EXPORT_ORDER, Phase.NAME,
                    "Imports must precede declaration exports", uri, span(ctx)));
            } else if (child instanceof SprigParser.ExportStatementContext ctx) {
                if (sawBody) diagnostics.add(Diagnostic.error(Codes.MODULE_EXPORT_ORDER, Phase.NAME,
                    "Declaration exports must precede local declarations/statements", uri, span(ctx)));
                Module.Export exported = new Module.Export(ctx.IDENT(1).getText(),ctx.IDENT(2).getText());
                exported.span = span(ctx);
                module.exports.add(exported);
                sawExport = true;
            } else if (child instanceof org.antlr.v4.runtime.ParserRuleContext) sawBody = true;
            if (child instanceof SprigParser.GenericDefinitionContext ctx) {
                module.decls.add(buildGeneric(ctx));
            } else if (child instanceof SprigParser.ClassDefinitionContext ctx) {
                module.decls.add(buildClass(ctx));
            } else if (child instanceof SprigParser.EnumDefinitionContext ctx) {
                module.decls.add(buildEnum(ctx));
            } else if (child instanceof SprigParser.VariantDefinitionContext ctx) {
                module.decls.add(buildVariant(ctx));
            } else if (child instanceof SprigParser.ConformDefinitionContext ctx) {
                module.decls.add(buildConform(ctx));
            } else if (child instanceof SprigParser.FunctionDefinitionContext ctx) {
                module.decls.add(buildFunction(ctx));
            } else if (child instanceof SprigParser.StatementContext ctx) {
                module.topStatements.add(buildStatement(ctx));
            }
        }
        return module;
    }

    private Decl buildGeneric(SprigParser.GenericDefinitionContext ctx) {
        SprigParser.GenericBodyContext body = ctx.genericSuite().genericBody();
        Decl decl;
        if (body.classDefinition() != null) {
            decl = buildClass(body.classDefinition());
        } else if (body.variantDefinition() != null) {
            decl = buildVariant(body.variantDefinition());
        } else {
            decl = buildFunction(body.functionDefinition());
        }
        for (TerminalNode parameter : ctx.typeParameterList().IDENT()) {
            decl.typeParams.add(parameter.getText());
        }
        if (decl.span == null) {
            decl.span = span(ctx);
        }
        return decl;
    }

    private Decl.Import buildImport(SprigParser.ImportStatementContext ctx) {
        Decl.Import result;
        if (ctx.STRING() != null) {
            String text = ctx.STRING().getText();
            result = new Decl.Import(unquote(text), true, ctx.IDENT() == null ? null : ctx.IDENT().getText());
            result.targetSpan = span(ctx.STRING());
        } else {
            result = new Decl.Import(ctx.qualifiedName().getText(), false,
                    ctx.IDENT() == null ? null : ctx.IDENT().getText());
            result.targetSpan = span(ctx.qualifiedName());
        }
        result.aliasSpan = ctx.IDENT() == null ? null : span(ctx.IDENT());
        result.span = span(ctx);
        return result;
    }

    private Decl buildClass(SprigParser.ClassDefinitionContext ctx) {
        List<Decl.Field> fields = new ArrayList<>();
        List<Decl.Func> methods = new ArrayList<>();
        for (SprigParser.FieldDeclarationContext field : ctx.classSuite().fieldDeclaration()) {
            fields.add(new Decl.Field(field.IDENT().getText(), field.VAR() != null,
                    buildTypeRef(field.typeRef()),
                    field.expression() == null ? null : buildExpression(field.expression())));
            fields.get(fields.size() - 1).span = span(field);
            fields.get(fields.size() - 1).nameSpan = span(field.IDENT());
        }
        for (SprigParser.FunctionDefinitionContext method : ctx.classSuite().functionDefinition()) {
            methods.add(buildFunction(method));
        }
        Decl.ClassDecl decl = new Decl.ClassDecl(ctx.IDENT().getText(), fields, methods);
        decl.span = span(ctx);
        decl.nameSpan = span(ctx.IDENT());
        return decl;
    }

    private Decl buildConform(SprigParser.ConformDefinitionContext ctx) {
        Decl.Conform decl = new Decl.Conform(ctx.IDENT(0).getText(), ctx.IDENT(1).getText());
        decl.span = span(ctx);
        decl.nameSpan = span(ctx.IDENT(0));
        return decl;
    }

    private Decl buildEnum(SprigParser.EnumDefinitionContext ctx) {
        List<String> cases = new ArrayList<>();
        for (TerminalNode ident : ctx.enumSuite().IDENT()) {
            cases.add(ident.getText());
        }
        Decl.EnumDecl decl = new Decl.EnumDecl(ctx.IDENT().getText(), cases);
        for (TerminalNode ident : ctx.enumSuite().IDENT()) {
            decl.caseSpans.add(span(ident));
        }
        decl.span = span(ctx);
        decl.nameSpan = span(ctx.IDENT());
        return decl;
    }

    private Decl buildVariant(SprigParser.VariantDefinitionContext ctx) {
        List<Decl.VariantCase> cases = new ArrayList<>();
        for (SprigParser.VariantCaseContext caseCtx : ctx.variantSuite().variantCase()) {
            List<Decl.Field> fields = new ArrayList<>();
            List<SprigParser.VariantFieldContext> fieldContexts = new ArrayList<>();
            if (caseCtx.variantFields() != null) {
                fieldContexts.addAll(caseCtx.variantFields().variantField());
            }
            fieldContexts.addAll(caseCtx.variantField());
            for (SprigParser.VariantFieldContext fieldCtx : fieldContexts) {
                Decl.Field field = new Decl.Field(fieldCtx.IDENT().getText(), false,
                        buildTypeRef(fieldCtx.typeRef()), null);
                field.span = span(fieldCtx);
                field.nameSpan = span(fieldCtx.IDENT());
                fields.add(field);
            }
            Decl.VariantCase variantCase = new Decl.VariantCase(caseCtx.IDENT().getText(), fields);
            variantCase.span = span(caseCtx);
            variantCase.nameSpan = span(caseCtx.IDENT());
            cases.add(variantCase);
        }
        Decl.VariantDecl decl = new Decl.VariantDecl(ctx.IDENT().getText(), cases);
        decl.span = span(ctx);
        decl.nameSpan = span(ctx.IDENT());
        return decl;
    }

    private Decl.Func buildFunction(SprigParser.FunctionDefinitionContext ctx) {
        List<Decl.Param> params = new ArrayList<>();
        if (ctx.parameters() != null) {
            for (SprigParser.ParameterContext param : ctx.parameters().parameter()) {
                Decl.Param p = new Decl.Param(param.IDENT().getText(), buildTypeRef(param.typeRef()));
                p.symbol = null;
                p.nameSpan = span(param.IDENT());
                params.add(p);
            }
        }
        List<TypeRef> throwsRefs = new ArrayList<>();
        for (SprigParser.TypeRefContext ref : ctx.typeRef().subList(1, ctx.typeRef().size())) {
            throwsRefs.add(buildTypeRef(ref));
        }
        Decl.Func func = new Decl.Func(ctx.IDENT().getText(), params, buildTypeRef(ctx.typeRef(0)),
                throwsRefs, buildSuite(ctx.suite()));
        func.span = span(ctx);
        func.nameSpan = span(ctx.IDENT());
        return func;
    }

    private TypeRef buildTypeRef(SprigParser.TypeRefContext ctx) {
        if (ctx.functionType() != null) {
            List<SprigParser.TypeRefContext> written = ctx.functionType().typeRef();
            List<TypeRef> params = new ArrayList<>();
            for (int i = 0; i < written.size() - 1; i++) params.add(buildTypeRef(written.get(i)));
            TypeRef ref = new TypeRef(List.of("fn"), params, ctx.QUESTION() != null,
                    buildTypeRef(written.get(written.size() - 1)));
            ref.span = span(ctx);
            return ref;
        }
        List<String> parts = new ArrayList<>();
        for (TerminalNode ident : ctx.qualifiedName().IDENT()) {
            parts.add(ident.getText());
        }
        List<TypeRef> args = new ArrayList<>();
        for (SprigParser.TypeRefContext arg : ctx.typeRef()) {
            args.add(buildTypeRef(arg));
        }
        TypeRef ref = new TypeRef(parts, args, ctx.QUESTION() != null);
        ref.span = span(ctx);
        List<TerminalNode> idents = ctx.qualifiedName().IDENT();
        ref.nameSpan = span(idents.get(idents.size() - 1));
        return ref;
    }

    private List<Stmt> buildSuite(SprigParser.SuiteContext ctx) {
        List<Stmt> body = new ArrayList<>();
        for (SprigParser.StatementContext stmt : ctx.statement()) {
            body.add(buildStatement(stmt));
        }
        return body;
    }

    private Stmt buildStatement(SprigParser.StatementContext ctx) {
        if (ctx.simpleStatement() != null) {
            return buildSimple(ctx.simpleStatement());
        }
        if (ctx.ifStatement() != null) {
            return buildIf(ctx.ifStatement());
        }
        if (ctx.whileStatement() != null) {
            return buildWhile(ctx.whileStatement());
        }
        if (ctx.forStatement() != null) {
            return buildFor(ctx.forStatement());
        }
        if (ctx.tryStatement() != null) {
            return buildTry(ctx.tryStatement());
        }
        return buildMatch(ctx.matchStatement());
    }

    private Stmt buildSimple(SprigParser.SimpleStatementContext ctx) {
        if (ctx.variableDeclaration() != null) {
            SprigParser.VariableDeclarationContext decl = ctx.variableDeclaration();
            Stmt.VarDecl stmt = new Stmt.VarDecl(decl.VAR() != null, decl.IDENT().getText(),
                    decl.typeAnnotation() == null ? null : buildTypeRef(decl.typeAnnotation().typeRef()),
                    buildExpression(decl.expression()));
            stmt.span = span(ctx);
            stmt.nameSpan = span(decl.IDENT());
            return stmt;
        }
        if (ctx.assignment() != null) {
            SprigParser.AssignmentContext assign = ctx.assignment();
            Stmt.Assign stmt = new Stmt.Assign(buildAssignTarget(assign.assignmentTarget()),
                    assign.assignmentOperator().getText(), buildExpression(assign.expression()));
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.requiresStatement() != null) {
            SprigParser.RequiresStatementContext requires = ctx.requiresStatement();
            Stmt.Requires stmt = new Stmt.Requires(requires.IDENT().getText(),
                    requires.qualifiedName().getText());
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.RETURN() != null) {
            Stmt.Return stmt = new Stmt.Return(ctx.expression() == null ? null : buildExpression(ctx.expression()));
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.BREAK() != null) {
            Stmt.Break stmt = new Stmt.Break();
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.CONTINUE() != null) {
            Stmt.Continue stmt = new Stmt.Continue();
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.PASS() != null) {
            Stmt.Pass stmt = new Stmt.Pass();
            stmt.span = span(ctx);
            return stmt;
        }
        if (ctx.THROW() != null) {
            Stmt.Throw stmt = new Stmt.Throw(buildExpression(ctx.expression()));
            stmt.span = span(ctx);
            return stmt;
        }
        Stmt.ExprStmt stmt = new Stmt.ExprStmt(buildExpression(ctx.expression()));
        stmt.span = span(ctx);
        return stmt;
    }

    private Expr buildAssignTarget(SprigParser.AssignmentTargetContext ctx) {
        Expr current = new Expr.Name(ctx.IDENT(0).getText());
        current.span = span(ctx.IDENT(0));
        for (int i = 0; i < ctx.children.size(); i++) {
            ParseTree child = ctx.children.get(i);
            if (child instanceof TerminalNode node && node.getSymbol().getType() == SprigLexer.DOT) {
                if (!(ctx.children.get(i + 1) instanceof TerminalNode ident)) {
                    continue;
                }
                Expr.FieldAccess access = new Expr.FieldAccess(current, ident.getText());
                access.span = span(ctx);
                access.nameSpan = span(ident);
                current = access;
            } else if (child instanceof SprigParser.ExpressionContext exprCtx) {
                Expr.Index idx = new Expr.Index(current, buildExpression(exprCtx));
                idx.span = span(exprCtx);
                current = idx;
            }
        }
        return current;
    }

    private Stmt buildIf(SprigParser.IfStatementContext ctx) {
        List<Stmt> thenBody = buildSuite(ctx.suite(0));
        List<Stmt.IfStmt.Elif> elifs = new ArrayList<>();
        int elifCount = ctx.ELIF().size();
        for (int i = 0; i < elifCount; i++) {
            elifs.add(new Stmt.IfStmt.Elif(buildExpression(ctx.expression(i + 1)), buildSuite(ctx.suite(i + 1))));
        }
        List<Stmt> elseBody = null;
        if (ctx.ELSE() != null) {
            elseBody = buildSuite(ctx.suite(ctx.suite().size() - 1));
        }
        Stmt.IfStmt stmt = new Stmt.IfStmt(buildExpression(ctx.expression(0)), thenBody, elifs, elseBody);
        stmt.span = span(ctx);
        return stmt;
    }

    private Stmt buildWhile(SprigParser.WhileStatementContext ctx) {
        Stmt.WhileStmt stmt = new Stmt.WhileStmt(buildExpression(ctx.expression()), buildSuite(ctx.suite()));
        stmt.span = span(ctx);
        return stmt;
    }

    private Stmt buildFor(SprigParser.ForStatementContext ctx) {
        Stmt.ForStmt stmt = new Stmt.ForStmt(ctx.IDENT().getText(), buildExpression(ctx.expression()),
                buildSuite(ctx.suite()));
        stmt.span = span(ctx);
        stmt.nameSpan = span(ctx.IDENT());
        return stmt;
    }

    private Stmt buildTry(SprigParser.TryStatementContext ctx) {
        List<Stmt.Try.CatchClause> catches = new ArrayList<>();
        for (SprigParser.CatchClauseContext catchCtx : ctx.catchClause()) {
            Stmt.Try.CatchClause clause = new Stmt.Try.CatchClause(catchCtx.IDENT().getText(),
                    buildTypeRef(catchCtx.typeRef()), buildSuite(catchCtx.suite()));
            clause.nameSpan = span(catchCtx.IDENT());
            catches.add(clause);
        }
        List<Stmt> finallyBody = null;
        if (ctx.FINALLY() != null) {
            finallyBody = buildSuite(ctx.suite(ctx.suite().size() - 1));
        }
        Stmt.Try stmt = new Stmt.Try(buildSuite(ctx.suite(0)), catches, finallyBody);
        stmt.span = span(ctx);
        return stmt;
    }

    private Stmt buildMatch(SprigParser.MatchStatementContext ctx) {
        List<Stmt.Match.Branch> branches = new ArrayList<>();
        for (SprigParser.MatchBranchContext branchCtx : ctx.matchSuite().matchBranch()) {
            List<TerminalNode> idents = branchCtx.qualifiedName().IDENT();
            List<String> parts = new ArrayList<>();
            for (TerminalNode ident : idents) {
                parts.add(ident.getText());
            }
            String caseName = parts.get(parts.size() - 1);
            List<String> typeParts = parts.subList(0, parts.size() - 1);
            TypeRef caseType = new TypeRef(typeParts, List.of(), false);
            caseType.span = span(branchCtx.qualifiedName());
            caseType.nameSpan = idents.size() < 2 ? null : span(idents.get(idents.size() - 2));
            Stmt.Match.Branch branch = new Stmt.Match.Branch(caseType, caseName,
                    branchCtx.IDENT() == null ? null : branchCtx.IDENT().getText(),
                    buildSuite(branchCtx.suite()));
            branch.caseSpan = span(idents.get(idents.size() - 1));
            branch.binderSpan = branchCtx.IDENT() == null ? null : span(branchCtx.IDENT());
            branches.add(branch);
        }
        Stmt.Match stmt = new Stmt.Match(buildExpression(ctx.expression()), branches);
        stmt.span = span(ctx);
        return stmt;
    }

    // ---- expressions ----

    Expr buildExpression(SprigParser.ExpressionContext ctx) {
        if (ctx.matchExpression() != null) {
            var matchCtx = ctx.matchExpression();
            List<Stmt.Match.Branch> branches = new ArrayList<>();
            for (var branchCtx : matchCtx.matchExpressionBranch()) {
                List<TerminalNode> idents = branchCtx.qualifiedName().IDENT();
                List<String> parts = idents.stream().map(TerminalNode::getText).toList();
                TypeRef owner = new TypeRef(parts.subList(0,parts.size()-1),List.of(),false);
                owner.span = span(branchCtx.qualifiedName());
                owner.nameSpan = idents.size() < 2 ? null : span(idents.get(idents.size() - 2));
                Stmt.ExprStmt value = new Stmt.ExprStmt(buildExpression(branchCtx.expression()));
                value.span = span(branchCtx.expression());
                Stmt.Match.Branch branch = new Stmt.Match.Branch(owner, parts.get(parts.size() - 1),
                        branchCtx.IDENT() == null ? null : branchCtx.IDENT().getText(), List.of(value));
                branch.caseSpan = span(idents.get(idents.size() - 1));
                branch.binderSpan = branchCtx.IDENT() == null ? null : span(branchCtx.IDENT());
                branches.add(branch);
            }
            Stmt.Match cases = new Stmt.Match(buildExpression(matchCtx.expression()),branches);
            cases.span = span(matchCtx);
            Expr.Match expr = new Expr.Match(cases);
            expr.span = span(ctx);
            return expr;
        }
        return buildOr(ctx.orExpression());
    }

    private Expr buildOr(SprigParser.OrExpressionContext ctx) {
        Expr left = buildAnd(ctx.andExpression(0));
        for (int i = 1; i < ctx.andExpression().size(); i++) {
            Expr.Binary binary = new Expr.Binary("or", left, buildAnd(ctx.andExpression(i)));
            binary.span = span(ctx);
            left = binary;
        }
        return left;
    }

    private Expr buildAnd(SprigParser.AndExpressionContext ctx) {
        Expr left = buildNot(ctx.notExpression(0));
        for (int i = 1; i < ctx.notExpression().size(); i++) {
            Expr.Binary binary = new Expr.Binary("and", left, buildNot(ctx.notExpression(i)));
            binary.span = span(ctx);
            left = binary;
        }
        return left;
    }

    private Expr buildNot(SprigParser.NotExpressionContext ctx) {
        if (ctx.NOT() != null) {
            Expr.Unary unary = new Expr.Unary("not", buildNot(ctx.notExpression()));
            unary.span = span(ctx);
            return unary;
        }
        return buildComparison(ctx.comparisonExpression());
    }

    private Expr buildComparison(SprigParser.ComparisonExpressionContext ctx) {
        Expr left = buildAdditive(ctx.additiveExpression(0));
        if (ctx.comparisonOperator() != null) {
            Expr.Binary binary = new Expr.Binary(ctx.comparisonOperator().getText(),
                    left, buildAdditive(ctx.additiveExpression(1)));
            binary.span = span(ctx);
            return binary;
        }
        return left;
    }

    private Expr buildAdditive(SprigParser.AdditiveExpressionContext ctx) {
        Expr left = buildMultiplicative(ctx.multiplicativeExpression(0));
        for (int i = 1; i < ctx.multiplicativeExpression().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            Expr.Binary binary = new Expr.Binary(op, left, buildMultiplicative(ctx.multiplicativeExpression(i)));
            binary.span = span(ctx);
            left = binary;
        }
        return left;
    }

    private Expr buildMultiplicative(SprigParser.MultiplicativeExpressionContext ctx) {
        Expr left = buildUnary(ctx.unaryExpression(0));
        for (int i = 1; i < ctx.unaryExpression().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            Expr.Binary binary = new Expr.Binary(op, left, buildUnary(ctx.unaryExpression(i)));
            binary.span = span(ctx);
            left = binary;
        }
        return left;
    }

    private Expr buildUnary(SprigParser.UnaryExpressionContext ctx) {
        if (ctx.unaryExpression() != null) {
            String op = ctx.PLUS() != null ? "+" : "-";
            Expr.Unary unary = new Expr.Unary(op, buildUnary(ctx.unaryExpression()));
            unary.span = span(ctx);
            return unary;
        }
        return buildPostfix(ctx.postfixExpression());
    }

    private Expr buildPostfix(SprigParser.PostfixExpressionContext ctx) {
        Expr current = buildPrimary(ctx.primaryExpression());
        for (int i = 0; i < ctx.children.size(); i++) {
            ParseTree child = ctx.children.get(i);
            if (child instanceof SprigParser.ArgumentsContext argsCtx) {
                Expr.Call call = new Expr.Call(current, buildArguments(argsCtx));
                call.span = span(ctx);
                current = call;
            } else if (child instanceof SprigParser.SubscriptContext subscriptCtx) {
                SprigParser.SubscriptContentContext content = subscriptCtx.subscriptContent();
                Expr.Subscript subscript;
                if (content.expression() != null) {
                    subscript = new Expr.Subscript(current, buildExpression(content.expression()), null);
                } else {
                    List<TypeRef> args = new ArrayList<>();
                    for (SprigParser.TypeRefContext typeRef : content.typeRef()) {
                        args.add(buildTypeRef(typeRef));
                    }
                    subscript = new Expr.Subscript(current, null, args);
                }
                subscript.span = span(subscriptCtx);
                current = subscript;
            } else if (child instanceof TerminalNode node) {
                if (node.getSymbol().getType() == SprigLexer.LPAREN) {
                    boolean hasArgs = i + 1 < ctx.children.size()
                            && ctx.children.get(i + 1) instanceof SprigParser.ArgumentsContext;
                    if (!hasArgs) {
                        Expr.Call call = new Expr.Call(current, List.of());
                        call.span = span(ctx);
                        current = call;
                    }
                } else if (node.getSymbol().getType() == SprigLexer.DOT) {
                    ParseTree next = ctx.children.get(i + 1);
                    if (next instanceof TerminalNode ident) {
                        Expr.FieldAccess access = new Expr.FieldAccess(current, ident.getText());
                        access.span = span(ctx);
                        access.nameSpan = span(ident);
                        current = access;
                    }
                }
            }
        }
        return current;
    }

    private List<Expr.Arg> buildArguments(SprigParser.ArgumentsContext ctx) {
        List<Expr.Arg> args = new ArrayList<>();
        if (ctx.positionalArguments() != null) {
            for (SprigParser.ExpressionContext expr : ctx.positionalArguments().expression()) {
                args.add(new Expr.Arg(null, buildExpression(expr)));
            }
        } else if (ctx.namedArguments() != null) {
            for (SprigParser.NamedArgumentContext named : ctx.namedArguments().namedArgument()) {
                Expr.Arg arg = new Expr.Arg(named.IDENT().getText(), buildExpression(named.expression()));
                arg.nameSpan = span(named.IDENT());
                args.add(arg);
            }
        }
        return args;
    }

    private Expr buildPrimary(SprigParser.PrimaryExpressionContext ctx) {
        if (ctx.literal() != null) {
            return buildLiteral(ctx.literal());
        }
        if (ctx.IDENT() != null) {
            Expr.Name name = new Expr.Name(ctx.IDENT().getText());
            name.span = span(ctx.IDENT());
            return name;
        }
        if (ctx.expression() != null) {
            return buildExpression(ctx.expression());
        }
        if (ctx.listLiteral() != null) {
            List<Expr> items = new ArrayList<>();
            for (SprigParser.ExpressionContext item : ctx.listLiteral().expression()) {
                items.add(buildExpression(item));
            }
            Expr.ListLit lit = new Expr.ListLit(items);
            lit.span = span(ctx);
            return lit;
        }
        if (ctx.mapLiteral() != null) {
            List<Expr> keys = new ArrayList<>();
            List<Expr> values = new ArrayList<>();
            for (SprigParser.MapEntryContext entry : ctx.mapLiteral().mapEntry()) {
                keys.add(buildExpression(entry.expression(0)));
                values.add(buildExpression(entry.expression(1)));
            }
            Expr.MapLit lit = new Expr.MapLit(keys, values);
            lit.span = span(ctx);
            return lit;
        }
        return buildLambda(ctx.lambdaExpression());
    }

    private Expr buildLambda(SprigParser.LambdaExpressionContext ctx) {
        List<Decl.Param> params = new ArrayList<>();
        if (ctx.parameters() != null) {
            for (SprigParser.ParameterContext param : ctx.parameters().parameter()) {
                Decl.Param p = new Decl.Param(param.IDENT().getText(), buildTypeRef(param.typeRef()));
                p.nameSpan = span(param.IDENT());
                params.add(p);
            }
        }
        Expr.Lambda lambda = new Expr.Lambda(params, buildExpression(ctx.expression()));
        lambda.span = span(ctx);
        return lambda;
    }

    private Expr buildLiteral(SprigParser.LiteralContext ctx) {
        Expr literal;
        if (ctx.INT() != null) {
            literal = new Expr.IntLit(ctx.INT().getText());
        } else if (ctx.FLOAT() != null) {
            literal = new Expr.FloatLit(ctx.FLOAT().getText());
        } else if (ctx.STRING() != null) {
            literal = new Expr.StringLit(unescape(ctx.STRING().getText()));
        } else if (ctx.TRUE() != null) {
            literal = new Expr.BoolLit(true);
        } else if (ctx.FALSE() != null) {
            literal = new Expr.BoolLit(false);
        } else {
            literal = new Expr.NullLit();
        }
        literal.span = span(ctx);
        return literal;
    }

    // ---- helpers ----

    static String unquote(String text) {
        return text.substring(1, text.length() - 1);
    }

    static String unescape(String quoted) {
        String body = unquote(quoted);
        StringBuilder sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static Span span(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = lastMeaningful(ctx);
        int startLine = Math.max(0, start.getLine() - 1);
        int startCol = Math.max(0, start.getCharPositionInLine());
        int endLine = startLine;
        int endCol = startCol;
        int startOffset = start.getStartIndex();
        int endOffset = start.getStopIndex() + 1;
        if (stop != null) {
            endLine = Math.max(0, stop.getLine() - 1);
            endCol = Math.max(0, stop.getCharPositionInLine())
                    + (stop.getText() == null ? 0 : stop.getText().length());
            endOffset = stop.getStopIndex() + 1;
        }
        return new Span(startLine, startCol, endLine, endCol, startOffset, endOffset);
    }

    static Span span(TerminalNode node) {
        return span(node.getSymbol());
    }

    static Span span(Token token) {
        int line = Math.max(0, token.getLine() - 1);
        int col = Math.max(0, token.getCharPositionInLine());
        int length = token.getText() == null ? 0 : token.getText().length();
        return new Span(line, col, line, col + length, token.getStartIndex(), token.getStopIndex() + 1);
    }

    private static Token lastMeaningful(ParseTree tree) {
        if (tree instanceof TerminalNode node) {
            int type = node.getSymbol().getType();
            if (type == SprigLexer.NEWLINE || type == SprigLexer.INDENT
                    || type == SprigLexer.DEDENT || type == Token.EOF) {
                return null;
            }
            return node.getSymbol();
        }
        if (tree instanceof ParserRuleContext ctx) {
            for (int i = ctx.getChildCount() - 1; i >= 0; i--) {
                Token token = lastMeaningful(ctx.getChild(i));
                if (token != null) {
                    return token;
                }
            }
        }
        return null;
    }
}
