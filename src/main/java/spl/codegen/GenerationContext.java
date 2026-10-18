package spl.codegen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import spl.analysis.SymbolTable;
import spl.analysis.SymbolTable.Symbol;
import spl.tree.Node;
import spl.types.NumericMode;

/** One body under construction. All child contexts share the fresh-name allocator. */
public final class GenerationContext {
    static final class Names {
        private int variable, label;
        CodeName variable() { return new CodeName("spl_tmp_" + ++variable, CodeName.Kind.VARIABLE); }
        CodeName label() { return new CodeName("spl_label_" + ++label, CodeName.Kind.LABEL); }
    }

    private final SymbolTable symbols;
    private final NumericMode mode;
    private final Map<Symbol, CodeName> storage;
    private final Names names;
    private final FunctionInliner inliner;
    private final AlgorithmEmitter algorithms;
    private final Set<CodeName> variables = new LinkedHashSet<>();
    private final Set<CodeName> labels = new LinkedHashSet<>();
    private final List<List<Object>> lines = new ArrayList<>();

    GenerationContext(SymbolTable symbols, NumericMode mode, Map<Symbol, CodeName> storage,
            Names names, FunctionInliner inliner, AlgorithmEmitter algorithms) {
        this.symbols = symbols;
        this.mode = mode;
        this.storage = storage;
        this.names = names;
        this.inliner = inliner;
        this.algorithms = algorithms;
    }

    public NumericMode mode() { return mode; }
    public SymbolTable symbols() { return symbols; }

    public CodeName variable(Node nameOccurrence) {
        return storageOf(symbols.requireBinding(nameOccurrence));
    }

    public CodeName storageOf(Symbol symbol) {
        CodeName value = storage.get(symbol);
        if (value == null) throw new CodeGenerationException("No variable storage for " + symbol);
        return value;
    }

    CodeName declare(Symbol symbol) {
        CodeName value = storageOf(symbol);
        variables.add(value);
        return value;
    }

    public CodeName temporary() {
        CodeName value = names.variable();
        variables.add(value);
        return value;
    }

    public CodeName label() {
        CodeName value = names.label();
        labels.add(value);
        return value;
    }

    /** Text is punctuation/operators/literals; identifiers MUST be CodeName objects. */
    public void emit(Object... parts) {
        for (Object part : parts) {
            Objects.requireNonNull(part, "code fragment");
            if (!(part instanceof CodeName) && !(part instanceof String)) {
                throw new IllegalArgumentException("Expected String or CodeName, got " + part.getClass());
            }
            if (part instanceof String text && (text.contains("\n") || text.contains("\r"))) {
                throw new IllegalArgumentException("Emit one target line at a time.");
            }
        }
        lines.add(List.of(parts));
    }

    public void assign(CodeName target, CodeName source) {
        requireVariable(target);
        requireVariable(source);
        emit(target, " = ", source, ";");
    }

    public void mark(CodeName label) {
        requireLabel(label);
        emit(label, ": ;");
    }

    public void jump(CodeName label) {
        requireLabel(label);
        emit("goto ", label, ";");
    }

    public void emitTerm(Node term, CodeName result) {
        requireVariable(result);
        algorithms.emitTerm(term, result, this);
    }

    /** result is null for a procedure instruction; numeric calls require a result. */
    public void emitCall(Node call, CodeName result) {
        inliner.emitCall(call, result, this);
    }

    public List<CodeName> variables() { return List.copyOf(variables); }
    public List<CodeName> labels() { return List.copyOf(labels); }
    public CodeBlock block() { return new CodeBlock(lines); }

    void append(CodeBlock block) { lines.addAll(block.lines()); }

    static void requireVariable(CodeName name) {
        if (name == null || name.kind() != CodeName.Kind.VARIABLE) {
            throw new IllegalArgumentException("Expected a result/storage variable.");
        }
    }

    private static void requireLabel(CodeName name) {
        if (name == null || name.kind() != CodeName.Kind.LABEL) {
            throw new IllegalArgumentException("Expected a label.");
        }
    }
}
