# Contributing

## Release

To create a release, run `scripts/release.sh <new_version>` from the main
branch. This script will:

1. Automatically update the version in all relevant files
2. Create a commit with the version changes
3. Generate the `CHANGELOG.md` (skipped for prereleases)
4. Tag and create the release on GitHub
