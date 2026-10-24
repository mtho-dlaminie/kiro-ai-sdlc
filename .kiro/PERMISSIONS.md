# Tool Permission Matrix (least privilege)

Each agent in the `feature-delivery` workflow is granted only the capabilities its role
requires. External **write** capability (git push, `gh`) is isolated to the `git-governance`
agent, invoked only behind recorded human approval. Future Jira/Confluence/GitLab MCP are
listed as design points — they are NOT provisioned for this POC.

| Capability | requirements-analyst | wf-design (spec/design) | test-engineer | wf-coder (impl) | semantic_reviewer | api-verifier | git-governance |
|---|---|---|---|---|---|---|---|
| File read | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| File write | docs artifact only | docs artifact only | test dirs + matrix | src + test | review dir | `acceptance-tests/` | audit + staging notes |
| Shell: build/test (`./gradlew`,`java`,`npx`) | ∅ | ∅ | ✅ | ✅ | read/verify | ✅ | ∅ |
| Shell: git **read** (`status`,`diff`,`log`) | ∅ | ∅ | ∅ | ∅ | ✅ | ∅ | ✅ |
| Shell: git **write** (`branch`,`add`,`commit`,`push`) | ∅ | ∅ | ∅ | ∅ | ∅ | ∅ | ✅ (gated) |
| GitHub (`gh`) | ∅ | ∅ | ∅ | ∅ | ∅ | ∅ | ✅ (gated) |
| Playwright MCP (interactive) | ∅ | ∅ | ∅ | ∅ | ∅ | ✅ | ∅ |
| Audit scripts (`python3 tools/audit/*`) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| web_search / web_fetch | as needed | as needed | ∅ | as needed | ∅ | ∅ | ∅ |
| **Future** Jira MCP | design-point (read issues) | ∅ | ∅ | ∅ | ∅ | ∅ | ∅ |
| **Future** Confluence MCP | design-point (read docs) | ∅ | ∅ | ∅ | ∅ | ∅ | ∅ |
| **Future** GitLab | ∅ | ∅ | ∅ | ∅ | ∅ | ∅ | design-point (MR instead of PR) |

## Enforcement notes

- Custom agents (`requirements-analyst`, `test-engineer`, `api-verifier`, `git-governance`)
  declare their own `permissions.rules` in `.kiro/agents/*.json`, scoping shell commands to
  the minimum set. Built-in `wf-*` / `semantic_reviewer` inherit the session toolset but are
  directed by step prompts and steering.
- The workspace/user `permissions.yaml` is the outer gate; a sensitive command still prompts
  per policy. **Workflow/plan approval does NOT authorize later git push / PR creation** — those
  are separate, individually-approved gates (see below).
- Where a single recipe cannot force an OS-credential prompt mid-run (push, PR create), that
  operation is split into a separately-authorized step/invocation (e.g. the bundled
  `publish-pr` recipe), and the limitation is documented in `governance.md`.

## Approval gate → operation mapping

| Gate | Authorizes | Actor |
|---|---|---|
| SPECIFICATION | proceed past spec | human |
| TECHNICAL_DESIGN | proceed past design | human |
| BRANCH_PROPOSAL | create branch/worktree | git-governance |
| DIFF_STAGING | stage named files | git-governance |
| COMMIT | create commit | git-governance |
| PUSH | push to remote feature branch | git-governance |
| PULL_REQUEST | open the PR | git-governance |

Never grant broad permissions for convenience. Never reuse a stale approval (hash-bound).
