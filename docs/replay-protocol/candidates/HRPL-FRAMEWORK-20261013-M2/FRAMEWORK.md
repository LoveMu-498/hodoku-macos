# HRPL-FRAMEWORK-0.2 — Mac amendment M2

Package: HRPL-FRAMEWORK-20261013-M2. Status: complete Mac amendment for consolidated Windows adaptation; bilateral agreement and application acceptance require actual evidence.
Baseline: HRPL-FRAMEWORK-20261013-W1. The original W1 four-contract bytes are retained under baseline-w1/ and remain the complete historical HRPL-FRAME/0.1 definition. This document defines HRPL-FRAME/0.2; it does not silently reinterpret files selecting 0.1.
This optional v4 profile adds frozen display frames. Existing v3 and O6/O10/O11/MT layouts remain unchanged.
The candidate contract is this file plus the accompanying SPEC.md, CONVERGENCE.md and INITIATIVE.md. Mutual tests must identify all four SHA-256 values and the package ID. The previous Windows ACK does not accept this new profile.

## 1. Transport and limits

Binary layout: big-endian uint32 magic 0x4852504C; uint32 version=4; uint32 payloadByteLength; exactly that many UTF-8 JSON bytes; EOF. Length includes only payload, not the 12-byte header. BOM, malformed UTF-8, duplicate JSON keys, trailing bytes and unknown version are errors. No modified UTF-8, compression, scripts or external resource URLs.

Maximum total file size 128 MiB. Frames: 1..100000; objects per frame: 0..10000; native items per frame: 0..256. JSON nesting depth<=64. Every text field except defined base64 data and the exact frame phaseLabel field: at most 65535 UTF-8 bytes. In profile 0.2 only, phaseLabel permits at most 16777216 UTF-8 bytes (16 MiB); IDs: 1..128 printable ASCII characters. All JSON numbers are integers (no floats or booleans as integers); time and counters range 0..9007199254740991. Unicode strings contain scalar values, not unpaired surrogates. Readers validate lengths before allocation. An unsuccessful write preserves the last successful file.

Canonical writer encoding: recursively sort object keys by Unicode scalar value, compact JSON with no spaces, UTF-8 without BOM, non-ASCII characters literal; escape quotation marks, backslashes and controls with standard JSON escapes. All numeric values are decimal integers. Arrays retain order. Canonicalization is used for hashes; an unmodified direct save preserves original file bytes, including noncanonical valid JSON.

Root fields (no unregistered fields in known records):

| Field | Definition |
| --- | --- |
| format | literal `HRPL-FRAME/0.2` |
| session | id, startedAtMs, endedAtMs, elapsedMs, completed:boolean |
| frames | ordered frame array |
| source | null or {fileSha256:64 lowercase hex, frameIds:ID array}; provenance only, never executable |
| losses | array of {frameId:ID, namespace:ID, reason:text}; empty for lossless exchange |

Readers may support both exact profiles 0.1 and 0.2. Under 0.1 the original 65535-byte phaseLabel bound remains. Unknown profile values are errors; a 0.1-only reader must reject 0.2 instead of truncating its text. Large-label permission belongs only to the actual validated frame record, never to an unknown object with a similarly named field. All other wire fields, digest rules and limits remain unchanged.

Version 4 must be deployed as a separate opt-in reader/writer before exporting it by default. Existing Windows v3 readers reject version 4; Mac must report its actual old-reader behavior. A v4 file must never be labeled v3. A v3 compatibility export is a distinct, explicitly lossy file when it cannot carry frozen evidence. It must keep the original v4 source and declare losses; it is not a transparent downgrade.

## 2. Frame records

Fields: id:ID; operationGroup:ID; phase:one of input/evidence/result/applied/other; phaseLabel:text (empty allowed, at most 16 MiB UTF-8 in 0.2); wallTimeMs:time; elapsedMs:time; board:record; display:array; native:array; preview:null or record.
Frame IDs are unique. The array defines frame order. Frames belonging to an operationGroup are contiguous. elapsedMs never decreases; wallTimeMs is the actual recording clock and may move backwards. No initial frame or missing event is invented. Session elapsedMs is at least the last frame elapsedMs; playback dwell time is not written into elapsedMs.

