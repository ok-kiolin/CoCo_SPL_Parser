package spl.lexer;

public enum TokenType {


    KEYWORD_VOID, KEYWORD_NUM, KEYWORD_RETURN, KEYWORD_PRINT, KEYWORD_NOP,
    KEYWORD_COMMENT, KEYWORD_IF, KEYWORD_THEN, KEYWORD_ELSE, KEYWORD_WHILE,
    KEYWORD_UNTIL, KEYWORD_DO, KEYWORD_MOD, KEYWORD_ADD, KEYWORD_SUB,
    KEYWORD_MUL, KEYWORD_DIV, KEYWORD_NEG, KEYWORD_NOT, KEYWORD_AND,
    KEYWORD_OR, KEYWORD_EQ, KEYWORD_LARGER, KEYWORD_LESSER,

    // Punctuation / symbols 
    SYMBOL_DOLLAR,      // $  (Rule 0)
    SYMBOL_COLON,       // :
    SYMBOL_LPAREN,      // (
    SYMBOL_RPAREN,      // )
    SYMBOL_LBRACE,      // {
    SYMBOL_RBRACE,      // }
    SYMBOL_ASSIGN,      // =
    SYMBOL_SEMICOLON,   // ;

    // Lexical categories with variable content 
    NUMBER,             // NUM literal
    NAME,                // USER-DEFINED-NAME
    STRING,              // STRING literal


    EOF
}