---
name: release
description: Release checklist for FreeXmlToolkit — version bump location, VersionUtil resolution chain, GitHub-release build trigger, and the tag-pinning gotcha when a release needs a fix commit.
---

# Release Checklist

The version is defined in a **single location**:

1. **build.gradle.kts** (line ~35): `version = "X.Y.Z"`

The Gradle build generates `build-info.properties` from this value during
`processResources`. At runtime, `org.fxt.freexmltoolkit.util.VersionUtil`
resolves the version (JAR manifest → `build-info.properties` → fallback),
so the About dialog, update check, etc. always show the current version —
including in IDE / `./gradlew run` mode.

**Release builds** are triggered by creating a GitHub release (`release: created`)
and are pinned to the tag's commit — a `gh run rerun` rebuilds the OLD commit.
If the release needs a fix commit: delete the release and tag
(`gh release delete <tag> --yes` + `git push origin :refs/tags/<tag>`), push the
fix, and recreate the release (lossless while no assets are attached).
