package spl.analysis;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import spl.Main;
import spl.analysis.SymbolTable.Symbol;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.*;

/** Task 9: independently specified naming cases, checked through the full compiler. */
class ScopeAcceptanceTest {
    @TempDir Path directory;
    private record Case(String file, boolean accepted, String name, String diagnostic, String rule) {
        @Override public String toString() { return file + " — " + rule; }
    }

    private static Path resources() throws Exception {
        var manifest = ScopeAcceptanceTest.class.getResource("/scope-acceptance/cases.tsv");
        assertNotNull(manifest, "Scope acceptance resources were not copied to the test classpath");
        return Path.of(manifest.toURI()).getParent();
    }

    private static List<Case> loadCases() throws Exception {
        List<Case> result = new ArrayList<>();
        for (String line : Files.readAllLines(resources().resolve("cases.tsv"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            assertEquals(5, fields.length, "Bad manifest row: " + line);
            assertTrue(fields[1].equals("ACCEPT") || fields[1].equals("REJECT"), line);
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
    void expectedScopeOutcomeThroughCompleteCompiler(Case test) throws Exception {
        Path fixture = resources().resolve(test.file()).normalize();
        assertTrue(fixture.startsWith(resources()));
        String source = Files.readString(fixture, StandardCharsets.US_ASCII);
        // Every rejection must be a naming error in syntactically valid SPL.
        Node tree = assertDoesNotThrow(() -> parse(source), "Invalid syntax in fixture " + test.file());
        SemanticException scopeError = null;
        if (test.accepted()) {
            SymbolTable table = assertDoesNotThrow(() -> SymbolTable.analyze(tree), test.rule());
            verifyBindingInvariants(table);
        } else {
            scopeError = assertThrows(SemanticException.class, () -> SymbolTable.analyze(tree), test.rule());
            assertEquals(test.name(), scopeError.getSourceName());
            assertTrue(scopeError.getMessage().contains(test.diagnostic()), scopeError.getMessage());
            assertTrue(scopeError.getNodeId() > 0);
            assertTrue(scopeError.getLine() > 0);
            assertTrue(scopeError.getColumn() > 0);
            String atError = source.lines().toList().get(scopeError.getLine() - 1)
                    .substring(scopeError.getColumn() - 1).split("\\s+", 2)[0];
            assertEquals(test.name(), atError, "Diagnostic position must identify the offending source token");
        }

        Path input = directory.resolve("scope acceptance.spl");
        Files.writeString(input, source, StandardCharsets.US_ASCII);
        Path target = directory.resolve("target.cpp"), xml = directory.resolve("tree.xml");
        Files.writeString(target, "stale target from a previous program");
        Files.writeString(xml, "stale tree from a previous program");
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        int code = Main.run(new String[]{input.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        String errors = err.toString(StandardCharsets.UTF_8), output = out.toString(StandardCharsets.UTF_8);
        assertEquals(source, Files.readString(input), "Compiler must not change source input");
        assertTrue(Files.readString(xml).contains("SPL_PROG"), "Syntactically valid input must retain its XML tree");
        if (test.accepted()) {
            assertEquals(Main.OK, code, errors);
            assertEquals("", errors);
            assertTrue(output.contains("Compilation PASSED"));
            assertTrue(Files.readString(target).contains("int main()"));
        } else {
            assertEquals(Main.SEMANTIC_ERROR, code, errors);
            assertTrue(errors.contains("Scope Error"), errors);
            assertTrue(errors.contains(test.diagnostic()), errors);
            assertTrue(errors.contains("source name '" + test.name() + "'"), errors);
            assertTrue(errors.contains("node " + scopeError.getNodeId()), errors);
            assertTrue(errors.contains("line " + scopeError.getLine()), errors);
            assertTrue(errors.contains("column " + scopeError.getColumn()), errors);
            assertFalse(output.contains("PASSED"), output);
            assertFalse(Files.exists(target), "Rejected input must remove stale generated code");
        }
    }

    private static void verifyBindingInvariants(SymbolTable table) {
        Map<String, Symbol> owners = new HashMap<>();
        for (SymbolTable.Scope scope : table.getScopes()) {
            List<Symbol> declarations = new ArrayList<>(scope.getVariables());
            declarations.addAll(scope.getFunctions());
            for (Symbol symbol : declarations) {
                assertSame(symbol, table.requireBinding(symbol.getNameNode()));
                assertNull(owners.put(symbol.getInternalName(), symbol), "Distinct declarations must have distinct internal names");
                assertTrue(symbol.getInternalName().matches("[A-Za-z_][A-Za-z0-9_]*"));
            }
        }
        for (Node name : names(table.getRoot(), null)) {
            Symbol binding = table.requireBinding(name);
            assertEquals(name.contents, binding.getSourceName(), "Retain the original spelling for diagnostics");
            assertSame(binding, owners.get(table.getInternalName(name)));
            boolean functionRole = name.parent.contents.equals("F_TYPE") || name.parent.contents.equals("CALL");
            assertEquals(functionRole, binding.isFunction(), "Names must bind in the namespace required by their role");
        }
    }

    private static List<Node> names(Node root, String spelling) {
        List<Node> result = new ArrayList<>();
        if (root.kind == Node.Kind.LEAF && root.contents.startsWith("#")
                && (spelling == null || root.contents.equals(spelling))) result.add(root);
        for (Node child : root.children) result.addAll(names(child, spelling));
        return result;
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
        assertEquals(listed, actual, "Every fixture needs an expected outcome; no manifest entry may be missing");
        assertTrue(cases.stream().anyMatch(Case::accepted));
        assertTrue(cases.stream().anyMatch(test -> !test.accepted()));
    }

    @Test void globalAndParameterUsesHaveExplicitExpectedDeclarationIdentities() {
        Node tree = parse("#x : num #outer ( #x ) { #y : "
                + "num #inner ( ) { : : return ( #x ) } : #y = #x ; return ( #inner ( ) ) } "
                + ": #x = 7 ; print ( #outer ( #x ) ) ;");
        SymbolTable table = SymbolTable.analyze(tree);
        Symbol global = table.getGlobalScope().getDeclaredVariable("#x");
        Symbol parameter = table.getGlobalScope().getFunctions().get(0).getParameters().get(0);
        List<Node> uses = names(tree, "#x");
        List<Symbol> expected = List.of(global, parameter, parameter, parameter, global, global);
        assertEquals(expected.size(), uses.size());
        for (int i = 0; i < uses.size(); i++) assertSame(expected.get(i), table.requireBinding(uses.get(i)));
        assertNotEquals(global.getInternalName(), parameter.getInternalName());
    }

    @Test void nearestDeclarationWinsAcrossThreeAncestors() throws Exception {
        SymbolTable table = SymbolTable.analyze(parse(Files.readString(resources().resolve("valid/08-nearest-three-level.spl"))));
        Symbol outer = table.getGlobalScope().getFunctions().get(0);
        Symbol middle = outer.getFunctionScope().getFunctions().get(0);
        Symbol inner = middle.getFunctionScope().getFunctions().get(0);
        assertSame(middle.getParameters().get(0), table.requireBinding(inner.getReturnTerm().children.get(0)));
        assertNotSame(outer.getLocals().get(0), table.requireBinding(inner.getReturnTerm().children.get(0)));
        assertNotSame(table.getGlobalScope().getDeclaredVariable("#x"), table.requireBinding(inner.getReturnTerm().children.get(0)));
    }

    @Test void siblingLocalsAndHelpersBindToTheirOwnDeclarations() throws Exception {
        SymbolTable table = SymbolTable.analyze(parse(Files.readString(resources().resolve("valid/15-duplicated-helper-names.spl"))));
        Symbol first = table.getGlobalScope().getFunctions().get(0);
        Symbol second = table.getGlobalScope().getFunctions().get(1);
        Symbol firstHelper = first.getFunctionScope().getFunctions().get(0);
        Symbol secondHelper = second.getFunctionScope().getFunctions().get(0);
        Node firstCall = first.getBody().children.get(4).children.get(0).children.get(0);
        Node secondCall = second.getBody().children.get(4).children.get(0).children.get(0);
        assertSame(firstHelper, table.requireBinding(firstCall.children.get(0)));
        assertSame(secondHelper, table.requireBinding(secondCall.children.get(0)));
        assertNotSame(firstHelper, secondHelper);
        assertNotEquals(firstHelper.getInternalName(), secondHelper.getInternalName());

        SymbolTable locals = SymbolTable.analyze(parse(Files.readString(resources().resolve("valid/13-sibling-local-names.spl"))));
        Symbol firstLocal = locals.getGlobalScope().getFunctions().get(0).getLocals().get(0);
        Symbol secondLocal = locals.getGlobalScope().getFunctions().get(1).getLocals().get(0);
        List<Node> occurrences = names(locals.getRoot(), "#x");
        List<Symbol> expected = List.of(firstLocal, firstLocal, firstLocal, secondLocal, secondLocal, secondLocal);
        assertEquals(expected.size(), occurrences.size());
        for (int i = 0; i < occurrences.size(); i++) {
            assertSame(expected.get(i), locals.requireBinding(occurrences.get(i)));
        }
        assertNotEquals(firstLocal.getInternalName(), secondLocal.getInternalName());
    }

    @Test void sameNamedChildIsADifferentFunctionRatherThanRecursion() throws Exception {
        SymbolTable table = SymbolTable.analyze(parse(Files.readString(resources().resolve("valid/21-child-function-same-name-as-parent.spl"))));
        Symbol parent = table.getGlobalScope().getFunctions().get(0);
        Symbol child = parent.getFunctionScope().getFunctions().get(0);
        Node call = parent.getReturnTerm().children.get(0);
        assertSame(child, table.requireBinding(call.children.get(0)));
        assertNotSame(parent, child);
    }
}
