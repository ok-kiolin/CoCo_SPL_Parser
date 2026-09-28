package spl.errors;

public class SyntaxException extends RuntimeException {

    private final int line;
    private final int column;
    private final String hint;

    public SyntaxException(String message, int line, int column, String hint) {
        super(message);
        this.line = line;
        this.column = column;
        this.hint = hint;
    }

    @Override
    public String getMessage() {
        return "Syntax Error [line %d, column %d]:%n%s%nHint: %s"
            .formatted(line, column, super.getMessage(), hint);
    }
}