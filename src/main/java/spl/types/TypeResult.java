package spl.types;

import java.util.Map;
import spl.tree.Node;

public final class TypeResult {
    private final Map<Integer, Type> types;
    private final NumericMode mode;

    TypeResult(Map<Integer, Type> types, NumericMode mode) {
        this.types = types;
        this.mode = mode;
    }

    public Type typeOf(Node n) {
        return types.getOrDefault(n.id, Type.UNKNOWN); 
    }

    public Type typeOf(int nodeId) { 
        return types.getOrDefault(nodeId, Type.UNKNOWN); 
    }


    public Map<Integer, Type> types() { 
        return types; 
    }


    public NumericMode mode() {
         return mode; 
    }
    
    
}
