package spl.xml;

import org.junit.jupiter.api.Test;
import spl.tree.Node;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XMLRoundTripTest {

    @Test
    void writesExpectedExample() throws Exception {
        Node root = Node.root("SPL_PROG");
        Node p = root.add(Node.inner("P"));

        Node vDecl = p.add(Node.inner("V_DECL"));
        vDecl.add(Node.leaf("#x", 1, 1));
        vDecl.add(Node.inner("V_DECL"));

        p.add(Node.leaf(":", 1, 4));
        p.add(Node.inner("F_DECL"));
        p.add(Node.leaf(":", 1, 6));

        Node algo = p.add(Node.inner("ALGO"));
        Node instr = algo.add(Node.inner("INSTR"));
        instr.add(Node.leaf("print", 1, 9));
        Node outp = instr.add(Node.inner("OUTP"));
        outp.add(Node.leaf("\"hi\"", 1, 15));
        algo.add(Node.leaf(";", 1, 20));
        algo.add(Node.inner("ALGO"));

        Path first = Files.createTempFile("tree", ".xml");
        Path second = Files.createTempFile("tree-roundtrip", ".xml");

        XMLWriter.write(root, first);
        Node loaded = XMLReader.read(first);
        XMLWriter.write(loaded, second);

        assertEquals(Files.readString(first), Files.readString(second));
        assertEquals(1, loaded.id);
        assertEquals("SPL_PROG", loaded.contents);
        assertEquals("\"hi\"", loaded.children.get(0).children.get(4).children.get(0).children.get(1).children.get(0).contents);

        Files.deleteIfExists(first);
        Files.deleteIfExists(second);
    }
}
