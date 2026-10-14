package spl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainArgumentsTest {
    private static final String VALID = "#x : :\n#x = add ( 2 3 ) ;\nprint ( #x ) ;\n";
    private static final String INVALID = "#x : :\n#x = add ( 2 3 )\nprint ( #x ) ;\n";

    private static int run(String... args) {
        PrintStream sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        return Main.run(args, sink, sink);
    }

    @Test void anyFileNameWorks(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("lecturer-test-07.txt");
        Files.writeString(f, VALID);
        assertEquals(Main.OK, run(f.toString()));
        assertTrue(Files.exists(dir.resolve("tree.xml")));
    }

    @Test void tenFilesOneAfterAnother(@TempDir Path dir) throws IOException {
        for (int i = 1; i <= 10; i++) {
            Path f = dir.resolve("case" + i + ".txt");
            Files.writeString(f, i % 2 == 1 ? VALID : INVALID);
            int code = run(f.toString());
            assertEquals(i % 2 == 1 ? Main.OK : Main.SYNTAX_ERROR, code, "case" + i);
            assertEquals(i % 2 == 1, Files.exists(dir.resolve("tree.xml")), "tree.xml after case" + i);
        }
    }

    @Test void twoTestFilesAreRefusedAndNeitherIsTouched(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("test1.txt"), b = dir.resolve("test2.txt");
        Files.writeString(a, VALID);
        Files.writeString(b, INVALID);
        assertEquals(Main.INPUT_ERROR, run(a.toString(), b.toString()));
        assertEquals(VALID, Files.readString(a));
        assertEquals(INVALID, Files.readString(b), "the second test file must never be overwritten");
        assertFalse(Files.exists(dir.resolve("tree.xml")));
    }

    @Test void unquotedPathWithSpaces(@TempDir Path dir) throws IOException {
        Path folder = Files.createDirectories(dir.resolve("my tests"));
        Path f = folder.resolve("test 1.txt");
        Files.writeString(f, VALID);
        assertEquals(Main.OK, run(f.toString().split(" ")));
        assertTrue(Files.exists(folder.resolve("tree.xml")));
    }

    @Test void hiddenDoubleTxtExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("SPL.txt.txt"), VALID);
        assertEquals(Main.OK, run(dir.resolve("SPL.txt").toString()));
    }

    @Test void outputCanNeverBeTheInput(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("prog.xml");
        Files.writeString(f, VALID);
        assertEquals(Main.INPUT_ERROR, run(f.toString(), f.toString()));
        assertEquals(VALID, Files.readString(f));
    }

    @Test void textThatIsNotAValidPathIsReportedNotCrashed(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("test1.txt");
        Files.writeString(a, VALID);
        assertEquals(Main.INPUT_ERROR, run("bad\u0000name.txt"));
        assertEquals(Main.INPUT_ERROR, run(a.toString(), "x\u0000y.txt"));
        assertEquals(Main.INPUT_ERROR, run(a.toString(), "out\u0000.xml"));
        assertEquals(VALID, Files.readString(a));
    }
}