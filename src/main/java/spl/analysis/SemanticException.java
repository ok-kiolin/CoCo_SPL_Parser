package spl.analysis;

import spl.tree.Node;

public final class SemanticException extends RuntimeException 
{
    private final String sourceName;
    private final int nodeId;
    private final int line;
    private final int column;

    public SemanticException(String message, Node node) 
    {
        super(format(message, sourceNameOf(node), node == null ? -1 : node.id, node == null ? -1 : node.line, node == null ? -1 : node.col));
        this.sourceName = sourceNameOf(node);
        this.nodeId = node == null ? -1 : node.id;
        this.line = node == null ? -1 : node.line;
        this.column = node == null ? -1 : node.col;
    }

    public SemanticException(String message, int line, int column) 
    {
        super(format(message, null, -1, line, column));
        this.sourceName = null;
        this.nodeId = -1;
        this.line = line;
        this.column = column;
    }

    public String getSourceName() { return sourceName; }
    public int getNodeId() { return nodeId; }
    public int getLine() { return line; }
    public int getColumn() { return column; }

    private static String sourceNameOf(Node node) 
    {
        if (node == null || node.kind != Node.Kind.LEAF || !node.contents.startsWith("#")) 
            {
            return null;
        }
        return node.contents;
    }

    private static String format(String message, String sourceName, int nodeId, int line, int column) 
    {
        StringBuilder result = new StringBuilder("Scope Error");
        boolean hasDetails = false;
        if (nodeId > 0) 
            {
            result.append(" [node ").append(nodeId);
            hasDetails = true;
        }
        if (sourceName != null) 
            {
            if (hasDetails) 
            {
                result.append(", ");
            }
            else 
            {
                result.append(" [");
            }
            result.append("source name '").append(sourceName).append('\'');
            hasDetails = true;
        }
        if (line > 0 && column > 0) 
            {
            if (hasDetails) 
            {
                result.append(", ");
            }
            else 
            {
                result.append(" [");
            }
            result.append("line ").append(line).append(", column ").append(column);
            hasDetails = true;
        }
        if (!hasDetails) 
        {
            return result.append(": ").append(message).toString();
        }
        return result.append("]: ").append(message).toString();
    }
}