# Contributing

## Release

To create a release, run `scripts/release.sh <new_version>` from the main
branch. This script will:

1. Automatically update the version in all relevant files
2. Create a commit with the version changes
3. Generate the `CHANGELOG.md` (skipped for prereleases)
4. Tag, push, and create a **draft** release on GitHub

The release is created as a draft — review the auto-generated notes at
[github.com/CodSpeedHQ/codspeed-jvm/releases](https://github.com/CodSpeedHQ/codspeed-jvm/releases)
and click "Publish" to ship.

### Pre-releases

For alpha versions (e.g. `0.2.0-alpha`), the script:

- Allows running from a non-main branch
- Skips `CHANGELOG.md` regeneration
- Marks the GitHub Release as `--latest=false` so it doesn't appear as the latest release
