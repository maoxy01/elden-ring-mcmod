# Third-Party Mods

> 2026-10-21 repository note: the repository is now public. The table below describes the historical 0.1.0 prototype, including its former Epic Fight dependency; it is not the current dependency lock. Current development uses vanilla combat on Minecraft 1.21.1 / NeoForge 21.1.219. Read `modpack/mods.lock.json` for the seven baseline dependencies and `modpack/candidates/*.lock.json` for separate candidates. Do not combine candidates or treat an old table as the installed pack.

New local Mojang modelling references, raw game extractions, third-party JARs, credentials, runtime instances and caches are excluded from this sync. Original project assets retain their existing per-directory notices; this note adds no new licence for third-party works. See [repository sync scope](REPOSITORY-ORGANIZATION-20261021.md).

## Historical prototype notice

This private combat prototype downloads unmodified releases from official Modrinth CDN URLs.
No third-party JAR, shader pack, Minecraft file or ripped Elden Ring asset is embedded in the archive.
Version IDs and original binary hashes are recorded in `eldencombat-pack.json` and the modpack index.

| Project | Pinned Version | License Declared by JAR | Official Page |
| --- | --- | --- | --- |
| Epic Fight | 21.17.3.1 | Code GPL-3.0; assets All Rights Reserved | https://modrinth.com/mod/epic-fight |
| Sodium | 0.6.13 | Polyform-Shield-1.0.0 | https://modrinth.com/mod/sodium |
| Iris | 1.8.12 | LGPL-3.0-only | https://modrinth.com/mod/iris |
| Entity Culling | 1.11.3 | tr7zw Protective License | https://modrinth.com/mod/entityculling |
| Cloth Config | 15.0.140 | GNU LGPLv3 | https://modrinth.com/mod/cloth-config |
| FerriteCore | 7.0.3 | MIT | https://modrinth.com/mod/ferrite-core |
| Lithium | 0.15.4 | LGPL-3.0-only | https://modrinth.com/mod/lithium |
| JEI | 19.27.0.340 | MIT | https://modrinth.com/mod/jei |

Epic Fight is an API dependency, not a modified or vendored fork. Its source release was inspected for integration; its animation files remain in the original dependency.
Iris bundles parts of Forgified Fabric API; Entity Culling bundles TRansition and TRender. Preserve the original projects' licenses and notices.
The prototype is fan work and is not an official FromSoftware product. This file does not grant rights to third-party names, code or assets.
