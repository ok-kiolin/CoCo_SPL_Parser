package spl;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import spl.tree.Node;
import spl.xml.XMLWriter;

/** Validated output paths and publication through complete temporary files. */
final class CompilerOutputs {
    final Path input, xml, target;

    CompilerOutputs(Path input, Path xml) {
        this.input = input;
        this.xml = xml;
        this.target = input.resolveSibling("target.cpp");
    }

    void prepare() throws IOException {
        // Validate every path before deleting anything, including hard-link and
        // symlink aliases that lexical Path.equals alone would not detect.
        if (sameFile(input, xml) || sameFile(input, target) || sameFile(xml, target)) {
            throw new IOException("Input, XML output and target.cpp must be different files.");
        }
        rejectDirectory(xml);
        rejectDirectory(target);
        remove(target); // Every mode invalidates the previous generated target.
        remove(xml);
    }

    void writeTree(Node tree) throws IOException {
        publish(xml, temporary -> XMLWriter.write(tree, temporary));
    }

    void writeTarget(String source) throws IOException {
        publish(target, temporary -> Files.writeString(temporary, source, StandardCharsets.UTF_8));
    }

    void removeTarget(PrintStream err) { removeAfterFailure(target, err); }
    void removeTree(PrintStream err) { removeAfterFailure(xml, err); }

    private static boolean sameFile(Path first, Path second) throws IOException {
        return first.equals(second) || (Files.exists(first) && Files.exists(second)
                && Files.isSameFile(first, second));
    }

    private static void rejectDirectory(Path file) throws IOException {
        if (Files.isDirectory(file)) {
            throw new IOException("Output path is a directory; it was not removed: " + file);
        }
    }

    private static void remove(Path file) throws IOException {
        rejectDirectory(file);
        Files.deleteIfExists(file);
    }

    private static void removeAfterFailure(Path file, PrintStream err) {
        try { remove(file); }
        catch (IOException cleanup) {
            err.println("Could not remove output " + file + ": " + cleanup.getMessage());
        }
    }

    @FunctionalInterface private interface Writer { void write(Path temporary) throws IOException; }

    private static void publish(Path destination, Writer writer) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".spl-output-", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
