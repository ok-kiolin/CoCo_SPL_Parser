package spl;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    private List<Token> lex(String s) { return new Lexer().tokenize(s); }
    private TokenType typeOf(String chunk) { return lex(chunk).get(0).type; }

    @Test void classifiesKeywords() {
        assertEquals(TokenType.PRINT, typeOf("print"));
        assertEquals(TokenType.KW_NUM, typeOf("num"));
        assertEquals(TokenType.WHILE, typeOf("while"));
    }
    @Test void classifiesSymbols() {
        assertEquals(TokenType.COLON, typeOf(":"));
        assertEquals(TokenType.LBRACE, typeOf("{"));
        assertEquals(TokenType.EQUALS, typeOf("="));
    }
    @Test void classifiesNames() {
        assertEquals(TokenType.NAME, typeOf("#x"));
        assertEquals(TokenType.NAME, typeOf("#counter1"));
        assertEquals(TokenType.NAME, typeOf("#"));
    }
    @Test void classifiesStrings() {
        assertEquals(TokenType.STRING, typeOf("\"hello\""));
        assertEquals(TokenType.STRING, typeOf("\"\""));
        assertEquals(TokenType.STRING, typeOf("\"a,b.c:d-e?f!\""));
    }

    @Test void acceptsValidNumbers() {
        for (String n : new String[]{"0","42","-42","0.5","-0.5","0.05","1.105","10"})
            assertEquals(TokenType.NUM, typeOf(n), "should accept " + n);
    }
    @Test void rejectsInvalidNumbers() {
        for (String n : new String[]{"-0","00","007","0.0","1.10","2.50","1."})
            assertThrows(SyntaxException.class, () -> lex(n), "should reject " + n);
    }

    @Test void rejectsUppercaseInString() { assertThrows(SyntaxException.class, () -> lex("\"Hello\"")); }
    @Test void rejectsUppercaseInName()   { assertThrows(SyntaxException.class, () -> lex("#X")); }
    @Test void rejectsUnknownWord()       { assertThrows(SyntaxException.class, () -> lex("prnt")); }

    @Test void endsWithEof() {
        List<Token> t = lex("#x : :");
        assertEquals(TokenType.EOF, t.get(t.size() - 1).type);
    }
    @Test void tracksPositions() {
        Token second = lex("#x\nprint").get(1);
        assertEquals(2, second.line);
        assertEquals(1, second.col);
    }
    @Test void splitsWholeProgram() {
        assertEquals(7, lex("#x : : print \"hi\" ;").size());
    }
    @Test void acceptsLfAndCrAsBlankSpace() {
        assertEquals(3, lex("#x\r#y\n").size());
    }
}
