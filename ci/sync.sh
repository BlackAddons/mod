#!/bin/sh
# Keeps GitLab main (canonical) and GitHub main (where outside pull requests land) equal. Runs in GitLab CI
# under one resource_group, on every GitLab push to main and when GitHub reports a merge-queue merge.
#   equal              -> push missing tags, lift the freeze if it is on, delete merged sync/ branches
#   one side behind    -> fast-forward it
#   diverged           -> merge GitHub into GitLab as "GitLab", gradle build-all, push to both
#   merge/build fails  -> freeze GitHub merges, open a GitLab MR with GitHub's state, fail the job
#
# Needs git, curl and jq; GITHUB_SYNC_TOKEN, GITLAB_SYNC_TOKEN and GITHUB_REPO; DISCORD_WEBHOOK is optional.
set -eu

: "${GITHUB_SYNC_TOKEN:?}" "${GITLAB_SYNC_TOKEN:?}" "${GITHUB_REPO:?}"
BRANCH=$CI_DEFAULT_BRANCH
FREEZE_RULESET=sync-freeze
GH_API=https://api.github.com/repos/$GITHUB_REPO
GL_API=$CI_API_V4_URL/projects/$CI_PROJECT_ID

gh_api() {
  method=$1; path=$2; shift 2
  curl -fsS -X "$method" -H "Authorization: Bearer $GITHUB_SYNC_TOKEN" -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" "$GH_API$path" "$@"
}
gl_api() {
  method=$1; path=$2; shift 2
  curl -fsS -X "$method" -H "PRIVATE-TOKEN: $GITLAB_SYNC_TOKEN" "$GL_API$path" "$@"
}
notify() {
  echo "NOTIFY: $1"
  [ -z "${DISCORD_WEBHOOK:-}" ] && return 0
  curl -fsS -H 'Content-Type: application/json' -d "$(jq -n --arg c "$1" '{content: $c}')" "$DISCORD_WEBHOOK" >/dev/null || true
}

freeze_id() { gh_api GET /rulesets | jq -r --arg n "$FREEZE_RULESET" '.[] | select(.name == $n) | .id'; }
frozen() {
  id=$(freeze_id)
  [ -n "$id" ] && [ "$(gh_api GET "/rulesets/$id" | jq -r .enforcement)" = active ]
}
set_freeze() {
  id=$(freeze_id)
  [ -n "$id" ] || { echo "no $FREEZE_RULESET ruleset on GitHub"; return 1; }
  gh_api PUT "/rulesets/$id" -d "{\"enforcement\": \"$1\"}" >/dev/null
}

# Merges that came from GitHub get a comment with their pull request and its author. Read anonymously:
# the repository is public, and the sync token needs no pull-request scope this way.
comment_prs() {
  for sha in $(git rev-list --merges "$1..$2"); do
    pr=$(curl -fsS -H "Accept: application/vnd.github+json" "$GH_API/commits/$sha/pulls" \
      | jq -c '[.[] | select(.merged_at != null)][0] // empty') || continue
    [ -n "$pr" ] || continue
    note=$(printf '%s' "$pr" | jq -r '"Merged on GitHub from [pull request \(.number)](\(.html_url)) by [\(.user.login)](\(.user.html_url))."')
    gl_api POST "/repository/commits/$sha/comments" --data-urlencode "note=$note" >/dev/null && echo "commented on $sha"
  done
}

