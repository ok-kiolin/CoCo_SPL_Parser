# CoCo_SPL_Parser
CoCo_SPL_Parser

## Project Overview

A Java-based parser for the SPL programming language.

The project consists of:
- A lexer for lexical analysis
- A parser for syntactic analysis
- Syntax-tree construction
- XML generation of the syntax tree
- Syntax-error handling

## Project Structure

```text
src/
├── main/
│   └── java/
│       └── spl/
│           └── Main.java
│
└── test/
    └── java/
        └── spl/

tests/
├── valid/
├── invalid/
└── expected/

examples/
├── valid/
└── invalid/

docs/
├── grammar/
├── architecture/
└── testing/

lexer -> parser -> tree -> xml


# mvn needs to be installed.

git clone <https://github.com/ok-kiolin/CoCo_SPL_Parser.git>
cd CoCo_SPL_Parser
mvn clean test
mvn exec:java

#syntax Error outline(example): 
Syntax Error [line X, column Y]:
Expected <what was expected>, found <what was actually there>.
Hint: <plain-language suggestion>