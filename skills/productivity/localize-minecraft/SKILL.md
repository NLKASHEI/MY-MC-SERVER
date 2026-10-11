---
name: localize-minecraft
description: Translate player-facing Minecraft plugin text and menus into Simplified Chinese without changing commands, YAML keys, permissions or placeholders.
---

Read `GLOSSARY.md` and `docs/operations/server-guide.md`. Prefer the plugin's maintained Chinese language pack, then edit the remaining player-visible text in `config/survival`.

Preserve command names, permission nodes, configuration keys, indentation, color codes, placeholders and numbered substitution arguments. Do not translate identifier-like tokens. Keep tutorials short and action-oriented, and use the same terms across menu, chat and shop.

Validate YAML and Skript by starting the test server. Inspect actual Java inventory menus and Bedrock forms when clients are available. Report partial translation honestly; untranslated administrator-only diagnostics do not prevent player-facing localization. See [localization documentation](../../../docs/productivity/localize-minecraft.md).
