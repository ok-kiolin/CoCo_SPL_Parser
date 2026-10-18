package spl.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import spl.analysis.SymbolTable.Symbol;
import spl.tree.Node;

/** Expands already-built templates, preserving free ancestor references. */
public final class FunctionInliner {
    private final Map<Symbol, FunctionTemplate> templates;

    FunctionInliner(Map<Symbol, FunctionTemplate> templates) {
        this.templates = templates;
    }

    public void emitCall(Node call, CodeName result, GenerationContext caller) {
        if (!"CALL".equals(call.contents)) {
            throw new CodeGenerationException("Expected CALL", call);
        }
        Symbol callee = caller.symbols().requireBinding(call.children.get(0));
        FunctionTemplate template = templates.get(callee);
        if (template == null) throw new CodeGenerationException("Missing template for " + callee, call);
        if (callee.isNumericFunction() != (result != null)) {
            throw new CodeGenerationException("Numeric calls require a result; procedures must not have one", call);
        }
        if (result != null) GenerationContext.requireVariable(result);

        List<Node> terms = new ArrayList<>();
        for (Node input = call.children.get(2); !input.children.isEmpty(); input = input.children.get(1)) {
            terms.add(input.children.get(0));
        }
        if (terms.size() != template.parameters().size()) {
            throw new CodeGenerationException("Argument count was not checked before generation", call);
        }

        // Capture ALL arguments before changing any formal parameter storage.
        List<CodeName> arguments = new ArrayList<>();
        for (Node term : terms) {
            CodeName argument = caller.temporary();
            caller.emitTerm(term, argument);
            arguments.add(argument);
        }

        Map<CodeName, CodeName> replacements = new LinkedHashMap<>();
        for (CodeName variable : template.variables()) replacements.put(variable, caller.temporary());
        for (CodeName label : template.labels()) replacements.put(label, caller.label());
        caller.emit("// inline ", callee.getInternalName());
        for (int i = 0; i < arguments.size(); i++) {
            caller.assign(replacements.get(template.parameters().get(i)), arguments.get(i));
        }
        caller.append(template.code().renamed(replacements));
        if (result != null) caller.assign(result, replacements.get(template.result()));
        caller.emit("// end inline ", callee.getInternalName());
    }
}
