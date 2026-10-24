package spl.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import spl.Main;
import static org.junit.jupiter.api.Assertions.*;

/** External java -jar checks, restricted target syntax, and independent output expectations. */
class Task12ReleaseTest {
    @TempDir static Path distribution;
    @TempDir Path directory;
    private static Path jar;
    private record Example(String name, String source, String expected) {
        @Override public String toString() { return name; }
    }
    private record Result(int code, String output) { }

    @BeforeAll static void packageCompilerClasses() throws Exception {
        Path classes = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(classes));
        jar = distribution.resolve("group-5.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "spl.Main");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
    }

    private Result launch(Path working, Path executableJar, String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-jar", executableJar.toString()));
        command.addAll(List.of(args));
        Path log = Files.createTempFile(directory, "launcher-", ".txt");
        Process process = new ProcessBuilder(command).directory(working.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "JAR invocation timed out");
            return new Result(process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
            process.getOutputStream().close();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    static Stream<Example> examples() {
        return Stream.of(
            new Example("empty", ": :", ""),
            new Example("integer arithmetic and mod", ": : print ( add ( sub ( 8 3 ) mul ( 2 neg ( 4 ) ) ) ) ; print \":\" ; print ( mod ( 7 2 ) ) ;", "-3:1"),
            new Example("float division", ": : print ( div ( 7 2 ) ) ; print \":\" ; print ( add ( 1.5 2 ) ) ;", "3.5:3.5"),
            new Example("all loop forms and entry conditions", "#x : : #x = 0 ; while lesser ( #x 0 ) do { #x = 99 ; } ; print ( #x ) ; print \":\" ; until eq ( #x 0 ) do { #x = 99 ; } ; print ( #x ) ; print \":\" ; do { #x = add ( #x 1 ) ; } while lesser ( #x 0 ) ; print ( #x ) ; print \":\" ; #x = 0 ; do { #x = add ( #x 1 ) ; } until larger ( #x 0 ) ; print ( #x ) ;", "0:0:1:1"),
            new Example("short circuit with side effects", ": num #tick ( ) { : : print \"z\" ; return ( 1 ) } : if or ( eq ( 1 1 ) eq ( #tick ( ) 1 ) ) then { print \"a\" ; } else { print \"x\" ; } ; if and ( eq ( 1 0 ) eq ( #tick ( ) 1 ) ) then { print \"x\" ; } else { print \"b\" ; } ; if not ( lesser ( 2 1 ) ) then { print \"c\" ; } else { nop ; } ;", "abc"),
            new Example("capture and repeated inline expansion", ": num #outer ( #p ) { #x : num #inner ( #n ) { : : #x = add ( #x #n ) ; return ( #x ) } : #x = #p ; #x = #inner ( 2 ) ; return ( #inner ( 3 ) ) } : print ( #outer ( 4 ) ) ; print \":\" ; print ( #outer ( 7 ) ) ;", "9:12"),
            new Example("arguments captured left to right", "#x : num #next ( ) { : : #x = add ( #x 1 ) ; return ( #x ) } void #show ( #a #b ) { : : print ( #a ) ; print \":\" ; print ( #b ) ; return } : #x = 1 ; #show ( #x #next ( ) ) ; print \":\" ; print ( #x ) ;", "1:2:2"),
            new Example("string punctuation and comments", ": : comment \"else-while!\" ; nop ; print \"else-while!?.,:\" ;", "else-while!?.,:")
        );
    }

    @ParameterizedTest(name = "{0}") @MethodSource("examples")
    void packagedCompilerProducesRestrictedExecutableTarget(Example example) throws Exception {
        Path input = directory.resolve("input with spaces.txt");
        Files.writeString(input, example.source());
        Result result = launch(directory, jar, input.toString());
        assertEquals(Main.OK, result.code(), result.output());
        String target = Files.readString(directory.resolve("target.cpp"));
        assertRestrictedTarget(target);
        assertTrue(Files.readString(directory.resolve("tree.xml")).contains("SPL_PROG"));
        assertEquals(example.expected(), CppTestSupport.execute(directory, target));
    }

    /** Match every executable line, rather than merely searching for forbidden keywords. */
    private static void assertRestrictedTarget(String target) {
        assertTrue(target.startsWith("/* begin non-executable code mine\n"));
        int endMine = target.indexOf("*/");
        assertTrue(endMine > 0);
        assertTrue(target.substring(0, endMine).contains("end non-executable code mine"));
        String executable = target.substring(endMine + 2)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        String name = "[A-Za-z_][A-Za-z_0-9]*";
        String number = "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?";
        String atom = "(?:" + name + "|" + number + ")";
        Pattern declaration = Pattern.compile("(?:int|float) " + name + ";");
        Pattern assignment = Pattern.compile(name + " = (?:-?" + atom + "|" + atom + " [\\+*/%\\-] " + atom + ");");
        Pattern output = Pattern.compile("std::cout << (?:" + name + "|\"[,.:\\-?!0-9a-z]*\");");
        Pattern jump = Pattern.compile("goto (" + name + ");");
        Pattern branch = Pattern.compile("if \\(" + atom + " (?:==|<|>) " + atom + "\\) goto (" + name + ");");
        Pattern label = Pattern.compile("(" + name + "): ;");
        Set<String> defined = new HashSet<>(), used = new HashSet<>();
        boolean instructions = false;
        int includes = 0, mains = 0, opens = 0, closes = 0, returns = 0;
        for (String raw : executable.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            switch (line) {
                case "#include <iostream>" -> { includes++; continue; }
                case "int main()" -> { mains++; continue; }
                case "{" -> { opens++; continue; }
                case "}" -> { closes++; continue; }
                case "return 0;" -> { returns++; continue; }
            }
            if (declaration.matcher(line).matches()) {
                assertFalse(instructions, "Declaration occurs after instructions: " + line);
                continue;
            }
            instructions = true;
            var l = label.matcher(line);
            if (l.matches()) { assertTrue(defined.add(l.group(1)), "Duplicate label"); continue; }
            var j = jump.matcher(line);
            if (j.matches()) { used.add(j.group(1)); continue; }
            var b = branch.matcher(line);
            if (b.matches()) { used.add(b.group(1)); continue; }
            assertTrue(line.equals(";") || assignment.matcher(line).matches() || output.matcher(line).matches(),
                    "Line is outside the restricted executable forms: " + line);
        }
        assertEquals(1, includes); assertEquals(1, mains);
        assertEquals(1, opens); assertEquals(1, closes); assertEquals(1, returns);
        assertTrue(defined.containsAll(used), "Jump to an undefined label");
    }

    @Test void noArgumentLaunchFindsInputBesideJarFromAnotherWorkingDirectory() throws Exception {
        Path folder = Files.createDirectory(directory.resolve("submission with spaces"));
        Path copied = Files.copy(jar, folder.resolve("group-5.jar"));
        Files.writeString(folder.resolve("SPL.txt"), ": : print \"ok\" ;");
        Result result = launch(directory, copied);
        assertEquals(0, result.code(), result.output());
        assertEquals("ok", CppTestSupport.execute(folder, Files.readString(folder.resolve("target.cpp"))));
    }

    @Test void defaultInputUsesWorkingDirectoryBeforeJarDirectory() throws Exception {
        Files.writeString(directory.resolve("SPL.txt"), ": : print \"cwd\" ;");
        Result result = launch(directory, jar);
        assertEquals(0, result.code(), result.output());
        assertEquals("cwd", CppTestSupport.execute(directory, Files.readString(directory.resolve("target.cpp"))));
    }

    @Test void failedPackagedRunsRemoveStaleCodeAndReturnCorrectExitCodes() throws Exception {
        Path input = directory.resolve("SPL.txt");
        String[] sources = {": : $", ": : print ( #missing ) ;", ": void #p ( ) { : : return } : print ( #p ( ) ) ;"};
        int[] codes = {Main.SYNTAX_ERROR, Main.SEMANTIC_ERROR, Main.SEMANTIC_ERROR};
        for (int i = 0; i < sources.length; i++) {
            Files.writeString(input, sources[i]);
            Files.writeString(directory.resolve("target.cpp"), "stale target");
            Files.writeString(directory.resolve("tree.xml"), "stale tree");
            Result result = launch(directory, jar, input.toString());
            assertEquals(codes[i], result.code(), result.output());
            assertFalse(Files.exists(directory.resolve("target.cpp")));
            assertEquals(i != 0, Files.exists(directory.resolve("tree.xml")));
            assertFalse(result.output().contains("PASSED"));
        }
    }

    @Test void packagedModesHelpAndMissingInputBehaveAsDocumented() throws Exception {
        Path input = directory.resolve("SPL.txt");
        Files.writeString(input, ": : print ( #missing ) ;");
        assertEquals(Main.OK, launch(directory, jar, "--frontend-only", input.toString()).code());
        assertEquals(Main.SEMANTIC_ERROR, launch(directory, jar, "--check", input.toString()).code());
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        Files.writeString(input, ": : print \"ok\" ;");
        assertEquals(Main.OK, launch(directory, jar, "--compile", input.toString()).code());
        String target = Files.readString(directory.resolve("target.cpp"));
        assertEquals(Main.OK, launch(directory, jar, "--help").code());
        assertEquals(target, Files.readString(directory.resolve("target.cpp")));
        assertEquals(Main.INPUT_ERROR, launch(directory, jar, "absent.spl").code());
        assertFalse(Files.exists(directory.resolve("target.cpp")));
        assertFalse(Files.exists(directory.resolve("tree.xml")));
    }
}
