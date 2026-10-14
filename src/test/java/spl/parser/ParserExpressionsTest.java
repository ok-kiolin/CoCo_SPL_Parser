package spl.parser;
import org.junit.jupiter.api.Test;
import spl.errors.SyntaxException;
import spl.lexer.Lexer;
import spl.tree.Node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserExpressionsTest {

    private static Node parse(String source) {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    private static String shape(Node node) {
        if (node.kind == Node.Kind.LEAF) return node.contents;
        StringBuilder b = new StringBuilder("(" + node.contents);
        for (Node c : node.children) b.append(' ').append(shape(c));
        return b.append(')').toString();
    }

    private static String printedTerm(String term) {
        Node root = parse(": : print ( " + term + " ) ;");
        Node outp = root.children.get(0).children.get(4).children.get(0).children.get(1);
        return shape(outp.children.get(1));
    }

    private static String branchBool(String bool) {
        Node root = parse(": : if " + bool + " then { } else { } ;");
        Node branch = root.children.get(0).children.get(4).children.get(0).children.get(0);
        return shape(branch.children.get(1));
    }

    private static void rejects(String source) {
        assertThrows(SyntaxException.class, () -> parse(source), "should be rejected: " + source);
    }

    @Test void termName()   { assertEquals("(TERM #x)", printedTerm("#x")); }
    @Test void termNumber() { assertEquals("(TERM 0.5)", printedTerm("0.5")); }
    @Test void termCall() {
        assertEquals("(TERM (CALL #f ( (INPUT (TERM 1) (INPUT (TERM #y) (INPUT))) )))", printedTerm("#f ( 1 #y )"));
    }
    @Test void termCallNoArgs() { assertEquals("(TERM (CALL #f ( (INPUT) )))", printedTerm("#f ( )")); }
    @Test void termBinaryOps() {
        for (String op : new String[]{"mod", "add", "sub", "mul", "div"}) {
            assertEquals("(TERM " + op + " ( (TERM 1) (TERM #a) ))", printedTerm(op + " ( 1 #a )"));
        }
    }
    @Test void termNeg() { assertEquals("(TERM neg ( (TERM -3) ))", printedTerm("neg ( -3 )")); }
    @Test void termNested() {
        assertEquals("(TERM add ( (TERM mul ( (TERM 2) (TERM 3) )) (TERM neg ( (TERM (CALL #f ( (INPUT) ))) )) ))",
                printedTerm("add ( mul ( 2 3 ) neg ( #f ( ) ) )"));
    }
    @Test void termInNumFunctionReturn() {
        Node root = parse(": num #f ( #p ) { : : return ( neg ( #p ) ) } : ");
        Node fType = root.children.get(0).children.get(2).children.get(0);
        assertEquals("(TERM neg ( (TERM #p) ))", shape(fType.children.get(9)));
    }

    @Test void boolComparisons() {
        for (String op : new String[]{"eq", "larger", "lesser"}) {
            assertEquals("(BOOL " + op + " ( (TERM #x) (TERM 0) ))", branchBool(op + " ( #x 0 )"));
        }
    }
    @Test void boolNot() {
        assertEquals("(BOOL not ( (BOOL eq ( (TERM 1) (TERM 2) )) ))", branchBool("not ( eq ( 1 2 ) )"));
    }
    @Test void boolAndOr() {
        for (String op : new String[]{"and", "or"}) {
            assertEquals("(BOOL " + op + " ( (BOOL eq ( (TERM 1) (TERM 1) )) (BOOL larger ( (TERM 2) (TERM 1) )) ))",
                    branchBool(op + " ( eq ( 1 1 ) larger ( 2 1 ) )"));
        }
    }

    @Test void branchFullShape() {
        assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : (ALGO (INSTR (BRANCH if (BOOL eq ( (TERM 1) (TERM 1) )) then "
                + "{ (ALGO (INSTR print (OUTP \"yes\")) ; (ALGO)) } else { (ALGO (INSTR nop) ; (ALGO)) })) ; (ALGO))))",
                shape(parse(": : if eq ( 1 1 ) then { print \"yes\" ; } else { nop ; } ;")));
    }
    @Test void branchEmptyBlocksKeepAlgoNodes() {
        assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : (ALGO (INSTR (BRANCH if (BOOL eq ( (TERM 1) (TERM 1) )) then "
                + "{ (ALGO) } else { (ALGO) })) ; (ALGO))))",
                shape(parse(": : if eq ( 1 1 ) then { } else { } ;")));
    }

    @Test void loopConditionFirst() {
        for (String c : new String[]{"while", "until"}) {
            assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : (ALGO (INSTR (LOOP (COND " + c + ") (BOOL lesser ( (TERM 1) (TERM 2) )) do "
                    + "{ (ALGO (INSTR nop) ; (ALGO)) })) ; (ALGO))))",
                    shape(parse(": : " + c + " lesser ( 1 2 ) do { nop ; } ;")));
        }
    }
    @Test void loopBodyFirst() {
        for (String c : new String[]{"while", "until"}) {
            assertEquals("(SPL_PROG (P (V_DECL) : (F_DECL) : (ALGO (INSTR (LOOP do { (ALGO (INSTR nop) ; (ALGO)) } "
                    + "(COND " + c + ") (BOOL eq ( (TERM 1) (TERM 1) )))) ; (ALGO))))",
                    shape(parse(": : do { nop ; } " + c + " eq ( 1 1 ) ;")));
        }
    }
    @Test void nestedControlFlow() {
        String src = ": : if not ( eq ( 1 2 ) ) then { while eq ( 1 1 ) do { do { nop ; } until eq ( 1 1 ) ; } ; } else { } ;";
        String s = shape(parse(src));
        assertTrue(s.contains("(LOOP (COND while)") && s.contains("(COND until)"), s);
    }

    @Test void rejectsMalformedTerms() {
        rejects(": : print ( add ( 2 ) ) ;");
        rejects(": : print ( add ( 1 2 3 ) ) ;"); 
        rejects(": : print ( neg ( ) ) ;");
        rejects(": : print ( neg ( 1 2 ) ) ;");
        rejects(": : print ( add 1 2 ) ;");
        rejects(": : print ( ) ;");
        rejects(": : #x = ;");
        rejects(": : print ( mul ( 1 2 ) ;");
        rejects(": : print ( print ) ;");
    }
    @Test void rejectsMalformedBooleans() {
        rejects(": : if eq ( 1 ) then { } else { } ;");
        rejects(": : if not ( 1 ) then { } else { } ;");
        rejects(": : if and ( eq ( 1 1 ) ) then { } else { } ;");
        rejects(": : if #x then { } else { } ;");
        rejects(": : if eq ( 1 1 then { } else { } ;");
    }
    @Test void rejectsMalformedBranches() {
        rejects(": : if eq ( 1 1 ) then { nop ; } ;");
        rejects(": : if eq ( 1 1 ) { nop ; } else { nop ; } ;");
        rejects(": : if eq ( 1 1 ) then { nop ; else { nop ; } ;");
        rejects(": : if eq ( 1 1 ) then { nop } else { nop ; } ;");
    }
    @Test void rejectsMalformedLoops() {
        rejects(": : do { nop ; } ;");
        rejects(": : do { nop ; } eq ( 1 1 ) ;");
        rejects(": : while eq ( 1 1 ) { nop ; } ;");
        rejects(": : while do { nop ; } ;");
        rejects(": : until eq ( 1 1 ) do nop ; ;");
    }
}