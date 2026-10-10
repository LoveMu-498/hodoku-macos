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

Standard candidate circles/crosses additionally use a `DM1` overlay line:
`cell digit kind group ARGB widthFactor radius flags conditionId groupKind visibleGroup pixelPathCount`.
The candidate anchor, normalized radius and width reconstruct the standard glyph
without guessing a foreign viewport. Flags are outline/start/witness/inverse bits
1/2/4/8. The supplied Windows reader ignores this unknown tag and its writer retains
it alongside the `P` fallback. Cross fallbacks have two separate diagonal paths.
Unassociated free pixel ink still stays hidden with a notice. `DT operation phase`
records a public operation tag; complete descriptions remain in the shared frame
label and `CD` text. These additional tags do not change the HRPL v3 field schema.

Native proof/application pairs and authored input/result/application triples keep
the same field schema. Applied frames display the frozen proof from the same
operation on the actual post-operation board; proof candidate glyphs may remain
visible after deletion or when ordinary candidates are hidden. Their premise
membership is evaluated on the saved pre-operation board, not by restoring live
candidates. Actual placements are highlighted from the recorded board difference.
BOX authored input, result and application frames show the complete saved native
technique, including premise, fin and deletion candidates. Free Chain authored input
keeps its captured source display. Initial frames without proof do not invent one.
Shared G/R/C/CD projection covers BOX triples and ordinary native application frames;
G marks are filtered by the pre-board candidate set. `PF1 atom FIN` or
`PF1 atom ENDO_FIN` preserves the explicit fin role alongside the existing C fallback.
The supplied Windows reader ignores PF1 and its writer retains it. Mac uses those
roles with native fin colors after Windows re-save without reconstructing a lost
SolutionStep. Deleted candidates take precedence over fins. These overlay tags do
not change the envelope, extension or proof codec versions. A Windows re-save preserves those existing shared tags; Mac can display them
without inventing the lost native proof. Branching uses the native editable overlay
and excludes historical proof tags (including PF1), including earlier tails that lacked separate
editable text. User K/M palette marks and source geometry remain intact.

## Optional Mac tail

After the common frames: int `0x484d5831` (`HMX1`), int extension version `5`,
boolean foreignTimeline, boolean retained, boolean pinned, UTF interruption,
int repeated frameCount. Each frame has UTF kind, length-prefixed proof/evidence
bytes, then a native annotation snapshot: boolean nativeData, length-prefixed raw
geometry evidence, two counted int-key/ARGB maps, the visible outline thought group
(int, `-1` when none), and counted doodle strokes (ARGB int, width float, anchor
cell/digit ints, candidate mark kind byte, thought group int, outlined boolean,
point count, double x/y pairs). Extension versions 1 and 2 remain readable; their
strokes have no explicit outline selection. Extension 4 adds hypothesis-start,
condition/group-kind, contradiction-witness and inverse-result fields per stroke.
Extension 5 adds a length-prefixed optional frozen doodle-step capsule after each
annotation snapshot, followed by UTF editable-overlay text. This separate native
overlay preserves the actual post-state when common text borrows pre-state
evidence; a new branch exports the editable text without the past operation.
The capsule HDS1/version-1 schema stores the operation enum, thought
group, premise kind, application phase, bounded candidate sets (premise, authored
truths/falses, selected conclusions, actual fills/direct deletions/automatic
deletions), and typed contradiction endpoints with scope, digit and candidate set.
Both phases must agree and their recorded effects must match the actual board
changes. Earlier extensions 1–4 remain readable; absent steps are not invented.
Then counted bookmarks (int frame, UTF name, long wall/effective times), UTF source
replay id, int source frame and length-prefixed initial raw geometry.

The reference Windows reader ignores this tail; its writer drops it. That accepted
loss includes full proofs, bookmarks, provenance, precise native free ink and the per-group
outline state. Standard glyph anchors and complete explanation text survive in
the common overlay/label when unknown lines are preserved. The common `P` overlay also contains the visible group's high-contrast
outline paths, so Windows can show the applied group's evidence. A Mac re-save
preserves unknown overlay lines. Unknown tail identifiers/versions and truncated
tails fail explicitly. Mac evidence/proof codecs remain version 2 with version-1
evidence support; this is not support for the retired file envelope.

## Reported new Windows V3 variant (2026-10-05)

Mac additionally reads exactly ten fields:
`O ARGB kind cell digit concl x1 y1 x2 y2`. Kind 1 is a true circle, 2 a false
cross, and 0 an unanchored free circle. Candidate anchors require cell 0..80 and
digit 1..9; free circles require -1/-1. Concl is strictly 0/1. All four coordinates
must be finite and within 0..1. Legacy O6 still requires integer pixel coordinates;
it is never reinterpreted as relative geometry. Extra fields and malformed known
tags are rejected. The envelope remains V3; v1/v2 are not implicitly enabled.

Anchored O10 glyphs use native candidate positions and standard dimensions without
guessing the unspecified foreign viewport. Distinct ARGB values are replay visual
slots, never inferred thought-group identities. DM1 takes precedence over O10 for
the same cell/digit/kind/ARGB while both raw lines survive direct saves. Outlines
on unassigned foreign marks remain visible independently of a local palette.
Candidate glyphs remain visible after deletion or with ordinary candidates hidden;
the actual board masks remain unchanged. Unanchored O10, O6 and free P are retained
but hidden with a notice when their reference is unknown.

