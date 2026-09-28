package spl.parser;

import java.util.List;
import spl.errors.SyntaxException;
import spl.lexer.Token;
import spl.lexer.TokenType;
import spl.tree.Node;

public final class Parser {
    private final TokenCursor cursor;


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
        cursor.expect(TokenType.EOF, HINT_END);
        return root;
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

    public Node parseAlgo() {
        Node node = Node.inner("ALGO");
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

    public Node parseTerm()   { throw todo("TERM"); }
    public Node parseBranch() { throw todo("BRANCH"); }
    public Node parseBool()   { throw todo("BOOL"); }
    public Node parseLoop()   { throw todo("LOOP"); }
    public Node parseCond()   { throw todo("COND"); }

    private static Node leafFor(Token t) {
        return Node.leaf(t.lexeme(), t.line(), t.column());
    }

    private static UnsupportedOperationException todo(String rule) {
        return new UnsupportedOperationException(rule + " not implemented yet");
    }
}