board has exactly four arrays of 81 elements: values (0..9), fixed (boolean), candidates (0..511), userCandidates (0..511). Index=9*row+column, zero based. Digit d maps to bit d-1. fixed=true implies value!=0; value!=0 implies candidates=0. userCandidates retains its original low nine bits. These arrays alone are the editable snapshot; frozen display never restores deleted candidates. The generic shared viewer shows values and ordinary candidates from candidates. userCandidates is retained independently, byte-for-byte in its nine bits, for continuation; it must not replace candidates based on a local candidate-view preference. Complete native previews may capture the native candidate-view choice because their pixels carry that choice explicitly.

Frame content digest = SHA-256 of canonical JSON containing exactly id, operationGroup, phase, phaseLabel, wallTimeMs, elapsedMs, board and display. preview and native are excluded from this digest to avoid a cycle. A binding mismatch is rejected, not treated as valid current proof.

## 3. Frozen display objects

Object common fields: id:ID unique within frame; type:ID; required:boolean; important:boolean; z:int -32768..32767. Paint order is ascending z then original array order. These are inert drawing records, never instructions to a solver. required=true means an unsupported type makes the frame unreadable. Unknown optional objects are preserved; if important=true and no complete preview exists, report PARTIAL_VIEW. Unknown optional nonimportant objects report a skipped-object notice. Recognized malformed objects always fail.

For an otherwise readable frame, if any important display object cannot be rendered and a valid complete preview exists, that preview replaces the entire generic board and display. Otherwise render the generic board and supported objects; the mere presence of a preview does not select it. If important content remains unavailable without a complete preview, report PARTIAL_VIEW. A native projection carried only by a complete preview includes an optional (`required=false`), important (`important=true`) native-view marker whose unsupported type triggers this fallback. A receiver that understands that marker must still display its complete native view; it must not treat the marker as an empty drawing. An unknown required object continues to reject the frame even when a preview exists.

The generic shared board/canvas uses a fixed opaque white background and black values and ordinary candidate digits, independent of the receiving application theme. The board has nine equal-width columns and nine equal-height rows within the square board side. Values are centered in each cell; candidate digits use the centers of the corresponding 3x3 slots (1 top-left through 9 bottom-right). Values and candidates use generic sans-serif; givens are bold and other digits regular. Grid lines remain visible in black/gray. Recorded object ARGB values are composited over this white canvas without palette substitution; in particular, black marks remain visible on a host using dark mode. Exact fonts, antialiasing, grid thickness and window chrome need not match. A complete native preview instead retains its captured visual style and is not recolored.

Coordinates use integer micro-board units: board left/top=(0,0), board right/bottom=(1000000,1000000). Both axes scale by the board's square side length, independent of surrounding window. All recorded points/bounds range -500000..1500000, allowing visible margins. Widths range 1..100000 micro-units, fontSize 1000..500000. These geometric sizes carry meaning and must scale proportionally. Cell/candidate anchors name the role and do not override recorded geometry. Colors are exactly `#AARRGGBB` hexadecimal. Opacity is carried by AA, not guessed from a platform palette.

Anchor: null or {cell:int 0..80, digit:int 0..9}; digit=0 is a cell anchor. Shape fields are mandatory, including for anchored marks; receivers use them as the frozen geometry. Source group identity is null or an explicit ID, never inferred from RGB. It indicates historical grouping, not a coverage proof.

