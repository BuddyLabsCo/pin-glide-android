# Issue tracker: GitHub

Issues and specs live in BuddyLabsCo/pin-glide-android.
Use the gh CLI from this repository.

## Operations

- Create: gh issue create --title "..." --body-file <file>
- Read: gh issue view <number> --comments
- List: gh issue list --state open --json number,title,body,labels
- Comment: gh issue comment <number> --body-file <file>
- Apply labels: gh issue edit <number> --add-label "..."
- Remove labels: gh issue edit <number> --remove-label "..."
- Close: gh issue close <number>

Use UTF-8 files for multiline bodies.
Match the language of existing items; if none exist, ask first.

## E2E sub-issues

Read `docs/agents/triage-labels.md` for issue types and hierarchy.
Create the epic and child issues before attaching the children.

- Read children: `gh api --paginate repos/BuddyLabsCo/pin-glide-android/issues/<epic-number>/sub_issues`
- Read a child's parent: `gh api repos/BuddyLabsCo/pin-glide-android/issues/<child-number>/parent`

Attach a child using its database ID, not its issue number. Replace the number
placeholders before running:

```powershell
$childId = gh api repos/BuddyLabsCo/pin-glide-android/issues/<child-number> --jq .id
gh api --method POST repos/BuddyLabsCo/pin-glide-android/issues/<epic-number>/sub_issues -F "sub_issue_id=$childId"
```

Verify the resulting parent with the read-parent operation.

## Pull requests as a triage surface

PRs as a request surface: no.

## Skill conventions

"Publish to the issue tracker" means create a GitHub issue.
"Fetch the relevant ticket" means read the issue and its comments.

## Pull requests

- **Title** — follows the Conventional Commits spec in the form
  `<type>: [issue-<issue-number>] <subject>`, for example `fix: [issue-42] correct retry backoff`.
- **Description** — must end with `Closes #<issue-number>`
