package spl.codegen;

import java.util.List;
import spl.analysis.SymbolTable.Symbol;

public record FunctionTemplate(Symbol function, List<CodeName> parameters,
        List<CodeName> variables, List<CodeName> labels, CodeName result, CodeBlock code) {
    public FunctionTemplate {
        parameters = List.copyOf(parameters);
        variables = List.copyOf(variables);
        labels = List.copyOf(labels);
    }
}
