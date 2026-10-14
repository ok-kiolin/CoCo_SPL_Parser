package spl.lexer;

import spl.errors.SyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Lexer {
    private static final Pattern NUM = Pattern.compile(
        "0|-?0\\.[0-9]*[1-9]|-?[1-9][0-9]*\\.[0-9]*[1-9]|-?[1-9][0-9]*");
    private static final Pattern NAME = Pattern.compile("#[0-9a-z]*");
    private static final Pattern STRING = Pattern.compile("\"[,.:\\-?!0-9a-z]*\"");
    private static final String SYMBOLS = ":;(){}=";
    private static boolean isBlank(char c) { return c == ' ' || c == '\r' || c == '\n' || c == '\t'; }

    public List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        int line = 1, col = 1, i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (isBlank(c)) {
                // A line ends at CR+LF (Windows), LF (Linux/macOS) or a lone CR (the spec's ASCII 13 'enter').
                if (c == '\n' || (c == '\r' && (i + 1 >= n || src.charAt(i + 1) != '\n'))) { line++; col = 1; }
                else if (c != '\r') { col++; }
                i++;
                continue;
            }
            int startCol = col; StringBuilder sb = new StringBuilder();
            while (i < n && !isBlank(src.charAt(i))) { sb.append(src.charAt(i)); i++; col++; }
            out.add(classify(sb.toString(), line, startCol));
        }
        out.add(new Token(TokenType.EOF, "$", line, col));
        return out;
    }
    private Token classify(String chunk, int line, int col) {
        TokenType kw = Keywords.RESERVED.get(chunk);
        if (kw != null) return new Token(kw, chunk, line, col);
        if (chunk.length() == 1 && SYMBOLS.indexOf(chunk.charAt(0)) >= 0)
            return new Token(symbol(chunk.charAt(0)), chunk, line, col);
        if (NUM.matcher(chunk).matches())    return new Token(TokenType.NUMBER, chunk, line, col);
        if (NAME.matcher(chunk).matches())   return new Token(TokenType.NAME, chunk, line, col);
        if (STRING.matcher(chunk).matches()) return new Token(TokenType.STRING, chunk, line, col);
        throw new SyntaxException("Unrecognised token '" + chunk + "'.", line, col, hintFor(chunk));
    }
    private TokenType symbol(char c) {
        switch (c) {
            case ':': return TokenType.SYMBOL_COLON;   case ';': return TokenType.SYMBOL_SEMICOLON;
            case '(': return TokenType.SYMBOL_LPAREN;  case ')': return TokenType.SYMBOL_RPAREN;
            case '{': return TokenType.SYMBOL_LBRACE;  case '}': return TokenType.SYMBOL_RBRACE;
            case '=': return TokenType.SYMBOL_ASSIGN;  default: throw new IllegalStateException();
        }
    }
    /** A short hint for the most common lexical mistakes, chosen by what the bad chunk looks like. */
    private String hintFor(String chunk) {
        if (chunk.startsWith("\"")) {
            if (chunk.length() == 1 || !chunk.endsWith("\""))
                return "a string must be closed with \" and may not contain spaces, e.g. \"hello\" (write \"hello-world\", not \"hello world\")";
            return "strings may contain only lowercase letters, digits and , . : - ? ! (no spaces, no uppercase)";
        }
        if (chunk.chars().anyMatch(ch -> ":;(){}=".indexOf(ch) >= 0))
            return "every token must be followed by a blank space, e.g. write  print ( #x ) ;  not  print(#x);";
        if (chunk.chars().anyMatch(Character::isUpperCase))
            return "SPL has no uppercase letters: keywords are lowercase (print, if, while, ...) and names are # followed by lowercase letters and digits";
        if (chunk.startsWith("#"))
            return "names are # followed by only lowercase letters and digits, e.g. #count1";
        if (Character.isDigit(chunk.charAt(0)) || (chunk.charAt(0) == '-' && chunk.length() > 1))
            return "numbers have no leading zero (write 7, not 007), -0 is not allowed, and a decimal must end in a non-zero digit (write 1.5, not 1.50)";
        if (chunk.chars().allMatch(ch -> ch >= 'a' && ch <= 'z'))
            return "'" + chunk + "' is not a keyword of SPL - check the spelling. Variable and function names must start with #, e.g. #" + chunk;
        return "'" + chunk + "' is not part of SPL. The only symbols are  : ; ( ) { } =  and every token must be followed by a blank space";
    }
}