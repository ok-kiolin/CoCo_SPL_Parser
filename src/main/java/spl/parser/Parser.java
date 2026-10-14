package spl.parser;

import java.util.List;
import spl.errors.SyntaxException;
import spl.lexer.Token;
import spl.lexer.TokenType;
import spl.tree.Node;

public final class Parser {
    private final TokenCursor cursor;

    //Hints with their errors
    private static final String HINT_END =
        "The main algorithm is finished here but more input follows. Check for a missing ';' " + "after the previous instruction, an extra '}', or a 'return' outside a function.";
    private static final String HINT_FIRST_COLON =
        "A program (and every function body) has the shape: variables : functions : algorithm, " + "e.g. '#x #y : : print ( #x ) ;'. Both ':' are needed even if a part is empty. "
        + "Variable names start with '#'; functions (void/num) go after the first ':'.";
    private static final String HINT_SECOND_COLON =
        "Put a ':' between the function declarations and the algorithm, even when there are no " + "functions. Variables must be declared before the first ':'. Every function must be "
        + "closed with '}' before the next one starts.";
    private static final String HINT_FUNC_NAME =
        "A function name starts with '#', e.g. void #show ( #a ) { : : print ( #a ) ; return }";
    private static final String HINT_PARAMS_OPEN =
        "Parameters are written in brackets after the function name, even when there are none: #f ( )";
    private static final String HINT_PARAMS_CLOSE =
        "Parameters are variable names starting with '#', separated by spaces, and closed with ')'.";
    private static final String HINT_BODY_OPEN =
        "The function body goes in braces: { : : ... return }";
    private static final String HINT_RETURN =
        "Every function body must end with 'return' just before its closing '}'. " + "Also check that every instruction in the body ends with ';'.";
    private static final String HINT_VOID_END =
        "A 'void' function ends with: return }  - it returns no value. Use 'num' to return a value.";
    private static final String HINT_NUM_RETURN =
        "A 'num' function must return a value: return ( TERM ). Use 'void' if it returns nothing.";
    private static final String HINT_NUM_RETURN_CLOSE =
        "The return value must be a single term inside brackets, e.g. return ( #x )";
    private static final String HINT_NUM_END =
        "A 'num' function ends with: return ( TERM ) }";

    public Parser(List<Token> tokens){
        this.cursor = new TokenCursor(tokens);
    }

    public Node parse() {
        return parseSplProg();
    }

    public Node parseSplProg(){
        Node root = Node.root("SPL_PROG");
        root.add(parseP());
        expectEndOfProgram();
        return root;
    }

    /**
     * After the main algorithm only the end of the file may follow. If something else is there,
     * say specifically why it cannot be there instead of just "expected end of file".
     */
    private void expectEndOfProgram() {
        Token t = cursor.peek();
        if (t.type() == TokenType.EOF) return;
        String problem = switch (t.type()) {
            case KEYWORD_RETURN -> "'return' is only allowed at the end of a function body.";
            case KEYWORD_VOID, KEYWORD_NUM ->
                "Functions must be declared between the first and the second ':', not in the algorithm.";
            case SYMBOL_RBRACE -> "This '}' has no matching '{'.";
            case SYMBOL_SEMICOLON -> "Unexpected ';' - there is no instruction before it (is there a doubled ';'?).";
            case SYMBOL_COLON -> "Unexpected ':' - a program has exactly two ':' (variables : functions : algorithm).";
            default -> "'" + t.lexeme() + "' cannot start an instruction. Instructions start with print, nop, "
                    + "comment, a name, if, while, until or do.";
        };
        throw new SyntaxException("Unexpected '" + t.lexeme() + "'. " + problem, t.line(), t.column(), HINT_END);
    }

    public Node parseP() {
        Node p = Node.inner("P");
        p.add(parseVDecl());
        p.add(leafFor(cursor.expect(TokenType.SYMBOL_COLON, HINT_FIRST_COLON))); 
        p.add(parseFDecl());
        p.add(leafFor(cursor.expect(TokenType.SYMBOL_COLON, HINT_SECOND_COLON)));
        p.add(parseAlgo());
        return p;
    }

    public Node parseVDecl() {
        Node node = Node.inner("V_DECL");
        if (cursor.peek().type() == TokenType.NAME) {
            node.add(leafFor(cursor.expect(TokenType.NAME)));
            node.add(parseVDecl());
        }
        return node;
    }

