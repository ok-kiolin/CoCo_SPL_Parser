package spl;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Lexer {

    private static final Pattern NUM = Pattern.compile("0|-?0\\.[0-9]*[1-9]|-?[1-9][0-9]*\\.[0-9]*[1-9]|-?[1-9][0-9]*");
    private static final Pattern NAME = Pattern.compile("#[0-9a-z]*");
    private static final Pattern STRING = Pattern.compile("\"[,.:\\-?!0-9a-z]*\"");

    private static final String SYMBOLS = ":;(){}=";

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t';
    }

    public List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        int line = 1, col = 1, i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (isBlank(c)) {
                if (c == '\n') { line++; col = 1; } else { col++; }
                i++;
                continue;
            }
            int startCol = col;
            StringBuilder sb = new StringBuilder();
            while (i < n && !isBlank(src.charAt(i))) {
                sb.append(src.charAt(i));
                i++; col++;
            }
            out.add(classify(sb.toString(), line, startCol));
        }
        out.add(new Token(TokenType.EOF, "$", line, col));
        return out;
    }

    private Token classify(String chunk, int line, int col) {
        TokenType kw = Keywords.TABLE.get(chunk);
        if (kw != null) return new Token(kw, chunk, line, col);
        if (chunk.length() == 1 && SYMBOLS.indexOf(chunk.charAt(0)) >= 0)
            return new Token(symbol(chunk.charAt(0)), chunk, line, col);
        if (NUM.matcher(chunk).matches())    return new Token(TokenType.NUM, chunk, line, col);
        if (NAME.matcher(chunk).matches())   return new Token(TokenType.NAME, chunk, line, col);
        if (STRING.matcher(chunk).matches()) return new Token(TokenType.STRING, chunk, line, col);
        throw new SyntaxException(line, col, "unrecognised token '" + chunk + "'",
            hintFor(chunk));
    }

    private TokenType symbol(char c) {
        switch (c) {
            case ':': return TokenType.COLON;
            case ';': return TokenType.SEMICOLON;
            case '(': return TokenType.LPAREN;
            case ')': return TokenType.RPAREN;
            case '{': return TokenType.LBRACE;
            case '}': return TokenType.RBRACE;
            case '=': return TokenType.EQUALS;
            default:  throw new IllegalStateException();
        }
    }

    private String hintFor(String chunk) {
        if (chunk.startsWith("\""))
            return "strings may contain only lowercase letters, digits and , . : - ? ! "
                 + "(no spaces, no uppercase) and must be closed with \"";
        if (chunk.startsWith("#"))
            return "names are # followed by only lowercase letters and digits";
        if (!chunk.isEmpty() && (Character.isDigit(chunk.charAt(0)) || chunk.charAt(0) == '-'))
            return "numbers have no leading zero, and any decimal must end in a non-zero digit";
        if (chunk.chars().allMatch(ch -> ch >= 'a' && ch <= 'z'))
            return "not a reserved keyword - check the spelling";
        return "every token must be separated by a blank space";
    }
}
