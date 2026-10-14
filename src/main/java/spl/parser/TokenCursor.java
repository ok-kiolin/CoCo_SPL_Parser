package spl.parser;

import java.util.List;
import java.util.Map;

import spl.errors.SyntaxException;
import spl.lexer.Token;
import spl.lexer.TokenType;

public final class TokenCursor {
    
    private final List<Token> tokens;
    private int pos = 0;

    public TokenCursor(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Token peek() {
        return peek(1);
    }

    public Token peek(int distance) {
        int idx = pos + (distance - 1);

        if (idx >= tokens.size()){
            idx = tokens.size() - 1;
        }

        return tokens.get(idx);
    }

    public Token match(TokenType type){
        if(peek().type() == type) {
            return tokens.get(pos++);
        }

        return null;
    }

    public Token expect(TokenType type){
        return expect(type, hintFor(type));
    }

    public Token expect(TokenType type, String hint) {
        Token t = match(type);

        if(t!=null) {
            return t;
        }

        Token found = peek();

        String message = "Expected " + describe(type) + ", found " + describeFound(found) + ".";


        throw new SyntaxException(message, found.line(), found.column(), hint);
    }

    private static final Map<TokenType, String> SYMBOL_TEXT = Map.of(
        TokenType.SYMBOL_DOLLAR, "$",
        TokenType.SYMBOL_COLON, ":",
        TokenType.SYMBOL_LPAREN, "(",
        TokenType.SYMBOL_RPAREN, ")",
        TokenType.SYMBOL_LBRACE, "{",
        TokenType.SYMBOL_RBRACE, "}",
        TokenType.SYMBOL_ASSIGN, "=",
        TokenType.SYMBOL_SEMICOLON, ";"
    );

    private static String describe(TokenType type){
        String name = type.name();

        if (name.startsWith("KEYWORD_")){
            return "'" + name.substring(8).toLowerCase() + "'";
        }

        if (name.startsWith("SYMBOL_")){
            return "'" + SYMBOL_TEXT.get(type) + "'";
        }

        return switch (type){
            case NAME -> "a name (e.g. #x)";
            case NUMBER -> "a number";
            case STRING -> "a string";
            case EOF -> "end of file";
            default -> name;
        };
    }

    private static String describeFound(Token found) {
        return found.type() == TokenType.EOF ? "end of file" : "'" + found.lexeme() + "'";
    }

    private static String hintFor(TokenType expected) {
        return "Check the SPL grammar rule you are in: " + describe(expected) + " is required at this point.";
    }
}
