---
name: audit-evidence
description: Record tamper-evident audit events and human approvals, build an evidence manifest, and render an offline HTML audit report using the deterministic tools in tools/audit/. Use throughout a feature-delivery run whenever a required audit event or approval occurs. Do not hand-author audit JSON.
---

# Audit Evidence Skill

Auditing runs throughout the workflow, not only at the end. **Agents never hand-write audit
JSON** — they invoke the deterministic scripts in `tools/audit/`, which validate shape, scrub
secrets, and maintain the hash-linked chain.

## Run directory

Every run has a unique `run_id` propagated through all artifacts. Evidence lives in
`audit/<run_id>/` (gitignored by default): `events.jsonl`, `approvals.jsonl`, `manifest.json`,
`report.html`.

## Recording an event

```bash
python3 tools/audit/record_event.py --run-dir audit/<run_id> --run-id <run_id> \
  --type <EVENT_TYPE> --action "<what happened>" \
  [--step <stepId>] [--agent <agent>] [--result SUCCESS|FAILURE|REJECTED|BLOCKED|PENDING|NOT_RUN] \
  [--artifact <path>] [--git-ref <ref>] [--authorization-ref <approvalId>] [--details '<json>']
```

Record at each milestone: WORKFLOW_STARTED, ENVIRONMENT_VALIDATED, REQUIREMENTS_RETRIEVED,
SPECIFICATION_GENERATED/REVISED, APPROVAL_REQUESTED/GRANTED/REJECTED, TECHNICAL_DESIGN_APPROVED,
BRANCH_PROPOSED/CREATED, WORKTREE_CREATED, TEST_CASES_GENERATED, RED_/GREEN_TEST_EXECUTION,
REFACTOR_VERIFICATION, PLAYWRIGHT_EXECUTION, CODE_REVIEW_FINDINGS, REVIEW_REMEDIATION,
DIFF_GENERATED, FILES_APPROVED_FOR_STAGING, FILES_STAGED, COMMIT_CREATED,
PUSH_ATTEMPTED/COMPLETED/FAILED, PULL_REQUEST_CREATED, WORKFLOW_PAUSED/RESUMED/FAILED/COMPLETED.
Also RETRY and AUTHORIZATION_FAILURE. The recorder drops exact duplicates (retry-safe).

## Recording an approval

```bash
python3 tools/audit/record_approval.py --run-dir audit/<run_id> --run-id <run_id> \
  --gate SPECIFICATION|TECHNICAL_DESIGN|BRANCH_PROPOSAL|DIFF_STAGING|COMMIT|PUSH|PULL_REQUEST \
  --operation "<exact op>" --artifact-hash <sha256-of-artifact> --decision GRANTED|REJECTED \
  [--approver <verified-identity>] [--scope "..."]
```

Bind the approval to the artifact's SHA-256. When the authorized action runs, its event's
`--authorization-ref` must point at this approval. A changed artifact (new hash) invalidates
the approval — re-approve.

## Manifest, verification, report

```bash
python3 tools/audit/manifest.py    --run-dir audit/<run_id> --run-id <run_id> <artifact paths...>
python3 tools/audit/verify_run.py  --run-dir audit/<run_id> --check-manifest   # exit 1 on tamper
python3 tools/audit/render_report.py --run-dir audit/<run_id> --run-id <run_id> [--pr-url <url>]
```

## Rules

- Record real events only. Never fabricate approvals, test outcomes, or git results.
- Failed/blocked/rejected operations MUST be recorded and visible.
- Never pass secrets, tokens, or PII to the scripts (the recorder scrubs common patterns, but do not rely on it).
- The local trail is tamper-evident, not tamper-proof — see tools/audit/README.md for enterprise immutability.
