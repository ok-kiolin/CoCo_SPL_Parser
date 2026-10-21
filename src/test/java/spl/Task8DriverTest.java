package spl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.codegen.AlgorithmEmitter;
import spl.codegen.CodeGenerationException;
import spl.codegen.CodeGenerator;
import spl.codegen.CodeName;
import spl.codegen.GenerationContext;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

class Task8DriverTest {
    @TempDir Path directory;
    private record Result(int code, String out, String err) { }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(out), new PrintStream(err));
        return new Result(code, out.toString(), err.toString());
    }

    private Path input(String source) throws Exception {
        Path file = directory.resolve("program with spaces.spl");
        Files.writeString(file, source);
        return file;
    }

    private void oldOutputs() throws Exception {
        Files.writeString(directory.resolve("tree.xml"), "old tree");
        Files.writeString(directory.resolve("target.cpp"), "old target");
    }

    private void noTemporaryFiles(Path folder) throws Exception {
        try (var files = Files.list(folder)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".spl-output-")));
        }
    }

    @Test void ordinaryInvocationCompilesAndReportsNumericMode() throws Exception {
        Path file = input(": num #f ( ) { : : return ( div ( 5 2 ) ) } : print ( #f ( ) ) ;");
        Result result = run(file.toString());
        assertEquals(Main.OK, result.code(), result.err());
        assertTrue(result.out().contains("Compilation PASSED"));
        assertTrue(result.out().contains("Numeric mode: float"));
        assertTrue(Files.readString(directory.resolve("target.cpp")).contains("int main()"));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        noTemporaryFiles(directory);
    }

    @Test void compileAliasAndDefaultProduceIdenticalTargets() throws Exception {
        Path file = input(": num #f ( #x ) { : : return ( mul ( #x #x ) ) } : print ( #f ( 3 ) ) ;");
        assertEquals(Main.OK, run(file.toString()).code());
        String first = Files.readString(directory.resolve("target.cpp"));
        assertEquals(Main.OK, run("--compile", file.toString()).code());
        assertEquals(first, Files.readString(directory.resolve("target.cpp")));
    }

    @Test void checkModeRunsSemanticsAndRemovesStaleTarget() throws Exception {
        Path file = input(": : nop ;");
        oldOutputs();
        Result result = run("--check", file.toString());
        assertEquals(Main.OK, result.code(), result.err());
        assertTrue(result.out().contains("Semantic check PASSED"));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
    }

    @Test void frontendOnlyRetainsOriginalSyntaxAcceptance() throws Exception {
        Path file = input(": : print ( #undeclared ) ;");
        oldOutputs();
        Result result = run("--frontend-only", file.toString());
        assertEquals(Main.OK, result.code(), result.err());
        assertTrue(result.out().contains("Syntax check PASSED"));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
    }

    @Test void scopeAndTypeErrorsNeverLeaveEarlierTarget() throws Exception {
        for (String source : List.of(": : print ( #missing ) ;",
                ": void #f ( ) { : : return } : print ( #f ( ) ) ;")) {
            Path file = input(source);
            for (String mode : List.of("--compile", "--check")) {
                oldOutputs();
                Result result = run(mode, file.toString());
                assertEquals(Main.SEMANTIC_ERROR, result.code(), result.err());
                assertFalse(result.out().contains("PASSED"));
                assertTrue(Files.exists(directory.resolve("tree.xml")));
                assertFalse(Files.exists(directory.resolve("target.cpp")));
            }
        }
    }

    @Test void syntaxFailureClearsOutputsInEveryMode() throws Exception {
        Path file = input(": : print ( ) ;");
        for (String mode : List.of("--compile", "--check", "--frontend-only")) {
            oldOutputs();
            assertEquals(Main.SYNTAX_ERROR, run(mode, file.toString()).code());
            assertFalse(Files.exists(directory.resolve("target.cpp")));
            assertFalse(Files.exists(directory.resolve("tree.xml")));
        }
    }

    @Test void missingInputInvalidatesEarlierOutputs() throws Exception {
        oldOutputs();
        Result result = run(directory.resolve("missing.spl").toString());
        assertEquals(Main.INPUT_ERROR, result.code());
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertFalse(Files.exists(directory.resolve("tree.xml")));
    }

    @Test void invalidOptionsDoNotTouchFiles() throws Exception {
        Path file = input(": :");
        oldOutputs();
        assertEquals(Main.INPUT_ERROR, run("--unknown", file.toString()).code());
        assertEquals(Main.INPUT_ERROR, run("--compile", "--check", file.toString()).code());
        assertEquals("old target", Files.readString(directory.resolve("target.cpp")));
        assertEquals(": :", Files.readString(file));
    }

    @Test void helpIsReadOnlyAndExplainsModes() throws Exception {
        oldOutputs();
        Result result = run("--help");
        assertEquals(Main.OK, result.code());
        assertTrue(result.out().contains("--frontend-only"));
        assertTrue(result.out().contains("--check"));
        assertEquals("old target", Files.readString(directory.resolve("target.cpp")));
        assertEquals(Main.INPUT_ERROR, run("--help", "extra").code());
    }

    @Test void inputCannotBeEitherReservedOutput() throws Exception {
        for (String name : List.of("tree.xml", "target.cpp")) {
            Path file = directory.resolve(name);
            Files.writeString(file, ": :");
            assertEquals(Main.INPUT_ERROR, run(file.toString()).code());
            assertEquals(": :", Files.readString(file));
        }
    }

    @Test void directoryIsNeverRemovedOrAcceptedAsInput() throws Exception {
        Path file = input(": :");
        Path target = Files.createDirectory(directory.resolve("target.cpp"));
        Files.writeString(target.resolve("keep.txt"), "keep");
        assertEquals(Main.INPUT_ERROR, run(file.toString()).code());
        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        assertEquals(Main.INPUT_ERROR, run(directory.toString()).code());
    }

    @Test void customXmlPathDoesNotRelocateTarget() throws Exception {
        Path file = input(": :");
        Path other = Files.createDirectory(directory.resolve("other"));
        Path xml = other.resolve("custom.xml");
        assertEquals(Main.OK, run(file.toString(), xml.toString()).code());
        assertTrue(Files.exists(xml));
        assertTrue(Files.exists(directory.resolve("target.cpp")));
        assertFalse(Files.exists(other.resolve("target.cpp")));
        noTemporaryFiles(directory);
        noTemporaryFiles(other);
    }

    @Test void unwritableXmlDestinationReportsInputErrorWithoutTarget() throws Exception {
        Path file = input(": :");
        oldOutputs();
        Result result = run(file.toString(), directory.resolve("missing/custom.xml").toString());
        assertEquals(Main.INPUT_ERROR, result.code());
        assertTrue(result.err().contains("custom.xml"));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        noTemporaryFiles(directory);
    }

    @Test void hardLinkedXmlOutputCannotOverwriteSource() throws Exception {
        Path file = input(": : nop ;");
        Path xml = directory.resolve("alias.xml");
        Files.createLink(xml, file);
        assertEquals(Main.INPUT_ERROR, run(file.toString(), xml.toString()).code());
        assertEquals(": : nop ;", Files.readString(file));
        assertEquals(": : nop ;", Files.readString(xml));
    }

    @Test void hardLinkedTargetCannotOverwriteSource() throws Exception {
        Path file = input(": : nop ;");
        Files.createLink(directory.resolve("target.cpp"), file);
        assertEquals(Main.INPUT_ERROR, run(file.toString()).code());
        assertEquals(": : nop ;", Files.readString(file));
    }

    private Result injectedFailure(Path file, RuntimeException failure) {
        AlgorithmEmitter failing = new AlgorithmEmitter() {
            @Override public void emitAlgorithm(Node algo, GenerationContext context) { throw failure; }
            @Override public void emitTerm(Node term, CodeName result, GenerationContext context) { throw failure; }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        int code = Main.run(new String[]{file.toString()}, new PrintStream(out), new PrintStream(err),
                new CodeGenerator(failing));
        return new Result(code, out.toString(), err.toString());
    }

    @Test void generationFailureHasDistinctExitCodeAndNoPartialTarget() throws Exception {
        Path file = input(": :");
        oldOutputs();
        Result result = injectedFailure(file, new CodeGenerationException("injected generator failure"));
        assertEquals(Main.GENERATION_ERROR, result.code());
        assertTrue(result.err().contains("injected generator failure"));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertTrue(Files.exists(directory.resolve("tree.xml")));
        noTemporaryFiles(directory);
    }

    @Test void unexpectedFailureIsNotReportedAsSourceError() throws Exception {
        Path file = input(": :");
        Result result = injectedFailure(file, new IllegalStateException("injected internal failure"));
        assertEquals(Main.INTERNAL_ERROR, result.code());
        assertTrue(result.err().contains("Internal error"));
        assertFalse(Files.exists(directory.resolve("target.cpp")));
    }
}
