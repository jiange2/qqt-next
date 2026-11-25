---
name: archive-spec
description: "Archive a finished spec: move its .scratch directory to .scratch/archive/, close out Status lines, and append the dated archive entry."
argument-hint: "<feature-slug>"
disable-model-invocation: true
---

# Archive Spec

Freeze a finished feature's spec and tickets, then move them out of the active `.scratch/` namespace.

The issue tracker conventions should have been provided to you. If not, tell the user to run `/setup-matt-pocock-skills`.

## Process

### 1. Pick the feature

If the user passed a slug or path, use it. Otherwise scan `.scratch/*/` and present each candidate: slug, spec `Status:`, ticket counts open / resolved, date of the last `## Comments` entry. Skip `archive/`, `*.stale-pre-merge` directories, and anything without a `spec.md` — stale-pre-merge copies are merge leftovers, not archives, so only touch them when the user names them.

Ask the user which feature(s) to archive. Never guess.

### 2. Check preconditions (report, don't block)

Collect facts, don't assume:

- The spec's `Status:` line — already `resolved`?
- Every `issues/*.md` `Status:` line — list any that aren't `resolved` along with their `Blocked by:` edges.
- Branch state: `git branch --no-merged main` — is the integration branch (`feat/<slug>` or similar) still unmerged? Confirm with `git merge-base --is-ancestor <branch> main`.
- Whether a review happened: look for a code-review / 评审 entry in the spec's `## Comments`. Absence is a signal, not proof.
- Unchecked `- [ ]` items in tickets: device-level acceptance stays unchecked until a human verifies on a real device (repo rule: AI does not compile or run gradlew). That alone is not a reason to hold the archive.

Present the findings as a short checklist. If anything is unmet, say so plainly and ask whether to archive anyway. Proceed only on an explicit yes, and record the unmet items in the archive entry rather than marking them done.

### 3. Move

```bash
mkdir -p .scratch/archive
git mv .scratch/<feature-slug> .scratch/archive/<feature-slug>
```

Use `git mv` so history follows the rename. Never copy-then-delete, never `rm`.

### 4. Close out states

- Spec: `Status: resolved`. Keep that word — the `archive/` location is what marks it archived, and the status vocabulary is shared with tickets.
- Tickets: set `Status: resolved` only for tickets that genuinely are. Leave open ones untouched and list them in the archive entry; don't fabricate a status.
- Leave device-level acceptance checkboxes unchecked, following existing resolved specs.

### 5. Append the archive entry

Append one bullet under the spec's `## Comments` heading (create the heading if missing), matching the shape of earlier entries:

```
- YYYY-MM-DD 归档：resolved。集成分支 `<branch>`（基于 main <sha>）。提交链：<commits>。评审结论：<verdict，或「未评审」>。未完成项：<…，或「无」>。归档位置 `.scratch/archive/<slug>/`。
```

Take the date from the system clock, and branch / base commit / commit chain from `git log` — never invent SHAs. Keep unverified work explicit (e.g. 「勾选项待人工编译真机确认」).

### 6. Fix cross-references in active work

Search live docs for the old path (`.scratch/<feature-slug>`), excluding `.scratch/archive/` and git history. Update references in active specs, ticket files, and `docs/` to the new path. Do not rewrite already-archived specs or past commit messages.

### 7. Report

State the new path, which `Status:` lines changed, the entry appended, which references were updated, and anything still unverified (device acceptance, pending review, unmerged work).
