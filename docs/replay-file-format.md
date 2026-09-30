# Replay interchange — Windows v3 + optional Mac extension

The base format is HRPL v3 with an optional Mac extension. The common prefix is
compatible with implementations following the complete schema below; a shared
version number alone does not establish interoperability or upstream endorsement.
Old Mac HRPL v1–v4 is incompatible and is no longer supported or migrated.
The shared magic/version alone does not prove a file is compatible: the whole
schema, indices, lengths and event timeline are checked.

## Common prefix

Big-endian `DataOutputStream` primitives; strings use Java modified UTF-8 (`writeUTF`,
maximum 65,535 encoded bytes per string). No Java serialization, XML beans or external
resources are involved.

| Field | Type |
| --- | --- |
| magic / version | int `0x4852504c`, int `3` |
| id | UTF |
| startedAt / elapsedMillis / endedAt | 3 longs |
| completed / frameCount | boolean, int |
| each frame | long operationId, long wallTimeMillis, long elapsedMillis, UTF label, UTF overlayData, boolean methodStep |
| each frame's 81 cells | byte value, boolean fixed, short candidates, short userCandidates |

Overlay tags: `B` cell-set boxes; `G/R/C` per-cell candidate marks; `CG/CS/CH/CL/CD`
current chain/result/description; `UC/UN/UL/UG` completed chains and full candidate
groups; `P/O` pixel ink/circles; `K/M` cell/candidate palette indices. Candidate atoms
are `cellIndex * 10 + digit`. Unknown lines are retained verbatim and never executed.
Closed Mac chains export a repeated endpoint group because Windows draws adjacent
segments only. Generalized groups retain all atoms; geometry is not proof of a
valid OR inference. Native technique descriptions and result marks are projected
into common overlay text; complete native proof remains in the Mac extension.

Windows palette roles use the supplied source's ten-color default palette; native
Mac exact colors remain in the extension. Different receiver palettes may change
colors. Cell/box/chain positions remain semantic, independent of window size.
Windows `P/O` lacks reliable canvas/anchor/width metadata. Such imported ink remains
in the file, is hidden with a visible notice, and is not guessed from possibly stale
box rectangles. Mac ink exports a current-canvas pixel projection for Windows;
Mac-to-Mac keeps exact anchors, relative points and widths in the extension. This
cannot guarantee pixel-ink alignment in Windows at another window size.

## Optional Mac tail

After the common frames: int `0x484d5831` (`HMX1`), int extension version `3`,
boolean foreignTimeline, boolean retained, boolean pinned, UTF interruption,
int repeated frameCount. Each frame has UTF kind, length-prefixed proof/evidence
bytes, then a native annotation snapshot: boolean nativeData, length-prefixed raw
geometry evidence, two counted int-key/ARGB maps, the visible outline thought group
(int, `-1` when none), and counted doodle strokes (ARGB int, width float, anchor
cell/digit ints, candidate mark kind byte, thought group int, outlined boolean,
point count, double x/y pairs). Extension versions 1 and 2 remain readable; their
strokes have no explicit outline selection.
Then counted bookmarks (int frame, UTF name, long wall/effective times), UTF source
replay id, int source frame and length-prefixed initial raw geometry.

The reference Windows reader ignores this tail; its writer drops it. That accepted
loss includes full proofs, bookmarks, provenance, precise native ink and the per-group
outline state. The common `P` overlay also contains the visible group's high-contrast
outline paths, so Windows can show the applied group's evidence. A Mac re-save
preserves unknown overlay lines. Unknown tail identifiers/versions and truncated
tails fail explicitly. Mac evidence/proof codecs remain version 2 with version-1
evidence support; this is not support for the retired file envelope.

## Timeline and validation

Native attempts retain real t0 frames, proof/application pairs, authored
input/result/application triples, append-only undo, effective solve time and
independent editable branches. Annotation-only gestures do not create frames.
Every actual board operation captures then-current annotations; authored preframes
retain their captured input. Imported Windows frames retain their original IDs,
ordering and first timestamp; no absent initial state is fabricated. Windows leaves
header elapsedMillis at zero, so a missing duration uses the final frame time,
not `endedAt-startedAt` (which can contain an unrecorded tail).

Limits: 128 MiB file, 100,000 frames, 4 MiB proof/geometry item, 10,000 bookmarks;
overlay and annotation collections have bounded counts and valid cell/atom indices.
Too-large common strings fail the atomic save explicitly, without truncating data
or replacing the last successful file. Malformed/unknown local event groups fail
at interchange validation. Incorrect historical player edits are preserved, while
claimed completion requires a correct final board.

Export snapshots clear local pin/retention flags and detailed recovery errors;
user labels/bookmark names remain intended content. Import never changes the live
attempt or automatically adds an archive. Export cannot overwrite managed storage.
At startup only UUID-named, positively identified retired Mac v4 managed files and
the checkpoint pointing to them are removed. Renamed exports, symbolic links,
foreign files, personal settings and puzzle/savepoint files are not removed.
