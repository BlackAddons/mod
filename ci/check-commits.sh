#!/bin/sh
# The Conventional Commits rules, in one place: the commit-msg hook, the GitLab merge-request job and the
# GitHub verify workflow all run this, so a message the hook accepts is exactly one CI accepts.
#
#   sh ci/check-commits.sh <base> <head>    every commit in base..head
#   sh ci/check-commits.sh --file <path>    one message file, as the hook gets it
#
# Run from the repository root. The types come from ci/commit-types.
set -eu

types_file=ci/commit-types
types=$(sed -e 's/#.*//' -e '/^[[:space:]]*$/d' "$types_file" | cut -d: -f1 | tr '\n' '|' | sed 's/|$//')
[ -n "$types" ] || { echo "check-commits: cannot read $types_file" >&2; exit 2; }

# Prints why the message in file $1 breaks the rules; prints nothing when it is fine.
problems() {
    subject=$(head -n 1 "$1")
    # git's own generated commits, and the rebase autosquash markers.
    case $subject in
        Merge\ *|Revert\ *|fixup!\ *|squash!\ *|amend!\ *) return 0 ;;
    esac
    printf '%s\n' "$subject" | grep -Eq "^($types)(\([a-z0-9._/-]+\))?!?: .+" \
        || echo 'subject must start with a type, e.g. "feat: ..."'
    # 72 keeps `git log --oneline` and the GitLab and GitHub commit lists from truncating.
    [ "${#subject}" -le 72 ] || echo "subject is ${#subject} chars, keep it to 72"
    case $subject in
        *.) echo 'subject must not end with a period' ;;
    esac
    # A body needs a blank line after the subject, or git treats both as one paragraph.
    [ -z "$(sed -n '2p' "$1")" ] || echo 'leave line 2 blank - the subject ends at line 1'
    if grep -qi '^Co-Authored-By:.*claude' "$1"; then
        echo 'drop the Co-Authored-By: Claude trailer'
    fi
}

# Prints one rejected message: its subject, then each problem.
report() {
    printf '\n  %s\n' "$1" >&2
    printf '%s\n' "$2" | sed 's/^/    - /' >&2
}

usage() {
    printf '\n  Conventional Commits:  <type>[(scope)][!]: <description>\n' >&2
    printf '  types: %s\n' "$(echo "$types" | tr '|' ' ')" >&2
    printf '  e.g.   feat(hud): show overflow mana in StatBars\n' >&2
    printf '         fix!: drop the no-op Modded spoof mode\n\n' >&2
}

if [ "${1:-}" = --file ]; then
    found=$(problems "$2")
    [ -z "$found" ] && exit 0
    report "commit rejected: $(head -n 1 "$2")" "$found"
    usage
    exit 1
fi

base=$1
head=$2
message=$(mktemp)
trap 'rm -f "$message"' EXIT
bad=0
for sha in $(git rev-list --reverse "$base..$head"); do
    git log -1 --format=%B "$sha" > "$message"
    found=$(problems "$message")
    if [ -n "$found" ]; then
        report "$(git log -1 --format='%h %s' "$sha")" "$found"
        bad=1
    fi
done

if [ "$bad" = 1 ]; then
    usage
    exit 1
fi
echo "commit messages are fine"
