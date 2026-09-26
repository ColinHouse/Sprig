// Sprig v0.7 / Java target. LayoutTokenSource adds INDENT and DEDENT.
// Lexical rules only: all type and effect checks are performed after parsing.
lexer grammar SprigLexer;

tokens { INDENT, DEDENT }

GENERIC: 'generic';
CLASS: 'class';
ENUM: 'enum';
VARIANT: 'variant';
MATCH: 'match';
CASE: 'case';
FUNC: 'func';
FN: 'fn';
VAR: 'var';
LET: 'let';
IF: 'if';
ELIF: 'elif';
ELSE: 'else';
WHILE: 'while';
FOR: 'for';
IN: 'in';
RETURN: 'return';
BREAK: 'break';
CONTINUE: 'continue';
PASS: 'pass';
IMPORT: 'import';
AS: 'as';
TRY: 'try';
CATCH: 'catch';
FINALLY: 'finally';
THROW: 'throw';
THROWS: 'throws';
REQUIRES: 'requires';
AND: 'and';
OR: 'or';
NOT: 'not';
TRUE: 'true';
FALSE: 'false';
NULL: 'null';

ARROW: '->';
FAT_ARROW: '=>';
EQEQ: '==';
NEQ: '!=';
LE: '<=';
GE: '>=';
PLUS_ASSIGN: '+=';
MINUS_ASSIGN: '-=';
STAR_ASSIGN: '*=';
SLASH_ASSIGN: '/=';
ASSIGN: '=';
LT: '<';
GT: '>';
PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
PERCENT: '%';
DOT: '.';
QUESTION: '?';
COLON: ':';
COMMA: ',';
LPAREN: '(';
RPAREN: ')';
LBRACK: '[';
RBRACK: ']';
LBRACE: '{';
RBRACE: '}';

FLOAT: DIGIT+ '.' DIGIT+ EXP? | DIGIT+ EXP;
INT: DIGIT+;
STRING: '"' ('\\' ["\\nrt] | ~["\\\r\n])* '"';
IDENT: [a-zA-Z_] [a-zA-Z_0-9]*;
NEWLINE: '\r\n' | '\n' | '\r';
COMMENT: '#' ~[\r\n]* -> skip;
SPACE: [ ]+ -> skip;
TAB: '\t'; // LayoutTokenSource rejects tabs, including inline tabs.
ERROR_CHAR: .; // LayoutTokenSource rejects unknown characters; never silently skip.
fragment EXP: [eE] [+-]? DIGIT+;
fragment DIGIT: [0-9];