conflict() {
  set_freeze active
  branch="sync/github-$(git rev-parse --short "$2")"
  git push -q -f gl "$2:refs/heads/$branch"
  url=$(gl_api GET "/merge_requests?state=opened&source_branch=$branch" | jq -r '.[0].web_url // empty')
  if [ -z "$url" ]; then
    desc="GitHub \`$BRANCH\` has commits that GitLab \`$BRANCH\` does not, and the two do not merge into a green build.
GitHub merges are frozen until this merge request is merged.

1. Resolve here (conflict editor) or locally: \`git fetch origin && git checkout $branch && git merge origin/$BRANCH\`, fix, \`gradle build-all\`, push.
2. Merge this MR with a merge commit. The sync job then pushes \`$BRANCH\` to GitHub and lifts the freeze."
    url=$(gl_api POST /merge_requests --data-urlencode "source_branch=$branch" --data-urlencode "target_branch=$BRANCH" \
      --data-urlencode "title=Sync: resolve conflict with GitHub $BRANCH" --data-urlencode "remove_source_branch=true" \
      --data-urlencode "description=$desc" | jq -r .web_url)
  fi
  notify "Mirror: GitLab and GitHub conflict. GitHub merges are frozen until this is merged: $url"
}

# A clone of its own: the job's checkout is one fixed commit, and the repair merge needs both branch tips.
work=$(mktemp -d)
cd "$work"
git init -q
git config user.name GitLab
git config user.email "noreply@$CI_SERVER_HOST"
git remote add gl "https://oauth2:$GITLAB_SYNC_TOKEN@${CI_SERVER_URL#https://}/$CI_PROJECT_PATH.git"
git remote add gh "https://x-access-token:$GITHUB_SYNC_TOKEN@github.com/$GITHUB_REPO.git"

for attempt in 1 2 3 4 5; do
  git fetch -q gl "+refs/heads/$BRANCH:refs/remotes/gl/$BRANCH" '+refs/tags/*:refs/tags/*'
  git fetch -q gh "+refs/heads/$BRANCH:refs/remotes/gh/$BRANCH"
  G=$(git rev-parse "gl/$BRANCH")
  H=$(git rev-parse "gh/$BRANCH")

  if [ "$G" = "$H" ]; then
    git push -q gh 'refs/tags/*:refs/tags/*' || echo "some tags were not pushed to GitHub"
    if frozen; then
      set_freeze disabled
      notify "Mirror: GitLab and GitHub are equal again, GitHub merges unfrozen."
    fi
    # Sync branches are already in main once their MR is merged; a merge by push leaves them behind.
    git fetch -q gl '+refs/heads/sync/*:refs/remotes/gl-sync/*' 2>/dev/null || true
    for ref in $(git for-each-ref --format='%(refname)' refs/remotes/gl-sync/); do
      if git merge-base --is-ancestor "$ref" "$G"; then
        git push -q gl ":refs/heads/sync/${ref#refs/remotes/gl-sync/}" && echo "deleted merged sync/${ref#refs/remotes/gl-sync/}"
      fi
    done
    echo "in sync at $G"
    exit 0
  elif git merge-base --is-ancestor "$G" "$H"; then
    git push -q gl "$H:refs/heads/$BRANCH" || { echo "GitLab moved, retrying"; continue; }
    comment_prs "$G" "$H"
    echo "GitLab fast-forwarded to $H"
    continue
  elif git merge-base --is-ancestor "$H" "$G"; then
    git push -q gh "$G:refs/heads/$BRANCH" || { echo "GitHub moved, retrying"; continue; }
    echo "GitHub fast-forwarded to $G"
    continue
  fi

  if frozen; then
    echo "diverged and frozen: waiting for the sync merge request"
    exit 1
  fi
  git checkout -q -f -B repair "$G"
  # Each side green on its own does not make the merge green, so the merge is built the way verify builds.
  if git merge -q --no-ff --no-edit -m "Merge GitHub $BRANCH into GitLab $BRANCH" "$H" && gradle build-all --stacktrace; then
    git push -q gh "repair:refs/heads/$BRANCH" || { echo "GitHub moved during repair, retrying"; continue; }
    git push -q gl "repair:refs/heads/$BRANCH" || { echo "GitLab moved during repair, retrying"; continue; }
    comment_prs "$G" "$H"
    notify "Mirror: GitLab and GitHub diverged and were merged automatically ($(git rev-parse --short repair))."
    continue
  fi
  git merge --abort 2>/dev/null || true
  conflict "$G" "$H"
  exit 1
done
echo "still moving after 5 attempts; the next push or trigger picks it up"
exit 1
