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
import spl.types.TypeResult;

public final class Main {
    static final String DEFAULT_INPUT = "SPL.txt";
    static final String OUTPUT_NAME = "tree.xml";

    public static final int OK = 0, SYNTAX_ERROR = 1, INPUT_ERROR = 2, INTERNAL_ERROR = 3;
    public static final int SEMANTIC_ERROR = 4, GENERATION_ERROR = 5;

    private Main() {}

    static final long STACK_BYTES = 256L * 1024 * 1024;

    public static void main(String[] args) throws InterruptedException {
        int[] result = {INTERNAL_ERROR};
        Thread worker = new Thread(null, () -> result[0] = run(args, System.out, System.err), "spl-compiler", STACK_BYTES);
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

    enum Mode { COMPILE, CHECK, FRONTEND_ONLY }

    private record Arguments(Mode mode, String input, String xml, boolean help) { }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, new CodeGenerator());
    }

    // Package-private injection boundary for exercising generation failures.
    static int run(String[] args, PrintStream out, PrintStream err, CodeGenerator generator) {
        Arguments arguments;
        try {
            arguments = arguments(args);
        } catch (IllegalArgumentException invalidArguments) {
            err.println(invalidArguments.getMessage());
            usage(err);
            err.println("No files were changed.");
            return INPUT_ERROR;
        }
        if (arguments.help()) {
            usage(out);
            return OK;
        }

        Path input, xml;
        try {
            input = resolveInput(arguments.input());
            xml = (arguments.xml() == null ? input.resolveSibling(OUTPUT_NAME) : Path.of(arguments.xml()))
                    .toAbsolutePath().normalize();
        } catch (InvalidPathException invalidPath) {
            err.println("Not a valid file path: " + invalidPath.getInput());
            err.println("No files were changed.");
            return INPUT_ERROR;
        }
        CompilerOutputs outputs = new CompilerOutputs(input, xml);
        if (Files.exists(input) && !Files.isRegularFile(input)) {
            err.println("Input is not a regular file: " + input);
            return INPUT_ERROR;
        }
        try {
            outputs.prepare();
        } catch (IOException preparation) {
            err.println("Cannot prepare compiler outputs: " + preparation.getMessage());
            return INPUT_ERROR;
        }

        String source;
        try {
            source = readSource(input);
        } catch (NoSuchFileException missing) {
            err.println("Input file not found: " + input);
            String seen = textFilesIn(input.getParent());
            if (!seen.isEmpty()) err.println("Text files in that folder: " + seen);
            err.println("Put SPL.txt beside the program, or give its path in quotes.");
            return INPUT_ERROR;
        } catch (IOException unreadable) {
            err.println("Cannot read input file " + input + ": " + unreadable.getMessage());
            return INPUT_ERROR;
        }

        boolean treeWritten = false;
        Path writing = xml;
        try {
            List<Token> tokens = new Lexer().tokenize(source);
            Node tree = new Parser(tokens).parse();
            outputs.writeTree(tree);
            treeWritten = true;

            if (arguments.mode() != Mode.FRONTEND_ONLY) {
                SymbolTable symbols = SymbolTable.analyze(tree);
                TypeResult types = new TypeAnalyser(new SymbolTableLookup(symbols)).analyse(tree);
                if (arguments.mode() == Mode.COMPILE) {
                    // Consume the exact semantic results above: do not analyse twice.
                    String generated = generator.generate(tree, symbols, types);
                    writing = outputs.target;
                    outputs.writeTarget(generated);
                    out.println("Compilation PASSED: " + input);
                    out.println("Target source written to: " + outputs.target);
                } else {
                    out.println("Semantic check PASSED: " + input);
                }
                out.println("Numeric mode: " + types.mode().cType);
            } else {
                out.println("Syntax check PASSED: " + input);
            }
            out.println("Syntax tree (" + countNodes(tree) + " nodes) written to: " + xml);
            return OK;
        } catch (SyntaxException syntax) {
            outputs.removeTarget(err);
            outputs.removeTree(err);
            err.println("Syntax check FAILED: " + input);
            err.println(syntax.getMessage());
            return SYNTAX_ERROR;
        } catch (SemanticException | TypeException semantic) {
            outputs.removeTarget(err);
            err.println("Semantic check FAILED: " + input);
            err.println(semantic.getMessage());
            if (treeWritten) err.println("Syntax tree available at: " + xml);
            return SEMANTIC_ERROR;
        } catch (CodeGenerationException generation) {
            outputs.removeTarget(err);
            err.println("Code generation FAILED: " + input);
            err.println(generation.getMessage());
            return GENERATION_ERROR;
        } catch (IOException outputFailure) {
            outputs.removeTarget(err);
            if (!treeWritten) outputs.removeTree(err);
            err.println("Could not write " + writing + ": " + outputFailure.getMessage());
            return INPUT_ERROR;
        } catch (StackOverflowError tooDeep) {
            outputs.removeTarget(err);
            if (!treeWritten) outputs.removeTree(err);
            err.println("The program in " + input + " is nested too deeply or is too long to process.");
            return INTERNAL_ERROR;
        } catch (RuntimeException internal) {
            outputs.removeTarget(err);
            if (!treeWritten) outputs.removeTree(err);
            err.println("Internal error while processing " + input + ": " + internal);
            return INTERNAL_ERROR;
        }
    }

    private static Arguments arguments(String[] args) {
        Mode mode = Mode.COMPILE;
        int offset = 0;
        if (args.length > 0 && args[0].startsWith("--")) {
            switch (args[0]) {
                case "--compile" -> mode = Mode.COMPILE;
                case "--check" -> mode = Mode.CHECK;
                case "--frontend-only" -> mode = Mode.FRONTEND_ONLY;
                case "--help" -> {
                    if (args.length != 1) throw new IllegalArgumentException("--help takes no file arguments.");
                    return new Arguments(mode, null, null, true);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[0]);
            }
            offset = 1;
        }
        String[] files = Arrays.copyOfRange(args, offset, args.length);
        for (String file : files) {
            if (file.startsWith("--")) throw new IllegalArgumentException("Specify only one leading mode option.");
        }
        if (files.length == 0) return new Arguments(mode, null, null, false);
        if (files.length == 1) return new Arguments(mode, files[0], null, false);
        String joined = String.join(" ", files);
        if (isExistingFile(joined)) return new Arguments(mode, joined, null, false);
        if (files.length == 2 && files[1].toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            return new Arguments(mode, files[0], files[1], false);
        }
        throw new IllegalArgumentException("Please give ONE SPL file per run and optionally an XML output path.");
    }

    private static void usage(PrintStream stream) {
        stream.println("Usage: java -jar group-5.jar [--compile|--check|--frontend-only] [SPL.txt [tree.xml]]");
        stream.println("Default: full compilation to tree.xml and target.cpp beside the input.");
        stream.println("--check: names and types only. --frontend-only: syntax and XML only.");
        stream.println("No input argument: look for SPL.txt. Put paths containing spaces in quotes.");
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

    private static int countNodes(Node root) {
        java.util.ArrayDeque<Node> pending = new java.util.ArrayDeque<>();
        pending.push(root);
        int count = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            count++;
            for (Node child : node.children) pending.push(child);
        }
        return count;
    }
}
