# HRPL-FRAMEWORK-0.1 — Windows candidate W1

Package: HRPL-FRAMEWORK-20261013-W1. Status: complete proposal, not bilateral agreement or application support.
This optional v4 profile adds frozen display frames. Existing v3 and O6/O10/O11/MT layouts remain unchanged.
The candidate contract is this file plus the accompanying SPEC.md, CONVERGENCE.md and INITIATIVE.md. Mutual tests must identify all four SHA-256 values and the package ID. The previous Windows ACK does not accept this new profile.

## 1. Transport and limits

Binary layout: big-endian uint32 magic 0x4852504C; uint32 version=4; uint32 payloadByteLength; exactly that many UTF-8 JSON bytes; EOF. Length includes only payload, not the 12-byte header. BOM, malformed UTF-8, duplicate JSON keys, trailing bytes and unknown version are errors. No modified UTF-8, compression, scripts or external resource URLs.

Maximum total file size 128 MiB. Frames: 1..100000; objects per frame: 0..10000; native items per frame: 0..256. JSON nesting depth<=64. Every text field except defined base64 data: at most 65535 UTF-8 bytes; IDs: 1..128 printable ASCII characters. All JSON numbers are integers (no floats or booleans as integers); time and counters range 0..9007199254740991. Unicode strings contain scalar values, not unpaired surrogates. Readers validate lengths before allocation. An unsuccessful write preserves the last successful file.

Canonical writer encoding: recursively sort object keys by Unicode scalar value, compact JSON with no spaces, UTF-8 without BOM, non-ASCII characters literal; escape quotation marks, backslashes and controls with standard JSON escapes. All numeric values are decimal integers. Arrays retain order. Canonicalization is used for hashes; an unmodified direct save preserves original file bytes, including noncanonical valid JSON.

Root fields (no unregistered fields in known records):

| Field | Definition |
| --- | --- |
| format | literal `HRPL-FRAME/0.1` |
| session | id, startedAtMs, endedAtMs, elapsedMs, completed:boolean |
| frames | ordered frame array |
| source | null or {fileSha256:64 lowercase hex, frameIds:ID array}; provenance only, never executable |
| losses | array of {frameId:ID, namespace:ID, reason:text}; empty for lossless exchange |

Version 4 must be deployed as a separate opt-in reader/writer before exporting it by default. Existing Windows v3 readers reject version 4; Mac must report its actual old-reader behavior. A v4 file must never be labeled v3. A v3 compatibility export is a distinct, explicitly lossy file when it cannot carry frozen evidence. It must keep the original v4 source and declare losses; it is not a transparent downgrade.

## 2. Frame records

Fields: id:ID; operationGroup:ID; phase:one of input/evidence/result/applied/other; phaseLabel:text (empty allowed); wallTimeMs:time; elapsedMs:time; board:record; display:array; native:array; preview:null or record.
Frame IDs are unique. The array defines frame order. Frames belonging to an operationGroup are contiguous. elapsedMs never decreases; wallTimeMs is the actual recording clock and may move backwards. No initial frame or missing event is invented. Session elapsedMs is at least the last frame elapsedMs; playback dwell time is not written into elapsedMs.

board has exactly four arrays of 81 elements: values (0..9), fixed (boolean), candidates (0..511), userCandidates (0..511). Index=9*row+column, zero based. Digit d maps to bit d-1. fixed=true implies value!=0; value!=0 implies candidates=0. userCandidates retains its original low nine bits. These arrays alone are the editable snapshot; frozen display never restores deleted candidates.

Frame content digest = SHA-256 of canonical JSON containing exactly id, operationGroup, phase, phaseLabel, wallTimeMs, elapsedMs, board and display. preview and native are excluded from this digest to avoid a cycle. A binding mismatch is rejected, not treated as valid current proof.

## 3. Frozen display objects

Object common fields: id:ID unique within frame; type:ID; required:boolean; important:boolean; z:int -32768..32767. Paint order is ascending z then original array order. These are inert drawing records, never instructions to a solver. required=true means an unsupported type makes the frame unreadable. Unknown optional objects are preserved; if important=true and no complete preview exists, report PARTIAL_VIEW. Unknown optional nonimportant objects report a skipped-object notice. Recognized malformed objects always fail.

Coordinates use integer micro-board units: board left/top=(0,0), board right/bottom=(1000000,1000000). Both axes scale by the board's square side length, independent of surrounding window. All recorded points/bounds range -500000..1500000, allowing visible margins. Widths range 1..100000 micro-units, fontSize 1000..500000. These geometric sizes carry meaning and must scale proportionally. Cell/candidate anchors name the role and do not override recorded geometry. Colors are exactly `#AARRGGBB` hexadecimal. Opacity is carried by AA, not guessed from a platform palette.

Anchor: null or {cell:int 0..80, digit:int 0..9}; digit=0 is a cell anchor. Shape fields are mandatory, including for anchored marks; receivers use them as the frozen geometry. Source group identity is null or an explicit ID, never inferred from RGB. It indicates historical grouping, not a coverage proof.

