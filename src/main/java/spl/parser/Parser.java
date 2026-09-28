package spl.parser;

import java.util.List;
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

    //the skeleton. task 3
    public Node parseAlgo() {
        Node node = Node.inner("ALGO");
        TokenType t = cursor.peek().type();

        boolean startsInstr = t == TokenType.KEYWORD_PRINT || t == TokenType.KEYWORD_NOP
                || t == TokenType.KEYWORD_COMMENT ||t == TokenType.NAME
                || t == TokenType.KEYWORD_IF ||t == TokenType.KEYWORD_WHILE
                || t == TokenType.KEYWORD_UNTIL ||t == TokenType.KEYWORD_DO;

        if(startsInstr) {
            //TASK 3

            throw todo("ALGO -> INSTR ; ALGO");
        }

        return node; //epsilon

    }
    public Node parseInstr()  { throw todo("INSTR"); }
    public Node parseOutp()   { throw todo("OUTP"); }
    public Node parseAssign() { throw todo("ASSIGN"); }
    public Node parseCall()   { throw todo("CALL"); }
    public Node parseInput()  { throw todo("INPUT"); }

    //the skeleton task 4
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
