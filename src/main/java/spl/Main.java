package spl;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;

import spl.analysis.SemanticException;
import spl.analysis.SymbolTable;
import spl.codegen.CodeGenerationException;
import spl.codegen.CodeGenerator;
import spl.errors.SyntaxException;
import spl.lexer.Lexer;
import spl.lexer.Token;
import spl.parser.Parser;
import spl.tree.Node;
import spl.types.SymbolTableLookup;
import spl.types.TypeAnalyser;
import spl.types.TypeException;
import spl.xml.XMLWriter;

public final class Main {
    static final String DEFAULT_INPUT = "SPL.txt";
    static final String OUTPUT_NAME = "tree.xml";

    public static final int OK = 0, SYNTAX_ERROR = 1, INPUT_ERROR = 2, INTERNAL_ERROR = 3;
    public static final int SEMANTIC_ERROR = 4, GENERATION_ERROR = 5;

    private Main() {}

    static final long STACK_BYTES = 256L * 1024 * 1024;

    public static void main(String[] args) throws InterruptedException {
        int[] result = {INTERNAL_ERROR};
        Thread worker = new Thread(null, () -> result[0] = run(args, System.out, System.err), "spl-front-end", STACK_BYTES);
        worker.start();
        worker.join();
        int code = result[0];

        if (args.length == 0 && System.getProperty("jpackage.app-path") != null) {
            System.out.println();
            System.out.println("Press Enter to close this window.");
            try { System.in.read(); } catch (IOException ignored) { }
        }
        System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        // Keep P1's default behaviour while Task 3 is being completed.
        boolean compile = args.length > 0 && "--compile".equals(args[0]);
        if (compile) args = Arrays.copyOfRange(args, 1, args.length);
        String inputArg = null, outputArg = null;
        if (args.length == 1) {
            inputArg = args[0];
        } else if (args.length >= 2) {
            String joined = String.join(" ", args);
            if (isExistingFile(joined)) {
                inputArg = joined;
            } else if (args.length == 2 && args[1].toLowerCase().endsWith(".xml")) {
                inputArg = args[0];
                outputArg = args[1];
            } else {
                err.println("Please give ONE SPL file per run, for example:");
                err.println("  java -jar group-5.jar C:\\tests\\test1.txt");
                err.println("  group-5.exe C:\\tests\\test1.txt");
                err.println("Put the path in quotes if it contains spaces: \"C:\\my tests\\test1.txt\"");
                err.println("No files were changed.");
                return INPUT_ERROR;
            }
        }

        Path input, output, target;
        try {
            input = resolveInput(inputArg);
            output = (outputArg != null ? Path.of(outputArg) : input.resolveSibling(OUTPUT_NAME))
                    .toAbsolutePath().normalize();
            target = input.resolveSibling("target.cpp");
        } catch (InvalidPathException e) {
            err.println("Not a valid file path: " + e.getInput());
            err.println("Give the path of ONE SPL file, in quotes if it contains spaces, e.g.:");
            err.println("  java -jar group-5.jar \"C:\\my tests\\test1.txt\"");
            err.println("No files were changed.");
            return INPUT_ERROR;
        }
        if (output.equals(input)) {
            err.println("The output file cannot be the input file itself: " + input);
            return INPUT_ERROR;
        }
        if (compile && (target.equals(input) || target.equals(output))) {
            err.println("The target.cpp output must differ from both input and XML output.");
            return INPUT_ERROR;
        }

        try {
            if (compile) Files.deleteIfExists(target);
            Files.deleteIfExists(output);
        } catch (IOException e) {
            err.println("Cannot replace the existing output file " + output + ": " + e.getMessage());
            return INPUT_ERROR;
        }

        String source;
        try {
            source = readSource(input);
        } catch (NoSuchFileException e) {
            err.println("Input file not found: " + input);
            String seen = textFilesIn(input.getParent());
            if (!seen.isEmpty()) err.println("Text files in that folder: " + seen);
            err.println("Put SPL.txt in the same folder as the program, or give its path, e.g.:");
            err.println("  group-5.exe C:\\tests\\SPL.txt");
            err.println("  java -jar group-5.jar C:\\tests\\SPL.txt");
            return INPUT_ERROR;
        } catch (IOException e) {
            err.println("Cannot read input file " + input + ": " + e.getMessage());
            return INPUT_ERROR;
        }

        try {
            List<Token> tokens = new Lexer().tokenize(source);
            Node tree = new Parser(tokens).parse();
            XMLWriter.write(tree, output);

            // Every parsed program must pass both semantic phases, even when the
            // caller only requests frontend validation.  Type analysis consumes
            // the occurrence-to-declaration bindings produced by scope analysis.
            SymbolTable symbolTable = SymbolTable.analyze(tree);
            new TypeAnalyser(new SymbolTableLookup(symbolTable)).analyse(tree);

            if (compile) {
                String generated = new CodeGenerator().compile(tree);
                Files.writeString(target, generated, StandardCharsets.UTF_8);
                out.println("Compilation PASSED: " + input);
                out.println("Target source written to: " + target);
            }
            out.println("Syntax check PASSED: " + input);
            out.println("Syntax tree (" + countNodes(tree) + " nodes) written to: " + output);
            return OK;
        } catch (SyntaxException e) {
            if (compile) deleteQuietly(target);
            deleteQuietly(output);
            err.println("Syntax check FAILED: " + input);
            err.println(e.getMessage());
            err.println("No tree.xml was written.");
            return SYNTAX_ERROR;
        } catch (SemanticException | TypeException e) {
            deleteQuietly(target);
            err.println("Semantic check FAILED: " + input);
            err.println(e.getMessage());
            err.println("No target.cpp was written. The syntax tree remains available.");
            return SEMANTIC_ERROR;
        } catch (CodeGenerationException e) {
            deleteQuietly(target);
            err.println("Code generation FAILED: " + input);
            err.println(e.getMessage());
            return GENERATION_ERROR;
        } catch (IOException e) {
            if (compile) deleteQuietly(target);
            deleteQuietly(output);
            err.println("Could not write " + output + ": " + e.getMessage());
            return INPUT_ERROR;
        } catch (StackOverflowError e) {
            if (compile) deleteQuietly(target);
            deleteQuietly(output);
            err.println("The program in " + input + " is nested too deeply or is too long to process.");
            err.println("No tree.xml was written.");
            return INTERNAL_ERROR;
        } catch (RuntimeException e) {
            if (compile) deleteQuietly(target);
            deleteQuietly(output);
            err.println("Internal error while processing " + input + ": " + e);
            return INTERNAL_ERROR;
        }
    }

