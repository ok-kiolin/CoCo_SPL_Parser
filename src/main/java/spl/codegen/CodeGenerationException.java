package spl.codegen;

import spl.tree.Node;

public final class CodeGenerationException extends RuntimeException {
    public CodeGenerationException(String message) {
        super(message);
    }

    public CodeGenerationException(String message, Node node) {
        super(message + " [node " + node.id + ": " + node.contents + "]");
    }
}
