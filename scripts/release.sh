#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/release.sh <version>
#
# <version> is a Maven-style version without the leading 'v', e.g. 0.2.0
# or 0.2.0-beta.1. The git tag will be prefixed with 'v' (vX.Y.Z).
#
# This script bumps the jmh-fork version everywhere it's pinned,
# regenerates CHANGELOG.md from conventional commits (skipped for
# alpha/beta/rc), commits, tags, and prints the push command.
# It does NOT push.
#
# Based on this: https://github.com/CodSpeedHQ/codspeed-cpp/blob/main/scripts/release.sh

# First and only argument is the version number
VERSION_NO_V=${1}   # The version number without the 'v' prefix
VERSION=v$1         # The version number, prefixed with 'v'

# Validate version format (X.Y.Z or X.Y.Z-alpha where X, Y, Z are integers)
if [[ ! "$VERSION_NO_V" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-alpha)?$ ]]; then
    echo "Error: Invalid version format '$VERSION_NO_V'"
    echo "Usage: $0 <version>"
    echo "  Version must be in format X.Y.Z or X.Y.Z-alpha (e.g., 1.2.3 or 1.2.3-alpha)"
    exit 1
fi

# Check is on main (unless releasing an alpha version)
if [[ ! "$VERSION_NO_V" =~ -alpha ]]; then
    if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
        echo "Not on main branch (only alpha releases can be made from non-main branches)"
        exit 1
    fi
fi

# Check that GITHUB_TOKEN is set
if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "GITHUB_TOKEN is not set. Trying to fetch it from gh"
    GITHUB_TOKEN=$(gh auth token)
fi

# Check that the tag doesn't already exist
if git rev-parse "$VERSION" >/dev/null 2>&1; then
    echo "error: tag $VERSION already exists" >&2
    exit 1
fi


## Bump all the versions in the repo
##

# List of files to update with version numbers.
VERSION_FILES=(
    "jmh-fork/build.gradle.kts"
    "examples/example-maven/pom.xml"
    "examples/example-gradle/build.gradle.kts"
)

# Get current version from jmh-fork/build.gradle.kts
PREVIOUS_VERSION=$(awk -F'"' '/version = / {print $2; exit}' jmh-fork/build.gradle.kts)

# Prompt the release version
echo "Previous version: ${PREVIOUS_VERSION}"
echo "New version:      ${VERSION_NO_V}"
read -p "Are you sure you want to release this version? (y/n): " confirm
if [ "$confirm" != "y" ]; then
    echo "Aborting release"
    exit 1
fi

# Update version in all relevant files
echo "Updating version numbers in source files..."

# Use sed in a cross-platform way (macOS requires empty string after -i)
sed_inplace() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

for file in "${VERSION_FILES[@]}"; do
    sed_inplace "s/${PREVIOUS_VERSION}/${VERSION_NO_V}/g" "$file"
    echo "  Updated $file"
done

# Bump the jmh-fork poms (parent + all child modules) in one shot
(cd jmh-fork && mvn versions:set -DnewVersion="$VERSION_NO_V" -DgenerateBackupPoms=false -q)
echo "  Updated jmh-fork/**/pom.xml (via mvn versions:set)"

# Commit version changes
git add \
    jmh-fork \
    examples/example-maven/pom.xml \
    examples/example-gradle/build.gradle.kts
git cliff -o CHANGELOG.md --tag "$VERSION" --github-token "$GITHUB_TOKEN"
git add CHANGELOG.md
git commit -m "chore: Release $VERSION"
git tag -s "$VERSION" -m "Release $VERSION"
git push origin HEAD
git push origin "$VERSION"

# Create GitHub release
if [[ "$VERSION_NO_V" =~ -alpha ]]; then
    gh release create "$VERSION" -t "$VERSION" --generate-notes --latest=false --draft
else
    gh release create "$VERSION" -t "$VERSION" --generate-notes --latest --draft
fi
