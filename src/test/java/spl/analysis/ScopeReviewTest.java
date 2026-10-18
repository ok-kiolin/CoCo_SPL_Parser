package spl.analysis;

import org.junit.jupiter.api.Test;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

class ScopeReviewTest {
    private static SymbolTable check(String source) {
        return SymbolTable.analyze(new Parser(new Lexer().tokenize(source)).parse());
    }

    @Test void deeplyNestedReturnSeesNearestAncestorParameter() {
        SymbolTable table = check("#x : num #f ( #x ) { : num #g ( ) { : "
                + "num #h ( ) { : : return ( #x ) } : return ( #h ( ) ) } "
                + ": return ( #g ( ) ) } : print ( #f ( 2 ) ) ;");
        SymbolTable.Symbol f = table.getGlobalScope().getFunctions().get(0);
        SymbolTable.Symbol h = f.getFunctionScope().getFunctions().get(0)
                .getFunctionScope().getFunctions().get(0);
        assertSame(f.getParameters().get(0), table.requireBinding(h.getReturnTerm().children.get(0)));
    }

    @Test void selfCallAndSiblingCallAreForbidden() {
        assertThrows(SemanticException.class, () -> check(
                ": void #f ( ) { : : #f ( ) ; return } :"));
        assertThrows(SemanticException.class, () -> check(
                ": void #f ( ) { : : #g ( ) ; return } void #g ( ) { : : return } :"));
    }

    @Test void functionAndVariableWithSameSpellingBindByRole() {
        SymbolTable table = check("#x : num #x ( ) { : : return ( #x ) } "
                + ": #x = 3 ; print ( #x ( ) ) ;");
        SymbolTable.Symbol function = table.getGlobalScope().getFunctions().get(0);
        assertSame(table.getGlobalScope().getDeclaredVariable("#x"),
                table.requireBinding(function.getReturnTerm().children.get(0)));
        assertNotSame(table.getGlobalScope().getDeclaredVariable("#x"), function);
    }

    @Test void duplicateParametersAndUndeclaredReturnAreRejected() {
        assertThrows(SemanticException.class, () -> check(
                ": void #f ( #x #x ) { : : return } :"));
        assertThrows(SemanticException.class, () -> check(
                ": num #f ( ) { : : return ( #missing ) } :"));
    }
}
