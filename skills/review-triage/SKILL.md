---
name: review-triage
description: How to respond to code review feedback (pull request comments, reviews, inline remarks, bot or code-reviewer findings) without implementing everything blindly - verify each remark against the code, accept, partly accept or reject it with a short reason, then report point by point. Use when asked to address review comments, before changing code in response to a review.
---

# Review Triage Skill

Review remarks are hypotheses, not orders. Before changing code in response to feedback, decide for each remark whether it is right, and say so. This skill holds the decision rules and the reply format; the review itself belongs to the `code-reviewer` agent, project rules to `conventions`.

## When to Use
- "address the review comments" / "fix the PR feedback"
- A new comment, review or inline remark on a pull request
- Findings from `code-reviewer` or from another bot that are about to be implemented

## Process

1. **Split** the feedback into separate points. One remark = one decision. Questions are answered, not implemented.
2. **Verify** each point in the code before judging it: read the cited file and line, and the change (`git diff <base>...HEAD`). Check that the problem exists, that a later commit in the branch has not already fixed it, and that the suggestion does what the reviewer thinks.
3. **Check the rules.** The repo wins: `AGENTS.md`, `CLAUDE.md`, `conventions` and documented decisions override a generic suggestion.
4. **Decide** with one of the outcomes below.
5. **Report** in the format below. Implement only what was accepted.

## Outcomes

| Outcome | When |
|---------|------|
| **Accept** | The problem is real, in scope of this change, and the fix is consistent with the repo rules |
| **Partly accept** | Part of the remark holds; do that part and name what you left |
| **Reject** | Factually wrong, already handled, contradicts a repo rule or a documented decision, out of scope, or the cost is far above the gain |
| **Defer** | Valid but out of scope: do not change code, suggest a follow-up issue |
| **Answer** | A question or a request for explanation: reply, no code change |

## Rules

- **Evidence over deference.** Reject only with a reason you can state in one sentence and, where possible, point to in the code or in a rule. If you cannot tell whether a remark is right, say what you checked and what is unclear; do not guess in either direction.
- **Raise the bar for rejecting safety.** Security, data loss and correctness remarks need concrete evidence to reject. When in doubt, take the careful fix.
- **Keep to the scope.** Do not widen the change because a remark hints at a larger refactor; use Defer.
- **Humans can overrule.** If a human reviewer explicitly repeats a request that was rejected earlier in the conversation, implement it, unless it breaks security or a hard rule of the repo; in that case say which rule and stop there.
- **Bots are no more authoritative than humans.** Automated findings are often generic or wrong for this codebase; verify them the same way.
- **Do not argue.** Reasons are one factual sentence, no defensiveness, no thanks or filler.
- **Tests follow the change.** An accepted remark that changes behavior gets a test update.

## Output Format

When the caller prescribes a format, use it. Otherwise:

**Plan (before implementing):**

```markdown
### Implementation plan
- [ ] concrete step, in the order it will be done (code, tests)

### Not doing
- "short name of the remark" - reason in one sentence
```

Write `_No code changes._` instead of the checklist when nothing was accepted, and `_Nothing skipped._` when nothing was left out.

**Reply (after implementing)**, one bullet per remark:

```markdown
- **Done** - "remark": what was changed
- **Partly done** - "remark": what was changed, what was left and why
- **Not done** - "remark": reason
- **Follow-up** - "remark": suggested issue title
```

Finish with the test result and anything a reviewer should look at.
