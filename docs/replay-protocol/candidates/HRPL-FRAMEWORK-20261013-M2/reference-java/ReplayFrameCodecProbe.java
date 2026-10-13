/* Copyright (C) 2026 HoDoKu macOS contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package sudoku;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Independent Java conformance against the unchanged Windows W1 fixtures. */
public final class ReplayFrameCodecProbe {
    private static int checks;

    private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("fixture directory required");
        Path fixtures = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : Files.createTempDirectory("hrpl-java-conformance-");
        Files.createDirectories(output);
        Map<String, Object> expected = ReplayFrameCodec.map(ReplayFrameCodec.parseJson(Files.readAllBytes(fixtures.resolve("expected.json"))));
        List<Object> outcomes = new ArrayList<Object>();
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Map<String, Object> item = ReplayFrameCodec.map(entry.getValue());
            byte[] raw = Files.readAllBytes(fixtures.resolve(entry.getKey()));
            check(raw.length == ReplayFrameCodec.number(item.get("bytes")), entry.getKey() + " bytes");
            check(ReplayFrameCodec.sha256(raw).equals(item.get("sha256")), entry.getKey() + " hash");
            String outcome;
            try {
                ReplayFrameCodec.Document doc = ReplayFrameCodec.read(raw);
                outcome = doc.notices.contains("PREVIEW_FALLBACK") ? "PREVIEW_FALLBACK"
                        : doc.notices.contains("PARTIAL_VIEW") ? "PARTIAL_VIEW" : "PASS";
            } catch (IOException ex) { outcome = "FAIL"; }
            check(outcome.equals(item.get("expected")), entry.getKey() + " expected " + item.get("expected") + " got " + outcome);
            outcomes.add(object("case", entry.getKey(), "result", outcome));
        }
        byte[] canonical = Files.readAllBytes(fixtures.resolve("all-content.hrep"));
        ReplayFrameCodec.Document document = ReplayFrameCodec.read(canonical);
        check(Arrays.equals(canonical, ReplayFrameCodec.encode(document.root)), "independent canonical byte round trip");
        byte[] noncanonical = Files.readAllBytes(fixtures.resolve("noncanonical.hrep"));
        ReplayFrameCodec.Document original = ReplayFrameCodec.read(noncanonical);
        check(Arrays.equals(noncanonical, original.original), "noncanonical original retention");
        check(Arrays.equals(canonical, ReplayFrameCodec.encode(original.root)), "noncanonical canonicalizes to Windows bytes");
        ReplayFrameCodec.writeBytes(output.resolve("direct-copy.hrep"), original.original);
        check(Arrays.equals(noncanonical, Files.readAllBytes(output.resolve("direct-copy.hrep"))), "unchanged disk save");
        ReplayFrameCodec.write(output.resolve("java-canonical.hrep"), document.root);
        check(Arrays.equals(canonical, Files.readAllBytes(output.resolve("java-canonical.hrep"))), "Java write independently matches Windows bytes");

        unknownScalarTests(document.root);
        blobExemptionTests(document.root);
        strictJsonTests();
        extendedLabelTests(document.root, output);
        pngTests();

        final Path protectedFile = output.resolve("protected.hrep");
        Files.write(protectedFile, noncanonical);
        final Map<String, Object> invalid = ReplayFrameCodec.deepCopy(document.root);
        ReplayFrameCodec.list(board(frame(invalid, 0)).get("values")).set(0, 10L);
        rejected("invalid write", new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.write(protectedFile, invalid);
        }});
        check(Arrays.equals(noncanonical, Files.readAllBytes(protectedFile)), "failed write preserved successful destination");
        Map<String, Object> report = object("codec", "independent Java 8", "fixtureOutcomes", outcomes,
                "checks", checks, "canonicalSha256", ReplayFrameCodec.sha256(canonical),
                "profile01", "18 original W1 outcomes PASS", "profile02", "bounded 16 MiB phaseLabel PASS",
                "scope", "codec only; application and renderer verified separately");
        Files.write(output.resolve("java-codec-results.json"), ReplayFrameCodec.canonical(report));
        System.out.println("PASS ReplayFrameCodecProbe: " + expected.size() + " unchanged Windows outcomes; " + checks
                + " checks; independent canonical bytes; bounded profile 0.2 labels; original preservation");
    }

    private static void unknownScalarTests(Map<String, Object> baseline) throws Exception {
        final Map<String, Object> document = ReplayFrameCodec.deepCopy(baseline);
        final Map<String, Object> unknown = object("id", "future", "type", "example.future", "required", false,
                "important", false, "z", 0L, "namespace", "synthetic", "binding", "not-a-native-binding", "data", 1.5);
        ReplayFrameCodec.list(frame(document, 0).get("display")).add(unknown);
        rejected("unknown blob-shaped float", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
        unknown.put("data", repeat('x', 65536));
        rejected("unknown blob-shaped oversized text", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
        unknown.put("data", repeat('x', 65535));
        ReplayFrameCodec.Document valid = ReplayFrameCodec.read(ReplayFrameCodec.encode(document));
        check(valid.notices.contains("UNKNOWN_OPTIONAL:future"), "unknown 65535-byte data retained");
        check(ReplayFrameCodec.map(ReplayFrameCodec.list(frame(valid.root, 0).get("display")).get(0)).get("data").equals(unknown.get("data")),
                "unknown opaque content exact");
        unknown.put("data", true);
        check(ReplayFrameCodec.encode(document).length > 0, "unknown boolean remains JSON boolean");
        unknown.put("data", object("nested", Double.NaN));
        rejected("unknown nested NaN", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
    }

    private static void strictJsonTests() throws Exception {
        String[] invalid = { "\ufeff{}", "{\"a\":1,\"a\":2}", "[1,]", "{\"a\":1,}", "[01]", "[1.0]", "[1e0]",
                "[9007199254740992]", "[-9007199254740992]", "[NaN]", "true false", "[\"\\ud800\"]", "[\"\\udfff\"]",
                "[\"\\uｆｆｆｆ\"]", "[\"line\nraw\"]", "[\"\\x20\"]" };
        for (final String text : invalid) rejected("strict JSON " + text, new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.parseJson(text.getBytes(StandardCharsets.UTF_8));
        }});
        rejected("malformed UTF8", new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.parseJson(new byte[] { '"', (byte) 0xc0, (byte) 0xaf, '"' });
        }});
        final String deep = repeat('[', 65) + "0" + repeat(']', 65);
        rejected("depth65", new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.parseJson(deep.getBytes(StandardCharsets.UTF_8));
        }});
        check(ReplayFrameCodec.parseJson((repeat('[', 64) + "0" + repeat(']', 64)).getBytes(StandardCharsets.UTF_8)) instanceof List,
                "depth64 exact accepted");
        check(ReplayFrameCodec.parseJson("\"\\ud83d\\ude00\"".getBytes(StandardCharsets.UTF_8)).equals("😀"), "paired escaped Unicode");
        Map<String, Object> ordering = object("𐀀", 2L, "\ue000", 1L, "z", 0L);
        check(new String(ReplayFrameCodec.canonical(ordering), StandardCharsets.UTF_8).equals("{\"z\":0,\"\ue000\":1,\"𐀀\":2}"),
                "Unicode scalar key order differs correctly from UTF16 order");
        String controls = "\u0000\b\f\n\r\t\u001f/\\\"中😀";
        check(new String(ReplayFrameCodec.canonical(controls), StandardCharsets.UTF_8)
                .equals("\"\\u0000\\b\\f\\n\\r\\t\\u001f/\\\\\\\"中😀\""), "canonical escapes match Python");
    }

    private static void blobExemptionTests(Map<String, Object> baseline) throws Exception {
        Map<String, Object> document = ReplayFrameCodec.deepCopy(baseline);
        Map<String, Object> evidence = frame(document, 1);
        byte[] opaque = new byte[100000];
        for (int i = 0; i < opaque.length; i++) opaque[i] = (byte) (i * 37);
        Map<String, Object> nativeItem = ReplayFrameCodec.map(ReplayFrameCodec.list(evidence.get("native")).get(0));
        nativeItem.put("data", Base64.getEncoder().encodeToString(opaque));
        ReplayFrameCodec.Document withNative = ReplayFrameCodec.read(ReplayFrameCodec.encode(document));
        check(Arrays.equals(opaque, ReplayFrameCodec.blob(ReplayFrameCodec.map(
                ReplayFrameCodec.list(frame(withNative.root, 1).get("native")).get(0)).get("data"))),
                "validated native base64 above text bound retained");
        byte[] pixels = new byte[256 * 1025];
        java.util.Random random = new java.util.Random(6142584473L);
        random.nextBytes(pixels);
        for (int y = 0; y < 256; y++) pixels[y * 1025] = 0;
        byte[] image = png(256, 256, pixels);
        check(image.length > 65535, "large preview test exercises blob exemption");
        evidence.put("preview", object("mime", "image/png", "coverage", "full-frame", "binding", ReplayFrameCodec.binding(evidence),
                "data", Base64.getEncoder().encodeToString(image)));
        ReplayFrameCodec.Document withPreview = ReplayFrameCodec.read(ReplayFrameCodec.encode(document));
        check(Arrays.equals(image, ReplayFrameCodec.blob(ReplayFrameCodec.map(frame(withPreview.root, 1).get("preview")).get("data"))),
                "validated preview base64 above text bound retained");
    }

    private static void extendedLabelTests(Map<String, Object> baseline, Path output) throws Exception {
        final Map<String, Object> document = ReplayFrameCodec.deepCopy(baseline);
        final Map<String, Object> first = frame(document, 0);
        first.put("phaseLabel", repeat('中', 21846)); // 65538 UTF-8 bytes, native label remains intact.
        rejected("0.1 keeps original 65535-byte label bound", new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.encode(document);
        }});
        document.put("format", ReplayFrameCodec.PROFILE_EXTENDED_LABEL);
        byte[] extended = ReplayFrameCodec.encode(document);
        check(frame(ReplayFrameCodec.read(extended).root, 0).get("phaseLabel").equals(first.get("phaseLabel")), "0.2 Unicode label unchanged");
        Files.write(output.resolve("mac-v02-long-label.hrep"), extended);
        first.put("phaseLabel", repeat('中', 5592405) + "x"); // Exactly 16 MiB in UTF-8.
        byte[] exact = ReplayFrameCodec.encode(document);
        check(frame(ReplayFrameCodec.read(exact).root, 0).get("phaseLabel").equals(first.get("phaseLabel")), "0.2 exact16MiB label unchanged");
        first.put("phaseLabel", first.get("phaseLabel") + "x");
        rejected("0.2 over16MiB label", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
        first.put("phaseLabel", repeat('x', ReplayFrameCodec.MAX_PHASE_LABEL_BYTES));
        byte[] ascii = ReplayFrameCodec.encode(document);
        check(frame(ReplayFrameCodec.read(ascii).root, 0).get("phaseLabel").equals(first.get("phaseLabel")),
                "0.2 ASCII16MiB parser capacity is independent of smaller base64 cap");
        first.put("phaseLabel", "valid");
        final Map<String, Object> unknown = object("id", "future-label", "type", "example.future", "required", false,
                "important", false, "z", 0L, "phaseLabel", repeat('x', 65536));
        ReplayFrameCodec.list(first.get("display")).add(unknown);
        rejected("0.2 unknown object label cannot borrow exemption", new Throwing() { public void run() throws Exception {
            ReplayFrameCodec.encode(document);
        }});
        unknown.put("phaseLabel", "valid");
        unknown.put("text", repeat('x', 65536));
        rejected("0.2 sibling text remains65535", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
        unknown.put("text", "valid");
        document.put("format", "HRPL-FRAME/0.3");
        rejected("unknown profile rejects", new Throwing() { public void run() throws Exception { ReplayFrameCodec.encode(document); }});
    }

    private static void pngTests() throws Exception {
        byte[] valid = png(2, 2, new byte[18]);
        ReplayFrameCodec.validatePng(valid);
        check(ReplayFrameCodec.blob(Base64.getEncoder().encodeToString(valid)).length == valid.length, "valid PNG/base64");
        final byte[] badFilter = png(1, 1, new byte[] { 5, 0, 0, 0, 0 });
        rejected("PNG scan filter", new Throwing() { public void run() throws Exception { ReplayFrameCodec.validatePng(badFilter); }});
        final byte[] shortScan = png(2, 2, new byte[17]);
        rejected("PNG short inflate", new Throwing() { public void run() throws Exception { ReplayFrameCodec.validatePng(shortScan); }});
        final byte[] bomb = png(1, 1, new byte[100000]);
        rejected("PNG inflated bound", new Throwing() { public void run() throws Exception { ReplayFrameCodec.validatePng(bomb); }});
        final byte[] tooWide = png(4097, 1, new byte[1]);
        rejected("PNG dimensions", new Throwing() { public void run() throws Exception { ReplayFrameCodec.validatePng(tooWide); }});
        final byte[] crc = valid.clone(); crc[crc.length - 1] ^= 1;
        rejected("PNG CRC", new Throwing() { public void run() throws Exception { ReplayFrameCodec.validatePng(crc); }});
        for (final String invalid : new String[] { "A===", "AA", "A A=", "AA=A", "!notbase64!" }) {
            rejected("base64 malformed", new Throwing() { public void run() throws Exception { ReplayFrameCodec.blob(invalid); }});
        }
    }

    private static byte[] png(int width, int height, byte[] scan) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 });
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put(new byte[] { 8, 6, 0, 0, 0 }).array());
        Deflater compressor = new Deflater();
        compressor.setInput(scan); compressor.finish();
        java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!compressor.finished()) compressed.write(buffer, 0, compressor.deflate(buffer));
        compressor.end();
        chunk(out, "IDAT", compressed.toByteArray()); chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(java.io.ByteArrayOutputStream out, String tag, byte[] content) throws Exception {
        byte[] name = tag.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(content.length).array()); out.write(name); out.write(content);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(content);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static Map<String, Object> frame(Map<String, Object> doc, int i) throws IOException {
        return ReplayFrameCodec.map(ReplayFrameCodec.list(doc.get("frames")).get(i));
    }

    private static Map<String, Object> board(Map<String, Object> frame) throws IOException { return ReplayFrameCodec.map(frame.get("board")); }

    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }

    private static String repeat(char c, int n) {
        char[] value = new char[n]; Arrays.fill(value, c); return new String(value);
    }

    private static void rejected(String label, Throwing test) throws Exception {
        try { test.run(); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("accepted invalid: " + label);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
