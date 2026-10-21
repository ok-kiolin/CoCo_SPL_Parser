package spl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Task4DriverTest {
    @TempDir Path directory;

    private int run(String source, ByteArrayOutputStream errors) throws Exception {
        Path input = directory.resolve("program with spaces.spl");
        Files.writeString(input, source);
        return Main.run(new String[]{"--compile", input.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
    }

    @Test void compileModeWritesBothOutputs() throws Exception {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(Main.OK, run(": num #f ( ) { : : return ( 2 ) } : print ( #f ( ) ) ;", errors), errors.toString());
        assertTrue(Files.readString(directory.resolve("target.cpp")).contains("int main()"));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
    }

    @Test void semanticFailureRemovesOldTargetAndPreservesSyntaxTree() throws Exception {
        Files.writeString(directory.resolve("target.cpp"), "old target");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(Main.SEMANTIC_ERROR, run(": : print ( #missing ) ;", errors));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        assertTrue(errors.toString().contains("Undeclared variable"));
    }

    @Test void arityFailureHasSemanticExitCode() throws Exception {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(Main.SEMANTIC_ERROR, run(": void #f ( #a ) { : : return } : #f ( ) ;", errors));
        assertTrue(errors.toString().contains("expects 1"));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
    }

    @Test void syntaxFailureRemovesBothOldOutputs() throws Exception {
        Files.writeString(directory.resolve("target.cpp"), "old target");
        Files.writeString(directory.resolve("tree.xml"), "old tree");
        assertEquals(Main.SYNTAX_ERROR, run(": : print ( ) ;", new ByteArrayOutputStream()));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertFalse(Files.exists(directory.resolve("tree.xml")));
    }

    @Test void missingInputRemovesOldTarget() throws Exception {
        Files.writeString(directory.resolve("target.cpp"), "old target");
        assertEquals(Main.INPUT_ERROR, Main.run(new String[]{"--compile", directory.resolve("missing.spl").toString()},
                System.out, new PrintStream(new ByteArrayOutputStream())));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
    }

    @Test void integratedTask3GeneratesControlFlow() throws Exception {
        Files.writeString(directory.resolve("target.cpp"), "old target");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(Main.OK, run(": : if eq ( 1 1 ) then { } else { } ;", errors), errors.toString());
        assertTrue(Files.readString(directory.resolve("target.cpp")).contains("goto"));
    }

    @Test void defaultInvocationRunsFullSemanticPipeline() throws Exception {
        Path input = directory.resolve("input.spl");
        Files.writeString(input, ": : print ( #undeclared ) ;");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = Main.run(new String[]{input.toString()}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertEquals(Main.SEMANTIC_ERROR, result);
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        assertTrue(errors.toString().contains("Undeclared variable"));
    }

    @Test void cannotOverwriteSourceWithTarget() throws Exception {
        Path input = directory.resolve("target.cpp");
        Files.writeString(input, ": :");
        assertEquals(Main.INPUT_ERROR, Main.run(new String[]{"--compile", input.toString()},
                System.out, new PrintStream(new ByteArrayOutputStream())));
        assertEquals(": :", Files.readString(input));
    }
}
