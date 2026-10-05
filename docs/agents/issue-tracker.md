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

## Pull requests as a triage surface

PRs as a request surface: no.

## Skill conventions

"Publish to the issue tracker" means create a GitHub issue.
"Fetch the relevant ticket" means read the issue and its comments.
