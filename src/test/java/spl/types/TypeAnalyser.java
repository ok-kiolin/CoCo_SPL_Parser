package spl.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import spl.analysis.SemanticException;
import spl.analysis.SymbolTable;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;

class TypeAnalyserTest {

    private static Node parse(String src) {
        return new Parser(new Lexer().tokenize(src)).parse();
    }

    /** Full pipeline: Task 1 symbol table, then type analysis. */
    private static TypeResult analyse(String src) {
        Node tree = parse(src);
        SymbolTable table = SymbolTable.analyze(tree);
        return new TypeAnalyser(new SymbolTableLookup(table)).analyse(tree);
    }

    /** Skips Task 1's scope checks, so the analyser's own defences can be tested. */
    private static TypeResult analyseWithoutScopeChecks(String src) {
        Node tree = parse(src);
        return new TypeAnalyser(new FlatSymbols(tree)).analyse(tree);
    }

    private static String errorOf(String src) {
        return assertThrows(TypeException.class, () -> analyse(src)).getMessage();
    }

    //valid programs

    @Test void minimalProgramIsOk() {
        assertEquals(NumericMode.INT, analyse("#x : : print ( #x ) ;").mode());
    }

    @Test void emptyProgramIsOk() { analyse(": : "); }

    @Test void voidProcedureCallAsInstruction() {
        analyse("#a : void #show ( #p ) { : : print ( #p ) ; return } : #show ( #a ) ;");
    }

    @Test void numFunctionCallAsTerm() {
        analyse("#r : num #sq ( #n ) { : : return ( mul ( #n #n ) ) } : #r = #sq ( 4 ) ; print ( #r ) ;");
    }

    @Test void controlFlowIsOk() {
        analyse("#i : : #i = 0 ;"
              + " if and ( lesser ( #i 10 ) not ( eq ( #i 3 ) ) ) then { nop ; } else { print \"x\" ; } ;"
              + " while lesser ( #i 10 ) do { #i = add ( #i 1 ) ; } ;"
              + " do { #i = sub ( #i 1 ) ; } until eq ( #i 0 ) ;");
    }

    @Test void shadowingAndAncestorAccessAreOk() {
        analyse("#x : void #f ( ) { #x : : #x = 2 ; return } : print ( #x ) ;");
        analyse("#x : void #f ( ) { : void #g ( ) { : : #x = 1 ; return } : #g ( ) ; return } : #f ( ) ;");
    }


    @Test void modGivesInt()       { assertEquals(NumericMode.INT,   analyse("#x : : #x = mod ( 7 2 ) ;").mode()); }
    @Test void divGivesFloat()     { assertEquals(NumericMode.FLOAT, analyse("#x : : #x = div ( 7 2 ) ;").mode()); }
    @Test void decimalGivesFloat() { assertEquals(NumericMode.FLOAT, analyse("#x : : #x = 0.5 ;").mode()); }
    @Test void plainGivesInt()     { assertEquals(NumericMode.INT,   analyse("#x : : #x = 5 ;").mode()); }

    @Test void modAndDivConflict() {
        assertTrue(errorOf("#x : : #x = mod ( 7 2 ) ; #x = div ( 6 3 ) ;")
                .contains("A float-integer-conflict might perhaps be possible"));
    }

    @Test void modAndDivConflictEvenInUncalledFunction() {
        assertTrue(errorOf("#x : num #f ( ) { : : return ( div ( 1 2 ) ) } : #x = mod ( 7 2 ) ;")
                .contains("float-integer-conflict"));
    }

    @Test void decimalWithModRejected() {
        assertTrue(errorOf("#x : : #x = mod ( 7 2 ) ; #x = 1.5 ;").contains("1.5"));
    }

    @Test void decimalWithDivAllowed() { analyse("#x : : #x = div ( 1.5 2 ) ;"); }

    //type errors

    @Test void procedureCallInsideExpression() {
        assertThrows(TypeException.class, () ->
            analyse("#x : void #p ( ) { : : nop ; return } : #x = add ( #p ( ) 1 ) ;"));
    }

    @Test void numCallAsInstruction() {
        assertThrows(TypeException.class, () ->
            analyse(": num #f ( ) { : : return ( 1 ) } : #f ( ) ;"));
    }

    @Test void typeErrorInsideUncalledFunction() {
        assertThrows(TypeException.class, () ->
            analyse(": void #p ( ) { : num #g ( ) { : : return ( 1 ) } : #g ( ) ; return } : nop ;"));
    }

    @Test void typeErrorInsideReturnExpression() {
        assertThrows(TypeException.class, () ->
            analyse(": num #f ( ) { : void #p ( ) { : : nop ; return } : return ( add ( #p ( ) 1 ) ) } : nop ;"));
    }

    @Test void errorMessageHasLineAndColumn() {
        String msg = errorOf("#x : void #p ( ) { : : nop ; return } : #x = #p ( ) ;");
        assertTrue(msg.startsWith("Type Error [line "), msg);
    }

    //Task 1 will catch these first. stuff like scope errors

    @Test void siblingCallIsAScopeErrorNotATypeError() {
        assertThrows(SemanticException.class, () ->
            analyse(": void #p ( ) { : : nop ; return } num #f ( ) { : : return ( #p ( ) ) } : nop ;"));
    }

    @Test void functionNameUsedAsVariableIsAScopeError() {
        assertThrows(SemanticException.class, () ->
            analyse("#x : num #f ( ) { : : return ( 1 ) } : #x = #f ;"));
    }

    //the analyser's own defences if scope checks were skipped

    @Test void functionNameUsedAsVariable() {
        assertThrows(TypeException.class, () ->
            analyseWithoutScopeChecks("#x : num #f ( ) { : : return ( 1 ) } : #x = #f ;"));
    }

    @Test void assignToFunctionName() {
        assertThrows(TypeException.class, () ->
            analyseWithoutScopeChecks(": num #f ( ) { : : return ( 1 ) } : #f = 3 ;"));
    }

    @Test void variableCalledLikeFunction() {
        assertThrows(TypeException.class, () -> analyseWithoutScopeChecks("#x : : #x ( ) ;"));
    }
}