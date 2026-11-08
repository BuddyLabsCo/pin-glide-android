# Triage Labels

## Triage status

Use this mapping when a skill names a triage role.

| Role | Tracker label | Meaning |
| --- | --- | --- |
| needs-triage | needs-triage | Awaiting maintainer evaluation |
| needs-info | needs-info | Awaiting reporter information |
| ready-for-agent | ready-for-agent | Fully specified for an agent |
| ready-for-human | ready-for-human | Requires human implementation |
| wontfix | wontfix | Will not be actioned |

## E2E issue types

These type labels can be combined with a triage status.

| Label | Purpose | Issue content |
| --- | --- | --- |
| e2e-epic | Overall E2E test effort | Test objective, scope, environment, prerequisites, variable names/setup and completion criteria |
| e2e-case | One concrete test case under an E2E epic | Preconditions, steps, expected outcome and execution result/evidence |
| bug | A defect; E2E discoveries belong under their E2E epic | Reproduction steps, expected/actual behavior, environment, evidence and a link to the originating test case |

Keep secret values out of issue bodies; document variable names and how to supply them.
The `bug` label also applies to defects found outside E2E testing.

## E2E hierarchy

```text
e2e-epic
|-- e2e-case
|-- e2e-case
`-- bug
```

Attach each case and each E2E-discovered bug directly to the epic using GitHub's
native sub-issue relationship. Cases and bugs are siblings; the bug's body links
to the case that found it. Body links alone do not establish the hierarchy.
See `docs/agents/issue-tracker.md` for sub-issue operations.
