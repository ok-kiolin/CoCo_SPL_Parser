package spl.codegen;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.analysis.SemanticException;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import spl.types.TypeException;
import static org.junit.jupiter.api.Assertions.*;

class CodeGeneratorTest {
    @TempDir Path directory;

    private static Node parse(String source) {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    private String execute(String source) throws Exception {
        return execute(source, new CodeGenerator());
    }

    private String execute(String source, CodeGenerator generator) throws Exception {
        return CppTestSupport.execute(directory, generator.compile(parse(source)));
    }

    @Test void emptyProgramCompiles() throws Exception {
        assertEquals("", execute(": :"));
    }

    @Test void numericFunctionAndRepeatedCalls() throws Exception {
        assertEquals("1636", execute("#x : num #square ( #n ) { #local : : "
                + "#local = mul ( #n #n ) ; return ( #local ) } : "
                + "#x = #square ( 4 ) ; print ( #x ) ; print ( #square ( 6 ) ) ;"));
    }

    @Test void procedureParametersArePassedByValue() throws Exception {
        assertEquals("83", execute("#x : void #f ( #p ) { : : #p = 8 ; print ( #p ) ; return } "
                + ": #x = 3 ; #f ( #x ) ; print ( #x ) ;"));
    }

    @Test void argumentOrderAndCaptureBeforeBinding() throws Exception {
        assertEquals("12:1:2:2", execute("#x : num #next ( ) { : : "
                + "#x = add ( #x 1 ) ; print ( #x ) ; return ( #x ) } "
                + "num #pair ( #a #b ) { : : print \":\" ; print ( #a ) ; print \":\" ; "
                + "print ( #b ) ; print \":\" ; return ( #b ) } : #x = 0 ; "
                + "print ( #pair ( #next ( ) #next ( ) ) ) ;"));
    }

    @Test void capturesEachArgumentsValueBeforeLaterSideEffects() throws Exception {
        assertEquals("1:2:2", execute("#x : num #next ( ) { : : #x = add ( #x 1 ) ; return ( #x ) } "
                + "void #show ( #a #b ) { : : print ( #a ) ; print \":\" ; print ( #b ) ; return } "
                + ": #x = 1 ; #show ( #x #next ( ) ) ; print \":\" ; print ( #x ) ;"));
    }

    @Test void nestedFunctionCapturesCurrentOuterExpansion() throws Exception {
        assertEquals("21314", execute("#total : num #outer ( #p ) { #x : "
                + "num #inner ( #n ) { : : #x = add ( #x #n ) ; "
                + "#total = add ( #total 1 ) ; return ( #x ) } : "
                + "#x = #p ; #x = #inner ( 2 ) ; return ( #inner ( 3 ) ) } "
                + ": #total = 0 ; print ( #outer ( 16 ) ) ; print ( #outer ( 26 ) ) ; print ( #total ) ;"));
    }

    @Test void ancestorUpdatesAndLocalShadowingRemainDistinct() throws Exception {
        assertEquals("71", execute("#x : void #outer ( #x ) { : void #inner ( ) { : : "
                + "#x = 7 ; return } : #inner ( ) ; print ( #x ) ; return } "
                + ": #x = 1 ; #outer ( #x ) ; print ( #x ) ;"));
    }

    @Test void sameSpellingVariableAndFunctionWork() throws Exception {
        assertEquals("5", execute("#same : num #same ( ) { : : return ( add ( #same 1 ) ) } "
                + ": #same = 4 ; print ( #same ( ) ) ;"));
    }

    @Test void floatDivisionAndIntegerModulo() throws Exception {
        assertEquals("2.5", execute(": num #f ( #a #b ) { : : return ( div ( #a #b ) ) } "
                + ": print ( #f ( 5 2 ) ) ;"));
        assertEquals("1", execute(": num #f ( #a #b ) { : : return ( mod ( #a #b ) ) } "
                + ": print ( #f ( 7 3 ) ) ;"));
    }

    @Test void stringsInMineCannotBeBrokenByIdentifierRenaming() throws Exception {
        assertEquals("spl123!spl123!", execute(": void #f ( ) { : : comment \"note\" ; "
                + "print \"spl123!\" ; nop ; return } : #f ( ) ; #f ( ) ;"));
    }

    @Test void uncalledTemplatesCompileAndDoNotExecute() throws Exception {
        assertEquals("ok", execute(": void #unused ( ) { : : print \"wrong\" ; return } "
                + ": print \"ok\" ;"));
    }

    @Test void optionalStraightLineAdapterExplicitlyRejectsControlFlow() {
        CodeGenerationException error = assertThrows(CodeGenerationException.class, () ->
                new CodeGenerator(new StraightLineEmitter()).compile(parse(": : if eq ( 1 1 ) then { } else { } ;")));
        assertTrue(error.getMessage().contains("Task 3"));
    }

    @Test void rejectsSemanticErrorsBeforeGeneration() {
        assertThrows(TypeException.class, () -> new CodeGenerator().compile(parse(
                ": void #f ( #a ) { : : return } : #f ( ) ;")));
        assertThrows(SemanticException.class, () -> new CodeGenerator().compile(parse(
                ": : print ( #missing ) ;")));
        assertThrows(TypeException.class, () -> new CodeGenerator().compile(parse(
                ": void #f ( ) { : : return } : print ( #f ( ) ) ;")));
    }

    @Test void generatorReuseIsDeterministic() {
        CodeGenerator generator = new CodeGenerator();
        Node root = parse(": num #f ( ) { : : return ( 2 ) } : print ( #f ( ) ) ;");
        String first = generator.compile(root);
        generator.compile(parse(": :"));
        assertEquals(first, generator.compile(root));
    }

    @Test void structuralRenamingLeavesTextAndOtherNamesUntouched() {
        CodeName one = new CodeName("v1", CodeName.Kind.VARIABLE);
        CodeName ten = new CodeName("v10", CodeName.Kind.VARIABLE);
        CodeName replacement = new CodeName("fresh", CodeName.Kind.VARIABLE);
        CodeBlock block = new CodeBlock(java.util.List.of(java.util.List.of(one, " = ", ten,
                "; std::cout << \"v1\";")));
        assertEquals("fresh = v10; std::cout << \"v1\";\n", block.renamed(Map.of(one, replacement)).render(""));
        assertTrue(block.render("").startsWith("v1 ="));
    }

    @Test void duplicatedTemplatesWithLabelsAndAncestorCapturesRunCorrectly() throws Exception {
        // This fixture exercises the Task 3 interface without owning its production implementation.
        AlgorithmEmitter fixture = new AlgorithmEmitter() {
            private final StraightLineEmitter straight = new StraightLineEmitter();
            @Override public void emitTerm(Node term, CodeName result, GenerationContext context) {
                straight.emitTerm(term, result, context);
            }
            @Override public void emitAlgorithm(Node algo, GenerationContext context) {
                for (Node current = algo; !current.children.isEmpty(); current = current.children.get(2)) {
                    Node instruction = current.children.get(0), first = instruction.children.get(0);
                    if (!"LOOP".equals(first.contents)) {
                        straight.emitInstruction(instruction, context);
                        continue;
                    }
                    CodeName start = context.label(), body = context.label(), exit = context.label();
                    context.mark(start);
                    Node condition = first.children.get(1);
                    CodeName left = context.temporary(), right = context.temporary();
                    emitTerm(condition.children.get(2), left, context);
                    emitTerm(condition.children.get(3), right, context);
                    context.emit("if (", left, " < ", right, ") goto ", body, ";");
                    context.jump(exit);
                    context.mark(body);
                    emitAlgorithm(first.children.get(4), context);
                    context.jump(start);
                    context.mark(exit);
                }
            }
        };
        assertEquals("352", execute("#total : num #f ( #limit ) { #x : "
                + "void #g ( ) { : : while lesser ( #x #limit ) do { #x = add ( #x 1 ) ; } ; "
                + "#total = add ( #total 1 ) ; return } : #x = 0 ; #g ( ) ; return ( #x ) } "
                + ": #total = 0 ; print ( #f ( 3 ) ) ; print ( #f ( 5 ) ) ; print ( #total ) ;",
                new CodeGenerator(fixture)));
    }
}
