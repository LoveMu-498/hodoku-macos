# Windows M2 receipt validation

Input SHA-256: 5e9bb95a321819db691a69bb9c87465140adea280b0b9d983ab589f9063b2113

Mode: actual macOS native window, programmatic controls and window painting

- PASS: exact Windows application return bytes and SHA-256
- PASS: M2 profile 0.2
- PASS: actual application import of two common frames
- PASS: imported native data stays outside active initial evidence
- PASS: frame 0 exact four board arrays
- PASS: frame 0 exact wall and elapsed times
- PASS: frame 0 complete phaseLabel
- PASS: frame 0 unknown native stays inert
- PASS: frame 0 all common and unknown fields preserved
- PASS: frame 1 exact four board arrays
- PASS: frame 1 exact wall and elapsed times
- PASS: frame 1 complete phaseLabel
- PASS: frame 1 unknown native stays inert
- PASS: frame 1 all common and unknown fields preserved
- PASS: operation group retained
- PASS: partial frame clearly reports missing important display
- PASS: preview fallback correctly reported
- PASS: mac-direct-save.hrep retains every original byte
- PASS: mac-direct-save.hrep actual application reread
- PASS: mac-framework-export.hrep retains every original byte
- PASS: mac-framework-export.hrep actual application reread
- PASS: mac-store-write.hrep retains every original byte
- PASS: mac-store-write.hrep actual application reread
- PASS: LIGHT-480 partial frame renders red cross and blue outline
- PASS: LIGHT-480 vector rendering preserves four arrays
- PASS: LIGHT-480 candidate display independent of local toggle
- PASS: LIGHT-480 source complete PNG dimensions
- PASS: LIGHT-480 complete preview pixel exact after proportional scaling
- PASS: LIGHT-480 frozen rendering preserves four arrays
- PASS: LIGHT-480 frame navigation clears prior preview
- PASS: LIGHT-810 partial frame renders red cross and blue outline
- PASS: LIGHT-810 vector rendering preserves four arrays
- PASS: LIGHT-810 candidate display independent of local toggle
- PASS: LIGHT-810 source complete PNG dimensions
- PASS: LIGHT-810 complete preview pixel exact after proportional scaling
- PASS: LIGHT-810 frozen rendering preserves four arrays
- PASS: LIGHT-810 frame navigation clears prior preview
- PASS: DARK-480 partial frame renders red cross and blue outline
- PASS: DARK-480 vector rendering preserves four arrays
- PASS: DARK-480 candidate display independent of local toggle
- PASS: DARK-480 common canvas invariant across themes
- PASS: DARK-480 source complete PNG dimensions
- PASS: DARK-480 complete preview pixel exact after proportional scaling
- PASS: DARK-480 frozen rendering preserves four arrays
- PASS: DARK-480 frame navigation clears prior preview
- PASS: DARK-810 partial frame renders red cross and blue outline
- PASS: DARK-810 vector rendering preserves four arrays
- PASS: DARK-810 candidate display independent of local toggle
- PASS: DARK-810 common canvas invariant across themes
- PASS: DARK-810 source complete PNG dimensions
- PASS: DARK-810 complete preview pixel exact after proportional scaling
- PASS: DARK-810 frozen rendering preserves four arrays
- PASS: DARK-810 frame navigation clears prior preview
- PASS: actual native MainFrame and replay viewer are showing
- PASS: opening foreign viewer preserves live attempt
- PASS: actual viewer navigation selects frame 0
- PASS: actual viewer retains frame 0 full explanation
- PASS: actual viewer shows frame 0 honest completeness notice
- PASS: actual viewer frame 0 board is visible and exact
- PASS: actual viewer navigation selects frame 1
- PASS: actual viewer retains frame 1 full explanation
- PASS: actual viewer shows frame 1 honest completeness notice
- PASS: actual viewer frame 1 board is visible and exact
- PASS: actual previous button returns to first frame
- PASS: branch 0 independent fresh identity
- PASS: branch 0 actual live and stored four arrays exact
- PASS: branch 0 no executable imported history
- PASS: branch 0 historical display absent from active marks
- PASS: branch 0 provenance identifies selected frame
- PASS: branch 0 source archive preserves every byte
- PASS: branch 1 independent fresh identity
- PASS: branch 1 actual live and stored four arrays exact
- PASS: branch 1 no executable imported history
- PASS: branch 1 historical display absent from active marks
- PASS: branch 1 provenance identifies selected frame
- PASS: branch 1 source archive preserves every byte
- PASS: source return unchanged after all viewing/save/branch operations

Total: 77 PASS.

No Windows GUI or physical mouse validation claimed. Native-window PNGs are actual visible Swing window painting, not desktop screen captures.