`MT 0/1` is checked and retained as a display flag. Saved G/R/C highlights are shown;
the player never propagates assumptions or recomputes common conclusions. MT=1
without recognized frozen highlights reports missing results. With highlights it
reports that their common-conclusion provenance was not recorded, rather than
certifying the intersection as unconditional truth.

The separate **Windows V3 copy** export adds O10 for native independent standard
glyphs and keeps the full Mac tail, DM1, PF1, DT and unknown data. Native OR/grouped
premises, contradiction witnesses and inverse results are not rewritten as
individually true assumptions. Technique G marks remain technique premises.
Known native pixel fallbacks replaced by O10 are removed and their DM1 path counts
updated; unrelated pixels survive. Older native tails with anchors but no DM1 gain
an anchored projection without guessing which old pixel paths belong to it.
Repeated profile export does not accumulate O10 copies. Geometry fallback uses a
normalized 9×9 board square; candidate anchors are authoritative. This is a declared
Mac projection pending confirmation by the new Windows producer, not a verified
foreign viewport definition. Ordinary full export and system sharing are unchanged.

Foreign historical ink/results are excluded from editing branches, including
DM1/O10 outlines and G/R/C/PF1 colors. Known box/chain geometry and explicit K/M user
coloring continue through the existing source-restoration path. Native Mac branches
retain their existing editable source contract. The original foreign file and its
raw viewing data are not rewritten by branching.

Legacy Windows readers can preserve the shared prefix but may misread O10; it is not the target viewer for this export. New Windows GUI display,
editing/re-recording, grouping, coordinates and tail retention still require real
peer acceptance. The separate protocol proposal remains under review in [PR #2](https://github.com/LoveMu-498/hodoku-macos/pull/2); it is not an accepted peer contract.

## Timeline and validation

Native attempts retain real t0 frames, proof/application pairs, authored
input/result/application triples, append-only undo, effective solve time and
independent editable branches. Doodle conclusion/adoption pairs record the authored
input and actual result. Result-frame viewing may borrow the preceding input's
standard glyphs for readonly evidence even after their candidate is removed;
branching uses the stored post-operation ink and drops the past operation capsule.
This display-only borrowing also supports earlier extension-4 doodle pairs.
Annotation-only gestures do not create frames.
Every actual board operation captures then-current annotations; authored preframes
retain their captured input. Imported Windows frames retain their original IDs,
ordering and first timestamp; no absent initial state is fabricated. Windows leaves
header elapsedMillis at zero, so a missing duration uses the final frame time,
not `endedAt-startedAt` (which can contain an unrecorded tail).

Limits: 128 MiB file, 100,000 frames, 4 MiB proof/geometry item, 10,000 bookmarks;
overlay and annotation collections have bounded counts and valid cell/atom indices.
Common text above the 65535-byte modified-UTF limit uses the bounded native
extension 6 described below; no full native text is truncated. Text above 4 MiB
per item fails atomically without replacing the last successful file. Malformed/unknown local event groups fail
at interchange validation. Incorrect historical player edits are preserved, while
claimed completion requires a correct final board.

Export snapshots clear local pin/retention flags and detailed recovery errors;
user labels/bookmark names remain intended content. Import never changes the live
attempt or automatically adds an archive. Export cannot overwrite managed storage.
At startup only UUID-named, positively identified retired Mac v4 managed files and
the checkpoint pointing to them are removed. Renamed exports, symbolic links,
foreign files, personal settings and puzzle/savepoint files are not removed.

## Native long text extension 6 (2026-10-10)

Ordinary files retain extension 5. Only files needing long explanation, viewing
or editable overlay, bookmark name or interruption text use extension 6. The
HRPL v3 shared prefix remains unchanged. A common explanation or overlay too
large for modified UTF contains an explicit notice that its full content is in
the Mac extension; it is not presented as a complete common projection.

HMX6 replaces interruption and bookmark-name UTF with int byte length plus strict
UTF-8, and adds full label and full viewing-overlay after each frame's UTF kind,
before the existing evidence bytes. The annotation's editable overlay likewise
uses int-length UTF-8. Each text item is bounded to 4 MiB; aggregate files retain
the 128 MiB bound. Other extension-5 fields remain in the same order. Extensions
1–5 remain readable. Old Mac readers reject HMX6 explicitly. Windows reference
readers stop at the shared prefix and cannot show long native text; a Windows
re-save discards the tail and therefore loses it. Full long-text interoperability
requires peer implementation and acceptance, which has not been claimed.

See [2.3.4 upgrade and downgrade notes](releases/2.3.4.md).

Automatic native replay writes take detached immutable-frame snapshots and
coalesce the disk schedule over approximately 120ms. Every operation frame stays
in memory and is included in the next atomic save. Explicit save/quit and sleep
checkpoint drain pending writes in order; clean shutdown is marked only after
all required saves succeed. A crash before a pending batch commits may lose that
pending batch; the last complete committed operation remains recoverable.

Native session XML also records replay identity, frame count and effective time.
If an independently saved native session is ahead of its durable replay, recovery
keeps that newer board and annotations and appends an explicit evidence-unavailable
final snapshot. It does not invent missing history or replace the newer native state
with the older replay. Older session XML defaults to the existing recovery behavior.
