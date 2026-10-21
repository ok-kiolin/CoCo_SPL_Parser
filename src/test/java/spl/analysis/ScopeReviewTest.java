package spl.analysis;

import org.junit.jupiter.api.Test;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import spl.types.SymbolTableLookup;
import spl.types.Type;
import spl.types.TypeAnalyser;
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

    @Test void unrelatedFunctionLocalsHaveDifferentDeclarationIdentities() 
    {
        SymbolTable table = check(": void #f ( ) { #x : : #x = 1 ; return } " + "void #g ( ) { #x : : #x = 2 ; return } : #f ( ) ; #g ( ) ;");
        SymbolTable.Symbol f = table.getGlobalScope().getFunctionDeclaredHere("#f");
        SymbolTable.Symbol g = table.getGlobalScope().getFunctionDeclaredHere("#g");
        SymbolTable.Symbol fLocal = f.getLocals().get(0);
        SymbolTable.Symbol gLocal = g.getLocals().get(0);

        assertEquals("#x", fLocal.getSourceName());
        assertEquals("#x", gLocal.getSourceName());
        assertNotSame(fLocal, gLocal);
        assertNotEquals(fLocal.getInternalName(), gLocal.getInternalName());
        assertSame(fLocal, table.getBinding(f.getBody().children.get(0).children.get(0)));
        assertSame(gLocal, table.getBinding(g.getBody().children.get(0).children.get(0)));
    }

    @Test void typeAnalyserConsumesBindingsFromNestedParserTree() 
    {
        Node tree = new Parser(new Lexer().tokenize("#x : num #f ( #x ) { : num #g ( ) { : " + "num #h ( ) { : : return ( #x ) } : return ( #h ( ) ) } " + ": return ( #g ( ) ) } : print ( #f ( 2 ) ) ;")).parse();
        SymbolTable table = SymbolTable.analyze(tree);

        assertEquals(Type.OK, new TypeAnalyser(new SymbolTableLookup(table)).analyse(tree).typeOf(tree));
    }

    @Test void diagnosticIncludesNameNodeIdAndSourcePosition() 
    {
        SemanticException error = assertThrows(SemanticException.class, () -> check(": : print ( #missing ) ;"));

        assertEquals("#missing", error.getSourceName());
        assertTrue(error.getNodeId() > 0);
        assertTrue(error.getLine() > 0);
        assertTrue(error.getColumn() > 0);
        assertTrue(error.getMessage().contains("node " + error.getNodeId()));
        assertTrue(error.getMessage().contains("source name '#missing'"));
        assertTrue(error.getMessage().contains("line " + error.getLine()));
        assertTrue(error.getMessage().contains("column " + error.getColumn()));
    }

    @Test void duplicateParametersAndUndeclaredReturnAreRejected() 
    {
        assertThrows(SemanticException.class, () -> check(": void #f ( #x #x ) { : : return } :"));
        assertThrows(SemanticException.class, () -> check(": num #f ( ) { : : return ( #missing ) } :"));
    }
}