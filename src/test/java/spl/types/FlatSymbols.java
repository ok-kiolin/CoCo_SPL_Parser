package spl.types;

import java.util.HashMap;
import java.util.Map;
import spl.tree.Node;

public final class FlatSymbols implements SymbolLookup {
    private final Map<String, Node> firstDecl = new HashMap<>();

    public FlatSymbols(Node root) { collect(root); }

    private void collect(Node n) {
        if (n.kind == Node.Kind.INNER) {
            if (n.contents.equals("V_DECL") && !n.children.isEmpty()) {
                firstDecl.putIfAbsent(n.children.get(0).contents, n.children.get(0));
            } else if (n.contents.equals("F_TYPE")) {
                firstDecl.putIfAbsent(n.children.get(1).contents, n.children.get(1));
            }
        }
        for (Node c : n.children) collect(c);
    }

    @Override public Node declarationOf(Node nameLeaf) { return firstDecl.get(nameLeaf.contents); }
}