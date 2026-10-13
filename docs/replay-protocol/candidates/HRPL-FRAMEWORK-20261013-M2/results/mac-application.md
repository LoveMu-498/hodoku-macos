# Mac M2 implementation and validation

Package: `HRPL-FRAMEWORK-20261013-M2`. Validation date: 2026-10-13. Environment: macOS 27.2 arm64, Temurin 21.0.12.1; all application source and test source compiled with `--release 8`. Runs used synthetic replay content and isolated application data. Exact relevant source hashes are in `../source-hashes.json`; the four new helper classes and their four probes are in `../reference-java/`.

The real Mac paths now import HRPL v4 profiles 0.1/0.2, view frozen frames, explicitly export the cross-platform profile, save untouched imports byte for byte, archive source files and start independent solving from a selected snapshot. Ordinary native recording retains its original data path. Complete PNGs are generated on the explicit cross-platform export path from stored replay evidence; full explanation text is preserved separately in the scrollable phaseLabel.

| Validation | Result | What was actually checked |
| --- | --- | --- |
| Complete source/test compilation | PASS | Fixed JDK; Java 8 compatibility |
| `ReplayFrameCodecProbe` | 111 checks PASS | Independent Java codec; all 18 W1 expected outcomes; canonical bytes, exact saves, integer/text/binding/PNG limits and 0.2 long-label bounds |
| `ReplayFrameRendererProbe` | 132 checks PASS | Five generic primitives, actual W1 objects, alpha, stable z order, inert roles/anchors, circle/cross outline gaps, arrow direction, even-odd fill, Unicode text, margin fitting and deterministic common board |
| `ReplayFrameCaptureProbe` | 46 headless / 49 GUI PASS | Stored native proof/result capture, complete board view, explanation and snapshot preservation; a displayed native window was also checked with Robot |
| `ReplayFrameworkApplicationProbe` | 46 headless / 54 GUI PASS | Actual import/export/direct-save/view/archive/continuation paths, explicit export button availability, long description preservation, two sizes and host themes, source/live-board isolation |
| `ReplayWindowsV3Probe` | 38 PASS | Existing v3 import/export, frozen viewing, raw preservation and malformed data boundaries |
| `ReplayWindowsO11Probe` | 31 headless / 37 GUI PASS | Existing O11 roles, MT/raw preservation, rendering and branch isolation |
| Replay core / long text / branch / authored evidence probes | PASS | Existing timing/persistence and continuation behavior, long Unicode save protection, authored proof and source-isolation regressions |

GUI totals include the common headless checks; these columns are not additive independent coverage. A codec rejection recorded as `FAIL` in `java-codec.json` is the expected outcome for a malformed fixture, not a failing conformance assertion.

The generic renderer was tested at board sizes 300/600 and its common board at 450/900. Application viewing was tested at 480/810 under LIGHT and DARK host appearances. The shared vector board is a uniform white 9x9 grid with black sans-serif digits; givens are bold, ordinary candidate masks occupy fixed 3x3 slots, and private user-candidate masks remain preserved snapshot data. Local theme, ColorKu and candidate-display preferences do not change that common geometry. Mac native exports use complete captured previews to retain their own board and evidence appearance. Historical recordings that did not store their original theme cannot reconstruct that unrecorded theme; capture freezes their saved-state rendering at export time.

`../evidence/actual-windows-frame-viewer.png` and `../evidence/actual-mac-frozen-viewer.png` are rendered captures of displayed Swing application windows. The first displays a Windows W1 prototype fixture through the Mac application; it is not evidence of a Windows application export. `windows-generic-{LIGHT,DARK}-{480,810}.png` and `native-fallback-{LIGHT,DARK}-{480,810}.png` are pure-board render artifacts. The capture probe's Robot check and displayed-window painting establish the stated native GUI scope; they do not claim physical mouse interaction or installed, packaged application acceptance.

The included application fixtures are:

| File under `../mac-fixtures/` | Provenance and intended use |
| --- | --- |
| `native-proof.hrep` | Synthetic source in the existing Mac native replay format; retained unchanged by exchange export |
| `mac-native-proof-frames.hrep` | Real Mac cross-platform export of that stored native proof/result session |
| `mac-native-return.hrep` | Exact byte-preserving application save of the imported Mac exchange file |
| `mac-modified.hrep` | Metadata-modified export with new identities, source provenance and explicit stale-native losses |
| `mac-long-description.hrep` | Real native-session exchange export with a complete long Unicode explanation |
| `mac-w1-unchanged.hrep` | Actual Mac import/save result, byte-identical to the Windows prototype's `all-content.hrep` |
| `mac-noncanonical.hrep` | Actual Mac import/save result preserving valid noncanonical source bytes |
| `java-codec-long-label.hrep` | Independent Java codec fixture only; not an application export |

Unmodified import/save preserves unknown optional records and source bytes. Independent continuation uses only values, fixed flags, ordinary candidates and user candidates; it creates a new identity, archives the source and leaves historical marks, proof displays and opaque native data inactive. The GUI probe verified the actual controller path and confirmed the original source and unrelated live attempt were not changed by viewing.

Modified export was tested for metadata changes, new session/frame identities, exact source archival and recorded losses when stale native payloads are dropped. An unknown important whole-image frame cannot have its board or phaseLabel changed and then reuse the old picture as new evidence: that operation is rejected unless its complete display can be regenerated. The stale-board rejection and other failed writes preserved the last successful destination. This guard does not prevent read-only viewing, exact direct saving or independent continuation.

Code inspection confirms that PNG capture is called by the explicit exchange exporter, not the ordinary per-frame recorder. Existing replay regressions passed. No recording-performance benchmark was performed, so this report makes no measured latency, memory or throughput claim.

Windows M2 application adaptation is `NOT_RUN`. The Mac side is ready for the concentrated implementation and return described in `../WINDOWS-ADAPTATION.md`; both directions through the real Windows application still await that result. Agents exchange the batch directly in the shared PR, without requiring the user to relay logs. PR comments do not themselves wake an idle agent. Only a concrete unresolved conflict requiring loss of native functionality is escalated to the user.
