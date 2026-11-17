#!/bin/sh
# Release notes for both sides from the commits since the previous release tag: notes-gitlab.md and
# notes-github.md. Same sections, grouped by ci/commit-types; each side links commits and the full changelog on
# its own host and credits authors the way it shows people:
#   GitLab: @user (a GitLab noreply address, or a GitLab account with that public email), else a link to the
#           GitHub login, else the git name
#   GitHub: @login, else the git name
# The `release` job runs this before anything is published, so a failure here leaves nothing half-released.
#
#   sh ci/release-notes.sh <tag>
#
# Needs git, curl and jq; CI_PROJECT_URL, CI_SERVER_HOST, CI_API_V4_URL and GITHUB_REPO. GITHUB_SYNC_TOKEN and
# GITLAB_SYNC_TOKEN are optional: without them authors are credited by name.
set -eu

tag=$1
: "${CI_PROJECT_URL:?}" "${CI_SERVER_HOST:?}" "${CI_API_V4_URL:?}" "${GITHUB_REPO:?}"
head=${CI_COMMIT_SHA:-HEAD}
github_url=https://github.com/$GITHUB_REPO
sep=$(printf '\037')
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

warn() { echo "warning: $1" >&2; }
lowercase() { printf '%s' "$1" | tr 'A-Z' 'a-z'; }
# The second column of the first line in file $1 whose first column is $2.
lookup() { awk -v key="$2" '$1 == key { print $2; exit }' "$1"; }

types=$(sed -e 's/#.*//' -e '/^[[:space:]]*$/d' ci/commit-types)

# --match keeps this to release tags: a rollback tag like `pre-26.2-bump` would otherwise truncate the notes.
prev=$(git describe --tags --abbrev=0 --match 'v*' "$head" 2>/dev/null || true)