    static boolean isExistingFile(String candidate) {
        try {
            return Files.isRegularFile(withHiddenExtension(Path.of(candidate).toAbsolutePath()));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    static Path resolveInput(String arg) {
        if (arg != null) return withHiddenExtension(Path.of(arg).toAbsolutePath().normalize());
        Path inCwd = Path.of(DEFAULT_INPUT).toAbsolutePath().normalize();
        Path programDir = programDirectory();
        Path inProgramDir = programDir == null ? null : programDir.resolve(DEFAULT_INPUT).normalize();
        for (Path candidate : new Path[]{inCwd, inProgramDir}) {
            if (candidate != null && Files.exists(candidate)) return candidate;
        }
        for (Path candidate : new Path[]{inCwd, inProgramDir}) {
            if (candidate != null && Files.exists(withTxt(candidate))) return withTxt(candidate);
        }
        return inCwd;
    }

    static Path withHiddenExtension(Path p) {
        return !Files.exists(p) && Files.exists(withTxt(p)) ? withTxt(p) : p;
    }

    private static Path withTxt(Path p) {
        return p.resolveSibling(p.getFileName() + ".txt");
    }

    private static String textFilesIn(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) return "";
        try (var files = Files.list(folder)) {
            return String.join(", ", files
                    .map(f -> f.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".txt") || n.toLowerCase().startsWith("spl"))
                    .sorted().limit(10).toList());
        } catch (IOException e) {
            return "";
        }
    }

    static Path programDirectory() {
        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null) return Path.of(appPath).toAbsolutePath().getParent();
            java.net.URL location = Main.class.getProtectionDomain().getCodeSource().getLocation();
            Path jarOrClasses = Path.of(location.toURI()).toAbsolutePath();
            return Files.isRegularFile(jarOrClasses) ? jarOrClasses.getParent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String readSource(Path input) throws IOException {
        byte[] b = Files.readAllBytes(input);
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.ISO_8859_1);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static int countNodes(Node n) {
        int c = 1;
        for (Node child : n.children) c += countNodes(child);
        return c;
    }

    private static void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }
}
