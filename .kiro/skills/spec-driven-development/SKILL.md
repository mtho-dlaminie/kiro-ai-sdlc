---
name: spec-driven-development
description: Produce a structured, approvable specification from requirements before any code is written. Use at the start of a feature (after requirements analysis, before design and implementation) to turn requirements into a precise contract with stable IDs, acceptance criteria, and open questions. Do not use for pure refactors with no behaviour change.
---

# Specification-Driven Development Skill

The approved specification is the source of truth. Code and tests derive from it, not the
other way around. Your job is to make the spec precise enough that tests and review can be
mechanically checked against it.

## Procedure

```
1. START FROM REQUIREMENTS   read the REQ-IDed requirements artifact; never invent rules.
2. DEFINE THE CONTRACT       method, path, request/response schemas, status codes.
3. PIN THE BEHAVIOUR         business rules, formulas, rounding/precision, boundaries.
4. ENUMERATE ERRORS          every failure mode -> status + problem shape.
5. DERIVE ACCEPTANCE         one testable acceptance criterion per behaviour (AC-IDs).
6. LIST OPEN QUESTIONS       anything unspecified; propose a recommendation, don't guess.
7. VERSION + HASH            the spec is versioned; its SHA-256 binds the approval gate.
```

## Required sections

1. Business objective.
2. Functional scope and explicit exclusions.
3. API contract: request schema, response schema, validation rules.
4. Business rules and data transformations (with exact formulas where numeric).
5. Error handling: each error -> HTTP status + RFC 7807 problem shape.
6. Security considerations.
7. Dependencies (existing services/repositories reused).
8. Acceptance criteria, each with a stable AC-ID mapped to a REQ-ID.
9. Relevant non-functional requirements.
10. Open questions.

## Rules

- Assign stable identifiers: `REQ-<n>` and `AC-<n>`. Keep them stable across revisions.
- Numeric rules must state scale, rounding mode (HALF_EVEN), and currency assumptions.
- Missing-data and boundary behaviour must be specified, not left implicit.
- The spec gates **Human Approval Gate 1**. Do not proceed to production code on an unapproved spec.
- A materially revised spec is a new version with a new hash — the prior approval no longer applies.
