# Project Governance

This page documents how the maintainers track work and integrate contributions.
The conventions were agreed in the design discussions of
[#135](https://github.com/LF-Decentralized-Trust-labs/hypernate/issues/135) and
[#139](https://github.com/LF-Decentralized-Trust-labs/hypernate/issues/139).

## Work Tracking

Work is tracked on the [Hypernate GitHub Project](https://github.com/orgs/LF-Decentralized-Trust-labs/projects/3) as a **kanban flow** – deliberately not sprints, because a volunteer-maintained project has no plannable capacity.

- **Issues are the work items.**
  Project views display issues; a pull request surfaces on its issue's card through the *Linked pull requests* field.
  A dedicated review-pipeline view lists open PRs by review state, so the review queue stays visible alongside the issues.
- **The assignee is the driver:** whoever has taken responsibility for delivering the item, most often the person writing the change – an outside contributor just as readily as a maintainer.
  Anyone may ask for an unassigned `design/approved` issue, and a maintainer assigns it so that two people never start the same work.
  A maintainer may be assigned *in addition*, as the sponsor who reviews the work, unblocks it, and chases it to its next state.
- **The board must reflect repository reality** (labels, PR state) rather than act as a second tracker; anything that can be automated should be.

## Epics

Epics group issues for the roadmap view, and they follow one core rule: **an epic is a bounded increment within a topic, never the topic itself.**
A theme like "the registry" never finishes; an epic must have a definition of done and eventually close.

- The eternal, thematic axis is expressed with `area/*` labels, not epics.
- **Verb-first epic titles** ("Complete…", "Stand up…", "Assemble…") force the definition of done into the name.
  If an epic cannot be titled this way, it is probably a theme in disguise.
- **Bugs do not need parents** – they flow through the board on their own with an area label.
- **An epic needs critical mass** (roughly four issues or more), though maintainers may grant exemptions for larger undertakings that start small.
  Sub-issue nesting itself is free and does not require the `EPIC` label.
- **Scope freezes at epic creation.**
  A new related issue goes to the area label and backlog, not into the open epic, unless the maintainers deliberately re-scope.
- **No milestones.**
  Milestones imply plannable capacity; instead, releasing is a topic like any other (`area/release`), and the machinery to publish is built through bounded epics.

## Labels

Namespaces are separated with `/`, and each namespace shares one color family so the board reads at a glance.

| Label | Meaning |
| ----- | ------- |
| `needs-triage` | Added automatically to every new issue; awaiting maintainer review. |
| `design/pending` | The design is under active discussion – please wait before starting work. |
| `design/approved` | The design is approved; contributions are welcome. |
| `needs-approved-issue` | Added automatically to a pull request not linked to a `design/approved` issue. |
| `stale` | Added automatically to a pull request flagged `needs-approved-issue` after 14 days of inactivity, and closed after a further 60 – issues are never marked stale. |
| `EPIC` | A bounded, closeable increment grouping sub-issues (see above). |
| `area/registry`, `area/middleware`, `area/docs`, `area/release`, `area/governance` | The thematic axis: which part of the project an issue belongs to. |

## Merging Pull Requests

Pull requests are merged by **fast-forward only**: `main` is advanced to the PR's head commit, so the contributor's commits land exactly as authored – same hashes, same GPG signatures.

Why not the standard merge buttons:

- *Squash and merge* creates a new commit signed by GitHub's key instead of the author's.
- *Rebase and merge* always rewrites the commits and strips their signatures entirely, downgrading them to unverified.
- *Merge commits* would preserve the original commits but break the linear history that the branch ruleset requires.

Fast-forwarding keeps the history linear **and** verifiable, and it has a pleasant side effect for stacked PRs: when a parent PR merges, the child's diff collapses to its own commits automatically, with no rebase or force-push.

### How a merge happens

A maintainer comments `/fast-forward` on an approved, up-to-date pull request; the [Fast-Forward Merge workflow](../guides/cicd.md#fast-forward-merge) performs the push and reports the result in the PR thread.
A maintainer can equally perform the same operation locally:

```bash
git fetch upstream
git checkout main && git merge --ff-only <branch>
git push upstream main
```

GitHub marks the PR as merged automatically when its head commit reaches `main`.

### What this means for contributors

- Keep your branch rebased so it sits exactly on top of `main` – a branch that has drifted cannot be fast-forwarded.
- Because commits land verbatim, each commit should stand on its own: signed off, GPG-signed if you can, conventionally titled, and buildable.
