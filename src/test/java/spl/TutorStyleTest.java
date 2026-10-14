package spl;

import org.junit.jupiter.api.Test;
import spl.errors.SyntaxException;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import spl.xml.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tutor-style black-box cases (src/test/resources/tutor): 23 valid programs, each with the exact
 * tree.xml an independent reference implementation produces, and 52 programs that each break one rule.
 */
class TutorStyleTest {

    private static final Path VALID = Path.of("src/test/resources/tutor/valid");
    private static final Path INVALID = Path.of("src/test/resources/tutor/invalid");

    private static String source(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);   // byte-exact: keeps CR / tabs
    }

    private static List<Path> spl(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".spl")).sorted().toList(); }
    }

    private static String lf(String s) { return s.replace("\r\n", "\n"); }

    @Test void everyValidTutorProgramProducesTheExpectedTree() throws IOException {
        List<Path> programs = spl(VALID);
        assertEquals(23, programs.size());
        for (Path p : programs) {
            Node tree = new Parser(new Lexer().tokenize(source(p))).parse();
            StringWriter w = new StringWriter();
            XMLWriter.write(tree, w);
            Path expected = VALID.resolve(p.getFileName().toString().replace(".spl", ".xml"));
            assertEquals(lf(Files.readString(expected)), lf(w.toString()), p.getFileName().toString());
        }
    }

    @Test void everyInvalidTutorProgramIsRejected() throws IOException {
        List<Path> programs = spl(INVALID);
        assertEquals(52, programs.size());
        for (Path p : programs) {
            String src = source(p);
            assertThrows(SyntaxException.class, () -> new Parser(new Lexer().tokenize(src)).parse(),
                    p.getFileName() + " should be rejected");
        }
    }
}
