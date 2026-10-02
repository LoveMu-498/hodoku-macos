# HoDoKu macOS contributor instructions

## Cloud development

- This checkout is the public source repository. Read `docs/cloud-development.md` for environment setup, test profiles and PR/local synchronization; do not upload an unrelated private workspace or its Git history.
- Linux dependency/bootstrap command: `bash script/setup_cloud.sh`. With dependencies installed, run `python3 script/check_cloud.py --profile core`; run the full selected cloud regression with `xvfb-run -a -s '-screen 0 1600x1200x24' python3 script/check_cloud.py --profile all`.
- Also run `python3 -m unittest discover -s test/packaging -p test_distribution_sources.py` and the same command with `-p test_public_privacy.py`. Add focused existing probes for the changed behavior. Read failures; never skip valid assertions or hardcode a pass.
- Cloud checks compile all Java sources/tests but execute a bounded selection. Linux/Xvfb is not macOS input, Aqua or packaged-app acceptance. Keep macOS-only packaging/native checks on a Mac. Do not run macOS packaging/install scripts in Linux.
- Work on a task branch and review the diff and validation before an authorized PR. Merge and local pull/integration require authorization for that operation; preserve dirty work, never reset/stash/overwrite it automatically. Do not bump the app version, create a DMG or publish a Release for ordinary cloud development.
- No AI API keys or personal tokens are needed for build/tests. Use synthetic fixtures and isolated data directories; never upload user settings, real replay data, internal notes or build caches. Public pattern scans are one check, not a guarantee of privacy.

## Source, distribution and authorization

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
