\# SPL Grammar Analysis: Nullable, FIRST, FOLLOW and LL(1) check

Short names used below:

- `NAME` = USER-DEFINED-NAME
- `NUM` = number literal
- `STRING` = string literal

\## 0. Numbered productions

| #  | Production |

\|----|------------|

| 0  | SPL\_PROG → P $ |

| 1  | P → V\_DECL : F\_DECL : ALGO |

| 2  | V\_DECL → ε |

| 3  | V\_DECL → NAME V\_DECL |

| 4  | F\_DECL → ε |

| 5  | F\_DECL → F\_TYPE F\_DECL |

| 6  | F\_TYPE → void NAME ( V\_DECL ) { P return } |

| 7  | F\_TYPE → num NAME ( V\_DECL ) { P return ( TERM ) } |

| 8  | ALGO → ε |

| 9  | ALGO → INSTR ; ALGO |

| 10 | OUTP → ( TERM ) |

| 11 | OUTP → STRING |

| 12 | INSTR → print OUTP |

| 13 | INSTR → nop |

| 14 | INSTR → comment STRING |

| 15 | INSTR → ASSIGN |

| 16 | INSTR → BRANCH |

| 17 | INSTR → LOOP |

| 18 | INSTR → CALL |

| 19 | CALL → NAME ( INPUT ) |

| 20 | INPUT → ε |

| 21 | INPUT → TERM INPUT |

| 22 | ASSIGN → NAME = TERM |

| 23 | TERM → NAME |

| 24 | TERM → NUM |

| 25 | TERM → CALL |

| 26 | TERM → mod ( TERM TERM ) |

| 27 | TERM → add ( TERM TERM ) |

| 28 | TERM → sub ( TERM TERM ) |

| 29 | TERM → mul ( TERM TERM ) |

| 30 | TERM → div ( TERM TERM ) |

| 31 | TERM → neg ( TERM ) |

| 32 | BRANCH → if BOOL then { ALGO } else { ALGO } |

| 33 | BOOL → not ( BOOL ) |

| 34 | BOOL → and ( BOOL BOOL ) |

| 35 | BOOL → or ( BOOL BOOL ) |

| 36 | BOOL → eq ( TERM TERM ) |

| 37 | BOOL → larger ( TERM TERM ) |

| 38 | BOOL → lesser ( TERM TERM ) |

| 39 | LOOP → COND BOOL do { ALGO } |

| 40 | LOOP → do { ALGO } COND BOOL |

| 41 | COND → while |

| 42 | COND → until |

\## Nullability, FIRST and FOLLOW table

| Non-terminal | Nullable? | FIRST | FOLLOW |

\|--------------|-----------|-------|--------|

| SPL\_PROG | no  | { NAME, : } | ∅ |

| P        | no  | { NAME, : } | { $, return } |

| V\_DECL   | yes | { NAME } | { :, ) } |

| F\_DECL   | yes | { void, num } | { : } |

| F\_TYPE   | no  | { void, num } | { void, num, : } |

| ALGO     | yes | { print, nop, comment, NAME, if, while, until, do } | { $, return, } } |

| OUTP     | no  | { (, STRING } | { ; } |

| INSTR    | no  | { print, nop, comment, NAME, if, while, until, do } | { ; } |

| CALL     | no  | { NAME } | { ), ;, NAME, NUM, mod, add, sub, mul, div, neg } |

| INPUT    | yes | { NAME, NUM, mod, add, sub, mul, div, neg } | { ) } |

| ASSIGN   | no  | { NAME } | { ; } |

| TERM     | no  | { NAME, NUM, mod, add, sub, mul, div, neg } | { ), ;, NAME, NUM, mod, add, sub, mul, div, neg } |

| BRANCH   | no  | { if } | { ; } |

| BOOL     | no  | { not, and, or, eq, larger, lesser } | { then, do, ), ;, not, and, or, eq, larger, lesser } |

| LOOP     | no  | { while, until, do } | { ; } |

| COND     | no  | { while, until } | { not, and, or, eq, larger, lesser } |

FIRST sets are listed without ε; the "Nullable?" column shows whether ε belongs to FIRST.

\## LL(1) check

- Nullable non-terminals: FIRST ∩ FOLLOW = ∅ for V\_DECL, F\_DECL, ALGO and INPUT, so there are no FIRST/FOLLOW conflicts.
- FIRST/FIRST conflicts:
- INSTR: rule 15 (ASSIGN) and rule 18 (CALL) both start with `NAME`. e.g. #x = 5 and #f (5), #x and #f are both NAMES so parser doesnt know which rule to follow.
- TERM: rule 23 (NAME) and rule 25 (CALL) both start with `NAME`. e.g. add ( #x 3), here #x is a variable name and add ( #f(5) 3 ), here #f is a function call. So parser would see a name where a value should be and can't tell which is which.

\*\*Conclusion: the SPL grammar is NOT LL(1).\*\*

\## Resolution (my suggested solution)

In both cases the second token decides: read the NAME and look at what comes after it:

- INSTR on `NAME`: if the next token is `=`, use ASSIGN; if it is `(`, use CALL.
- TERM on `NAME`: if the next token is `(`, use CALL; otherwise use NAME. This is safe because in this grammar a variable is never followed by an opening bracket.

Proposed approach: peek one extra token (LL(2)) only at these two points, so the grammar and the tree.xml node names stay exactly as in the spec.

\### Other possible solutions considered

- \*\*Left-factoring:\*\* rewrite as `INSTR → NAME INSTR'` and `TERM → NAME TERM'`. This makes the grammar truly LL(1), but ASSIGN and CALL no longer appear as nodes in tree.xml.
- \*\*Backtracking:\*\* try ASSIGN first; if `=` does not follow, rewind and try CALL. This works but is slower and makes error messages harder.
- \*\*Lexer help:\*\* the lexer marks a NAME followed by `(` as a separate function-name token, which removes the conflict. This moves parsing logic into the lexer.
