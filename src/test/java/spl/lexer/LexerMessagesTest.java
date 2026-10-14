package spl.lexer;

import org.junit.jupiter.api.Test;
import spl.errors.SyntaxException;
import spl.parser.Parser;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Error positions and hint wording that a tutor actually reads. */
class LexerMessagesTest {

    private static String error(String source) {
        SyntaxException e = assertThrows(SyntaxException.class,
                () -> new Parser(new Lexer().tokenize(source)).parse(), "should be rejected: " + source);
        return e.getMessage();
    }

    private static void contains(String message, String expected) {
        assertTrue(message.contains(expected), "expected <" + expected + "> in:\n" + message);
    }

    // ---- line numbers for every line-ending style ----
    @Test void lineNumbersWithLf()   { contains(error("#a :\n:\n#a = 1\nprint ( #a ) ;\n"), "line 4"); }
    @Test void lineNumbersWithCrLf() { contains(error("#a :\r\n:\r\n#a = 1\r\nprint ( #a ) ;\r\n"), "line 4"); }
    @Test void lineNumbersWithCrOnly() { contains(error("#a :\r:\r#a = 1\rprint ( #a ) ;\r"), "line 4, column 1"); }
    @Test void columnsCountFromOneAfterCrLf() { contains(error(": :\r\n  prnt ;\r\n"), "line 2, column 3"); }

    // ---- hints must match the actual mistake ----
    @Test void uppercaseKeywordHint() { contains(error(": : PRINT \"hi\" ;"), "no uppercase"); }
    @Test void unknownSymbolHint()    { contains(error("#n : : #n = 1 + 2 ;"), "only symbols"); }
    @Test void dollarIsNotPartOfSpl() { contains(error(": : nop ; $"), "not part of SPL"); }
    @Test void nameWithoutHashHint()  { contains(error("#n : : n = 5 ;"), "must start with #"); }
    @Test void gluedTokensHint()      { contains(error(": : nop;"), "followed by a blank space"); }
    @Test void unclosedStringHint()   { contains(error(": : print \"hello world\" ;"), "may not contain spaces"); }
    @Test void badNumberHint()        { contains(error("#n : : #n = 007 ;"), "leading zero"); }

    // ---- input left over after the algorithm ----
    @Test void returnOutsideFunction()       { contains(error(": : return ;"), "only allowed at the end of a function"); }
    @Test void functionInAlgorithm()         { contains(error(": : void #f ( ) { : : return }"), "between the first and the second ':'"); }
    @Test void unmatchedClosingBrace()       { contains(error(": : nop ; }"), "no matching '{'"); }
    @Test void doubledSemicolon()            { contains(error(": : nop ; ;"), "doubled ';'"); }
    @Test void instructionCannotStartWithNumber() { contains(error(": : 5 ;"), "cannot start an instruction"); }
}
