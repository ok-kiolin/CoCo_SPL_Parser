package spl.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Shared process lifetime and bounded executable-cleanup handling for Windows. */
final class CppTestSupport {
    private CppTestSupport() { }

    static String execute(Path directory, String source) throws Exception {
        Path cpp = directory.resolve("target.cpp"), executable = directory.resolve("program.exe");
        Path diagnostics = directory.resolve("compiler.txt"), output = directory.resolve("output.txt");
        Files.writeString(cpp, source);
        try {
            Process compiler;
            try {
                compiler = new ProcessBuilder("g++", "-std=c++17", "-pedantic-errors", "-Wall",
                        cpp.toString(), "-o", executable.toString()).redirectErrorStream(true)
                        .redirectOutput(diagnostics.toFile()).start();
            } catch (IOException unavailable) {
                assumeTrue(false, "g++ is required for target execution tests: " + unavailable);
                return "";
            }
            assertEquals(0, await(compiler, 20, "Target compilation"),
                    () -> read(diagnostics) + "\n" + source);
            Process program = new ProcessBuilder(executable.toString()).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            assertEquals(0, await(program, 5, "Generated program"), () -> read(output));
            return Files.readString(output);
        } finally {
            // Windows can briefly retain an image lock after process exit. Keep
            // cleanup enabled and fail if the bounded retry cannot release it.
            deleteExecutable(executable);
        }
    }

    private static int await(Process process, int seconds, String description) throws Exception {
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                fail(description + " timed out after " + seconds + " seconds");
            }
            return process.exitValue();
        } finally {
            List<ProcessHandle> children = process.descendants().toList();
            for (ProcessHandle child : children) if (child.isAlive()) child.destroyForcibly();
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed-out process did not terminate: " + process.pid());
                }
            }
            close(process.getOutputStream());
            close(process.getInputStream());
            close(process.getErrorStream());
        }
    }

    private static void close(java.io.Closeable stream) {
        try { stream.close(); } catch (IOException ignored) { }
    }

    private static void deleteExecutable(Path executable) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                Files.deleteIfExists(executable);
                return;
            } catch (IOException locked) {
                if (System.nanoTime() >= deadline) {
                    throw new IOException("Executable is still locked after waiting for cleanup: " + executable, locked);
                }
                Thread.sleep(100);
            }
        }
    }

    private static String read(Path file) {
        try { return Files.readString(file); }
        catch (IOException e) { return e.toString(); }
    }
}
