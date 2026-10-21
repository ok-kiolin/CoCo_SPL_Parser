package spl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.errors.SyntaxException;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import spl.xml.XMLReader;
import spl.xml.XMLWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Path TASK3 = Path.of("src/test/resources/valid/task3-statements");
    private static final Path EXAMPLES_VALID = Path.of("examples/valid");
    private static final Path EXAMPLES_INVALID = Path.of("examples/invalid");

    private static String toXml(String source) throws IOException {
        Node tree = new Parser(new Lexer().tokenize(source)).parse();
        StringWriter w = new StringWriter();
        XMLWriter.write(tree, w);
        return w.toString();
    }

    private static String lf(String s) { return s.replace("\r\n", "\n"); }

    private static List<Path> files(Path dir, String ext) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(ext)).sorted().toList();
        }
    }

    @Test void goldenProgramsProduceExactlyTheExpectedTree() throws IOException {
        List<Path> programs = files(GOLDEN, ".spl");
        assertEquals(6, programs.size(), "expected the 6 golden programs in " + GOLDEN);
        for (Path spl : programs) {
            Path xml = GOLDEN.resolve(spl.getFileName().toString().replace(".spl", ".xml"));
            assertEquals(lf(Files.readString(xml)), lf(toXml(Files.readString(spl))),
                    spl.getFileName() + " must produce exactly " + xml.getFileName());
        }
    }

    @Test void writtenTreeReadsBackIdentically() throws IOException {
        for (Path spl : files(GOLDEN, ".spl")) {
            String written = toXml(Files.readString(spl));
            Node reread = XMLReader.read(new java.io.ByteArrayInputStream(written.getBytes(StandardCharsets.UTF_8)));
            StringWriter again = new StringWriter();
            XMLWriter.write(reread, again);
            assertEquals(written, again.toString(), spl.getFileName() + " must round-trip");
        }
    }

    @Test void everyValidProgramParses() throws IOException {
        List<Path> all = new java.util.ArrayList<>();
        all.addAll(files(EXAMPLES_VALID, ".spl"));
        all.addAll(files(TASK3, ".spl"));
        assertFalse(all.isEmpty(), "no valid example programs found");
        for (Path p : all) {
            assertDoesNotThrow(() -> toXml(Files.readString(p)), p + " should be accepted");
        }
    }

    @Test void everyInvalidProgramIsRejected() throws IOException {
        List<Path> all = files(EXAMPLES_INVALID, ".spl");
        assertFalse(all.isEmpty(), "no invalid example programs found in " + EXAMPLES_INVALID);
        for (Path p : all) {
            assertThrows(SyntaxException.class, () -> toXml(Files.readString(p)), p + " should be rejected");
        }
    }

    private record Result(int code, String out, String err) {}

    private static Result runMain(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        // These are P1 syntax/XML regressions; their fixtures need not satisfy P2 semantics.
        String[] frontendArgs = new String[args.length + 1];
        frontendArgs[0] = "--frontend-only";
        System.arraycopy(args, 0, frontendArgs, 1, args.length);
        int code = Main.run(frontendArgs, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test void validInputWritesTreeXmlNextToIt(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        Files.copy(GOLDEN.resolve("v5_control_flow.spl"), spl);
        Result r = runMain(spl.toString());
        assertEquals(Main.OK, r.code(), r.err());
        Path tree = dir.resolve("tree.xml");
        assertTrue(Files.exists(tree));
        assertEquals(lf(Files.readString(GOLDEN.resolve("v5_control_flow.xml"))), lf(Files.readString(tree)));
        assertTrue(r.out().contains("PASSED"));
    }

    @Test void invalidInputPrintsErrorWithHintAndLeavesNoTreeXml(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        Path tree = dir.resolve("tree.xml");
        Files.writeString(tree, "stale output from an earlier run");
        Files.writeString(spl, "#a : :\n#a = 5\nprint ( #a ) ;\n");
        Result r = runMain(spl.toString());
        assertEquals(Main.SYNTAX_ERROR, r.code());
        assertFalse(Files.exists(tree), "a failed run must not leave any tree.xml behind");
        assertTrue(r.err().contains("line 3"), r.err());
        assertTrue(r.err().toLowerCase().contains("hint"), r.err());
    }

    @Test void explicitOutputPathIsHonoured(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("in.txt"), xml = dir.resolve("out.xml");
        Files.writeString(spl, ": : nop ;\n");
        assertEquals(Main.OK, runMain(spl.toString(), xml.toString()).code());
        assertTrue(Files.exists(xml));
    }

    @Test void missingInputFileIsReportedNotCrashed(@TempDir Path dir) {
        Result r = runMain(dir.resolve("SPL.txt").toString());
        assertEquals(Main.INPUT_ERROR, r.code());
        assertTrue(r.err().contains("not found"), r.err());
    }

    @Test void windowsLineEndingsAreAccepted(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        String src = Files.readString(GOLDEN.resolve("v4_functions.spl")).replace("\r\n", "\n").replace("\n", "\r\n");
        Files.writeString(spl, src);
        assertEquals(Main.OK, runMain(spl.toString()).code());
        assertEquals(lf(Files.readString(GOLDEN.resolve("v4_functions.xml"))), lf(Files.readString(dir.resolve("tree.xml"))));
    }

    @Test void utf8ByteOrderMarkIsIgnored(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        byte[] body = Files.readAllBytes(GOLDEN.resolve("v2_print_string.spl"));
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(spl, withBom);
        assertEquals(Main.OK, runMain(spl.toString()).code());
    }

    @Test void utf16NotepadFileIsDecoded(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        byte[] body = Files.readString(GOLDEN.resolve("v3_assign_arith.spl")).getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[body.length + 2];
        withBom[0] = (byte) 0xFF; withBom[1] = (byte) 0xFE;
        System.arraycopy(body, 0, withBom, 2, body.length);
        Files.write(spl, withBom);
        assertEquals(Main.OK, runMain(spl.toString()).code());
    }

    @Test void nonAsciiInputIsASyntaxErrorNotACrash(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        Files.write(spl, new byte[]{':', ' ', ':', ' ', 'p', 'r', 'i', 'n', 't', ' ', '"', (byte) 0xE9, '"', ' ', ';', '\n'});
        assertEquals(Main.SYNTAX_ERROR, runMain(spl.toString()).code());
    }

    @Test void emptyFileIsASyntaxError(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        Files.writeString(spl, "");
        assertEquals(Main.SYNTAX_ERROR, runMain(spl.toString()).code());
    }

    @Test void literalDollarIsNotPartOfTheLanguage(@TempDir Path dir) throws IOException {
        Path spl = dir.resolve("SPL.txt");
        Files.writeString(spl, ": : nop ; $\n");
        assertEquals(Main.SYNTAX_ERROR, runMain(spl.toString()).code());
    }
}
