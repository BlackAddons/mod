#!/bin/sh
# The GitHub Release for a tag the `release` job already created and pushed: notes-github.md and every jar in
# release-jars/. Safe to rerun: an existing release is reused and only the missing jars are uploaded.
#
#   sh ci/github-release.sh <tag>
#
# Needs curl and jq; GITHUB_REPO and GITHUB_SYNC_TOKEN.
set -eu

tag=$1
: "${GITHUB_REPO:?}" "${GITHUB_SYNC_TOKEN:?}"
api=https://api.github.com/repos/$GITHUB_REPO
release=$(mktemp)
trap 'rm -f "$release"' EXIT

gh() {
  curl -fsS -H "Authorization: Bearer $GITHUB_SYNC_TOKEN" -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" "$@"
}

status=$(curl -sS -o "$release" -w '%{http_code}' -H "Authorization: Bearer $GITHUB_SYNC_TOKEN" \
  -H "Accept: application/vnd.github+json" "$api/releases/tags/$tag")
case $status in
  200)
    echo "reusing the existing release $tag"
    ;;
  404)
    # `-beta` and friends: GitHub's "latest" skips prereleases, and the legacy mod's update check reads it.
    case $tag in *-*) prerelease=true ;; *) prerelease=false ;; esac
    jq -n --arg tag "$tag" --rawfile body notes-github.md --argjson prerelease "$prerelease" \
      '{tag_name: $tag, name: $tag, body: $body, prerelease: $prerelease}' \
      | gh -X POST -d @- "$api/releases" > "$release"
    echo "created release $tag"
    ;;
  *)
    echo "GitHub answered $status for release $tag:"
    cat "$release"
    exit 1
    ;;
esac

id=$(jq -r .id "$release")
for jar in release-jars/*.jar; do
  name=$(basename "$jar")
  if jq -e --arg name "$name" '.assets | any(.name == $name)' "$release" >/dev/null; then
    echo "already attached: $name"
    continue
  fi
  gh -X POST -H "Content-Type: application/java-archive" --data-binary "@$jar" \
    "https://uploads.github.com/repos/$GITHUB_REPO/releases/$id/assets?name=$(jq -rn --arg n "$name" '$n | @uri')" >/dev/null
  echo "attached $name"
done
jq -r .html_url "$release"
