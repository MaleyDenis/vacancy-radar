---
name: finish-task
description: Finish a task: create a branch per naming convention, commit staged changes with the correct message format, push, open a PR, and move the issue to Done in GitHub project. Usage: /finish-task <issue-number>
user-invocable: true
allowed-tools: Bash, Read
---

Finish task #$ARGUMENTS — create branch, commit, push, open PR, and move to Done in GitHub project.

## Step 1 — Fetch issue

Run:
```
gh issue view $ARGUMENTS --repo MaleyDenis/vacancy-radar --json number,title,labels,url
```

Extract: `number`, `title`, `url`, and the first label name (if any).

## Step 2 — Determine branch type

Map the issue label to branch type using this table:

| Label      | Branch type |
|---|---|
| feature    | feat        |
| bug        | fix         |
| refactor   | refactor    |
| docs       | docs        |
| style      | style       |
| test       | test        |
| chore      | chore       |
| (no label) | feat        |

## Step 3 — Build branch name

1. Slugify the issue title: lowercase, replace spaces and special chars with `-`, strip leading/trailing `-`.
   - Example: "Add job listing parser" → `add-job-listing-parser`
2. For non-chore types: `<type>/VR-<number>-<slug>`
   - Example: `feat/VR-16-add-job-listing-parser`
3. For chore: `chore/<slug>` (no ticket number)
   - Example: `chore/setup-hooks`

## Step 4 — Create or switch to branch

Run `git branch --show-current` to check current branch.

- If already on the correct branch → skip creation.
- If not → run `git checkout -b <branch-name>`.

## Step 5 — Commit staged changes (if any)

Run `git status --short` to check for staged changes.

If there are staged changes, commit using the correct message format:

- **Non-chore:** `<type>(<scope>): VR-<number> <short description>`
  - Scope = component name derived from staged files (e.g. `scraper`, `enricher`, `api`)
  - Example: `feat(scraper): VR-16 add job listing parser`
- **Chore:** `chore(<scope>): <short description>`
  - Example: `chore(config): add pre-commit hooks`

If there are no staged changes, skip this step and inform the user.

## Step 6 — Push branch to remote

```
git push -u origin <branch-name>
```

## Step 7 — Create Pull Request

```
gh pr create --title "<type>(VR-<number>): <issue title>" --body "$(cat <<'EOF'
## Summary
- <bullet points summarizing what was done>

Closes #<number>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" --base main
```

- Title format matches commit message style: `<type>(VR-<number>): <short description>`
- Body should summarize the changes from the commit

## Step 8 — Move issue to Done in GitHub project

1. Get the project item ID for issue #$ARGUMENTS:
```
gh api graphql -f query='{ user(login: "MaleyDenis") { projectV2(number: 2) { items(first: 100) { nodes { id content { ... on Issue { number } } } } } } }'
```
Find the node where `content.number == $ARGUMENTS` and capture its `id`.

2. If found, update status to Done:
```
gh api graphql -f mutation='mutation { updateProjectV2ItemFieldValue(input: { projectId: "PVT_kwHOAIpoic4BRuoj" itemId: "<ITEM_ID>" fieldId: "PVTSSF_lAHOAIpoic4BRuojzg_eKyc" value: { singleSelectOptionId: "98236657" } }) { projectV2Item { id } } }'
```

3. If not found in project → warn the user that the issue is not in the project board, but continue.

## Step 9 — Report

Print a summary:
```
✓ Branch: <branch-name>
✓ Commit: <commit message or "no staged changes — commit manually">
✓ Pushed to origin
✓ PR: <pr url>
✓ Issue #$ARGUMENTS moved to Done
  URL: <issue url>
```
