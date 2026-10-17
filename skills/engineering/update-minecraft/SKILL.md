---
name: update-minecraft
description: Check, stage, verify and apply updates to this Windows Minecraft server, with pinned versions, stopped-server application and protected player data.
---

Read `docs/operations/update-guide.md` and the lock files in `manifests`.

For user-requested updates from this project's published releases, use `scripts/update-release.ps1` after the server exits. It validates the lightweight release package, downloads changed pinned components, retains locally modified and protected configuration, and records conflicts as `.new` files under `updates`. Per the owner's explicit policy, never automatically create a full-server update backup: Backuper handles scheduled backups. Temporary copies of changed files allow copy-failure rollback and are removed after success. Never bypass data protections. Maintainers publish `scripts/build-update.ps1` output; update `release-version.json` first.

Use `scripts/check-updates.ps1` to report versions; `-Download` creates candidates without applying them. Examine upstream compatibility and release notes, especially the Geyser Java protocol, AuthMe platform and EssentialsX development build. Stay on Minecraft 26.2 unless the user requests a version migration.

Only after the requested update scope is clear, stop the server with its console command and use `scripts/apply-update.ps1 -Stage <directory>`. The script verifies candidate hashes and replaces only selected binaries and locks. It must not invoke automatic full-server backups either.

Run checks appropriate to the changed code. For runtime changes, start and inspect enabled plugins and errors, then record client acceptance limits. Do not claim an update-time full restore point exists; use an available scheduled or explicitly requested manual backup if recovery is needed. Keep secrets out of Git. See [update documentation](../../../docs/engineering/update-minecraft.md).
