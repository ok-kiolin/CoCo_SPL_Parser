package spl.analysis;

import spl.tree.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SymbolTable 
{

    public enum Kind 
    { 
        VARIABLE, PARAMETER, FUNCTION 
    }
    public enum FunctionKind 
    { 
        PROCEDURE, NUMERIC_FUNCTION 
    }

    public static final class Symbol 
    {
        private final Kind kind;
        private final String sourceName;
        private final String internalName;
        private final Node declarationNode;
        private final Node nameNode;
        private final FunctionKind functionKind;
        private final List<Symbol> parameters = new ArrayList<>();
        private final List<Symbol> locals = new ArrayList<>();
        private Node body;
        private Node returnTerm;
        private Scope functionScope;

        private Symbol(Kind kind, String sourceName, String internalName, Node declarationNode, Node nameNode, FunctionKind functionKind) 
        {
            this.kind = kind;
            this.sourceName = sourceName;
            this.internalName = internalName;
            this.declarationNode = declarationNode;
            this.nameNode = nameNode;
            this.functionKind = functionKind;
        }

        public Kind getKind() { return kind; }

        public String getSourceName() { return sourceName; }
        public String getInternalName() { return internalName; }

        public Node getDeclarationNode() { return declarationNode; }
        public Node getNameNode() { return nameNode; }

        public FunctionKind getFunctionKind() { return functionKind; }

        public boolean isFunction() { return kind == Kind.FUNCTION; }
        public boolean isProcedure() { return isFunction() && functionKind == FunctionKind.PROCEDURE; }
        public boolean isNumericFunction() { return isFunction() && functionKind == FunctionKind.NUMERIC_FUNCTION; }

        public List<Symbol> getParameters() { return Collections.unmodifiableList(parameters); }
        public List<Symbol> getLocals() { return Collections.unmodifiableList(locals); }

        public Node getBody() { return body; }
        public Node getReturnTerm() { return returnTerm; }

        public Scope getFunctionScope() { return functionScope; }

        @Override public String toString() 
        {
            return kind + " " + sourceName + " -> " + internalName;
        }
    }

    public static final class Scope 
    {
        private final int id;
        private final Scope parent;
        private final Symbol ownerFunction;
        private final Node programNode;
        private final Map<String, Symbol> variablesByName = new LinkedHashMap<>();
        private final Map<String, Symbol> functionsByName = new LinkedHashMap<>();
        private final List<Symbol> variables = new ArrayList<>();
        private final List<Symbol> localDeclarations = new ArrayList<>();
        private final List<Symbol> functions = new ArrayList<>();
        private final List<Scope> children = new ArrayList<>();

        private Scope(int id, Scope parent, Symbol ownerFunction, Node programNode) 
        {
            this.id = id;
            this.parent = parent;
            this.ownerFunction = ownerFunction;
            this.programNode = programNode;
            if (parent != null) 
            {
                parent.children.add(this);
            }
        }

        public int getId() { return id; }
        public Scope getParent() { return parent; }
        public Symbol getOwnerFunction() { return ownerFunction; }
        public Node getProgramNode() { return programNode; }

        public List<Symbol> getVariables() { return Collections.unmodifiableList(variables); }
        public List<Symbol> getLocalDeclarations() { return Collections.unmodifiableList(localDeclarations); }
        public List<Symbol> getFunctions() { return Collections.unmodifiableList(functions); }
        public List<Scope> getChildren() { return Collections.unmodifiableList(children); }

        public Symbol getDeclaredVariable(String name) { return variablesByName.get(name); }

        public Symbol lookupVariable(String name) 
        {
            for (Scope s = this; s != null; s = s.parent) 
                {
                Symbol symbol = s.variablesByName.get(name);
                if (symbol != null)
                { 
                    return symbol;
                }
            }
            return null;
        }

        public Symbol getFunctionDeclaredHere(String name) { return functionsByName.get(name); }
    }

    private final Node root;
    private final Scope globalScope;
    private final List<Scope> scopes = new ArrayList<>();
    private final Map<Node, Symbol> bindings = new IdentityHashMap<>();
    private final Map<Node, Scope> programScopes = new IdentityHashMap<>();

    private int nextInternalName = 1;
    private int nextScopeId = 1;

    private SymbolTable(Node root) 
    {
        this.root = root;
        this.globalScope = newScope(null, null, root.children.get(0));
    }

    public static SymbolTable analyze(Node root) 
    {
        Objects.requireNonNull(root, "root");
        if (root.kind != Node.Kind.ROOT || !"SPL_PROG".equals(root.contents) || root.children.size() != 1 || !"P".equals(root.children.get(0).contents)) 
        {
            throw new IllegalArgumentException("Expected a parser-produced SPL_PROG tree.");
        }
        root.assignIds();
        SymbolTable table = new SymbolTable(root);
        table.prepareP(root.children.get(0), table.globalScope);
        table.resolveP(root.children.get(0), table.globalScope);
        return table;
    }

    public Node getRoot() { return root; }
    public Scope getGlobalScope() { return globalScope; }
    public List<Scope> getScopes() { return Collections.unmodifiableList(scopes); }

    public Scope getScopeFor(Node p) { return programScopes.get(p); }
    public Map<Node, Symbol> getBindings() { return Collections.unmodifiableMap(bindings); }
    public Symbol getBinding(Node nameOccurrence) { return bindings.get(nameOccurrence); }

    public String getInternalName(Node nameOccurrence) 
    {
        Symbol symbol = bindings.get(nameOccurrence);
        return symbol == null ? null : symbol.internalName;
    }

    public Symbol requireBinding(Node nameOccurrence) 
    {
        Symbol symbol = bindings.get(nameOccurrence);
        if (symbol == null) 
            {
            throw new IllegalArgumentException("This syntax-tree node has no name binding.");
        }
        return symbol;
    }

    private Scope newScope(Scope parent, Symbol ownerFunction, Node programNode) 
    {
        Scope scope = new Scope(nextScopeId++, parent, ownerFunction, programNode);
        scopes.add(scope);
        programScopes.put(programNode, scope);
        return scope;
    }

    private void prepareP(Node p, Scope scope) 
    {
        if (scope.programNode != p) programScopes.put(p, scope);
        Node variableDeclarations = p.children.get(0);
        declareVariables(variableDeclarations, scope, false);

        List<Node> functionNodes = functionTypeNodes(p.children.get(2));
        for (Node functionNode : functionNodes) 
            {
            Node nameNode = functionNode.children.get(1);
            String name = nameNode.contents;
            if (scope.functionsByName.containsKey(name)) 
            {
                throw error("Duplicate function declaration '" + name + "' in the same P scope.", nameNode);
            }
            FunctionKind functionKind = "void".equals(functionNode.children.get(0).contents) ? FunctionKind.PROCEDURE : FunctionKind.NUMERIC_FUNCTION;
            Symbol function = new Symbol(Kind.FUNCTION, name, freshInternalName(), nameNode, nameNode, functionKind);
            function.body = functionNode.children.get(6);
            if (functionKind == FunctionKind.NUMERIC_FUNCTION) 
            {
                function.returnTerm = functionNode.children.get(9);
            }
            scope.functionsByName.put(name, function);
            scope.functions.add(function);
            bindings.put(nameNode, function);
        }

        for (int i = 0; i < functionNodes.size(); i++) 
        {
            Node functionNode = functionNodes.get(i);
            Symbol function = scope.functions.get(i);
            Scope functionScope = newScope(scope, function, function.body);
            function.functionScope = functionScope;

            Node parameters = functionNode.children.get(3);
            declareVariables(parameters, functionScope, true);
            function.parameters.addAll(functionScope.localDeclarations);
            functionScope.localDeclarations.clear();

            prepareP(function.body, functionScope);
            function.locals.addAll(functionScope.localDeclarations);
        }
    }

    private void declareVariables(Node vDecl, Scope scope, boolean parameters) 
    {
        if (vDecl.children.isEmpty()) return;
        Node nameNode = vDecl.children.get(0);
        String name = nameNode.contents;
        Symbol previous = scope.variablesByName.get(name);
        if (previous != null) 
        {
            String what = (parameters || previous.kind == Kind.PARAMETER) ? "parameter/local variable" : "variable";
            throw error("Duplicate " + what + " declaration '" + name  + "'. Parameters and locals in the same function share a scope.", nameNode);
        }
        Kind kind = parameters ? Kind.PARAMETER : Kind.VARIABLE;
        Symbol symbol = new Symbol(kind, name, freshInternalName(), nameNode, nameNode, null);
        scope.variablesByName.put(name, symbol);
        scope.variables.add(symbol);
        scope.localDeclarations.add(symbol);
        bindings.put(nameNode, symbol);
        if (vDecl.children.size() > 1) 
        {
            declareVariables(vDecl.children.get(1), scope, parameters);
        }
    }

    private List<Node> functionTypeNodes(Node fDecl) 
    {
        List<Node> result = new ArrayList<>();
        Node current = fDecl;
        while (!current.children.isEmpty()) 
        {
            result.add(current.children.get(0));
            current = current.children.get(1);
        }
        return result;
    }

    private void resolveP(Node p, Scope scope) 
    {
        resolveNode(p.children.get(4), scope);
        for (Symbol function : scope.functions) 
        {
            resolveP(function.body, function.functionScope);
            if (function.returnTerm != null) 
            {
                resolveNode(function.returnTerm, function.functionScope);
            }
        }
    }

    private void resolveNode(Node node, Scope scope) 
    {
        switch (node.contents) 
        {
            case "ASSIGN" -> 
            {
                Node target = node.children.get(0);
                bindVariableUse(target, scope);
                resolveNode(node.children.get(2), scope);
            }
            case "CALL" -> 
            {
                Node nameNode = node.children.get(0);
                Symbol function = scope.getFunctionDeclaredHere(nameNode.contents);
                if (function == null) 
                {
                    throw error("Forbidden function call '" + nameNode.contents + "': calls may use only functions declared in this P node's F_DECL.", nameNode);
                }
                bindings.put(nameNode, function);
                resolveNode(node.children.get(2), scope);
            }
            case "TERM" -> 
            {
                if (node.children.size() == 1 && node.children.get(0).kind == Node.Kind.LEAF) 
                {
                    Node possibleName = node.children.get(0);
                    if (possibleName.contents.startsWith("#")) 
                    {
                        bindVariableUse(possibleName, scope);
                    }
                } 
                else 
                    {
                    for (Node child : node.children) 
                    {
                        if (child.kind != Node.Kind.LEAF) 
                        {
                            resolveNode(child, scope);
                        }
                    }
                }
            }
            case "INPUT" -> 
            {
                for (Node child : node.children)
                {
                    if (child.kind != Node.Kind.LEAF) resolveNode(child, scope);
                }
            }
            case "ALGO", "INSTR", "OUTP", "BRANCH", "LOOP", "BOOL" -> 
            {
                for (Node child : node.children) 
                {
                    if (child.kind != Node.Kind.LEAF) resolveNode(child, scope);
                }
            }
            default ->
            {
                for (Node child : node.children) 
                {
                    if (child.kind != Node.Kind.LEAF) 
                    {
                        resolveNode(child, scope);
                    }
                }
            }
        }
    }

    private void bindVariableUse(Node nameNode, Scope scope) 
    {
        Symbol symbol = scope.lookupVariable(nameNode.contents);
        if (symbol == null) 
        {
            throw error("Undeclared variable '" + nameNode.contents + "'.", nameNode);
        }
        bindings.put(nameNode, symbol);
    }

    private String freshInternalName() 
    {
        return "spl_internal_" + nextInternalName++;
    }

    private static SemanticException error(String message, Node node) 
    {
        return new SemanticException(message, node);
    }
}