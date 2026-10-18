package spl.types;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spl.analysis.SymbolTable;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

class TypeReviewTest {
    private static Node parse(String source) {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    private static TypeResult check(String source) {
        Node root = parse(source);
        SymbolTable table = SymbolTable.analyze(root);
        return new TypeAnalyser(new SymbolTableLookup(table)).analyse(root);
    }

    @Test void rejectsTooFewArguments() {
        String message = assertThrows(TypeException.class, () -> check(
                ": void #f ( #a #b ) { : : return } : #f ( 1 ) ;")).getMessage();
        assertTrue(message.contains("#f"));
        assertTrue(message.contains("expects 2"));
        assertTrue(message.contains("received 1"));
    }

    @Test void rejectsTooManyAndMissingArguments() {
        assertThrows(TypeException.class, () -> check(
                ": void #f ( ) { : : return } : #f ( 1 ) ;"));
        assertThrows(TypeException.class, () -> check(
                ": void #f ( #a ) { : : return } : #f ( ) ;"));
    }

    @Test void checksArityInUncalledFunctionAndReturnTerm() {
        assertThrows(TypeException.class, () -> check(
                ": num #f ( ) { : num #g ( #a ) { : : return ( #a ) } : return ( #g ( ) ) } :"));
    }

    @Test void validNestedArgumentsAndZeroParameters() {
        check(": num #f ( #a #b ) { : : return ( add ( #a #b ) ) } "
                + "num #g ( ) { : : return ( 3 ) } : print ( #f ( #g ( ) 2 ) ) ;");
    }

    @Test void resultIsAnImmutableSnapshotAcrossAnalyserReuse() {
        TypeAnalyser analyser = new TypeAnalyser(name -> null);
        TypeResult first = analyser.analyse(parse(": : print ( 0.5 ) ;"));
        Map<Integer, Type> snapshot = Map.copyOf(first.types());
        analyser.analyse(parse(": :"));
        assertEquals(snapshot, first.types());
        assertEquals(NumericMode.FLOAT, first.mode());
        assertThrows(UnsupportedOperationException.class, () -> first.types().clear());
    }
}
