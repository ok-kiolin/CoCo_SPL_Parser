package spl.xml;

import spl.tree.Node;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XMLReader {

    private XMLReader() {}

    public static Node read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return read(input);
        }
    }

    public static Node read(InputStream input) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(input);
            Element rootElement = document.getDocumentElement();

            if (!"root".equals(rootElement.getTagName())) {
                throw new IOException("tree.xml must have a single <root> element.");
            }

            Node root = readRoot(rootElement);
            validateTree(root);
            return root;
        } 
        catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Could not read tree.xml.", e);
        }
    }

    private static Node readRoot(Element element) throws IOException {
        requireAttributes(element, "id", "contents", "children");

        Node root = Node.root(element.getAttribute("contents"));
        root.id = parseId(element);

        String children = element.getAttribute("children").trim();
        NodeList childNodes = element.getChildNodes();
        int actualChildren = 0;

        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                Element child = (Element) childNodes.item(i);
                Node childNode = readNode(child);
                if (!child.hasAttribute("parent")) {
                    throw new IOException("Missing 'parent' attribute on <" + child.getTagName() + ">");
                }
                if (!Integer.toString(root.id).equals(child.getAttribute("parent"))) {
                    throw new IOException("Incorrect parent attribute for node " + childNode.id + ".");
                }
                root.add(childNode);
                actualChildren++;
            }
        }

        validateChildrenAttribute(element, root);

        if (countIds(children) != actualChildren) {
            throw new IOException("Root children attribute does not match nested children.");
        }

        return root;
    }

    private static Node readNode(Element element) throws IOException {
        String tag = element.getTagName();

        if ("leaf".equals(tag)) {
            requireAttributes(element, "id", "contents", "parent");
            if (hasElementChild(element)) {
                throw new IOException("A <leaf> cannot contain child elements.");
            }
            Node leaf = Node.leaf(element.getAttribute("contents"), -1, -1);
            leaf.id = parseId(element);
            return leaf;
        }

        if ("inner".equals(tag)) {
            requireAttributes(element, "id", "contents", "parent", "children");
            Node inner = Node.inner(element.getAttribute("contents"));
            inner.id = parseId(element);
            readChildren(element, inner);
            return inner;
        }

        throw new IOException("Unexpected XML tag: <" + tag + ">");
    }

    private static void readChildren(Element element, Node parent) throws IOException {
        NodeList childNodes = element.getChildNodes();
        int actualChildren = 0;

        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                Element child = (Element) childNodes.item(i);
                Node childNode = readNode(child);
                if (!child.hasAttribute("parent")) {
                    throw new IOException("Missing 'parent' attribute on <" + child.getTagName() + ">");
                }
                if (!Integer.toString(parent.id).equals(child.getAttribute("parent"))) {
                    throw new IOException("Incorrect parent attribute for node " + childNode.id + ".");
                }
                parent.add(childNode);
                actualChildren++;
            }
        }

        String children = element.getAttribute("children").trim();
        if (countIds(children) != actualChildren) {
            throw new IOException("Children attribute does not match nested children for node " + parent.id + ".");
        }

        validateChildrenAttribute(element, parent);
    }

    private static void validateChildrenAttribute(Element element, Node parent) throws IOException {
        String expected = element.getAttribute("children").trim();
        String actual = parent.childIdList();
        if (!expected.equals(actual)) {
            throw new IOException("Children attribute does not match nested children for node " + parent.id + ". Expected '" + actual + "' but found '" + expected + "'.");
        }

    }

    private static void validateTree(Node root) throws IOException {
        if (root.id != 1) {
            throw new IOException("Root node ID must be 1.");
        }

        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        validateNode(root, null, ids);

        for (int i = 1; i <= ids.size(); i++) {
            if (!ids.contains(i)) {
                throw new IOException("Node IDs must be 1..N with no gaps.");
            }
        }
    }

    private static void validateNode(Node node, Node expectedParent, java.util.Set<Integer> ids) throws IOException {
        if (!ids.add(node.id)) {
            throw new IOException("Duplicate node ID: " + node.id);
        }

        if (node.parent != expectedParent) {
            throw new IOException("Incorrect parent link for node " + node.id + ".");
        }

        if (node.kind == Node.Kind.ROOT && expectedParent != null) {
            throw new IOException("A root node cannot have a parent.");
        }

        for (Node child : node.children) {
            validateNode(child, node, ids);
        }
    }

    private static int parseId(Element element) throws IOException {
        try {
            int id = Integer.parseInt(element.getAttribute("id"));
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid node ID: " + element.getAttribute("id"));
        }
    }

    private static void requireAttributes(Element element, String... names) throws IOException {
        for (String name : names) {
            if (!element.hasAttribute(name)) {
                throw new IOException("Missing '" + name + "' attribute on <" + element.getTagName() + ">");
            }
        }
    }

    private static boolean hasElementChild(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private static int countIds(String children) throws IOException {
        if (children.isEmpty()) {
            return 0;
        }
        String[] ids = children.split("\\s+");
        for (String id : ids) {
            try {
                if (Integer.parseInt(id) < 1) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid child ID: " + id);
            }
        }
        return ids.length;
    }
}
