package spl.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Immutable statements containing literal fragments and structured name references. */
public final class CodeBlock {
    private final List<List<Object>> lines;

    CodeBlock(List<List<Object>> lines) {
        this.lines = lines.stream().map(List::copyOf).toList();
    }

    public CodeBlock renamed(Map<CodeName, CodeName> replacements) {
        List<List<Object>> result = new ArrayList<>();
        for (List<Object> line : lines) {
            List<Object> copy = new ArrayList<>();
            for (Object part : line) {
                copy.add(part instanceof CodeName name ? replacements.getOrDefault(name, name) : part);
            }
            result.add(copy);
        }
        return new CodeBlock(result);
    }

    List<List<Object>> lines() { return lines; }

    public String render(String indent) {
        StringBuilder out = new StringBuilder();
        for (List<Object> line : lines) {
            out.append(indent);
            for (Object part : line) {
                out.append(part instanceof CodeName name ? name.identifier() : part);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
