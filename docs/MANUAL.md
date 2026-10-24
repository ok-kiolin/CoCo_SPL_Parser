# SPL Compiler Manual

## Building

mvn needs to be installed (Java 17+).

```
mvn clean package
```

This produces `target/spl-parser.jar`.

## Running

```
java -jar target/spl-parser.jar [--compile|--check|--frontend-only] [SPL.txt [tree.xml]]
```

- No arguments: looks for `SPL.txt` in the current folder, then beside the jar. A hidden `.txt` extension is tried automatically (`myprogram` resolves to `myprogram.txt` if `myprogram` doesn't exist).
- One argument: the input SPL file.
- Two arguments: input file, then an explicit XML output path (must end in `.xml`).
- `--help`: prints usage and exits; takes no file arguments.
- A mode flag, if given, must come first.

### Modes

| Flag | What it does |
|---|---|
| `--compile` (default) | Full pipeline: syntax, semantics, code generation. Writes `tree.xml` and `target.cpp`. |
| `--check` | Syntax and semantic checking only. Writes `tree.xml`, no `target.cpp`. |
| `--frontend-only` | Syntax checking only. Writes `tree.xml`, no semantic or generation phase. |

## Outputs

- **`tree.xml`**: the syntax tree. Written beside the input by default; override with a second file argument. Written on success in all three modes.
- **`target.cpp`**: generated C++17 source. Only written in `--compile` mode, and always placed beside the input (not configurable). Compiles cleanly under `g++ -std=c++17 -pedantic-errors -Wall`. The file contains a non-executable "code mine" section near the top - function bodies wrapped in `/* */` comments - followed by the executable `main()`. The code mine isn't dead code: it's copied and "inlined" directly into `main()` (and into other functions that call it) wherever a call occurs, with fresh variable/label names generated for each copy.
- Both files are written atomically (via a temp file + atomic move), so a run that fails or is interrupted never leaves a half-written output in place.
- On failure, any output already written by the current run is removed, so a failed run doesn't leave a stale `target.cpp`/`tree.xml` lying around implying success. If that cleanup itself fails, a warning is printed but the run's exit code is unaffected.
- Exception: if semantic analysis fails *after* a successful parse, the syntax tree is deliberately kept on disk (not removed) so you can inspect it while debugging the semantic error.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | OK |
| 1 | Syntax error |
| 2 | Input error (bad path, missing file, unreadable, directory given, etc.) |
| 3 | Internal error (includes stack overflow from a program that's too deeply nested) |
| 4 | Semantic error (name/type/scope problems) |
| 5 | Code generation error |

## Diagnostics

Each phase prints a `PASSED`/`FAILED` line to stdout/stderr naming the input file, followed by phase-specific detail:

- Syntax failure = the syntax error message, with line/column.
- Semantic failure = the semantic/type error message; if the tree was already written, its path is also printed.
- Generation failure = the code generation error message.
- Input errors (missing file, bad path) print a hint - e.g. a missing input file lists other `.txt`/`SPL*` files found in the same folder, to help with typos.

On success, the compiler also prints the resolved numeric mode (`int` or `float`, whichever the program's divisions require) and the node count of the syntax tree.

## Input encoding

The compiler auto-detects and strips a UTF-8, UTF-16LE, or UTF-16BE byte-order mark if present. Files without a BOM are read as ISO-8859-1. Windows line endings are accepted.

## Compiling and running target code

`target.cpp` is plain C++17 with no external dependencies. Compile and run it directly:

```
g++ -std=c++17 -pedantic-errors -Wall target.cpp -o program
./program
```

(`program.exe` on Windows.)
