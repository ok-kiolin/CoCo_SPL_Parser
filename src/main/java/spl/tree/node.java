package spl.tree;
import java.util.ArrayList;
import java.util.List;

public class Node {

    private static int nextId = 1;

    private final int id;
    private final String contents;
    private final boolean isLeaf;
    private Node parent;
    private final List<Node> children = new ArrayList<>();

    private Node(String contents, boolean isLeaf) {
        this.id = nextId++;
        this.contents = contents;
        this.isLeaf = isLeaf;
    }

    public static Node nonTerminal(String grammarSymbol) {
        return new Node(grammarSymbol, false);
    }

    public static Node leaf(String tokenText) {
        return new Node(tokenText, true);
    }

    public void addChild(Node child) {
        children.add(child);
        child.parent = this;
    }

    public int id() { return id; }
    public String contents() { return contents; }
    public boolean isLeaf() { return isLeaf; }
    public Node parent() { return parent; }
    public List<Node> children() { return children; }
    public static void resetIdCounter() { nextId = 1; }
}