    public Node parseFDecl() {
        Node node = Node.inner("F_DECL");
        TokenType t = cursor.peek().type();
        if (t == TokenType.KEYWORD_VOID || t == TokenType.KEYWORD_NUM) {
            node.add(parseFType());
            node.add(parseFDecl());
        }
        return node;
    }

    public Node parseFType() {
        Node node = Node.inner("F_TYPE");
        if (cursor.peek().type() == TokenType.KEYWORD_VOID) {
            node.add(leafFor(cursor.expect(TokenType.KEYWORD_VOID)));
            node.add(leafFor(cursor.expect(TokenType.NAME, HINT_FUNC_NAME)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN, HINT_PARAMS_OPEN)));
            node.add(parseVDecl());
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN, HINT_PARAMS_CLOSE)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_BODY_OPEN)));
            node.add(parseP());
            node.add(leafFor(cursor.expect(TokenType.KEYWORD_RETURN, HINT_RETURN)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE, HINT_VOID_END)));
        } else {
            node.add(leafFor(cursor.expect(TokenType.KEYWORD_NUM)));
            node.add(leafFor(cursor.expect(TokenType.NAME, HINT_FUNC_NAME)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN, HINT_PARAMS_OPEN)));
            node.add(parseVDecl());
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN, HINT_PARAMS_CLOSE)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_BODY_OPEN)));
            node.add(parseP());
            node.add(leafFor(cursor.expect(TokenType.KEYWORD_RETURN, HINT_RETURN)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN, HINT_NUM_RETURN)));
            node.add(parseTerm());
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN, HINT_NUM_RETURN_CLOSE)));
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE, HINT_NUM_END)));
        }
        return node;
    }

    //the skeleton. task 3
    public Node parseAlgo() {
        Node node = Node.inner("ALGO");
        TokenType t = cursor.peek().type();
        if (startsInstruction(cursor.peek().type())) {
            node.add(parseInstr());
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_SEMICOLON,
                    "End each instruction with ';'.")));
            node.add(parseAlgo());
        }
        return node;
    }

    public Node parseInstr() {
        Node node = Node.inner("INSTR");
        switch (cursor.peek().type()) {
            case KEYWORD_PRINT -> {
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_PRINT)));
                node.add(parseOutp());
            }
            case KEYWORD_NOP -> node.add(leafFor(cursor.expect(TokenType.KEYWORD_NOP)));
            case KEYWORD_COMMENT -> {
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_COMMENT)));
                node.add(leafFor(cursor.expect(TokenType.STRING,
                        "A comment must be followed by a quoted string.")));
            }
            case NAME -> {
                Token next = cursor.peek(2);
                if (next.type() == TokenType.SYMBOL_ASSIGN) node.add(parseAssign());
                else if (next.type() == TokenType.SYMBOL_LPAREN) node.add(parseCall());
                else throw new SyntaxException(
                        "Expected '=' or '(' after a name, found '" + next.lexeme() + "'.",
                        next.line(), next.column(),
                        "Use '#x = TERM' for assignment or '#f ( INPUT )' for a call.");
            }
            case KEYWORD_IF -> node.add(parseBranch());
            case KEYWORD_WHILE, KEYWORD_UNTIL, KEYWORD_DO -> node.add(parseLoop());
            default -> {
                Token found = cursor.peek();
                throw new SyntaxException("Expected an instruction, found '" + found.lexeme() + "'.",
                        found.line(), found.column(), "Start with print, nop, comment, a name, if, while, until, or do.");
            }
        }
        return node;
    }

    public Node parseOutp() {
        Node node = Node.inner("OUTP");
        if (cursor.peek().type() == TokenType.SYMBOL_LPAREN) {
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN)));
            node.add(parseTerm());
            node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                    "Close the printed term with ')'.")));
        } else {
            node.add(leafFor(cursor.expect(TokenType.STRING,
                    "Write print STRING or print ( TERM ).")));
        }
        return node;
    }

    public Node parseAssign() {
        Node node = Node.inner("ASSIGN");
        node.add(leafFor(cursor.expect(TokenType.NAME)));
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_ASSIGN)));
        node.add(parseTerm());
        return node;
    }

    public Node parseCall() {
        Node node = Node.inner("CALL");
        node.add(leafFor(cursor.expect(TokenType.NAME)));
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                "Open function arguments with '('.")));
        node.add(parseInput());
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                "Close function arguments with ')'.")));
        return node;
    }

    public Node parseInput() {
        Node node = Node.inner("INPUT");
        if (startsTerm(cursor.peek().type())) {
            node.add(parseTerm());
            node.add(parseInput());
        }
        return node;
    }

    private static boolean startsInstruction(TokenType type) {
        return switch (type) {
            case KEYWORD_PRINT, KEYWORD_NOP, KEYWORD_COMMENT, NAME,
                    KEYWORD_IF, KEYWORD_WHILE, KEYWORD_UNTIL, KEYWORD_DO -> true;
            default -> false;
        };
    }

    private static boolean startsTerm(TokenType type) {
        return switch (type) {
            case NAME, NUMBER, KEYWORD_MOD, KEYWORD_ADD, KEYWORD_SUB,
                    KEYWORD_MUL, KEYWORD_DIV, KEYWORD_NEG -> true;
            default -> false;
        };
    }

    //the skeleton task 4
    // ---------------------------------------------------------------------
    // TASK 4: TERM, BOOL, BRANCH, LOOP, COND  (spec rules: TERM x9, BOOL x6,
    // BRANCH x1, LOOP x2, COND x2). Each method builds its node while parsing.
    // The two-token lookahead for TERM -> CALL vs TERM -> USER-DEFINED-NAME is
    // cursor.peek(2): a name followed by '(' is always a call, because '(' is
    // not in FOLLOW(TERM).
    // ---------------------------------------------------------------------
    private static final String HINT_TERM =
        "A term is a name (#x), a number (3, -2, 0.5), a call (#f ( ... )), "
        + "add/sub/mul/div/mod ( TERM TERM ) or neg ( TERM ).";
    private static final String HINT_BOOL =
        "A condition is not ( BOOL ), and ( BOOL BOOL ), or ( BOOL BOOL ), "
        + "or eq/larger/lesser ( TERM TERM ).";
    private static final String HINT_BRANCH =
        "A branch has the shape: if BOOL then { ... } else { ... } - the else part is required "
        + "(it may be empty: else { } ).";
    private static final String HINT_LOOP_DO =
        "A loop is either: while/until BOOL do { ... }  or  do { ... } while/until BOOL";
    private static final String HINT_COND =
        "After 'do { ... }' write the loop condition: while BOOL or until BOOL.";

    public Node parseTerm() {
        Node node = Node.inner("TERM");
        Token t = cursor.peek();
        switch (t.type()) {
            case NAME -> {
                if (cursor.peek(2).type() == TokenType.SYMBOL_LPAREN) {
                    node.add(parseCall());                                   // TERM -> CALL
                } else {
                    node.add(leafFor(cursor.expect(TokenType.NAME)));        // TERM -> USER-DEFINED-NAME
                }
            }
            case NUMBER -> node.add(leafFor(cursor.expect(TokenType.NUMBER)));  // TERM -> NUM
            case KEYWORD_MOD, KEYWORD_ADD, KEYWORD_SUB, KEYWORD_MUL, KEYWORD_DIV -> {
                String op = t.lexeme();                                      // TERM -> op ( TERM TERM )
                node.add(leafFor(cursor.expect(t.type())));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                        "Write the operands in brackets: " + op + " ( TERM TERM )")));
                node.add(parseTerm());
                node.add(parseTerm());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                        "'" + op + "' takes exactly two terms followed by ')', e.g. " + op + " ( 4 2 )")));
            }
            case KEYWORD_NEG -> {                                            // TERM -> neg ( TERM )
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_NEG)));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                        "Write the operand in brackets: neg ( TERM )")));
                node.add(parseTerm());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                        "'neg' takes exactly one term followed by ')', e.g. neg ( 5 )")));
            }
            default -> throw new SyntaxException("Expected a term, found " + describeFound(t) + ".",
                    t.line(), t.column(), HINT_TERM);
        }
        return node;
    }

    public Node parseBool() {
        Node node = Node.inner("BOOL");
        Token t = cursor.peek();
        switch (t.type()) {
            case KEYWORD_NOT -> {                                            // BOOL -> not ( BOOL )
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_NOT)));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                        "Write the condition in brackets: not ( BOOL )")));
                node.add(parseBool());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                        "'not' takes exactly one condition followed by ')'.")));
            }
            case KEYWORD_AND, KEYWORD_OR -> {                                // BOOL -> and/or ( BOOL BOOL )
                String op = t.lexeme();
                node.add(leafFor(cursor.expect(t.type())));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                        "Write the conditions in brackets: " + op + " ( BOOL BOOL )")));
                node.add(parseBool());
                node.add(parseBool());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                        "'" + op + "' takes exactly two conditions followed by ')'.")));
            }
            case KEYWORD_EQ, KEYWORD_LARGER, KEYWORD_LESSER -> {             // BOOL -> cmp ( TERM TERM )
                String op = t.lexeme();
                node.add(leafFor(cursor.expect(t.type())));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LPAREN,
                        "Write the operands in brackets: " + op + " ( TERM TERM )")));
                node.add(parseTerm());
                node.add(parseTerm());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RPAREN,
                        "'" + op + "' compares exactly two terms followed by ')', e.g. " + op + " ( #x 0 )")));
            }
            default -> throw new SyntaxException("Expected a condition, found " + describeFound(t) + ".",
                    t.line(), t.column(), HINT_BOOL);
        }
        return node;
    }

    public Node parseBranch() {                                              // BRANCH -> if BOOL then { ALGO } else { ALGO }
        Node node = Node.inner("BRANCH");
        node.add(leafFor(cursor.expect(TokenType.KEYWORD_IF, HINT_BRANCH)));
        node.add(parseBool());
        node.add(leafFor(cursor.expect(TokenType.KEYWORD_THEN, HINT_BRANCH)));
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_BRANCH)));
        node.add(parseAlgo());
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE,
                "Close the 'then' block with '}'. Also check that every instruction inside ends with ';'.")));
        node.add(leafFor(cursor.expect(TokenType.KEYWORD_ELSE, HINT_BRANCH)));
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_BRANCH)));
        node.add(parseAlgo());
        node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE,
                "Close the 'else' block with '}'. Also check that every instruction inside ends with ';'.")));
        return node;
    }

    public Node parseLoop() {
        Node node = Node.inner("LOOP");
        Token t = cursor.peek();
        switch (t.type()) {
            case KEYWORD_WHILE, KEYWORD_UNTIL -> {                           // LOOP -> COND BOOL do { ALGO }
                node.add(parseCond());
                node.add(parseBool());
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_DO, HINT_LOOP_DO)));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_LOOP_DO)));
                node.add(parseAlgo());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE,
                        "Close the loop body with '}'. Also check that every instruction inside ends with ';'.")));
            }
            case KEYWORD_DO -> {                                             // LOOP -> do { ALGO } COND BOOL
                node.add(leafFor(cursor.expect(TokenType.KEYWORD_DO)));
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_LBRACE, HINT_LOOP_DO)));
                node.add(parseAlgo());
                node.add(leafFor(cursor.expect(TokenType.SYMBOL_RBRACE,
                        "Close the loop body with '}'. Also check that every instruction inside ends with ';'.")));
                node.add(parseCond());
                node.add(parseBool());
            }
            default -> throw new SyntaxException("Expected a loop, found " + describeFound(t) + ".",
                    t.line(), t.column(), HINT_LOOP_DO);
        }
        return node;
    }

    public Node parseCond() {                                                // COND -> while | until
        Node node = Node.inner("COND");
        Token t = cursor.peek();
        switch (t.type()) {
            case KEYWORD_WHILE -> node.add(leafFor(cursor.expect(TokenType.KEYWORD_WHILE)));
            case KEYWORD_UNTIL -> node.add(leafFor(cursor.expect(TokenType.KEYWORD_UNTIL)));
            default -> throw new SyntaxException("Expected 'while' or 'until', found " + describeFound(t) + ".",
                    t.line(), t.column(), HINT_COND);
        }
        return node;
    }

    private static String describeFound(Token t) {
        return t.type() == TokenType.EOF ? "end of file" : "'" + t.lexeme() + "'";
    }

    private static Node leafFor(Token t) {
        return Node.leaf(t.lexeme(), t.line(), t.column());
    }
}