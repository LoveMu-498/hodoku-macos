# HoDoKu macOS contributor instructions

- Preserve the existing application behavior and original copyright/license notices; GPL-3.0-or-later applies to the main application. Independent components keep their own licenses.
- Read UPSTREAM.md, THIRD_PARTY_NOTICES.md and docs/open-source-release.md before distribution work.
- src/version.properties is the single version source. Update CHANGES.md with dates; keep UI, Info.plist, DMG name and release tag consistent. Build binaries from the exact committed release source.
- Only perform commits, uploads/pushes, branch merges, PR creation/merges, tags or Releases when explicitly requested for the current task. Prior publication and available credentials are not ongoing authorization. Local development never automatically publishes or merges.
- Keep source code in Git; distribute DMG and matching Java source materials together as Release assets. Preserve runtime licenses, full corresponding sources and build scripts.
- Before uploading, scan the selected public files and release contents for credentials, AI API keys, access tokens, private keys, personal paths and user state. Report only locations, never secret values. Block publication of detected private data and do not alter or revoke credentials without authorization.
- Publish only necessary macOS source, resources and build documentation. Exclude internal notes, contact drafts, backups, logs, account configuration and user progress. Do not reuse private Git history for a first public export.
- Run relevant build and packaging checks; audit native dependencies and verify the relocated packaged launcher. Do not substitute local compilation for distribution testing. Preserve installed apps and personal settings.
- Keep validation claims bounded to the tested hardware/system and actual checks. Ad-hoc signature verification is not Apple notarization.

- Do not proactively contact upstream maintainers. Explain provenance, attribution, modifications and licenses in project documentation, without implying endorsement; contact only upon a new explicit user request.
- Normal builds produce `.app` only. Create a DMG only when the user explicitly asks for a sharing build or a release task that includes a DMG.
- For `.app`, follow current task authorization: either build/verify at a separate path and let the user choose replacement or coexistence, or install over the specified app when replacement is already explicitly authorized. Do not ask again for the same authorization. If installation intent is unclear, build first and ask before replacing.
- Before authorized replacement, retain a recoverable old bundle and preserve user data. Save/quit a running instance normally; never force termination at the cost of unsaved work. Renaming an app does not isolate shared user settings.
- Prepare a separate public copy before an authorized upload. Review selected files, intended Git history and all attachments for credentials and private information, including personal email, paths and image metadata. Use a public/noreply commit identity. Automated patterns do not guarantee absence of every kind of private data.
