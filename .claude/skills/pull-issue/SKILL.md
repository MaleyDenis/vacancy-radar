---
name: pull-issue
description: Import a GitHub issue into TASKS.md as a structured task entry. Usage: /pull-issue <issue-number>
user-invocable: true
allowed-tools: Bash, Read, Edit
---

Import GitHub issue $ARGUMENTS into TASKS.md following the existing task format.

## Steps

1. Run `gh issue view $ARGUMENTS --repo MaleyDenis/vacancy-radar --json number,title,body,url` and capture the output.

2. Check if issue `#$ARGUMENTS` is already present in TASKS.md by searching for `issues/$ARGUMENTS)`. If found, stop and tell the user the issue is already in TASKS.md.

3. Read the issue body and extract intent. Use your judgment to populate these 5 sections based on the issue title and description:
   - **Context** — why this task exists, what the current state is
   - **Action** — numbered steps describing what needs to be built
   - **Acceptance Criteria** — unchecked checkboxes `- [ ]` for each verifiable outcome
   - **Boundaries** — what this task must NOT do
   - **Dependencies** — list `depends on #N` for known deps, otherwise `- TBD`

4. Format the entry exactly as follows (replace placeholders):

```
---

# [#NUMBER TITLE](URL)

## Context
- ...

## Action
1. ...

## Acceptance Criteria
- [ ] ...

## Boundaries
- ...

## Dependencies
- ...
```

5. Append the formatted block to the end of `TASKS.md`.

6. Tell the user: "Added #NUMBER to TASKS.md. Please review and adjust the Task Graph at the top."
