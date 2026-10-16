package spl.types;

import spl.tree.Node;

public class TypeException extends RuntimeException {
    private final int line;
    private final int column;

    public TypeException(String message, Node where) {
        super(message);
        Node leaf = firstLeaf(where);
        
        this.line = leaf == null ? -1 : leaf.line;
        this.column = leaf == null ? -1 : leaf.col;
    }

    public TypeException(String message) {
        super(message);

        this.line = -1;
        this.column = -1;
    }

    public static Node firstLeaf(Node n) {
        if(n==null){
            return null;
        }

        if(n.kind == Node.Kind.LEAF) {
            return n;
        }

        for(Node c: n.children) {
            Node found = firstLeaf(c);

            if(found != null) {
                return found;
            }
        }

            return null;
    }
    @Override
    public String getMessage() {
        if (line < 0) return "Type Error:%n%s".formatted(super.getMessage());
        return "Type Error [line %d, column %d]:%n%s".formatted(line, column, super.getMessage());
    }
}
