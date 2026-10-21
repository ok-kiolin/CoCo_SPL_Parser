package spl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Task6PipelineTest {
    @TempDir Path directory;

    private Result run(String source, boolean compile) throws Exception {
        Path input = directory.resolve("task6.spl");
        Files.writeString(input, source);
        Files.deleteIfExists(directory.resolve("target.cpp"));
        Files.deleteIfExists(directory.resolve("tree.xml"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] args = compile
                ? new String[]{"--compile", input.toString()}
                : new String[]{"--check", input.toString()};
        int code = Main.run(args, new PrintStream(out), new PrintStream(err));
        return new Result(code, out.toString(), err.toString());
    }

    @Test void acceptsValidCompleteProgram() throws Exception {
        Result result = run(": num #f ( #a #b ) { : : return ( add ( #a #b ) ) } "
                + "num #g ( ) { : : return ( 3 ) } : print ( #f ( #g ( ) 2 ) ) ;", false);
        assertEquals(Main.OK, result.code, result.err);
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
    }

    @Test void checksBodiesOfUncalledFunctions() throws Exception {
        Result result = run(": void #outer ( ) { : num #inner ( ) { : : return ( 1 ) } "
                + ": #inner ( ) ; return } :", false);
        assertEquals(Main.SEMANTIC_ERROR, result.code);
        assertTrue(result.err.contains("returns a number"), result.err);
    }

    @Test void enforcesProcedureAndNumericCallContexts() throws Exception {
        Result procedureAsValue = run(
                ": void #p ( ) { : : return } : print ( #p ( ) ) ;", false);
        assertEquals(Main.SEMANTIC_ERROR, procedureAsValue.code);
        assertTrue(procedureAsValue.err.contains("void procedure"), procedureAsValue.err);

        Result numericAsInstruction = run(
                ": num #f ( ) { : : return ( 1 ) } : #f ( ) ;", false);
        assertEquals(Main.SEMANTIC_ERROR, numericAsInstruction.code);
        assertTrue(numericAsInstruction.err.contains("returns a number"), numericAsInstruction.err);
    }

    @Test void numericModeIsCheckedAcrossTheWholeProgram() throws Exception {
        Result result = run(
                ": num #unused ( ) { : : return ( mod ( 4 2 ) ) } : print ( 1.5 ) ;", false);
        assertEquals(Main.SEMANTIC_ERROR, result.code);
        assertTrue(result.err.contains("Decimal number"), result.err);
        assertTrue(result.err.contains("mod"), result.err);
    }

    @Test void failedTypeCheckPreventsCodeGeneration() throws Exception {
        Result result = run(
                ": void #f ( #a #b ) { : : return } : #f ( 1 ) ;", true);
        assertEquals(Main.SEMANTIC_ERROR, result.code);
        assertTrue(result.err.contains("#f"), result.err);
        assertTrue(result.err.contains("expects 2"), result.err);
        assertTrue(result.err.contains("received 1"), result.err);
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
    }

    private record Result(int code, String out, String err) {}
}
