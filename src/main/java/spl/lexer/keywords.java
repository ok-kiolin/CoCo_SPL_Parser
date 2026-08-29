package spl.lexer;
import java.util.Map;

public final class Keywords {

    public static final Map<String, TokenType> RESERVED = Map.ofEntries(
        Map.entry("void", TokenType.KEYWORD_VOID),
        Map.entry("num", TokenType.KEYWORD_NUM),
        Map.entry("return", TokenType.KEYWORD_RETURN),
        Map.entry("print", TokenType.KEYWORD_PRINT),
        Map.entry("nop", TokenType.KEYWORD_NOP),
        Map.entry("comment", TokenType.KEYWORD_COMMENT),
        Map.entry("if", TokenType.KEYWORD_IF),
        Map.entry("then", TokenType.KEYWORD_THEN),
        Map.entry("else", TokenType.KEYWORD_ELSE),
        Map.entry("while", TokenType.KEYWORD_WHILE),
        Map.entry("until", TokenType.KEYWORD_UNTIL),
        Map.entry("do", TokenType.KEYWORD_DO),
        Map.entry("mod", TokenType.KEYWORD_MOD),
        Map.entry("add", TokenType.KEYWORD_ADD),
        Map.entry("sub", TokenType.KEYWORD_SUB),
        Map.entry("mul", TokenType.KEYWORD_MUL),
        Map.entry("div", TokenType.KEYWORD_DIV),
        Map.entry("neg", TokenType.KEYWORD_NEG),
        Map.entry("not", TokenType.KEYWORD_NOT),
        Map.entry("and", TokenType.KEYWORD_AND),
        Map.entry("or", TokenType.KEYWORD_OR),
        Map.entry("eq", TokenType.KEYWORD_EQ),
        Map.entry("larger", TokenType.KEYWORD_LARGER),
        Map.entry("lesser", TokenType.KEYWORD_LESSER)
    );

    private Keywords() {}
}