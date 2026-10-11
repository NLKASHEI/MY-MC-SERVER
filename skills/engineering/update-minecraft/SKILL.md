---
name: update-minecraft
description: Check, stage, verify and apply updates to this Windows Minecraft server, with pinned versions, stopped-server backups and recovery.
---

Read `docs/operations/update-guide.md` and the lock files in `manifests`.

For user-requested updates from this project's published releases, use `scripts/update-release.ps1` after the server exits. It validates the lightweight release package, downloads changed pinned components, creates a stopped-server backup, retains locally modified and protected configuration, and records conflicts as `.new` files. Never bypass those protections to force configuration or world data into a live installation. Maintainers publish `scripts/build-update.ps1` output alongside the full deployment package; update `release-version.json` first.

Use `scripts/check-updates.ps1` to report versions; `-Download` creates candidates without applying them. Examine upstream compatibility and release notes, especially the Geyser Java protocol, AuthMe platform and EssentialsX development build. Stay on Minecraft 26.2 unless the user requests a version migration.

Only after the requested update scope is clear, stop the server with its console command and use `scripts/apply-update.ps1 -Stage <directory>`. The script verifies candidate hashes and creates a full backup before replacement. Keep the backup path in the change notes.

Start, inspect enabled plugins and errors, test Java registration, Bedrock login, economy, menu, claims and reward persistence. Restore the whole backup if a failed migration changed player data. Keep secrets out of Git. See [update documentation](../../../docs/engineering/update-minecraft.md).
