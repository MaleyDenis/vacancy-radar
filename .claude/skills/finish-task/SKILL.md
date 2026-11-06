---
name: finish-task
description: Finish a task: create a branch, commit, push, open a PR, squash-merge it into main with a clean subject, and sync local main. Optional GitHub issue number closes the issue and moves it to Done. Usage: /finish-task [issue-number]
user-invocable: true
allowed-tools: Bash, Read
---

Finish the current task. **Full flow:** branch → commit → push → PR → squash-merge → checkout main → pull.

## Conventions (enforced by `scripts/hooks/`)

- **Commit / PR title / squash subject:** `<type>(<scope>): <description>`
  - types: `feat fix refactor docs style test chore`
  - scope required, lowercase; description lowercase, imperative, no trailing period, ≤ 70 chars total
  - **No ticket numbers (`VR-NN`), no PR numbers (`(#NN)`)**
  - Example: `feat(scan): add evaluate step`
- **Branch:** `<type>/<short-slug>` — e.g. `feat/evaluate-step`
- **No Claude attribution** anywhere — no `Co-Authored-By`, no "Generated with Claude Code".

## Steps

1. **Branch.** If on `main`, `git checkout -b <type>/<slug>`.
2. **Commit.** `git add` the task's files, `git commit -m "<type>(<scope>): <description>"`. One commit per task.
3. **Push.** `git push -u origin <branch>`.
4. **PR.**
   ```
   gh pr create --base main --title "<same subject as commit>" --body "## Summary
   - <bullets>"
   ```
   If an issue number was given, add `Closes #<n>` to the body.
5. **Merge** — always pass `--subject` so GitHub does NOT append `(#NN)`, and an empty body:
   ```
   gh pr merge <pr> --squash --delete-branch --subject "<same subject>" --body ""
   ```
   If blocked (checks, conflicts) — report and stop, never force.
6. **Sync.** `git checkout main && git pull --ff-only`.
7. **Issue (only if a number was given).** Move it to Done on the project board:
   ```
   gh api graphql -f query='{ user(login: "MaleyDenis") { projectV2(number: 2) { items(first: 100) { nodes { id content { ... on Issue { number } } } } } } }'
   gh api graphql -f query='mutation { updateProjectV2ItemFieldValue(input: { projectId: "PVT_kwHOAIpoic4BRuoj" itemId: "<ITEM_ID>" fieldId: "PVTSSF_lAHOAIpoic4BRuojzg_eKyc" value: { singleSelectOptionId: "98236657" } }) { projectV2Item { id } } }'
   ```
   (`gh api graphql` takes mutations via `-f query=`, not `-f mutation=`.)
8. **Report:** branch, commit subject, PR url (merged), local main up to date.