| type | Additional exact fields |
| --- | --- |
| mark | shape:circle/cross; bounds:[x1,y1,x2,y2] with x1<x2,y1<y2; anchor; color; width; initial:boolean; conclusion:boolean; outline:null or {color,width,gap}; group:ID or null |
| highlight | bounds; anchor; fill:color; group:ID or null |
| line | points:[[x,y],...] (2..4096); color; width; dash:solid/dashed; arrow:none/start/end/both; group:ID or null |
| region | points:[[x,y],...] (3..4096, implicit closed polygon); fill:color; stroke:color; width; group:ID or null |
| text | at:[x,y]; text; color; fontSize; align:left/center/right; group:ID or null |

mark initial means historical starting assumption, conclusion means historical conclusion role; they are independent. Receivers use recorded width even for initial marks. Outline color/width/gap are explicit, never inferred from conclusion or palette. For circle, outline is the ring between ellipses expanded by gap and gap+outline.width from the recorded bounds. For cross, it is the region between strokes of width base.width+2*gap and base.width+2*(gap+outline.width), with round caps/joins; bounds define the actual cross endpoints, not an assumed candidate box. Draw base mark then outline ring. Outline width is 1..100000, gap 0..100000. Neither flag asserts mathematical validity. Text preserves content; fonts/antialiasing may differ. All existing or new techniques use these objects without negotiating technique names. A new shared type or incompatible field requires a new profile proposal.

Base circle is an unfilled ellipse inscribed in bounds; cross is two diagonals between the bounds corners. Strokes use round caps/joins. Highlight fills the recorded rectangle. Region uses even-odd polygon fill and centered closed stroke. Dashed lines use dash=5*width, gap=3*width, offset=0 from the first point. Arrowheads are filled triangles at the endpoint, aligned to the nearest nonzero segment, length=8*width and base width=4*width (also for start arrows). Lines must have at least one nonzero segment. Text uses regular generic sans-serif, fontSize is em height, at is the baseline origin, align selects left/center/right justification. No HTML or markup execution. Native effective opacity, widths, outline colors and geometry must be captured as displayed; default platform styles cannot substitute for missing capture state.

MT is not a new display object. At record time the exporter receives the already displayed result objects and emits them, plus any historical explanation text and explicit group IDs supplied by the native state. An MT flag without these objects is incomplete evidence, not permission to recompute. This profile does not encode a proof calculus or assume a result intersection covers all cases. Coverage claims may be stored as optional native data, with verification status kept separate.

## 4. Preview and optional native data

preview fields: mime=`image/png`; coverage=`full-frame`; binding=frame content digest; data=base64. The preview must faithfully depict the entire board and important display of that frame. It is a viewing fallback only. It never supplies board values or editable marks. It is optional, not recording video.
PNG profile: signature and chunk CRCs valid; first chunk IHDR, last IEND, at least one IDAT; RGBA 8-bit, no interlace, width/height 1..4096, pixels<=16777216; only IHDR/IDAT/IEND permitted. Decoded compressed bytes<=8 MiB, decompressed scanline bytes<=67112960. Validate decompression and boundaries. No remote media. A valid PNG carrier alone does not prove visual fidelity; that requires the display matrix. If a reader cannot decode this profile, it reports partial viewing rather than silently claiming equivalence.

native item exact fields: namespace:ID; version:int 1..65535; binding:frame content digest; data:base64. Each item<=8 MiB decoded; namespace unique per frame. These are optional, opaque, nonexecuting bytes; no mandatory private algorithms. Binding is association only, not proof correctness. Unknown data is retained on unmodified save.

## 5. Save, edit and continuation

Unmodified direct save copies original file bytes exactly. Changing metadata, frame count/order, board or display is a modified export. Keep the original source as an immutable archive; create a new session ID and frame IDs, source={original file SHA-256, selected original frame IDs}. Never transplant opaque native data into changed frames. Drop it and list a loss with reason `stale-binding`; preview is dropped unless regenerated from captured display. If the original cannot be archived, refuse that modified export. Unknown display objects whose geometry still applies may be explicitly carried, otherwise list a loss. No silent discarding.

Independent continuation uses the selected board arrays, new session identity, unchanged source archive, and no historical display/native execution. This is a separate application path; the existing Windows load-and-truncate entry may remain. Known editable native data can be restored only through an explicit platform rule; this profile does not grant that permission. Application integration is not implemented by this proposal.

## 6. Batch matrix and candidate decisions

The accompanying generator/verifier produces one batch for board arrays, all five object types, initial/conclusion roles, frozen multigroup objects, multiple phases and operation groups, Unicode text, unknown optional/required objects, opaque native bytes, preview carrier, stale binding, duplicate keys, invalid coordinates, truncation and declared oversized payload. expected.json gives exact pass/fail or PARTIAL_VIEW outcomes.

For the same candidate hash manifest, each side reports: read; direct-save round trip; display at two board sizes (GUI and offscreen separately); edit/archive/loss handling; independent continuation; legacy v3/O11; malformed/version/limits. NOT_RUN is distinct from FAIL. Pure codec tests are not GUI or product acceptance.

Windows offers W1 as an AMEND with full files and accepts these exact candidate bytes within this task's document/prototype scope after manifest generation. Mac may ACK the same hashes or return a complete amended candidate. No previous ACK covers different bytes; no claim of bilateral acceptance, release, merge or background listening follows from a comment.
