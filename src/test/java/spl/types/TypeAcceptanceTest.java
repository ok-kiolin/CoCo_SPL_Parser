package spl.types;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import spl.Main;
import spl.analysis.SymbolTable;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

class TypeAcceptanceTest {
    @TempDir Path directory;

    private record Case(String file, boolean accepted, String mode, String diagnostic, String rule) {
        @Override public String toString() { return file + " — " + rule; }
    }

    private static Path resources() throws Exception {
        var manifest = TypeAcceptanceTest.class.getResource("/type-acceptance/cases.tsv");
        assertNotNull(manifest, "Type acceptance resources were not copied to the test classpath");
        return Path.of(manifest.toURI()).getParent();
    }

    private static List<Case> loadCases() throws Exception {
        List<Case> result = new ArrayList<>();
        for (String line : Files.readAllLines(resources().resolve("cases.tsv"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\\t", -1);
            assertEquals(5, fields.length, "Bad manifest row: " + line);
            assertTrue(fields[1].equals("ACCEPT") || fields[1].equals("REJECT"), line);
            if (fields[1].equals("ACCEPT")) {
                assertTrue(fields[2].equals("INT") || fields[2].equals("FLOAT"), line);
                assertEquals("", fields[3], line);
            } else {
                assertEquals("-", fields[2], line);
                assertFalse(fields[3].isBlank(), line);
            }
            result.add(new Case(fields[0], fields[1].equals("ACCEPT"), fields[2], fields[3], fields[4]));
        }
        return result;
    }

    static Stream<Case> cases() throws Exception { return loadCases().stream(); }

    private static Node parse(String source) {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void expectedTypeOutcomeThroughCompleteCompiler(Case test) throws Exception {
        Path fixture = resources().resolve(test.file()).normalize();
        assertTrue(fixture.startsWith(resources()));
        String source = Files.readString(fixture, StandardCharsets.US_ASCII);

        Node tree = assertDoesNotThrow(() -> parse(source), "Invalid syntax in fixture " + test.file());
        SymbolTable symbols = assertDoesNotThrow(() -> SymbolTable.analyze(tree),
                "Fixture must pass scope analysis before its type expectation: " + test.rule());
        TypeAnalyser analyser = new TypeAnalyser(new SymbolTableLookup(symbols));
        if (test.accepted()) {
            TypeResult result = assertDoesNotThrow(() -> analyser.analyse(tree), test.rule());
            assertEquals(NumericMode.valueOf(test.mode()), result.mode(), test.rule());
        } else {
            TypeException error = assertThrows(TypeException.class, () -> analyser.analyse(tree), test.rule());
            assertTrue(error.getMessage().startsWith("Type Error"), error.getMessage());
            assertTrue(error.getMessage().contains(test.diagnostic()), error.getMessage());
        }

        Path input = directory.resolve("type acceptance.spl");
        Files.writeString(input, source, StandardCharsets.US_ASCII);
        Path target = directory.resolve("target.cpp");
        Path xml = directory.resolve("tree.xml");
        Files.writeString(target, "stale target from a previous program");
        Files.writeString(xml, "stale tree from a previous program");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(new String[]{input.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        String output = out.toString(StandardCharsets.UTF_8);
        String errors = err.toString(StandardCharsets.UTF_8);

        assertEquals(source, Files.readString(input), "Compiler must not change source input");
        assertTrue(Files.readString(xml).contains("SPL_PROG"),
                "Syntactically valid input must retain its XML tree");
        if (test.accepted()) {
            assertEquals(Main.OK, code, errors);
            assertEquals("", errors);
            assertTrue(output.contains("Compilation PASSED"), output);
            assertTrue(Files.readString(target).contains("int main()"));
        } else {
            assertEquals(Main.SEMANTIC_ERROR, code, errors);
            assertTrue(errors.contains("Type Error"), errors);
            assertTrue(errors.contains(test.diagnostic()), errors);
            assertFalse(errors.contains("Scope Error"), errors);
            assertFalse(output.contains("PASSED"), output);
            assertFalse(Files.exists(target), "Rejected input must remove stale generated code");
        }
    }

    @Test void fixtureManifestIsCompleteAndUnambiguous() throws Exception {
        Set<String> listed = new HashSet<>();
        List<Case> cases = loadCases();
        for (Case test : cases) {
            assertTrue(listed.add(test.file()), "Duplicate fixture: " + test.file());
            assertTrue(Files.isRegularFile(resources().resolve(test.file())));
            assertTrue(test.accepted() ? test.file().startsWith("valid/") : test.file().startsWith("invalid/"));
        }
        Set<String> actual = new HashSet<>();
        Path root = resources();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".spl")).forEach(path ->
                    actual.add(root.relativize(path).toString().replace('\\', '/')));
        }
        assertEquals(listed, actual, "Every fixture needs exactly one expected outcome");
        assertTrue(cases.stream().anyMatch(Case::accepted));
        assertTrue(cases.stream().anyMatch(test -> !test.accepted()));
    }
}