git log "${prev:+$prev..}$head" --no-merges --format="%H$sep%h$sep%s$sep%an$sep%ae" > "$tmp/log"
: > "$tmp/commits"
while IFS=$sep read -r sha short subject name email; do
  # The version bumps the release job pushes itself.
  [ "$(lowercase "$email")" = "noreply@$CI_SERVER_HOST" ] && continue
  # `git revert` writes `Revert "<subject>"`; filed as `revert: <subject>` it lands under Reverts.
  case $subject in
    'Revert "'*'"') subject=${subject#Revert \"}; subject="revert: ${subject%\"}" ;;
  esac
  printf '%s\n' "$sha$sep$short$sep$subject$sep$name$sep$email" >> "$tmp/commits"
done < "$tmp/log"

# "<sha> <login>" for every commit GitHub links to an account, 100 commits per GraphQL query.
github_logins() {
  [ -n "${GITHUB_SYNC_TOKEN:-}" ] || return 0
  cut -d "$sep" -f 1 "$tmp/commits" | awk -v owner="${GITHUB_REPO%/*}" -v repo="${GITHUB_REPO#*/}" '
    function query(fields) { return sprintf("{ repository(owner: \"%s\", name: \"%s\") { %s} }", owner, repo, fields) }
    { fields = fields sprintf("c%s: object(oid: \"%s\") { ... on Commit { author { user { login } } } } ", $0, $0) }
    NR % 100 == 0 { print query(fields); fields = "" }
    END { if (fields != "") print query(fields) }
  ' > "$tmp/queries"
  while IFS= read -r query; do
    response=$(jq -n --arg q "$query" '{query: $q}' \
      | curl -fsS -H "Authorization: Bearer $GITHUB_SYNC_TOKEN" -H "User-Agent: blackaddons-release" \
          -d @- https://api.github.com/graphql) || return 1
    printf '%s' "$response" \
      | jq -r '.data.repository | to_entries[] | select(.value.author.user.login != null)
               | "\(.key[1:]) \(.value.author.user.login)"' || return 1
  done < "$tmp/queries"
}

# The GitLab username whose public email is $1, if exactly one account has it.
gitlab_user() {
  [ -n "${GITLAB_SYNC_TOKEN:-}" ] || return 0
  response=$(curl -fsS -H "PRIVATE-TOKEN: $GITLAB_SYNC_TOKEN" \
    "$CI_API_V4_URL/users?search=$(jq -rn --arg e "$1" '$e | @uri')") || { warn "GitLab user lookup failed for $1"; return 0; }
  printf '%s' "$response" | jq -r 'if length == 1 then .[0].username else empty end'
}

# <id>-<username>@users.noreply.<host> names its account outright.
noreply_user() {
  case $(lowercase "$1") in
    [0-9]*-*"@users.noreply.$CI_SERVER_HOST") local_part=${1%%@*}; echo "${local_part#*-}" ;;
  esac
}

github_logins > "$tmp/logins" || { warn "GitHub logins unavailable, crediting by name"; : > "$tmp/logins"; }

: > "$tmp/users"
cut -d "$sep" -f 5 "$tmp/commits" | sort -u | while IFS= read -r email; do
  [ -n "$(noreply_user "$email")" ] && continue
  user=$(gitlab_user "$email")
  if [ -n "$user" ]; then printf '%s %s\n' "$email" "$user" >> "$tmp/users"; fi
done

gitlab_credit() {
  user=$(noreply_user "$3")
  [ -n "$user" ] || user=$(lookup "$tmp/users" "$3")
  if [ -n "$user" ]; then echo "@$user"; return; fi
  login=$(lookup "$tmp/logins" "$1")
  if [ -n "$login" ]; then echo "[$login](https://github.com/$login)"; else echo "$2"; fi
}

github_credit() {
  login=$(lookup "$tmp/logins" "$1")
  if [ -n "$login" ]; then echo "@$login"; else echo "$2"; fi
}

section() {
  lines=$(awk -F "$sep" -v kind="$1" '$1 == kind { print $2 }' "$tmp/entries")
  if [ -n "$lines" ]; then printf '### %s\n%s\n\n' "$2" "$lines"; fi
}

# $1 = gitlab | github, $2 = commit URL prefix, $3 = full-changelog URL or empty.
render() {
  : > "$tmp/entries"
  while IFS=$sep read -r sha short subject name email; do
    # Classification reads the SUBJECT only, so a body line starting with "feat:" files nothing.
    kind=$(printf '%s\n' "$subject" | sed -nE 's/^([A-Za-z]+)(\([^)]+\))?!?:.*/\1/p' | tr 'A-Z' 'a-z')
    if [ -n "$kind" ] && printf '%s\n' "$types" | grep -q "^$kind:"; then
      text=$(printf '%s\n' "$subject" | sed -E 's/^[A-Za-z]+(\([^)]+\))?!?:[[:space:]]*//')
    else
      kind='~other'
      text=$subject
    fi
    printf '%s%s* %s ([%s](%s/%s)) by %s\n' "$kind" "$sep" "$text" "$short" "$2" "$sha" \
      "$("$1"_credit "$sha" "$name" "$email")" >> "$tmp/entries"
  done < "$tmp/commits"

  printf '## Changes\n\n'
  printf '%s\n' "$types" > "$tmp/types"
  while IFS=: read -r kind title; do
    section "$kind" "$title"
  done < "$tmp/types"
  # Kept on purpose: not every change fits the taxonomy, and the hook is bypassable.
  section '~other' 'Other Changes'
  if [ -n "$3" ]; then echo "**Full changelog**: $3"; fi
}

render gitlab "$CI_PROJECT_URL/-/commit" "${prev:+$CI_PROJECT_URL/-/compare/$prev...$tag}" > notes-gitlab.md
render github "$github_url/commit" "${prev:+$github_url/compare/$prev...$tag}" > notes-github.md
for notes in notes-gitlab.md notes-github.md; do
  echo "===== $notes"
  cat "$notes"
done
