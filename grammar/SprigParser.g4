// Sprig v0.7 syntax grammar. ANTLR4 + separate Java layout token source.
// This grammar recognizes syntax, NOT static types, nullability, exhaustiveness,
// read-only collection rules, constructor call categories or JVM API validity.
parser grammar SprigParser;
options { tokenVocab=SprigLexer; }

// Imports have one canonical location: at the start of a module.
program
    : NEWLINE* ((importStatement | exportStatement) NEWLINE NEWLINE*)*
      (NEWLINE | exportStatement NEWLINE | genericDefinition | classDefinition | enumDefinition
      | variantDefinition | conformDefinition NEWLINE | functionDefinition | statement)* EOF
    ;

// Declares a foreign JVM nominal contract: the Sprig class already specified
// by the left name satisfies the imported Java interface named on the right.
// 'to' stays a contextual word so existing identifiers named to keep working.
conformDefinition: CONFORM IDENT toClause IDENT superArguments? (AS IDENT)?;
toClause: {"to".equals(_input.LT(1).getText())}? IDENT;
// Fields handed to the Java superclass constructor; names only, no expressions.
superArguments: LPAREN (IDENT (COMMA IDENT)*)? RPAREN;

// v0.8: block-scoped generic parameters around exactly one declaration.
// Parameters are only visible inside this block and never leak. There is no
// arbitrary limit on their number.
genericDefinition: GENERIC typeParameterList COLON genericSuite;
typeParameterList: IDENT (COMMA IDENT)*;
genericSuite
    : NEWLINE INDENT genericBody DEDENT
    ;
genericBody: classDefinition | variantDefinition | functionDefinition;

importStatement: IMPORT (qualifiedName | STRING) (AS IDENT)?;
// Contextual keyword: existing variables/parameters/fields named export remain legal.
exportStatement: {"export".equals(_input.LT(1).getText())}? IDENT IDENT DOT IDENT;
qualifiedName: IDENT (DOT IDENT)*;

classDefinition: CLASS IDENT COLON classSuite;
classSuite
    : NEWLINE INDENT (NEWLINE | fieldDeclaration statementEnd | functionDefinition | PASS NEWLINE)+ DEDENT
    ;
fieldDeclaration: (VAR | LET) IDENT COLON typeRef (ASSIGN expression)?;

enumDefinition: ENUM IDENT COLON enumSuite;
enumSuite: NEWLINE INDENT (NEWLINE | IDENT NEWLINE)+ DEDENT;

// A sealed sum type: each variant case has immutable named fields.
// The semantic checker verifies unique cases, recursive type references,
// constructor field types, and exhaustive match coverage.
variantDefinition: VARIANT IDENT COLON variantSuite;
variantSuite: NEWLINE INDENT (NEWLINE | variantCase)+ DEDENT;
// v0.8 adds the expanded payload form for generic variants:
//     Some:
//         value: T
// The compact form Some(value: T) remains the canonical v0.7 style.
variantCase
    : IDENT (LPAREN variantFields RPAREN)? NEWLINE
    | IDENT COLON NEWLINE (INDENT (NEWLINE | variantField NEWLINE)+ DEDENT)?
    ;
variantFields: variantField (COMMA variantField)* COMMA?;
variantField: IDENT COLON typeRef;

functionDefinition
    : FUNC IDENT LPAREN parameters? RPAREN ARROW returnTypeRef
      (THROWS typeRef (COMMA typeRef)* | RETHROWS)? COLON suite
    ;
parameters: parameter (COMMA parameter)* COMMA?;
parameter: IDENT COLON typeRef;

// Named functions/methods have explicit parameter and return types.
// Local bindings can infer their type from their initializer.
typeRef
    : qualifiedName (LBRACK typeRef (COMMA typeRef)* RBRACK)? QUESTION?
    | functionType
    | LPAREN functionType RPAREN QUESTION?
    ;
// A function type's own throws clause binds to the nearest fn.
functionType: FN LPAREN (typeRef (COMMA typeRef)*)? RPAREN ARROW typeRef (THROWS typeRef)?;
// In a declaration's return position an unparenthesized function type takes no
// clause of its own, so `-> fn(Int) -> Int throws Error:` still declares that the
// function throws; a returned callable with a clause is written in parentheses.
returnTypeRef
    : qualifiedName (LBRACK typeRef (COMMA typeRef)* RBRACK)? QUESTION?
    | plainFunctionType
    | LPAREN functionType RPAREN QUESTION?
    ;
plainFunctionType: FN LPAREN (typeRef (COMMA typeRef)*)? RPAREN ARROW returnTypeRef;
variableDeclaration: (VAR | LET) IDENT typeAnnotation? ASSIGN expression;
typeAnnotation: COLON typeRef;

