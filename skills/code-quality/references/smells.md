# Code Smells

Heuristics, not violations. Phrase each as "possible <smell>", quote the hunk and suggest one fix. Report only what is visible in the diff and its direct context; skip anything a linter or formatter already enforces.

| Smell | Signal in a diff | Fix |
|-------|------------------|-----|
| Mysterious Name | `get(String s)`, `check(str)`, `doStuff(id)` | Rename to reveal intent (`findActiveUsersByRole`) |
| Long Method | method over ~40 lines, several levels of nesting | Extract methods |
| Duplicated Code | same validation/mapping in two methods | Extract one shared method or validator |
| Magic Number | `86400`, unexplained literals | Named constant |
| Feature Envy | method mostly calls getters of another class | Move it to that class |
| Shotgun Surgery | one change touches many unrelated classes | Consolidate the responsibility |
| Primitive Obsession | `String email`, `Long id` passed everywhere with checks | Value object / record |
| Long Parameter List | 5+ parameters | Parameter object (record) |
| Data Clumps | the same group of fields travels together | Extract a record |
| Speculative Generality | factory/provider/interface with one implementation and no second use (YAGNI) | Inline until needed |
| Message Chains | `a.getB().getC().getD()` | Hide delegate, ask the owner |
| Dead Code | unused methods, commented-out blocks | Delete |
