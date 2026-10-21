package spl.codegen;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end checks using the production Task 3 emitter and Task 4 inliner. */
class AlgorithmGeneratorTest {
    @TempDir Path directory;

    private static String generate(String source) {
        String generated = new CodeGenerator().compile(new Parser(new Lexer().tokenize(source)).parse());
        assertJumpsWellFormed(generated);
        return generated;
    }

    static void assertJumpsWellFormed(String generated) {
        String exe = generated.substring(generated.indexOf("#include <iostream>")).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        Set<String> defined = new HashSet<>();

        Matcher d = Pattern.compile("(?m)^\\s*(\\w+)\\s*:\\s*;").matcher(exe);


        while (d.find()) assertTrue(defined.add(d.group(1)), "duplicate label " + d.group(1));

        Set<String> used = new HashSet<>();

        Matcher g = Pattern.compile("\\bgoto\\s+(\\w+)\\s*;").matcher(exe);

        while (g.find()) {
            used.add(g.group(1));
            assertTrue(defined.contains(g.group(1)), "goto to missing label " + g.group(1));
        }
        assertTrue(used.containsAll(defined), "defined but never jumped to: " + defined.stream().filter(l -> !used.contains(l)).toList());
    }

    private String execute(String source) throws Exception {
        return CppTestSupport.execute(directory, generate(source));
    }

    @Test void allFourLoopFormsHaveCorrectContinuationConditions() throws Exception {
        assertEquals("3:3:3:3", execute("#i : : #i = 0 ; "
                + "while lesser ( #i 3 ) do { #i = add ( #i 1 ) ; } ; print ( #i ) ; print \":\" ; "
                + "#i = 0 ; until eq ( #i 3 ) do { #i = add ( #i 1 ) ; } ; print ( #i ) ; print \":\" ; "
                + "#i = 0 ; do { #i = add ( #i 1 ) ; } while lesser ( #i 3 ) ; print ( #i ) ; print \":\" ; "
                + "#i = 0 ; do { #i = add ( #i 1 ) ; } until eq ( #i 3 ) ; print ( #i ) ;"));
    }

    @Test void preTestMayRunZeroTimesAndPostTestRunsOnce() throws Exception {
        assertEquals("0:0:1:1", execute("#i : : #i = 0 ; "
                + "while lesser ( 1 0 ) do { #i = 99 ; } ; print ( #i ) ; print \":\" ; "
                + "until eq ( 1 1 ) do { #i = 99 ; } ; print ( #i ) ; print \":\" ; "
                + "do { #i = add ( #i 1 ) ; } while eq ( 0 1 ) ; print ( #i ) ; print \":\" ; "
                + "#i = 0 ; do { #i = add ( #i 1 ) ; } until eq ( 1 1 ) ; print ( #i ) ;"));
    }

    @Test void emptyBranchesAndBodiesRemainValidTargetCode() throws Exception {
        assertEquals("tf", execute(": : if eq ( 1 1 ) then { print \"t\" ; } else { } ; "
                + "if lesser ( 2 1 ) then { } else { print \"f\" ; } ; "
                + "if eq ( 0 0 ) then { } else { } ; "
                + "while eq ( 0 1 ) do { } ; do { } until eq ( 1 1 ) ;"));
    }

    @Test void shortCircuitSuppressesAndExecutesCallsCorrectly() throws Exception {
        assertEquals("ab0cd2", execute("#count : num #tick ( ) { : : "
                + "#count = add ( #count 1 ) ; return ( 1 ) } : #count = 0 ; "
                + "if and ( eq ( 0 1 ) eq ( #tick ( ) 1 ) ) then { print \"wrong\" ; } else { print \"a\" ; } ; "
                + "if or ( eq ( 1 1 ) eq ( #tick ( ) 1 ) ) then { print \"b\" ; } else { print \"wrong\" ; } ; "
                + "print ( #count ) ; "
                + "if and ( eq ( 1 1 ) eq ( #tick ( ) 1 ) ) then { print \"c\" ; } else { print \"wrong\" ; } ; "
                + "if not ( or ( eq ( 0 1 ) not ( eq ( #tick ( ) 1 ) ) ) ) "
                + "then { print \"d\" ; } else { print \"wrong\" ; } ; print ( #count ) ;"));
    }

    @Test void conditionCallsAreReevaluatedOnEveryIteration() throws Exception {
        assertEquals("0:4", execute("#n #tests : num #check ( ) { : : "
                + "#tests = add ( #tests 1 ) ; return ( #n ) } : #n = 3 ; #tests = 0 ; "
                + "while larger ( #check ( ) 0 ) do { #n = sub ( #n 1 ) ; } ; "
                + "print ( #n ) ; print \":\" ; print ( #tests ) ;"));
    }

    @Test void nestedLoopsAndBranchesPreserveTheirOwnLabels() throws Exception {
        assertEquals("4", execute("#i #j #sum : : #i = 0 ; #sum = 0 ; "
                + "while lesser ( #i 3 ) do { #j = 0 ; "
                + "until eq ( #j 3 ) do { "
                + "if and ( not ( eq ( #i 1 ) ) not ( eq ( #j 1 ) ) ) then { "
                + "#sum = add ( #sum 1 ) ; } else { nop ; } ; "
                + "#j = add ( #j 1 ) ; } ; #i = add ( #i 1 ) ; } ; print ( #sum ) ;"));
    }