// At the start of a statement, 'match' and 'if' always begin the statement
// forms below: an expression statement is an orExpression, which never starts
// with either keyword, so one token of lookahead decides. (A predicate would
// let prediction read a whole if statement as a possible if expression, and an
// error in a later branch would then be reported against the first line.)
statement
    : simpleStatement statementEnd
    | ifStatement | whileStatement | forStatement | tryStatement | matchStatement
    ;
// A block expression already ends in DEDENT, which closes its physical line.
statementEnd: NEWLINE | {_input.LT(-1).getType() == DEDENT}?;
simpleStatement
    : variableDeclaration | assignment | requiresStatement
    | RETURN expression? | BREAK | CONTINUE | PASS | THROW expression
    | orExpression
    ;
// v0.8 capability clause, valid only at the start of a generic function suite.
requiresStatement: REQUIRES IDENT COLON qualifiedName;
assignment: assignmentTarget assignmentOperator expression;
assignmentTarget: IDENT (DOT IDENT | LBRACK expression RBRACK)*;
assignmentOperator: ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN;

ifStatement: IF expression COLON suite
    (ELIF expression COLON suite)* (ELSE COLON suite)?;
whileStatement: WHILE expression COLON suite;
forStatement: FOR IDENT IN expression COLON suite;
tryStatement
    : TRY COLON suite catchClause+ (FINALLY COLON suite)?
    | TRY COLON suite FINALLY COLON suite
    ;
catchClause: CATCH IDENT COLON typeRef COLON suite;
suite: NEWLINE INDENT (NEWLINE | statement)+ DEDENT;

// Statement match retains its existing suites. A branch matches exactly one
// enum case or variant case, with an optional binding of the entire payload.
// The type checker rejects non-variant/non-enum scrutinees, duplicate branches,
// missing cases, and bindings on payloadless enum cases. No wildcard/default.
matchStatement: MATCH expression COLON matchSuite;
matchSuite: NEWLINE INDENT (NEWLINE | matchBranch)+ DEDENT;
matchBranch: CASE qualifiedName (AS IDENT)? COLON suite;

matchExpression: MATCH expression COLON NEWLINE INDENT (NEWLINE | matchExpressionBranch)+ DEDENT;
matchExpressionBranch: CASE qualifiedName (AS IDENT)? COLON NEWLINE INDENT NEWLINE* expression statementEnd NEWLINE* DEDENT;

// An if expression chooses one value. Like an expression-match branch, every
// branch is exactly one expression on its own indented line, and 'elif' and
// 'else' line up with the line that starts the expression. The else branch is
// required by the language; the grammar accepts its absence so that the AST
// builder can report it as the one syntax error and still build the if
// expression, which keeps the language server working while it is written.
ifExpression
    : IF expression COLON ifExpressionBranch
      (ELIF expression COLON ifExpressionBranch)*
      (ELSE COLON ifExpressionBranch)?
    ;
ifExpressionBranch: NEWLINE INDENT NEWLINE* expression statementEnd NEWLINE* DEDENT;
expression: ifExpression | matchExpression | orExpression;
orExpression: andExpression (OR andExpression)*;
andExpression: notExpression (AND notExpression)*;
notExpression: NOT notExpression | comparisonExpression;
// No chained comparisons, assignment expressions or pipe operators.
comparisonExpression: additiveExpression (comparisonOperator additiveExpression)?;
comparisonOperator: EQEQ | NEQ | LT | LE | GT | GE | IN;
additiveExpression: multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*;
multiplicativeExpression: unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*;
unaryExpression: (PLUS | MINUS) unaryExpression | postfixExpression;
postfixExpression
    : primaryExpression (DOT IDENT | LPAREN arguments? RPAREN
      | subscript)*
    ;
// The bracket payload is syntactically a type argument list when it parses
// as type references, otherwise an index expression. Which one applies is
// decided by the checker from symbol kinds (a type name versus a value), so
// `values[index]` keeps indexing while `Box[Int](...)` is a generic use.
subscript: LBRACK subscriptContent RBRACK;
subscriptContent: typeRef (COMMA typeRef)* | expression;
// Agent-friendly: positional and named arguments cannot mix syntactically.
// The checker ALSO enforces category: Sprig class/variant constructors are
// named-only; ordinary functions and JVM calls use positional arguments.
arguments: positionalArguments | namedArguments;
positionalArguments: expression (COMMA expression)* COMMA?;
namedArguments: namedArgument (COMMA namedArgument)* COMMA?;
namedArgument: IDENT ASSIGN expression;
primaryExpression
    : literal | IDENT | LPAREN expression RPAREN
    | listLiteral | mapLiteral | lambdaExpression
    ;
listLiteral: LBRACK (expression (COMMA expression)* COMMA?)? RBRACK;
mapLiteral: LBRACE (mapEntry (COMMA mapEntry)* COMMA?)? RBRACE;
mapEntry: expression COLON expression;
lambdaExpression: FN LPAREN parameters? RPAREN FAT_ARROW expression;
literal: INT | FLOAT | STRING | TRUE | FALSE | NULL;
