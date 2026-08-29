1. Token interface:

Token {
  type       // e.g. KEYWORD_IF, NAME, NUMBER, STRING, SYMBOL_LBRACE, EOF, ...
  lexeme    // the actual matched text, e.g. "#counter1", "123", "if"
  line      // 1-indexed line number
  column    // 1-indexed column number
}


2. Node Structure:

Node {
  id         // unique int, assigned at creation
  contents   // String — non-terminal name OR terminal token text
  isLeaf     // boolean
  parent     // Node reference (or ID)
  children   // List<Node> — empty/absent for leaves
}

/*
Root node

unique node ID (occurs nowhere else in the tree)
contents — the non-terminal Start-Symbol (i.e. SPL_PROG)
children — list of IDs of immediate children

Inner node

unique node ID
contents — the relevant non-terminal from the grammar
children — list of IDs of immediate children
parent — ID of the node this is a child of

Leaf node

unique node ID
contents — the actual terminal/token the parser consumed (a full lexical unit, since we're using a lexer — see section 1)
parent — ID of the node this is a child of
*/



3. Syntax Error Format

Syntax Error [line X, column Y]:
Expected <what was expected>, found <what was actually there>.
Hint: <plain-language suggestion>