package spl.analysis;

public final class SemanticException extends RuntimeException 
{
    private final int line;
    private final int column;

    public SemanticException(String message, int line, int column) 
    {
        super(format(message, line, column));
        this.line = line;
        this.column = column;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }

    private static String format(String message, int line, int column) 
    {
        if (line < 1 || column < 1) 
        {
            return "Scope Error: " + message;
        }
        return "Scope Error [line " + line + ", column " + column + "]: " + message;
    }
}