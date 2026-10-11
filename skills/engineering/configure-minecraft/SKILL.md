---
name: configure-minecraft
description: Configure this project's Paper survival plugins, permissions, menus, shop and crossplay while preserving player data and vanilla survival behavior.
---

Read `AGENTS.md`, `SCOPE.md`, and `docs/operations/server-guide.md` from the project root.

Use `config/survival` as the maintained template. Compare templates with the current generated file before editing because plugins can add keys during upgrades. On an established server, merge only the requested keys; never replace databases, worlds, or entire plugin directories.

Keep Java offline login protected by AuthMe, and Bedrock authenticated through Floodgate. The current profile requires password registration on both clients. Do not add login bypasses based only on usernames or prefixes.

Grant individual player permission nodes in `permissions.yml`; do not grant admin wildcards. Keep rarity, survival progression and currency supply in mind when changing shops or rewards.

After changes, run a disposable server or the local test helper while no other server is running, inspect logs, and record client checks still pending. See [configuration documentation](../../../docs/engineering/configure-minecraft.md).
