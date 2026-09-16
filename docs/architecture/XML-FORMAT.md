# tree.xml format

This is the format decided for the project. The parser writers build node
creation against it; the `golden/` files are the authoritative examples. If in
doubt, match a golden file exactly.

## Three tags, one per node kind

| Tag | Attributes | Used for |
|---|---|---|
| `<root>`  | `id`, `contents`, `children` | the single start-symbol node (`SPL_PROG`) |
| `<inner>` | `id`, `contents`, `parent`, `children` | every non-terminal |
| `<leaf>`  | `id`, `contents`, `parent` | every terminal token the parser ate |

- `id` — a unique integer, assigned by **pre-order traversal starting at 1**, so
  the root is always `1`. Deterministic: the same input always gives the same IDs.
- `contents` — the non-terminal name (root/inner) or the exact token text (leaf).
- `children` — space-separated child IDs, in order. Empty string when none.
- `parent` — the enclosing node's ID. The root has **no** `parent`. Leaves have
  **no** `children`.

The nodes are physically nested (so a browser renders a collapsible tree) **and**
carry explicit `parent`/`children` attributes (so the file is also the flat
node-set the spec describes and the back end can index). Both readings hold at once.

## The decisions that aren't spelled out in the spec

These were genuinely ambiguous; here is what we do and why. If a tutor states
otherwise, each is a small, local change.

1. **Empty productions get a node.** When `V_DECL`, `F_DECL`, `ALGO` or `INPUT`
   derives nothing, it still appears as an `<inner>` with `children=""`. This keeps
   the tree uniform — `P` always has exactly five children, every function's
   parameter list is always a `V_DECL` node — which is what lets the back end tell
   a function's parameters from its locals. No `ε` leaf is emitted (nothing was eaten).
2. **`$` (end of input) gets no node.** `SPL_PROG` has a single child, `P`. `$` is
   the end-of-file sentinel, not a token read from `SPL.txt`, so it isn't materialised.
3. **`contents` is an attribute, XML-escaped.** A STRING token carries its own quote
   marks, e.g. the token `"hi"`, which is written `contents="&quot;hi&quot;"` and
   renders as `"hi"`. Use a real XML writer (`javax.xml`), never string
   concatenation — that is the one place hand-built XML silently breaks. (No legal
   SPL token can contain `<`, `>` or `&`; the double quote is the only hostile char.)
4. **No `line`/`col` in the file.** Leaf positions are kept in the `Node` object for
   error messages and the back end, but are not written, so the graded output matches
   the spec's field lists exactly.

## Worked example

Source (`v2_print_string.spl`):

```
#x : : print "hi" ;
```

produces `golden/v2_print_string.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<root id="1" contents="SPL_PROG" children="2">
  <inner id="2" contents="P" parent="1" children="3 6 7 8 9">
    <inner id="3" contents="V_DECL" parent="2" children="4 5">
      <leaf id="4" contents="#x" parent="3"/>
      <inner id="5" contents="V_DECL" parent="3" children=""/>
    </inner>
    <leaf id="6" contents=":" parent="2"/>
    <inner id="7" contents="F_DECL" parent="2" children=""/>
    <leaf id="8" contents=":" parent="2"/>
    <inner id="9" contents="ALGO" parent="2" children="10 14 15">
      <inner id="10" contents="INSTR" parent="9" children="11 12">
        <leaf id="11" contents="print" parent="10"/>
        <inner id="12" contents="OUTP" parent="10" children="13">
          <leaf id="13" contents="&quot;hi&quot;" parent="12"/>
        </inner>
      </inner>
      <leaf id="14" contents=";" parent="9"/>
      <inner id="15" contents="ALGO" parent="9" children=""/>
    </inner>
  </inner>
</root>
```

Note the empty `V_DECL` (5), empty `F_DECL` (7) and trailing empty `ALGO` (15) —
decision 1 in action — and the escaped string leaf (13).

## Acceptance checks (also the back end's requirements)

- Opens in a browser and renders as a collapsible tree.
- Every ID is unique; across a whole tree they run `1..N` with no gaps.
- Every `children` attribute equals the actual nested child IDs; every `parent`
  points at the real enclosing node.
- Round-trips: reading the file back into a `Node` tree and re-writing it yields
  the identical file. This is the proof the back end can consume it.