| type | Additional exact fields |
| --- | --- |
| mark | shape:circle/cross; bounds:[x1,y1,x2,y2] with x1<x2,y1<y2; anchor; color; width; initial:boolean; conclusion:boolean; outline:null or {color,width,gap}; group:ID or null |
| highlight | bounds; anchor; fill:color; group:ID or null |
| line | points:[[x,y],...] (2..4096); color; width; dash:solid/dashed; arrow:none/start/end/both; group:ID or null |
| region | points:[[x,y],...] (3..4096, implicit closed polygon); fill:color; stroke:color; width; group:ID or null |
| text | at:[x,y]; text; color; fontSize; align:left/center/right; group:ID or null |

mark initial means historical starting assumption, conclusion means historical conclusion role; they are independent. Receivers use recorded width even for initial marks. Outline color/width/gap are explicit, never inferred from conclusion or palette. For circle, outline is the ring between ellipses expanded by gap and gap+outline.width from the recorded bounds. For cross, it is the region between strokes of width base.width+2*gap and base.width+2*(gap+outline.width), with round caps/joins; bounds define the actual cross endpoints, not an assumed candidate box. Draw base mark then outline ring. Outline width is 1..100000, gap 0..100000. Neither flag asserts mathematical validity. Text preserves content; fonts/antialiasing may differ. All existing or new techniques use these objects without negotiating technique names. A new shared type or incompatible field requires a new profile proposal. Existing generic primitives or an optional important native-view marker plus a faithful complete preview can carry new native techniques without adding shared fields or negotiating technique names.

Base circle is an unfilled ellipse inscribed in bounds; cross is two diagonals between the bounds corners. Strokes use round caps/joins. Highlight fills the recorded rectangle. Region uses even-odd polygon fill and centered closed stroke. Dashed lines use dash=5*width, gap=3*width, offset=0 from the first point. Arrowheads are filled triangles at the endpoint, aligned to the nearest nonzero segment, length=8*width and base width=4*width (also for start arrows). Lines must have at least one nonzero segment. Text uses regular generic sans-serif, fontSize is em height, at is the baseline origin, align selects left/center/right justification. No HTML or markup execution. Native effective opacity, widths, outline colors and geometry must be captured as displayed; default platform styles cannot substitute for missing capture state.

MT is not a new display object. The exporter receives the stored, already established result objects and emits them, plus the complete historical explanation text and explicit group IDs supplied by the native state. Export-time freezing is permitted: an adapter can render the existing saved native frame/evidence when cross-platform export is requested, preserving the ordinary recorder's performance and recording length. It must not run a solver, recalculate a historical proof/conclusion, or use the current puzzle to fill missing evidence. If a legacy recording did not persist its original theme/style, state that limitation; an export-time capture does not prove the recording-time theme. New recording-time captures may be reused when present. An MT flag without these objects is incomplete evidence, not permission to recompute. This profile does not encode a proof calculus or assume a result intersection covers all cases. Coverage claims may be stored as optional native data, with verification status kept separate.

## 4. Preview and optional native data

preview fields: mime=`image/png`; coverage=`full-frame`; binding=frame content digest; data=base64. The preview must faithfully depict the entire board/canvas and all important geometry of that frame. The complete phaseLabel is displayed separately as scrollable, selectable text; it need not be burned into the image or clipped to fit it. A receiver must always expose the complete phaseLabel for both generic geometry and preview viewing. The image is shown as a standalone full canvas, fitted while retaining its aspect ratio. Do not infer a hidden board viewport, overlay fresh board digits on it, crop important margins, or recolor it. It is a viewing fallback only and never supplies board values or editable marks. It is optional, not recording video.
PNG profile: signature and chunk CRCs valid; first chunk IHDR, last IEND, at least one IDAT; RGBA 8-bit, no interlace, width/height 1..4096, pixels<=16777216; only IHDR/IDAT/IEND permitted. Decoded compressed bytes<=8 MiB, decompressed scanline bytes<=67112960. Validate decompression and boundaries. No remote media. A valid PNG carrier alone does not prove visual fidelity; that requires the display matrix. If a reader cannot decode this profile, it reports partial viewing rather than silently claiming equivalence.

