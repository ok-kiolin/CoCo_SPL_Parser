package spl.tree;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class Node {

    public enum Kind {
        ROOT, INNER, LEAF 
    }

    public final Kind kind;
    public final String contents;
    public Node parent;
    public final List<Node> children = new ArrayList<>();
    public int id = -1;
    public final int line, col;    

    private Node(Kind kind, String contents, int line, int col) {
        this.kind = kind; this.contents = contents; this.line = line; this.col = col;
    }

    public static Node root(String startSymbol) {
        return new Node(Kind.ROOT, startSymbol, -1, -1); 
    }

    public static Node inner(String nonTerminal) {
        return new Node(Kind.INNER, nonTerminal, -1, -1); 
    }

    public static Node leaf(String tokenText, int line, int col) {
        return new Node(Kind.LEAF, tokenText, line, col);
    }

    public Node add(Node child) {
        child.parent = this; children.add(child); return child; 
    }

    public void assignIds() {
        int[] next = {1};
        preorder(this, next);
    }
    private static void preorder(Node n, int[] next) {
        n.id = next[0]++;
        for (Node c : n.children) preorder(c, next);
    }

    public String childIdList() {
        StringJoiner sj = new StringJoiner(" ");
        for (Node c : children) sj.add(Integer.toString(c.id));
        return sj.toString();
    }
}