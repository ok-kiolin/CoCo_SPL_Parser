package spl.analysis;

import org.junit.jupiter.api.Test;
import spl.lexer.Lexer;
import spl.parser.Parser;
import spl.tree.Node;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymbolTableTest 
{
    private static Node parse(String source) 
    {
        return new Parser(new Lexer().tokenize(source)).parse();
    }

    @Test
    void bindsOccurrencesAndStoresOrderedFunctionDetails() 
    {
        Node root = parse("#x : num #f ( #a ) { #b : : #b = add ( #a #x ) ; return ( #b ) } : #x = #f ( #x ) ;");
        SymbolTable table = SymbolTable.analyze(root);
        List<Node> names = nameLeaves(root);

        SymbolTable.Symbol globalX = table.getGlobalScope().getDeclaredVariable("#x");
        SymbolTable.Symbol function = table.getGlobalScope().getFunctionDeclaredHere("#f");
        assertNotNull(globalX);
        assertEquals(SymbolTable.Kind.VARIABLE, globalX.getKind());
        assertEquals(SymbolTable.Kind.FUNCTION, function.getKind());
        assertEquals(SymbolTable.FunctionKind.NUMERIC_FUNCTION, function.getFunctionKind());
        assertEquals(1, function.getParameters().size());
        assertEquals("#a", function.getParameters().get(0).getSourceName());
        assertEquals(1, function.getLocals().size());
        assertEquals("#b", function.getLocals().get(0).getSourceName());
        assertNotNull(function.getBody());
        assertNotNull(function.getReturnTerm());

        assertEquals(globalX, table.getBinding(names.get(0)));
        assertEquals(function, table.getBinding(names.get(1)));
        assertEquals(function.getParameters().get(0), table.getBinding(names.get(2)));
        assertEquals(function.getLocals().get(0), table.getBinding(names.get(3)));
        assertEquals(function.getLocals().get(0), table.getBinding(names.get(4)));
        assertEquals(function.getParameters().get(0), table.getBinding(names.get(5)));
        assertEquals(globalX, table.getBinding(names.get(6)));
        assertEquals(function.getLocals().get(0), table.getBinding(names.get(7)));
        assertEquals(globalX, table.getBinding(names.get(8)));
        assertEquals(function, table.getBinding(names.get(9)));
        assertEquals(globalX, table.getBinding(names.get(10)));
        assertEquals(function.getParameters().get(0).getInternalName(), table.getInternalName(names.get(5)));
        assertEquals(globalX.getInternalName(), table.getInternalName(names.get(6)));
        assertNotEquals(globalX.getInternalName(), function.getParameters().get(0).getInternalName());
        assertEquals(1, root.id);
    }

    @Test
    void nearestDeclarationWinsAndShadowingIsAllowed() 
    {
        Node root = parse("#x : void #f ( ) { #x : : #x = 2 ; return } : print ( #x ) ;");
        SymbolTable table = SymbolTable.analyze(root);
        List<Node> names = nameLeaves(root);
        SymbolTable.Symbol globalX = table.getGlobalScope().getDeclaredVariable("#x");
        SymbolTable.Symbol function = table.getGlobalScope().getFunctionDeclaredHere("#f");
        SymbolTable.Symbol localX = function.getLocals().get(0);

        assertNotEquals(globalX.getInternalName(), localX.getInternalName());
        assertEquals(localX, table.getBinding(names.get(3)));
        assertEquals(globalX, table.getBinding(names.get(4))); 
    }

    @Test
    void parameterAndLocalCannotHaveSameName() 
    {
        Node root = parse(": num #f ( #x ) { #x : : return ( #x ) } :");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Duplicate parameter/local variable declaration '#x'"));
    }

    @Test
    void duplicateVariablesInOneScopeAreRejected() 
    {
        Node root = parse("#x #x : :");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Duplicate variable declaration '#x'"));
    }

    @Test
    void undeclaredVariableIsRejected() 
    {
        Node root = parse(": : print ( #missing ) ;");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Undeclared variable '#missing'"));
    }

    @Test
    void callsCanOnlyUseFunctionsDeclaredInCurrentP() 
    {
        Node root = parse(": void #outer ( ) { : void #inner ( ) { : : return } : #inner ( ) ; return } : #outer ( ) ; #inner ( ) ;");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Forbidden function call '#inner'"));
    }

    @Test
    void ancestorFunctionCannotBeCalledFromNestedP() 
    {
        Node root = parse(": void #outer ( ) { : : #top ( ) ; return } void #top ( ) { : : return } : #outer ( ) ;");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Forbidden function call '#top'"));
    }

    @Test
    void siblingFunctionCannotAccessAnotherFunctionsLocal() 
    {
        Node root = parse(": void #first ( ) { #secret : : return } void #second ( ) { : : print ( #secret ) ; return } : #first ( ) ;");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Undeclared variable '#secret'"));
    }

    @Test
    void duplicateFunctionDeclarationsAreRejected() 
    {
        Node root = parse(": void #f ( ) { : : return } void #f ( ) { : : return } :");
        SemanticException error = assertThrows(SemanticException.class, () -> SymbolTable.analyze(root));
        assertTrue(error.getMessage().contains("Duplicate function declaration '#f'"));
    }

    @Test
    void variableAndFunctionKindsAreDistinct() 
    {
        Node root = parse("#same : num #same ( ) { : : return ( 1 ) } : print ( #same ) ;");
        SymbolTable table = SymbolTable.analyze(root);
        List<Node> names = nameLeaves(root);
        SymbolTable.Symbol variable = table.getGlobalScope().getDeclaredVariable("#same");
        SymbolTable.Symbol function = table.getGlobalScope().getFunctionDeclaredHere("#same");
        assertNotNull(variable);
        assertNotNull(function);
        assertNotEquals(variable.getInternalName(), function.getInternalName());
        assertEquals(variable, table.getBinding(names.get(2)));
    }

    private static List<Node> nameLeaves(Node root) 
    {
        List<Node> names = new ArrayList<>();
        collectNameLeaves(root, names);
        return names;
    }

    private static void collectNameLeaves(Node node, List<Node> result) 
    {
        if (node.kind == Node.Kind.LEAF && node.contents.startsWith("#")) result.add(node);
        for (Node child : node.children) collectNameLeaves(child, result);
    }
}