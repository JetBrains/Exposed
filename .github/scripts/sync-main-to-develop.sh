#!/usr/bin/env bash
# Per-commit cherry-pick sync from main to the current develop/* line.
# Runs from .github/workflows/sync-main-to-develop.yml on every push to main
# and on manual dispatch (INPUT_TARGET can pin a specific develop/* branch).
#
# Rules:
#   - Target: the highest-versioned develop/* branch (INPUT_TARGET overrides).
#   - Outstanding work: commits on main not on the target, deduped by patch-id,
#     minus merge commits (release merges carry the version bump + CHANGELOG,
#     which reconcile at the next minor's release bump instead) and minus
#     release-bump commits ("chore: Bump Exposed version ...").
#   - Clean picks land as one PR, auto-merged with the rebase method so every
#     fix keeps its own commit on the develop line.
#   - A conflicting pick halts the line: the bot commits the conflict markers,
#     opens a PR labeled sync-conflict, assigns the conflicting fix's author,
#     and skips further sweeps until the PR is closed.
#   - Picks applied earlier with a manual conflict resolution are recognized by
#     their "(cherry picked from commit <sha>)" trailer and never replayed.
#
# Repository settings this needs: "Allow auto-merge" and "Allow rebase merging".
set -euo pipefail

log() { echo "[sync] $*"; }

# ----------------------------------------------------------- target develop line
TARGET="${INPUT_TARGET:-}"
if [ -z "$TARGET" ]; then
  TARGET=$(git for-each-ref --sort=-version:refname --format='%(refname:lstrip=3)' \
    'refs/remotes/origin/develop/*' | head -1)
fi
if [ -z "$TARGET" ]; then
  log "no develop/* line exists yet; nothing to sync into"
  exit 0
fi
case "$TARGET" in
  develop/*) ;;
  *) log "refusing to sync into '$TARGET': not a develop/* branch"; exit 1 ;;
esac
log "target line: $TARGET"

SYNC_BRANCH="sync/main-into-${TARGET//\//-}"

# --------------------------------------------------- halt while a sync PR is open
if [ "$(gh pr list --base "$TARGET" --head "$SYNC_BRANCH" --state open \
  --json number --jq 'length')" != "0" ]; then
  log "a sync PR into $TARGET is still open; skipping this sweep"
  exit 0
fi

# --------------------------------------------------------- find outstanding work
outstanding=$(git rev-list --right-only --cherry-pick --no-merges --topo-order --reverse \
  "origin/${TARGET}...origin/main")
if [ -z "$outstanding" ]; then
  log "$TARGET is up to date with main"
  exit 0
fi

# A pick resolved by hand differs from the original patch, so patch-id dedup
# cannot see it; the -x trailer is the durable record of "already applied".
picked_trailers=$(git log "origin/${TARGET}" --format=%B \
  | grep -oE 'cherry picked from commit [0-9a-f]+' || true)

todo=""
for sha in $outstanding; do # shas are hex; safe to word-split
  subject=$(git log -1 --format=%s "$sha")
  case "$subject" in
    "chore: Bump Exposed version"*)
      log "skipping release commit: $subject"
      continue
      ;;
  esac
  if printf '%s\n' "$picked_trailers" | grep -qF "cherry picked from commit $sha"; then
    continue
  fi
  todo="$todo $sha"
done
if [ -z "${todo// /}" ]; then
  log "nothing to pick on $TARGET"
  exit 0
fi
log "cherry-picking onto $TARGET:"
for sha in $todo; do log "  $(git log -1 --format='%h %s' "$sha")"; done

# ------------------------------------------------------------------- the picks
git config user.name "exposed-sync-bot"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git checkout -B "$SYNC_BRANCH" "origin/${TARGET}"

applied=0
conflict=""
for sha in $todo; do
  if git cherry-pick -x "$sha"; then
    applied=$((applied + 1))
    continue
  fi
  if git diff --cached --quiet && git diff --quiet; then
    git cherry-pick --skip # empty pick: the change is already present
    log "skipped empty pick $sha"
    continue
  fi
  conflict="$sha"
  break
done

if [ -n "$conflict" ]; then
  short=$(git log -1 --format='%h' "$conflict")
  files_block=$(git diff --name-only --diff-filter=U | sed 's/^/  - /')
  git add -A
  git commit -m "chore: WIP pick of $short onto $TARGET - resolve conflict markers

(cherry picked from commit $conflict)

Conflicted files:
$files_block

Resolve the markers so the fix works on top of this line's features, then
push to this branch. The sweep halts while this PR is open and resumes with
whatever queued behind the conflict once it is closed."

  assignee=""
  pr_ref=$(git log -1 --format='%s' "$conflict" | grep -oE '\(#[0-9]+\)$' | tr -d '#()')
  if [ -n "$pr_ref" ]; then
    assignee=$(gh pr view "$pr_ref" --json author --jq '.author.login' 2>/dev/null || true)
  fi

  git push --force origin "HEAD:refs/heads/$SYNC_BRANCH"
  assign_args=()
  if [ -n "$assignee" ]; then
    assign_args=(--assignee "$assignee")
    log "conflict assigned to @$assignee (author of the conflicting fix)"
  fi
  pr_url=$(gh pr create --base "$TARGET" --head "$SYNC_BRANCH" \
    --title "chore: Sync main into $TARGET (conflict)" \
    --body "$(cat <<EOF
Automated cherry-pick sync from main into $TARGET hit a conflict.

The picks before the conflicting commit are already on this branch and valid.
The last one ($short) needs manual resolution; the conflict markers are
committed on the WIP commit of this branch.

How to resolve:
  1. Check out this branch.
  2. Resolve the markers so the fix works on this line.
  3. Amend the WIP commit, keeping the "(cherry picked from commit ...)" trailer.
  4. Push to this branch and merge.

The sweep skips further syncs while this PR is open, and resumes with
everything that queued up behind the conflict once it is closed.
EOF
)" "${assign_args[@]}")
  gh pr edit "$pr_url" --add-label sync-conflict 2>/dev/null \
    || log "note: add a 'sync-conflict' label once for filtering"
  log "conflict escalated: $pr_url"
  exit 0
fi

if [ "$applied" -eq 0 ]; then
  log "nothing to pick on $TARGET (all outstanding picks came out empty)"
  exit 0
fi

# -------------------------------------------------------------------- open the PR
git push --force origin "HEAD:refs/heads/$SYNC_BRANCH"
pr_url=$(gh pr create --base "$TARGET" --head "$SYNC_BRANCH" \
  --title "chore: Sync main into $TARGET" \
  --body "$(cat <<EOF
Automated cherry-pick sync from main into $TARGET.

Picked commits:
$(git log --format='  * %h %s' "origin/${TARGET}..HEAD")

Auto-merged with the rebase method once required checks pass, so every fix
lands as its own commit on the develop line. Never squash-merge this PR.
EOF
)")
if ! gh pr merge "$pr_url" --auto --rebase 2>/dev/null; then
  echo "::warning::auto-merge not enabled for $pr_url - enable 'Allow auto-merge' and 'Allow rebase merging' in repository settings, or merge it by hand"
fi
log "sync PR opened: $pr_url"
