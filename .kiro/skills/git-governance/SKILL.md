---
name: git-governance
description: Perform governed git and GitHub operations under recorded human approval — branch/worktree, selective staging, commit, push, PR. Use for any git write in this repository. Enforces hard limits (no force-push, no blanket staging, no unapproved push/PR, no auto-merge).
---

# Git Governance Skill

Every git write is gated by a recorded human approval bound to the exact artifact/diff. This
skill is the procedure; the `git-governance` agent is the only actor that runs it.

## Hard limits (never)

force-push · `reset --hard` · `clean -fd` · rewrite unrelated history · delete branches ·
modify protected branches · blanket stage (`git add .`/`-A`) · push or open a PR without the
matching GRANTED approval · merge · deploy.

## Lifecycle

```
Inspect repo (read-only)
 -> Propose branch (base branch + base SHA, verify name is free, show command)   [GATE 3]
 -> Create branch/worktree -> verify workspace (branch, path, base SHA, no stray edits)
 -> (spec & design already approved) -> test-first impl -> verification
 -> Show FULL diff (modified/added/deleted/untracked; scan for secrets)           [GATE 4]
 -> Stage ONLY named approved files -> verify staged diff
 -> Commit (show message + staged files)                                          [GATE 5]
 -> Record commit SHA
 -> Push (verify remote, branch, expected SHA, not a protected branch)            [GATE 6]
 -> Create PR (title, description, evidence)                                       [GATE 7]
 -> Human review. NEVER merge.
```

## Per-operation checklist

1. Read the approval record; confirm `decision=GRANTED` and `artifactHash` matches the current diff/artifact. Mismatch -> STOP, request re-approval.
2. Print the exact command before running it.
3. Run it; capture real exit code + sanitized output (never echo credentials).
4. Record the audit event (BRANCH_CREATED, FILES_STAGED, COMMIT_CREATED, PUSH_COMPLETED/PUSH_FAILED, PULL_REQUEST_CREATED) with `authorizationRef` = the approval id.

## Worktrees

Support `git worktree add .worktrees/<name> -b <branch> <base>` for isolation when the
developer has uncommitted changes or parallel work. Never silently move or discard changes.

## Honesty

Report SUCCESS/FAILURE/BLOCKED with real exit codes. A failed push/PR is visible, never
masked. Never fabricate a commit SHA or PR URL.