    @Test void repeatedNestedInlineCallsWithRealBranchesAndLoops() throws Exception {
        assertEquals("3:5:2", execute("#calls : num #outer ( #limit ) { #x : "
                + "void #inner ( ) { : : "
                + "while lesser ( #x #limit ) do { "
                + "if or ( eq ( #x 0 ) not ( lesser ( #x 0 ) ) ) then { "
                + "#x = add ( #x 1 ) ; } else { #x = #limit ; } ; } ; "
                + "#calls = add ( #calls 1 ) ; return } : #x = 0 ; #inner ( ) ; return ( #x ) } "
                + ": #calls = 0 ; print ( #outer ( 3 ) ) ; print \":\" ; "
                + "print ( #outer ( 5 ) ) ; print \":\" ; print ( #calls ) ;"));
    }

    @Test void argumentCallsWithBranchesCaptureValuesInOrder() throws Exception {
        assertEquals("1:2:2", execute("#x : num #next ( ) { : : "
                + "if lesser ( #x 10 ) then { #x = add ( #x 1 ) ; } else { #x = 99 ; } ; return ( #x ) } "
                + "void #show ( #a #b ) { : : print ( #a ) ; print \":\" ; print ( #b ) ; return } "
                + ": #x = 1 ; #show ( #x #next ( ) ) ; print \":\" ; print ( #x ) ;"));
    }

    @Test void nestedPostTestLoopsInsideRepeatedCalls() throws Exception {
        assertEquals("6:6", execute(": num #f ( ) { #i #j #sum : : #i = 0 ; #sum = 0 ; "
                + "do { #j = 0 ; do { #sum = add ( #sum 1 ) ; #j = add ( #j 1 ) ; } until eq ( #j 2 ) ; "
                + "#i = add ( #i 1 ) ; } while lesser ( #i 3 ) ; return ( #sum ) } "
                + ": print ( #f ( ) ) ; print \":\" ; print ( #f ( ) ) ;"));
    }

    @Test void shortCircuitSkipsPrintingSideEffectsForProvenConditions() throws Exception {
        assertEquals("abzzc", execute(": num #t ( ) { : : print \"z\" ; return ( 1 ) } : "
            + "if or ( eq ( 1 1 ) eq ( #t ( ) 1 ) ) then { print \"a\" ; } else { print \"x\" ; } ; "
            + "if and ( eq ( 1 0 ) eq ( #t ( ) 1 ) ) then { print \"x\" ; } else { print \"b\" ; } ; "
            + "if or ( eq ( #t ( ) 0 ) eq ( #t ( ) 1 ) ) then { print \"c\" ; } else { print \"x\" ; } ;"));
    }

    @Test void callsNestedInArithmeticInsideAPostTestLoopCondition() throws Exception {

        assertEquals("4", execute("#n : num #inc ( ) { : : #n = add ( #n 1 ) ; return ( #n ) } "
        + ": #n = 0 ; do { nop ; } until larger ( add ( #inc ( ) mul ( #inc ( ) 2 ) ) 10 ) ; print ( #n ) ;"));
    }

    private record Condition(String source, boolean value) { }

    private static Condition randomCondition(Random random, int depth) {
        if (depth == 0 || random.nextInt(4) == 0) {
            int left = random.nextInt(7) - 3, right = random.nextInt(7) - 3;
            String op = new String[]{"eq", "larger", "lesser"}[random.nextInt(3)];
            boolean result = switch (op) {
                case "eq" -> left == right;
                case "larger" -> left > right;
                default -> left < right;
            };
            return new Condition(op + " ( " + left + " " + right + " )", result);
        }
        Condition left = randomCondition(random, depth - 1);
        int op = random.nextInt(3);
        if (op == 0) return new Condition("not ( " + left.source() + " )", !left.value());
        Condition right = randomCondition(random, depth - 1);
        return new Condition((op == 1 ? "and" : "or") + " ( " + left.source() + " " + right.source() + " )",
                op == 1 ? left.value() && right.value() : left.value() || right.value());
    }

    @Test void eightyNestedBooleanExpressionsMatchIndependentOracle() throws Exception {
        Random random = new Random(34103);
        StringBuilder source = new StringBuilder(": : "), expected = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            Condition condition = randomCondition(random, 4);
            source.append("if ").append(condition.source())
                    .append(" then { print \"1\" ; } else { print \"0\" ; } ; ");
            expected.append(condition.value() ? '1' : '0');
        }
        assertEquals(expected.toString(), execute(source.toString()));
    }

    @Test void executableCodeUsesRequiredJumpTranslation() {
        String generated = generate("#x : : #x = 0 ; while lesser ( #x 1 ) do { "
                + "if and ( eq ( #x 0 ) not ( eq ( #x 1 ) ) ) then { #x = 1 ; } else { nop ; } ; } ;");
        String executable = generated.substring(generated.indexOf("#include"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        assertTrue(executable.contains("goto "));
        assertFalse(executable.matches("(?s).*\\belse\\b.*"));
        assertFalse(executable.matches("(?s).*\\b(while|for|do)\\b.*"));
        assertFalse(executable.contains("&&"));
        assertFalse(executable.contains("||"));
        assertFalse(executable.contains("!"));
    }

    @Test void malformedNodeUsesGenerationErrorType() {
        assertThrows(CodeGenerationException.class, () ->
                new AlgorithmGenerator().emitAlgorithm(Node.inner("P"), null));
    }
}