native item exact fields: namespace:ID; version:int 1..65535; binding:frame content digest; data:base64. Each item<=8 MiB decoded; namespace unique per frame. These are optional, opaque, nonexecuting bytes; no mandatory private algorithms. Binding is association only, not proof correctness. Unknown data is retained on unmodified save.

## 5. Save, edit and continuation

Unmodified direct save copies original file bytes exactly. Changing metadata, frame count/order, board or display is a modified export. Keep the original source as an immutable archive; create a new session ID and frame IDs, source={original file SHA-256, selected original frame IDs}. Never transplant opaque native data into changed frames. Drop it and list a loss with reason `stale-binding`; preview is dropped unless regenerated from captured display. If the original cannot be archived, refuse that modified export. Unknown display objects whose geometry still applies may be explicitly carried, otherwise list a loss. No silent discarding.

Complete read-only viewing and independent continuation from the true board snapshot are required capabilities. This profile does not require either application to add a replay editor or change existing native editing features. If an application offers modified export and cannot faithfully reconstruct the new view because important unknown content is available only in the old complete preview, it refuses that modification. It must not assign a new binding to a stale image, silently omit important content, or invent a board from pixels. The original complete view and independent board continuation remain available; refusing this unsupported exchange-frame edit does not remove an existing native function.

Independent continuation uses the selected board arrays, new session identity, unchanged source archive, and no historical display/native execution. This is a separate application path; the existing Windows load-and-truncate entry may remain. Known editable native data can be restored only through an explicit platform rule; this profile does not grant that permission. These are integration requirements; a document or prototype result alone does not prove their implementation.

## 6. Batch matrix and candidate decisions

The accompanying generator/verifier produces one batch for board arrays, all five object types, initial/conclusion roles, frozen multigroup objects, multiple phases and operation groups, Unicode text, unknown optional/required objects, opaque native bytes, preview carrier, stale binding, duplicate keys, invalid coordinates, truncation and declared oversized payload. expected.json gives exact pass/fail or PARTIAL_VIEW outcomes.

For the same candidate hash manifest, each side reports: read; direct-save round trip; display at two board sizes (GUI and offscreen separately); edit/archive/loss handling; independent continuation; legacy v3/O11; malformed/version/limits. NOT_RUN is distinct from FAIL. Pure codec tests are not GUI or product acceptance.

Mac provides M2 as one complete amendment incorporating the received W1 requirements and the native-preservation constraints discovered during independent Java integration. The only schema-capacity change is the bounded 0.2 phaseLabel; export-time freezing and canvas/preview/text rules above settle presentation and integration behavior without introducing fields. Actual Mac and Windows application outcomes belong in the accompanying result matrix. No old W1 ACK accepts these different bytes, and no comment implies release, merge or background listening.

## 7. Consolidated Windows adaptation task

Read the four M2 contracts and the provided actual Mac artifacts/results. Keep all native Windows solving, techniques, marks, recording operations and existing continuation paths. Implement the 0.2 reader and explicit writer in the replay adapter; preserve 0.1/v3 readers, ordinary recording performance and native files. Use the existing saved display as export-time frozen input when appropriate. Implement the shared generic canvas plus complete-preview fallback and full scrollable phaseLabel; preserve all four board arrays and unknown opaque data on direct saves. An independent continuation uses only selected board arrays and archives the source.

Run the matrix once as a batch: both directions, exact unchanged save, multiple sizes and themes, the five shapes/roles/groups, all board arrays, preview+long text, modified export archive/loss rules, safe continuation, v3/O11 and malformed inputs. Apply ordinary implementation fixes locally and report the complete outcomes/return fixtures in the same PR; do not send one comment per field or wait for separate technical approvals. If preserving a native function is impossible under the candidate, explain the concrete function, conflicting rule and full proposed relaxation together. The Mac side first adapts itself or widens the protocol; only a genuine feature sacrifice needs the user's decision. Do not weaken integrity/binding or silently discard evidence to report a pass.
