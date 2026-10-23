---
name: git-commit
description: "Create clean, atomic git commits using Conventional Commits. Use when the user asks to commit, when a piece of work is finished and AGENTS.md directs committing with this skill, or when the working tree holds several unrelated changes that need separate commits. Groups changes by intent, splits mixed files by hunk, and writes searchable type(scope): subject messages."
---

# Git commit

Commit early and in small, self-contained pieces. The test for every
commit: *could I revert just this one and leave everything else
sensible?* If not, it is too big or wrongly grouped.

## 1. Collect the state

Run these in parallel before deciding anything:

- `git status` — what is changed, staged, and untracked
- `git diff` and `git diff --staged` — the actual changes
- `git branch --show-current` — never commit on the wrong branch
- `git log --oneline -10` — recent context

If there is nothing to commit, say so and stop. Use the context of the
session (what was just built or fixed) to understand intent; check the
diff, don't trust memory.

## 2. Group by intent — one isolated change per commit

- A feature, a fix, a refactor, its tests, its docs: each its own commit.
  Tests belong with the code they test; a `docs/plan.md` update belongs
  with the change it records.
- One file can hold several intents. Stage by hunk (`git add -p`, or
  build a patch and `git apply --cached`) so each intent lands in its
  own commit.
- Order commits foundations-first: a refactor or build change the
  feature depends on comes before the feature.
- Never sweep up changes you cannot explain. Leave unrelated dirty
  files uncommitted and report them at the end.
- Never commit secrets or local/generated files: `local.properties`,
  `google-services.json`, keystores, IDE and OS noise. If one is about
  to be committed, stop and flag it (it probably belongs in
  `.gitignore`).
- If the user says everything goes in one commit, obey — still use the
  message format below.

## 3. Verify before committing code

Run the checks that cover the change (in this repo: `./gradlew test`
for code changes, per AGENTS.md). Trivial docs/config commits need
none. If checks fail, do not commit the broken work — report the
failure instead, unless the user explicitly says to commit anyway.

Also scan the diff once for little things left over from the work:
controls wired to nothing, hardcoded values that have a real source
(API, settings, resources), misleading names, leftover debug code or
TODOs. Fix what belongs to this change — each fix is its own atomic
commit — and report anything you deliberately leave.

## 4. Message format — Conventional Commits

```
<type>(<scope>): <subject>

<body — only when the subject cannot carry the why>

<footer — Refs / BREAKING CHANGE, when they apply>
```

### Types (pick exactly one)

| type       | for                                              |
|------------|--------------------------------------------------|
| `feat`     | new user-visible functionality                   |
| `fix`      | a bug fix                                        |
| `perf`     | a performance improvement, no behaviour change   |
| `refactor` | restructuring, no behaviour change               |
| `style`    | formatting or visual-only changes, no logic      |
| `docs`     | documentation only                               |
| `test`     | adding or fixing tests only                      |
| `build`    | Gradle, dependencies, version files              |
| `ci`       | CI workflow changes                              |
| `chore`    | maintenance: gitignore, config, cleanup          |

### Scope

The area touched, one per commit — a feature area (`scale`, `editor`,
`importer`, `pro`, `recipes`, `settings`), a module (`domain`, `data`,
`designsystem`), or a repo area (`ci`, `agents`). If a change needs two
scopes, that is usually a sign it should be two commits. Omit the scope
only for truly repo-wide changes.

### Subject

- Imperative mood, present tense: "add", "fix", "remove" — not
  "added" or "adding"
- Lowercase after the colon, no trailing period
- ≤ 72 characters; say *what* changed, specifically enough that the
  log is searchable

```
feat(scale): persist Scale To and Serves per recipe
fix(pro): show pending-payment state after checkout
refactor(data): extract purchase verification into its own class
test(scale): cover scale inputs restore on reopen
build: bump Play Billing to 9.0.0
ci: run workflow on dev branch PRs
```

Not: `fix stuff`, `update files`, `feat(scale): persisted the inputs.`

### Body and footer

- Body only when the *why* is not obvious from the subject: wrap at 72,
  blank line after the subject, explain why and any tradeoff — not a
  file-by-file recap of the diff.
- Footer: `Refs: #<issue>` when the commit relates to an issue;
  `BREAKING CHANGE: <what breaks>` when it does.

## 5. Commit and report

Create the commits, then report:

- `git log --oneline` of the new commits
- anything left uncommitted, and why

Commit only — push only when the user asks. Never amend or rewrite
commits that are already pushed, and never rewrite another person's
commits.
