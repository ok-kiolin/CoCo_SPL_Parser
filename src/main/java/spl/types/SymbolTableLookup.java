package spl.types;

import spl.analysis.SymbolTable;
import spl.tree.Node;

public final class SymbolTableLookup implements SymbolLookup {
    private final SymbolTable table;

    public SymbolTableLookup(SymbolTable table) {
        this.table = table;
    }

    @Override public Node declarationOf(Node nameLeaf) {
        SymbolTable.Symbol symbol = table.getBinding(nameLeaf);

        return symbol == null ? null : symbol.getNameNode();
    }
}
