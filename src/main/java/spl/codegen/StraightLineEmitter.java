package spl.codegen;

import spl.tree.Node;

/** Task 4 adapter for basic executable examples; Task 3 supplies control flow. */
public final class StraightLineEmitter implements AlgorithmEmitter {
    @Override public void emitAlgorithm(Node algo, GenerationContext context) {
        for (Node current = algo; !current.children.isEmpty(); current = current.children.get(2)) {
            emitInstruction(current.children.get(0), context);
        }
    }

    public void emitInstruction(Node instruction, GenerationContext context) {
        Node first = instruction.children.get(0);
        if (first.kind == Node.Kind.LEAF) {
            switch (first.contents) {
                    case "nop" -> context.emit("; // nop");
                    case "comment" -> context.emit("// ", instruction.children.get(1).contents);
                    case "print" -> {
                        Node output = instruction.children.get(1);
                        if (output.children.size() == 1) {
                            context.emit("std::cout << ", output.children.get(0).contents, ";");
                        } else {
                            CodeName result = context.temporary();
                            emitTerm(output.children.get(1), result, context);
                            context.emit("std::cout << ", result, ";");
                        }
                    }
                    default -> throw new CodeGenerationException("Unsupported instruction", instruction);
            }
        } else if ("CALL".equals(first.contents)) {
            context.emitCall(first, null);
        } else if ("ASSIGN".equals(first.contents)) {
            CodeName result = context.temporary();
            emitTerm(first.children.get(2), result, context);
            context.assign(context.variable(first.children.get(0)), result);
        } else {
            throw new CodeGenerationException("Task 3 AlgorithmEmitter is required for "
                    + first.contents + "; no target code was generated", first);
        }
    }

    @Override public void emitTerm(Node term, CodeName result, GenerationContext context) {
        GenerationContext.requireVariable(result);
        Node first = term.children.get(0);
        if (term.children.size() == 1) {
            if (first.kind != Node.Kind.LEAF) context.emitCall(first, result);
            else if (first.contents.startsWith("#")) context.assign(result, context.variable(first));
            else context.emit(result, " = ", first.contents, ";");
            return;
        }
        CodeName left = context.temporary();
        emitTerm(term.children.get(2), left, context);
        if ("neg".equals(first.contents)) {
            context.emit(result, " = -", left, ";");
            return;
        }
        CodeName right = context.temporary();
        emitTerm(term.children.get(3), right, context);
        String operator = switch (first.contents) {
            case "add" -> "+";
            case "sub" -> "-";
            case "mul" -> "*";
            case "div" -> "/";
            case "mod" -> "%";
            default -> throw new CodeGenerationException("Unsupported term operator", term);
        };
        context.emit(result, " = ", left, " ", operator, " ", right, ";");
    }
}
