package spl.xml;

import spl.tree.Node;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XMLWriter {

    private XMLWriter() {}

    public static void write(Node root, Path file) throws IOException {
        try (OutputStream output = Files.newOutputStream(file);
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            write(root, writer);
        }
    }

    public static void write(Node root, Writer writer) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null.");
        }
        if (root.kind != Node.Kind.ROOT) {
            throw new IllegalArgumentException("The XML tree must start with a ROOT node.");
        }

        root.assignIds();

        try {
            XMLOutputFactory factory = XMLOutputFactory.newFactory();
            XMLStreamWriter xml = factory.createXMLStreamWriter(writer);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            writeNode(xml, root, 0);
            xml.writeCharacters("\n");
            xml.writeEndDocument();
            xml.flush();
        } 
        catch (XMLStreamException e) {
            throw new IOException("Could not write tree.xml.", e);
        }
    }

    private static void writeNode(XMLStreamWriter xml, Node node, int depth)
            throws XMLStreamException {

        indent(xml, depth);
        if (node.children.isEmpty()) {
            xml.writeEmptyElement(tagFor(node));
        } else {
            xml.writeStartElement(tagFor(node));
        }

        xml.writeAttribute("id", Integer.toString(node.id));
        xml.writeAttribute("contents", node.contents);

        if (node.kind != Node.Kind.LEAF) {
            if (node.kind == Node.Kind.INNER) {
                if (node.parent == null) {
                    throw new IllegalArgumentException("An inner node must have a parent: " + node.contents);
                }
                xml.writeAttribute("parent", Integer.toString(node.parent.id));
            }
            xml.writeAttribute("children", node.childIdList());
        } else {
            if (node.parent == null) {
                throw new IllegalArgumentException("A leaf node must have a parent: " + node.contents);
            }
            xml.writeAttribute("parent", Integer.toString(node.parent.id));
        }

        if (node.children.isEmpty()) {
            return;
        }

        xml.writeCharacters("\n");
        for (Node child : node.children) {
            writeNode(xml, child, depth + 1);
            xml.writeCharacters("\n");
        }
        indent(xml, depth);
        xml.writeEndElement();
    }

    private static String tagFor(Node node) {
        switch (node.kind) {
            case ROOT:
                return "root";
            case INNER:
                return "inner";
            case LEAF:
                return "leaf";
            default:
                throw new IllegalArgumentException("Unknown node kind.");
        }
    }

    private static void indent(XMLStreamWriter xml, int depth) throws XMLStreamException {
        for (int i = 0; i < depth; i++) {
            xml.writeCharacters("  ");
        }
    }
}
