package spl.codegen;

import java.util.Objects;

public record CodeName(String identifier, Kind kind) {
    public enum Kind { VARIABLE, LABEL }

    public CodeName {
        Objects.requireNonNull(kind, "kind");
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid target identifier: " + identifier);
        }
    }
}
