# Mac source reference

These are the actual new Mac adapter helpers and probes used for this M2 result. Source hashes identify the tested local source snapshot. They are provided for inspection and adaptation; this folder is not a replacement application or a complete application checkout. ReplayFrameCodec and ReplayFrameRenderer are independent; ReplayFrameCapture, ReplayFramework and the application/capture probes require the existing Mac application classes. Existing integration changes are described in results/mac-application.md and identified in source-hashes.json.

The Java files retain their GPL-3.0-or-later notices. The repository root COPYING contains the complete license. No binary/runtime is distributed. The Python validator is the portable cross-language fixture check; Windows must still validate its own actual application.

The independent codec and renderer probes can be compiled from the package directory with a JDK supporting `--release 8`:

```text
javac --release 8 -encoding UTF-8 -d java-out reference-java/ReplayFrameCodec.java reference-java/ReplayFrameCodecProbe.java reference-java/ReplayFrameRenderer.java reference-java/ReplayFrameRendererProbe.java
java -Djava.awt.headless=true -cp java-out sudoku.ReplayFrameCodecProbe fixtures java-results
java -Djava.awt.headless=true -cp java-out sudoku.ReplayFrameRendererProbe java-results fixtures/all-content.hrep
```

These are independent helper tests, not Windows application acceptance. Use a separate output directory; do not change fixture inputs or their manifest to make tests pass.
