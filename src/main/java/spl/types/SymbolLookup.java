package spl.types;

import spl.tree.Node;

public interface SymbolLookup {

    Node declarationOf(Node nameLeaf);
    
}
