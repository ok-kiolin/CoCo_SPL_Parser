package spl.codegen;

import spl.tree.Node;

public final class AlgorithmGenerator implements AlgorithmEmitter {

    @Override
    public void emitAlgorithm(Node algo, GenerationContext context) {
        require(algo, "ALGO");
        if (algo.children.isEmpty()) {
            context.emit("/* end */");
            return;
        }

        emitInstruction(algo.children.get(0), context);
        emitAlgorithm(algo.children.get(2), context);
    }

    @Override
    public void emitTerm(Node term, CodeName result, GenerationContext context) {
        require(term, "TERM");
        if (term.children.isEmpty()) {
            throw malformed(term, "TERM has no children");
        }

        Node first = term.children.get(0);

        if (term.children.size() == 1) {
            if (is(first, "CALL")) {
                context.emitCall(first, result);
            } else if (first.contents.startsWith("#")) {
                context.assign(result, context.variable(first));
            } else {
                context.emit(result, " = ", first.contents, ";");
            }
            return;
        }

        switch (first.contents) {
            case "add", "sub", "mul", "div", "mod" ->
                    emitBinary(term, result, context, operator(first.contents));
            case "neg" -> emitNegation(term, result, context);
            default -> throw malformed(term, "Unsupported TERM form '" + first.contents + "'");
        }
    }

    private void emitInstruction(Node instruction, GenerationContext context) {
        require(instruction, "INSTR");
        if (instruction.children.isEmpty()) {
            throw malformed(instruction, "INSTR has no children");
        }

        Node first = instruction.children.get(0);
        if (first.kind == Node.Kind.LEAF) {
            switch (first.contents) {
                case "print" -> emitOutput(instruction.children.get(1), context);
                case "nop" -> context.emit("; /* nop */");
                case "comment" -> context.emit("; /* ", commentText(instruction.children.get(1).contents), " */");
                default -> throw malformed(instruction, "Unsupported instruction '" + first.contents + "'");
            }
            return;
        }

        switch (first.contents) {
            case "ASSIGN" -> emitAssignment(first, context);
            case "BRANCH" -> emitBranch(first, context);
            case "LOOP" -> emitLoop(first, context);
            case "CALL" -> context.emitCall(first, null);
            default -> throw malformed(instruction, "Unsupported instruction node '" + first.contents + "'");
        }
    }

    private void emitOutput(Node output, GenerationContext context) {
        require(output, "OUTP");
        if (output.children.size() == 1) {
            context.emit("std::cout << ", output.children.get(0).contents, ";");
            return;
        }

        CodeName value = context.temporary();
        emitTerm(output.children.get(1), value, context);
        context.emit("std::cout << ", value, ";");
    }

    private void emitAssignment(Node assignment, GenerationContext context) {
        require(assignment, "ASSIGN");
        CodeName value = context.temporary();
        emitTerm(assignment.children.get(2), value, context);
        context.assign(context.variable(assignment.children.get(0)), value);
    }

    private void emitBinary(Node term, CodeName result, GenerationContext context, String operator) {
        CodeName left = context.temporary();
        CodeName right = context.temporary();
        emitTerm(term.children.get(2), left, context);
        emitTerm(term.children.get(3), right, context);
        context.emit(result, " = ", left, " ", operator, " ", right, ";");
    }

    private void emitNegation(Node term, CodeName result, GenerationContext context) {
        CodeName value = context.temporary();
        emitTerm(term.children.get(2), value, context);
        context.emit(result, " = -", value, ";");
    }

    private void emitBranch(Node branch, GenerationContext context) {
        require(branch, "BRANCH");
        CodeName trueLabel = context.label();
        CodeName falseLabel = context.label();
        CodeName exitLabel = context.label();

        context.emit("/* if */");
        emitBoolean(branch.children.get(1), trueLabel, falseLabel, context);

        context.mark(falseLabel);
        context.emit("; /* else */");
        emitAlgorithm(branch.children.get(8), context);
        context.jump(exitLabel);

        context.mark(trueLabel);
        context.emit("; /* then */");
        emitAlgorithm(branch.children.get(4), context);

        context.mark(exitLabel);
        context.emit("; /* continue */");
    }

    private void emitLoop(Node loop, GenerationContext context) {
        require(loop, "LOOP");
        if (is(loop.children.get(0), "COND")) {
            emitPreTestLoop(loop, context);
        } else {
            emitPostTestLoop(loop, context);
        }
    }

