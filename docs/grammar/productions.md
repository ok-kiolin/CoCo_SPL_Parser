SPL_PROG → P $                                     // "Rule 0", $ = end of file

P → V_DECL : F_DECL : ALGO

V_DECL → ε
V_DECL → USER-DEFINED-NAME V_DECL

F_DECL → ε
F_DECL → F_TYPE F_DECL

F_TYPE → void USER-DEFINED-NAME ( V_DECL ) { P return }
F_TYPE → num  USER-DEFINED-NAME ( V_DECL ) { P return ( TERM ) }

ALGO → ε
ALGO → INSTR ; ALGO

OUTP → ( TERM )
OUTP → STRING

INSTR → print OUTP
INSTR → nop
INSTR → comment STRING
INSTR → ASSIGN
INSTR → BRANCH
INSTR → LOOP
INSTR → CALL

CALL → USER-DEFINED-NAME ( INPUT )

INPUT → ε
INPUT → TERM INPUT

ASSIGN → USER-DEFINED-NAME = TERM

TERM → USER-DEFINED-NAME
TERM → NUM
TERM → CALL
TERM → mod ( TERM TERM )
TERM → add ( TERM TERM )
TERM → sub ( TERM TERM )
TERM → mul ( TERM TERM )
TERM → div ( TERM TERM )
TERM → neg ( TERM )

BRANCH → if BOOL then { ALGO } else { ALGO }

BOOL → not ( BOOL )
BOOL → and ( BOOL BOOL )
BOOL → or  ( BOOL BOOL )
BOOL → eq ( TERM TERM )
BOOL → larger ( TERM TERM )
BOOL → lesser ( TERM TERM )

LOOP → COND BOOL do { ALGO }
LOOP → do { ALGO } COND BOOL

COND → while
COND → until