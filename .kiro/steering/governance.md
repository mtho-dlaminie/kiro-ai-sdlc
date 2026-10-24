---
inclusion: always
---

# Engineering Governance

These rules apply to every feature delivered through the `feature-delivery` workflow.

## Source of truth
- The **approved specification** is the source of truth. Requirements → spec → design are approved by a human before production code.
- Tests are derived from the approved spec **before** production implementation (TDD: RED → GREEN → REFACTOR).
- Passing tests do not by themselves prove specification compliance — independent review checks tests against requirements.

## Approval gates (human)
A feature cannot advance past a gate without recorded human approval. Gates:
1. Specification approval.
2. Technical design approval.
3. Branch proposal approval.
4. Diff / selective-staging approval.
5. Commit approval.
6. Push approval.
7. Pull-request creation approval.

Each approval records: proposed operation, approved scope, artifact version/hash, decision, UTC timestamp, approver identity (if available), and execution outcome. A materially changed spec/diff/operation (hash mismatch) invalidates a prior approval and must be re-approved. Never reuse a stale approval.

## Git governance (hard limits)
Never, under any circumstance initiated by an agent:
- force-push, `reset --hard`, `clean -fd`, rewrite unrelated history, delete branches, modify protected branches;
- blanket-stage (`git add .` / `git add -A`) — stage only explicitly approved files;
- push or create a PR without the corresponding recorded approval;
- merge or deploy automatically.

Branch creation: identify base branch, record base commit SHA, propose a feature branch, verify it does not already exist, show the exact command, and obtain approval before running it. Support optional worktrees; never silently move or discard a developer's uncommitted changes.

## Permission enforcement
- Check actual tool permissions before a sensitive operation. Plan/workflow approval does NOT authorize every later sensitive Git/GitHub operation.
- Where a single recipe cannot enforce a required approval for an OS-credential operation (push, PR create), that operation is split into a separately-authorized step/invocation and the limitation is documented.

## Auditing
- Every workflow run has a unique `run_id` propagated through all artifacts.
- Audit events are recorded by the deterministic recorder in `tools/audit/`, never free-authored by an agent.
- Record real events only: never fabricate approvals, test outcomes, or Git results. Failed/blocked operations must be visible.
- Never log secrets, tokens, cookies, auth headers, or PII. Synthetic data only.

## Loop limits
Review/remediation loops are bounded (fixed max iterations). On exhaustion, pause and escalate to a human; never loop indefinitely or weaken tests to exit a loop.

## Honest reporting
Report test/verification outcomes as PASSED / FAILED / NOT RUN / BLOCKED with real commands and exit codes. Never report an unexecuted check as passing.
