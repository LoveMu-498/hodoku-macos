# W1 batch acceptance matrix

Run (Python 3 standard library only):

```text
python replay_framework_candidate.py generate fixtures
python replay_framework_candidate.py verify fixtures
```

The public package includes exactly the same prototype, expected.json and binary fixtures. Generation is deterministic; UUID creation is only used in the continuation probe, not hashed fixtures. The prototype is isolated from the Windows application. all-content.json is a readable representation of the binary fixture. No private application source is included.

| Check | Windows W1 isolated prototype | Current Windows application | Mac W1 |
| --- | --- | --- | --- |
| Snapshot / types / phase / groups / clocks | 18 fixture outcomes verified | v4 rejected as unsupported (expected old-reader behavior) | NOT_RUN |
| Direct-save round trip / opaque native | Canonical and noncanonical unchanged bytes preserved | Mac v3 1894-byte input reads; common prefix 703 bytes exact on save, HMX tail lost | NOT_RUN |
| Frozen display / two sizes | O11 to geometry/style records tested; MT without captured result explicitly PARTIAL_VIEW | New display renderer and recorder not connected | NOT_RUN |
| GUI / raster drawing | NOT_RUN | NOT_RUN | NOT_RUN |
| Preview carrier | Bounded PNG CRC/decompression tested; visual fidelity NOT_RUN | NOT_RUN | NOT_RUN |
| Edited export | Archive bytes returned, new IDs, stale native omitted and losses recorded | Not integrated into editor | NOT_RUN |
| Independent continuation | New ID, same four board arrays, inert historical display/native, original bytes unchanged | Existing load-and-truncate retained; new path not integrated | NOT_RUN |
| Version / corruption / limits | Duplicate keys, integer/board/time/group limits, UTF overflow, native base64, PNG CRC, stale bindings, truncation and oversized declared length reject | Current authoritative v3 reader rejects v4 | NOT_RUN |
| Legacy O11 | Original Windows 703-byte sample remains unchanged; Mac return hash verified | Mac return snapshot and O11/MT raw roles verified without GUI | Mac O11 result reported in PR comment 6139987710; not a W1 result |

Capture integration needs actual native display state (effective opacity, stroke, outline geometry, group IDs and already displayed MT results). The standalone projection uses explicit synthetic style/result input and does not pretend to capture a live game. It does not run a solver. Integrating recorder capture, edited rerecording and a new continuation entry requires relaxing the current task's no-replay-logic-change constraint for those specific paths. Candidate definition, codec/projection preparation, fixtures and compatibility probes proceed without that integration.

Current legacy Mac return: decoded size 1894, SHA-256 135dae712b01125d37b396a9f336f64dafaf4a9a8a666b15d18efec27952c863. Windows direct-save result: size 703, SHA-256 a9b5c6cf3502afa23c3007348127700c7798f6703c93463872f3ac17395951a3. These are pure synthetic file hashes, not private source hashes.

Mac should use this same manifest, run the verifier, implement its own reader/projection as authorized, and return the matrix with only failures, missing common fields or complete amendments. Do not independently change wire bytes for mutual tests. Candidate acceptance and product acceptance remain separate.
