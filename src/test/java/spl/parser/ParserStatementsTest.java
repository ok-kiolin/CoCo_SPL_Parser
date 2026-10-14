package spl.parser;

import org.junit.jupiter.api.Test;
import spl.errors.SyntaxException;
import spl.lexer.Lexer;
import spl.tree.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserStatementsTest {
    private static Node parse(String source) {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    private static String shape(Node node) {
        if (node.kind == Node.Kind.LEAF) return node.contents;
        StringBuilder result = new StringBuilder("(" + node.contents);
        for (Node child : node.children) result.append(' ').append(shape(child));
        return result.append(')').toString();
    }

    @Test
    void printStringMatchesDocumentedGoldenTree() {
        Node root = parse("#x : : print \"hi\" ;");
        assertEquals("(SPL_PROG (P (V_DECL #x (V_DECL)) : (F_DECL) : "
                + "(ALGO (INSTR print (OUTP \"hi\")) ; (ALGO))))", shape(root));
        root.assignIds();
        Node firstAlgo = root.children.get(0).children.get(4);
        assertEquals(9, firstAlgo.id);
        assertEquals(13, firstAlgo.children.get(0).children.get(1).children.get(0).id);
        assertEquals(0, firstAlgo.children.get(2).children.size());
    }

    @Test
    void nopAndCommentAreSeparateInstructions() {
        assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : "
                + "(ALGO (INSTR nop) ; (ALGO (INSTR comment \"note\") ; (ALGO)))))",
                shape(parse(": : nop ; comment \"note\" ;")));
    }

    @Test
    void callWithoutArgumentsIncludesEmptyInputNode() {
        assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : "
                + "(ALGO (INSTR (CALL #f ( (INPUT) ))) ; (ALGO))))",
                shape(parse(": : #f ( ) ;")));
    }

    @Test
    void emptyAlgorithmKeepsItsNode() {
        assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : (ALGO)))",
                shape(parse(": :")));
    }

    @Test
    void rejectsBadNameDispatchAndMissingSemicolon() {
        assertThrows(SyntaxException.class, () -> parse(": : #x nop ;"));
        assertThrows(SyntaxException.class, () -> parse(": : nop"));
        assertThrows(SyntaxException.class, () -> parse(": : #f ( ;"));
    }
}
