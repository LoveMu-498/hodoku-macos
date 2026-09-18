# HoDoKu macOS contributor instructions

- Preserve the existing application behavior and original copyright/license notices; GPL-3.0-or-later applies to the main application. Independent components keep their own licenses.
- Read UPSTREAM.md, THIRD_PARTY_NOTICES.md and docs/open-source-release.md before distribution work.
- src/version.properties is the single version source. Update CHANGES.md with dates; keep UI, Info.plist, DMG name and release tag consistent. Build binaries from the exact committed release source.
- Only commit/push or advance a GitHub Release when the user explicitly requests it. Local development never automatically publishes.
- Keep source code in Git; distribute DMG and matching Java source materials together as Release assets. Preserve runtime licenses, full corresponding sources and build scripts.
- Before uploading, scan the selected public files and release contents for credentials, AI API keys, access tokens, private keys, personal paths and user state. Report only locations, never secret values. Block publication of detected private data and do not alter or revoke credentials without authorization.
- Publish only necessary macOS source, resources and build documentation. Exclude internal notes, contact drafts, backups, logs, account configuration and user progress. Do not reuse private Git history for a first public export.
- Run relevant build and packaging checks; audit native dependencies and verify the relocated packaged launcher. Do not substitute local compilation for distribution testing. Preserve installed apps and personal settings.
- Keep validation claims bounded to the tested hardware/system and actual checks. Ad-hoc signature verification is not Apple notarization.
