package spl.types;

import java.util.HashMap;
import java.util.Map;
import spl.tree.Node;



public final class TypeAnalyser {
    public static final String CONFLICT_MESSAGE = "A float-integer-conflict might perhaps be possible";

    private final SymbolLookup symbols;
    private  final Map<Integer, Type> types = new HashMap<>();

    public TypeAnalyser(SymbolLookup symbols) {
        this.symbols = symbols;
    }

    //ENTRY POINT
    public TypeResult analyse(Node root){
        root.assignIds();
        types.clear();
        initialiseUnknown(root);
        NumericMode mode = scanWholeProgram(root);
        recordDeclarations(root);
        require(check(root), Type.OK, root, "This program is not well-typed.");

        return new TypeResult(types, mode);
    }

    //STEP 1 & 3

    private void initialiseUnknown(Node n){
        types.put(n.id, Type.UNKNOWN);
        
        for (Node c : n.children) {
            initialiseUnknown(c);
        }
    }


        private void recordDeclarations(Node n) {

            if (is(n, "V_DECL") && !n.children.isEmpty()){
                types.put(n.children.get(0).id, Type.NUMERIC);
            }else if(is(n, "F_TYPE")) {
                boolean isVoid = n.children.get(0).contents.equals("void");
                types.put(n.children.get(1).id, isVoid ? Type.PROCEDURE : Type.NUMERIC);
            }

            for (Node c : n.children) {
                recordDeclarations(c);
            }
        }

   //STEP 2
 
    private boolean hasMod, hasDiv;
    private Node firstDecimal;
 
    private NumericMode scanWholeProgram(Node root) {
        hasMod = false; hasDiv = false; firstDecimal = null;
        scan(root);

        if (hasMod && hasDiv) {
            throw new TypeException(CONFLICT_MESSAGE);
        }


        if (hasMod && firstDecimal != null) {
            throw new TypeException("Decimal number '" + firstDecimal.contents + "' is not allowed: " + "this program uses mod, so all numbers must be whole numbers.", firstDecimal);
        }


        if (hasMod) {
            return NumericMode.INT;
        }


        if (hasDiv || firstDecimal != null) {
            return NumericMode.FLOAT;
        }


        return NumericMode.INT;
    }
 
    private void scan(Node n) {
        if (is(n, "TERM")) {
            Node first = n.children.get(0);
            if (first.kind == Node.Kind.LEAF) {
                String t = first.contents;
                if (t.equals("mod")) {
                    hasMod = true;
                } else if (t.equals("div")) {
                    hasDiv = true;
                } else if (n.children.size() == 1 && !isName(first) && t.contains(".") && firstDecimal == null) {
                    firstDecimal = first;
                }
            }
        }
        for (Node c : n.children) scan(c);
    }
 
    //STEP 4: THE RULES
 
    private Type check(Node n) {
        if (n.kind == Node.Kind.LEAF) return types.get(n.id);
        Type result;
        switch (n.contents) {
            case "SPL_PROG" -> result = checkAllOk(n);
            case "P"        -> result = checkAllOk(n);
            case "V_DECL"   -> result = checkVDecl(n);
            case "F_DECL"   -> result = checkAllOk(n);  
            case "F_TYPE"   -> result = checkFType(n);
            case "ALGO"     -> result = checkAllOk(n); 
            case "INSTR"    -> result = checkInstr(n);
            case "OUTP"     -> result = checkOutp(n);
            case "CALL"     -> result = checkCall(n);
            case "INPUT"    -> result = checkInput(n);
            case "ASSIGN"   -> result = checkAssign(n);
            case "TERM"     -> result = checkTerm(n);
            case "BRANCH"   -> result = checkBranch(n);
            case "BOOL"     -> result = checkBool(n);
            case "COND"     -> result = Type.OK;
            case "LOOP"     -> result = checkLoop(n);
            default -> throw new TypeException("Internal error: unknown node '" + n.contents + "'.", n);
        }
        types.put(n.id, result);
        return result;
    }
 
    private Type checkAllOk(Node n) {
        for (Node c : n.children) {
            if (c.kind == Node.Kind.INNER) {
                require(check(c), Type.OK, c, c.contents + " is not well-typed.");
            }
        }
        return Type.OK;
    }
 
    private Type checkVDecl(Node n) {
        if (n.children.isEmpty()) {
            return Type.OK;
        }

        Node name = n.children.get(0);
        types.put(name.id, Type.NUMERIC);
        require(check(n.children.get(1)), Type.OK, n.children.get(1), "Bad variable declaration list.");


        return Type.OK;
    }
 
    private Type checkFType(Node n) {
        boolean isVoid = n.children.get(0).contents.equals("void");
        Node vdecl = n.children.get(3), body = n.children.get(6);
        require(check(vdecl), Type.OK, vdecl, "Bad parameter list.");
        require(check(body), Type.OK, body, "Bad function body.");

        if (!isVoid) {
            Node ret = n.children.get(9);
            Type t = check(ret);
            if (t != Type.NUMERIC) {
                throw new TypeException("The return expression of num function '" + n.children.get(1).contents
                        + "' must be numeric.", ret);
            }
        }


        return Type.OK;
    }
 
    private Type checkInstr(Node n) {
        Node first = n.children.get(0);

        if (first.kind == Node.Kind.LEAF) {
            if (first.contents.equals("print")) {
                require(check(n.children.get(1)), Type.OK, n.children.get(1), "Cannot print this.");
            }


            return Type.OK;
        }

        Type t = check(first);


        if (first.contents.equals("CALL")) {
            if (t != Type.PROCEDURE) {
                throw new TypeException("'" + first.children.get(0).contents + "' returns a number, so its call "
                + "cannot be used as an instruction. Only void procedures can be called as instructions.", first);
            }
            return Type.OK;
        }
        require(t, Type.OK, first, first.contents + " is not well-typed.");

        return Type.OK;
    }
 
