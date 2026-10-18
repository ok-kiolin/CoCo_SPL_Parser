package spl.codegen;
import spl.tree.Node;

public interface AlgorithmEmitter {
    void emitAlgorithm(Node algo, GenerationContext context);
    void emitTerm(Node term, CodeName result, GenerationContext context);
}
