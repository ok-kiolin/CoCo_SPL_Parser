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
│   .gitignore
│   pom.xml
│   README.md
│   
├───docs
│   ├───architecture
│   │       architecture.md
│   │       
│   ├───grammar
│   │       productions.md
│   │       
│   └───testing
├───examples
│   ├───invalid
│   └───valid
├───src
│   ├───main
│   │   └───java
│   │       └───spl
│   │           │   Main.java
│   │           │   
│   │           ├───errors
│   │           │       syntaxException.java
│   │           │       
│   │           ├───lexer
│   │           │       keywords.java
│   │           │       token.java
│   │           │       tokenType.java
│   │           │       
│   │           ├───parser
│   │           ├───tree
│   │           │       node.java
│   │           │       
│   │           └───xml
│   └───test
│       └───java
│           └───spl
│               ├───lexer
│               ├───parser
│               ├───tree
│               └───xml
└───tests
    ├───expected
    ├───invalid
    └───valid

lexer -> parser -> tree -> xml


# mvn needs to be installed.

git clone <https://github.com/ok-kiolin/CoCo_SPL_Parser.git>
cd CoCo_SPL_Parser
mvn clean test
mvn exec:java