    private void emitPreTestLoop(Node loop, GenerationContext context) {
        String condition = loop.children.get(0).children.get(0).contents;
        Node bool = loop.children.get(1);
        Node body = loop.children.get(4);
        CodeName start = context.label();
        CodeName bodyLabel = context.label();
        CodeName exit = context.label();

        context.mark(start);
        context.emit("; /* ", condition, " */");
        if ("while".equals(condition)) {
            emitBoolean(bool, bodyLabel, exit, context);
        } else if ("until".equals(condition)) {
            emitBoolean(bool, exit, bodyLabel, context);
        } else {
            throw malformed(loop, "Unknown pre-test condition '" + condition + "'");
        }

        context.mark(bodyLabel);
        context.emit("; /* do */");
        emitAlgorithm(body, context);
        context.jump(start);

        context.mark(exit);
        context.emit("; /* exit */");
    }

    private void emitPostTestLoop(Node loop, GenerationContext context) {
        Node body = loop.children.get(2);
        String condition = loop.children.get(4).children.get(0).contents;
        Node bool = loop.children.get(5);
        CodeName start = context.label();
        CodeName exit = context.label();

        context.mark(start);
        context.emit("; /* do */");
        emitAlgorithm(body, context);
        context.emit("/* ", condition, " */");

        if ("while".equals(condition)) {
            emitBoolean(bool, start, exit, context);
        } else if ("until".equals(condition)) {
            emitBoolean(bool, exit, start, context);
        } else {
            throw malformed(loop, "Unknown post-test condition '" + condition + "'");
        }

        context.mark(exit);
        context.emit("; /* exit */");
    }

    private void emitBoolean(Node bool, CodeName trueLabel, CodeName falseLabel,
                             GenerationContext context) {
        require(bool, "BOOL");
        Node first = bool.children.get(0);
        switch (first.contents) {
            case "not" -> emitBoolean(bool.children.get(2), falseLabel, trueLabel, context);
            case "or" -> {
                CodeName next = context.label();
                emitBoolean(bool.children.get(2), trueLabel, next, context);
                context.mark(next);
                context.emit("; /* or */");
                emitBoolean(bool.children.get(3), trueLabel, falseLabel, context);
            }
            case "and" -> {
                CodeName next = context.label();
                emitBoolean(bool.children.get(2), next, falseLabel, context);
                context.mark(next);
                context.emit("; /* and */");
                emitBoolean(bool.children.get(3), trueLabel, falseLabel, context);
            }
            case "eq", "larger", "lesser" ->
                    emitComparison(bool, trueLabel, falseLabel, context, comparison(first.contents));
            default -> throw malformed(bool, "Unsupported BOOL form '" + first.contents + "'");
        }
    }

    private void emitComparison(Node bool, CodeName trueLabel, CodeName falseLabel,
                                GenerationContext context, String operator) {
        CodeName left = context.temporary();
        CodeName right = context.temporary();
        emitTerm(bool.children.get(2), left, context);
        emitTerm(bool.children.get(3), right, context);
        context.emit("if (", left, " ", operator, " ", right, ") goto ", trueLabel, ";");
        context.jump(falseLabel);
    }

    private static String operator(String splOperator) {
        return switch (splOperator) {
            case "add" -> "+";
            case "sub" -> "-";
            case "mul" -> "*";
            case "div" -> "/";
            case "mod" -> "%";
            default -> throw new IllegalArgumentException("Unknown arithmetic operator: " + splOperator);
        };
    }

    private static String comparison(String splOperator) {
        return switch (splOperator) {
            case "eq" -> "==";
            case "larger" -> ">";
            case "lesser" -> "<";
            default -> throw new IllegalArgumentException("Unknown comparison: " + splOperator);
        };
    }

    private static String commentText(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static boolean is(Node node, String contents) {
        return node != null && contents.equals(node.contents);
    }

    private static void require(Node node, String contents) {
        if (!is(node, contents)) {
            throw malformed(node, "Expected " + contents);
        }
    }

    private static IllegalArgumentException malformed(Node node, String message) {
        String location = node == null || node.line < 1 ? "" :
                " at line " + node.line + ", column " + node.col;
        return new IllegalArgumentException("Code generation error" + location + ": " + message);
    }
}
