package spl.codegen;

import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.lexer.Lexer;
import spl.parser.Parser;
import static org.junit.jupiter.api.Assertions.*;

/** Independent arithmetic oracle across many inline expansions in one target program. */
class GeneratedProgramMatrixTest {
    @TempDir Path directory;

    @Test void repeatedNestedCallsMatchIndependentIntegerOracle() throws Exception {
        StringBuilder source = new StringBuilder("#total : num #outer ( #p ) { #x : "
                + "num #inner ( #n ) { : : #x = add ( #x #n ) ; "
                + "#total = add ( #total 1 ) ; return ( #x ) } : "
                + "#x = #p ; #x = #inner ( 2 ) ; return ( #inner ( 3 ) ) } "
                + "num #square ( #p ) { : : return ( mul ( #p #p ) ) } : #total = 0 ; ");
        StringBuilder expected = new StringBuilder();
        Random random = new Random(341);
        for (int i = 0; i < 40; i++) {
            int argument = random.nextInt(31) - 15;
            source.append("print ( #outer ( #square ( ").append(argument).append(" ) ) ) ; print \":\" ; ");
            expected.append(argument * argument + 5).append(':');
        }
        source.append("print ( #total ) ;");
        expected.append(80);
        String generated = new CodeGenerator().compile(
                new Parser(new Lexer().tokenize(source.toString())).parse());
        assertEquals(expected.toString(), CppTestSupport.execute(directory, generated));
    }
}
