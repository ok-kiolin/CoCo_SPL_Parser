package spl.codegen;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import spl.analysis.SymbolTable;
import spl.analysis.SymbolTable.Symbol;
import spl.tree.Node;
import spl.types.SymbolTableLookup;
import spl.types.Type;
import spl.types.TypeAnalyser;
import spl.types.TypeResult;

/** Template/code-mine generation and the single executable C++ program wrapper. */
public final class CodeGenerator {
    private final AlgorithmEmitter algorithms;

    public CodeGenerator() { this(new AlgorithmGenerator()); }

    public CodeGenerator(AlgorithmEmitter algorithms) {
        this.algorithms = Objects.requireNonNull(algorithms, "algorithms");
    }

    public String compile(Node root) {
        SymbolTable symbols = SymbolTable.analyze(root);
        TypeResult types = new TypeAnalyser(new SymbolTableLookup(symbols)).analyse(root);
        return generate(root, symbols, types);
    }

    public String generate(Node root, SymbolTable symbols, TypeResult types) {
        if (symbols.getRoot() != root || types.typeOf(root) != Type.OK) {
            throw new IllegalArgumentException("Generation requires this tree's successful semantic results.");
        }
        Map<Symbol, CodeName> storage = new IdentityHashMap<>();
        for (SymbolTable.Scope scope : symbols.getScopes()) {
            for (Symbol variable : scope.getVariables()) {
                storage.put(variable, new CodeName(variable.getInternalName(), CodeName.Kind.VARIABLE));
            }
        }
        Map<Symbol, FunctionTemplate> templates = new LinkedHashMap<>();
        FunctionInliner inliner = new FunctionInliner(templates);
        GenerationContext.Names names = new GenerationContext.Names();
        for (Symbol function : symbols.getGlobalScope().getFunctions()) {
            buildTemplate(function, symbols, types, storage, names, inliner, templates);
        }
        GenerationContext main = new GenerationContext(symbols, types.mode(), storage, names, inliner, algorithms);
        for (Symbol variable : symbols.getGlobalScope().getVariables()) main.declare(variable);
        algorithms.emitAlgorithm(root.children.get(0).children.get(4), main);

        StringBuilder out = new StringBuilder("/* begin non-executable code mine\n");
        for (FunctionTemplate template : templates.values()) {
            out.append(safeComment(renderTemplate(template, types.mode().cType)));
        }
        out.append("end non-executable code mine */\n#include <iostream>\nint main()\n{\n");
        for (CodeName variable : main.variables()) {
            out.append("    ").append(types.mode().cType).append(' ').append(variable.identifier()).append(";\n");
        }
        out.append(main.block().render("    "));
        return out.append("    return 0;\n}\n").toString();
    }

    private void buildTemplate(Symbol function, SymbolTable symbols, TypeResult types,
            Map<Symbol, CodeName> storage, GenerationContext.Names names,
            FunctionInliner inliner, Map<Symbol, FunctionTemplate> templates) {
        for (Symbol child : function.getFunctionScope().getFunctions()) {
            buildTemplate(child, symbols, types, storage, names, inliner, templates);
        }
        GenerationContext body = new GenerationContext(symbols, types.mode(), storage, names, inliner, algorithms);
        List<CodeName> parameters = function.getParameters().stream().map(body::declare).toList();
        for (Symbol local : function.getLocals()) body.declare(local);
        algorithms.emitAlgorithm(function.getBody().children.get(4), body);
        CodeName result = null;
        if (function.isNumericFunction()) {
            result = body.temporary();
            body.emitTerm(function.getReturnTerm(), result);
        }
        templates.put(function, new FunctionTemplate(function, parameters, body.variables(),
                body.labels(), result, body.block()));
    }

    private static String renderTemplate(FunctionTemplate template, String type) {
        StringBuilder text = new StringBuilder("Function ").append(template.function().getInternalName())
                .append(" ").append(template.function().getSourceName()).append('\n')
                .append("Parameters (in order): ");
        for (CodeName parameter : template.parameters()) text.append(parameter.identifier()).append(' ');
        text.append("\nStorage:\n");
        for (CodeName variable : template.variables()) {
            text.append(type).append(' ').append(variable.identifier()).append(";\n");
        }
        text.append("Body:\n").append(template.code().render(""));
        if (template.result() != null) text.append("Result: ").append(template.result().identifier()).append('\n');
        return text.append("End function\n").toString();
    }

    private static String safeComment(String text) {
        return text.replace("/*", "/ *").replace("*/", "* /");
    }
}
