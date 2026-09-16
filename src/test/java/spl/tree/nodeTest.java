package spl.tree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    @Test void assignsPreorderIdsFromOne() {
        //        root
        //        /  \
        //     inner  leaf
        //       |
        //      leaf
        Node root = Node.root("SPL_PROG");
        Node inner = root.add(Node.inner("P"));
        Node l1 = inner.add(Node.leaf("#x", 1, 1));
        Node l2 = root.add(Node.leaf(":", 1, 4));
        root.assignIds();
        assertEquals(1, root.id);
        assertEquals(2, inner.id);
        assertEquals(3, l1.id);
        assertEquals(4, l2.id);    
    }

    @Test void idsAreUniqueAndDeterministic() {
        Node a = build(); a.assignIds();
        Node b = build(); b.assignIds();
        assertEquals(dumpIds(a), dumpIds(b));
    }

    @Test void linksParentOnAdd() {
        Node root = Node.root("SPL_PROG");
        Node child = root.add(Node.inner("P"));
        assertSame(root, child.parent);
        assertNull(root.parent);
    }

    @Test void childIdListMatchesChildrenInOrder() {
        Node root = Node.root("SPL_PROG");
        root.add(Node.inner("P"));
        root.add(Node.leaf("$", 1, 9));
        root.assignIds();
        assertEquals("2 3", root.childIdList());
    }

    @Test void emptyInnerHasEmptyChildList() {
        Node root = Node.root("SPL_PROG");
        Node empty = root.add(Node.inner("V_DECL"));
        root.assignIds();
        assertEquals("", empty.childIdList());
    }

    private Node build() {
        Node r = Node.root("SPL_PROG");
        Node p = r.add(Node.inner("P"));
        p.add(Node.leaf("#x", 1, 1));
        p.add(Node.inner("V_DECL"));
        return r;
    }
    private String dumpIds(Node n) {
        StringBuilder sb = new StringBuilder().append(n.id).append(' ');
        for (Node c : n.children) sb.append(dumpIds(c));
        return sb.toString();
    }
}