    private Type checkOutp(Node n) {
        if (n.children.size() == 3) { 
            Node term = n.children.get(1);
            require(check(term), Type.NUMERIC, term, "Only a numeric term can be printed.");
        }
        return Type.OK;
    }
 
    private Type checkCall(Node n) {
        Node nameLeaf = n.children.get(0), input = n.children.get(2);
        Node decl = declarationOf(nameLeaf);


        if (!isFunctionDecl(decl)) {
            throw new TypeException("'" + nameLeaf.contents + "' is a variable, not a function, so it cannot be called.", nameLeaf);
        }


        Type calleeType = types.get(decl.id);


        if (calleeType == Type.UNKNOWN) {
            throw new TypeException("The type of function '" + nameLeaf.contents + "' is unknown.", nameLeaf);
        }


        types.put(nameLeaf.id, calleeType);
        require(check(input), Type.OK, input, "Bad arguments in call to '" + nameLeaf.contents + "'.");
        return calleeType;
    }
 
    private Type checkInput(Node n) {
        if (n.children.isEmpty()) {
            return Type.OK;
        }

        Node term = n.children.get(0);
        require(check(term), Type.NUMERIC, term, "Function arguments must be numeric.");
        require(check(n.children.get(1)), Type.OK, n.children.get(1), "Bad argument list.");


        return Type.OK;
    }
 
    private Type checkAssign(Node n) {
        Node nameLeaf = n.children.get(0), term = n.children.get(2);
        Type lhs = typeOfVariableUse(nameLeaf, "assigned to");
        require(lhs, Type.NUMERIC, nameLeaf, "Cannot assign to '" + nameLeaf.contents + "'.");
        require(check(term), Type.NUMERIC, term, "The assigned value must be numeric.");
        return Type.OK;
    }
 
    private Type checkTerm(Node n) {
        Node first = n.children.get(0);
        if (n.children.size() == 1) {
            if (first.kind == Node.Kind.INNER) {   
                require(check(first), Type.NUMERIC, first,
                        "A call to a void procedure has no value and cannot be used inside an expression.");
            } else if (isName(first)) {  
                require(typeOfVariableUse(first, "used as a value"), Type.NUMERIC, first, "Bad name use.");
            } 
            return Type.NUMERIC;
        }
        String op = first.contents;
        int operands = op.equals("neg") ? 1 : 2;
        for (int i = 0; i < operands; i++) {
            Node arg = n.children.get(2 + i);
            require(check(arg), Type.NUMERIC, arg, "'" + op + "' needs numeric operands.");
        }
        return Type.NUMERIC;
    }
 
    private Type checkBranch(Node n) {
        for (Node c : n.children) {
            if (c.kind != Node.Kind.INNER) {
                continue;
            }


            if (c.contents.equals("BOOL")) {
                require(check(c), Type.BOOLEAN, c, "The if-condition must be boolean.");
            }

            else require(check(c), Type.OK, c, "Bad block inside if.");
        }
        return Type.OK;
    }
 
    private Type checkBool(Node n) {
        String op = n.children.get(0).contents;
        switch (op) {
            case "not" -> 
                requireBool(n.children.get(2));

            case "and", "or" -> {
                 requireBool(n.children.get(2)); requireBool(n.children.get(3)); 
                }

            default -> { 

                for (int i = 2; i <= 3; i++) {
                    Node t = n.children.get(i);
                    require(check(t), Type.NUMERIC, t, "'" + op + "' compares numeric terms.");
                }
            }
        }
        return Type.BOOLEAN;
    }
 
    private void requireBool(Node b) {
        require(check(b), Type.BOOLEAN, b, "Expected a boolean condition here.");
    }
 
    private Type checkLoop(Node n) {
        for (Node c : n.children) {
            if (c.kind != Node.Kind.INNER) continue;

            if (c.contents.equals("BOOL")) {
                require(check(c), Type.BOOLEAN, c, "The loop condition must be boolean.");
            }

            else require(check(c), Type.OK, c, "Bad loop " + c.contents.toLowerCase() + ".");
        }
        return Type.OK;
    }
 
    //HELPERS
    private Type typeOfVariableUse(Node nameLeaf, String how) {
        Node decl = declarationOf(nameLeaf);

        if (isFunctionDecl(decl)) {
            throw new TypeException("'" + nameLeaf.contents + "' is a function and cannot be " + how + " like a variable. Call it with ( ) instead.", nameLeaf);
        }
        Type t = types.get(decl.id);
        types.put(nameLeaf.id, t);

        return t;
    }
 
    private Node declarationOf(Node nameLeaf) {
        Node decl = symbols.declarationOf(nameLeaf);
        if (decl == null) {
            throw new TypeException("'" + nameLeaf.contents + "' has no declaration.", nameLeaf);
        }
        return decl;
    }
 
    private static boolean isFunctionDecl(Node decl) {
        return decl.parent != null && is(decl.parent, "F_TYPE");
    }
 
    private static boolean isName(Node leaf) { 
        return leaf.contents.startsWith("#"); 
    }

    private static boolean is(Node n, String name) { 
        return n.kind != Node.Kind.LEAF && n.contents.equals(name); 
    }
 
    private void require(Type actual, Type expected, Node where, String message) {
        if (actual != expected) {
            throw new TypeException(message + " (expected " + expected.name().toLowerCase() + ", found " + actual.name().toLowerCase() + ")", where);
        }
    }

}
