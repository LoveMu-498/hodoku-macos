/*
 * HoDoKu - a sudoku solver and generator.
 * Copyright (C) 2026 HoDoKu macOS contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package sudoku;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Independent Java implementation of the HRPL-FRAME/0.1 W1 candidate.
 * Shared records are inert: this class never invokes a solver or deserializes native data.
 */
public final class ReplayFrameCodec {
    public static final int MAGIC = 0x4852504c;
    public static final int VERSION = 4;
    public static final String PROFILE = "HRPL-FRAME/0.1";
    public static final String PROFILE_EXTENDED_LABEL = "HRPL-FRAME/0.2";
    public static final int MAX_PHASE_LABEL_BYTES = 16 * 1024 * 1024;
    public static final int MAX_FILE_BYTES = 128 * 1024 * 1024;
    public static final int MAX_BLOB_BYTES = 8 * 1024 * 1024;
    private static final int MAX_BASE64_CHARS = (MAX_BLOB_BYTES + 2) / 3 * 4;
    private static final long MAX_INTEGER = 9007199254740991L;
    private static final Set<String> DISPLAY_TYPES = new HashSet<String>(
            Arrays.asList("mark", "highlight", "line", "region", "text"));
    private static final String CONTENT_FIELDS =
            "id operationGroup phase phaseLabel wallTimeMs elapsedMs board display";

    private ReplayFrameCodec() { }

    public static final class Document {
        /** Mutable decoded document; use encode after changes, original only for unchanged saving. */
        public final Map<String, Object> root;
        /** Original, validated bytes, including any noncanonical whitespace. */
        public final byte[] original;
        public final List<String> notices;
        private final Map<String, List<String>> byFrame;

        private Document(Map<String, Object> root, byte[] original, Validation result) {
            this.root = root;
            this.original = original.clone();
            this.notices = Collections.unmodifiableList(result.notices);
            this.byFrame = result.byFrame;
        }

        public List<String> notices(Map<String, Object> frame) {
            List<String> found = byFrame.get(frame.get("id"));
            return found == null ? Collections.<String>emptyList() : found;
        }
    }

    public static Document read(Path path) throws IOException {
        long size = Files.size(path);
        require(size >= 12 && size <= MAX_FILE_BYTES, "file bound");
        // Bound reads as well as the initial stat, including files that change while reading.
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(size, 65536));
        try (InputStream input = Files.newInputStream(path)) {
            byte[] chunk = new byte[16384];
            int count;
            while ((count = input.read(chunk)) != -1) {
                require(out.size() <= MAX_FILE_BYTES - count, "file bound");
                out.write(chunk, 0, count);
            }
        }
        return read(out.toByteArray());
    }

    public static Document read(byte[] raw) throws IOException {
        require(raw != null && raw.length >= 12 && raw.length <= MAX_FILE_BYTES, "file bound");
        ByteBuffer header = ByteBuffer.wrap(raw);
        require(header.getInt() == MAGIC && header.getInt() == VERSION, "magic/version");
        long length = header.getInt() & 0xffffffffL;
        require(length <= MAX_FILE_BYTES - 12 && length == raw.length - 12L, "payload length/EOF");
        Map<String, Object> root = map(parseJson(Arrays.copyOfRange(raw, 12, raw.length)));
        Validation result = validateDocument(root);
        return new Document(root, raw, result);
    }

    /** Strict scalar JSON reader, also useful for reading a conformance manifest. */
    public static Object parseJson(byte[] raw) throws IOException {
        require(raw != null && raw.length <= MAX_FILE_BYTES, "JSON byte limit");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException ex) {
            throw error("malformed UTF-8", ex);
        }
        return new JsonReader(json).parse();
    }

    public static byte[] encode(Map<String, Object> root) throws IOException {
        validateDocument(root);
        byte[] payload = canonical(root);
        require(payload.length <= MAX_FILE_BYTES - 12, "file limit");
        ByteBuffer result = ByteBuffer.allocate(payload.length + 12);
        result.putInt(MAGIC).putInt(VERSION).putInt(payload.length).put(payload);
        return result.array();
    }

    /** Validate before touching the destination; rename only after complete temporary-file write. */
    public static void write(Path path, Map<String, Object> root) throws IOException {
        writeBytes(path, encode(root));
    }

    public static void writeBytes(Path path, byte[] raw) throws IOException {
        read(raw);
        Path absolute = path.toAbsolutePath();
        Path temporary = Files.createTempFile(absolute.getParent(), ".hrpl-frame-", ".tmp");
        try {
            Files.write(temporary, raw);
            // If atomic replacement is unavailable, leave the existing destination intact.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static List<String> validate(Map<String, Object> root) throws IOException {
        return Collections.unmodifiableList(validateDocument(root).notices);
    }

    public static String binding(Map<String, Object> frame) throws IOException {
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        for (String name : CONTENT_FIELDS.split(" ")) {
            require(frame.containsKey(name), "missing frame content " + name);
            content.put(name, frame.get(name));
        }
        return sha256(canonical(content));
    }

    public static String sha256(byte[] raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) {
                out.append(Character.forDigit((b & 255) >>> 4, 16));
                out.append(Character.forDigit(b & 15, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Same canonical form as Python json.dumps(sort_keys=True, ensure_ascii=False, separators=(",", ":")). */
    public static byte[] canonical(Object value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeJson(value, out, 0);
        return out.toByteArray();
    }

    public static Map<String, Object> deepCopy(Map<String, Object> value) throws IOException {
        return map(parseJson(canonical(value)));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) throws IOException {
        require(value instanceof Map, "object required");
        Map<?, ?> found = (Map<?, ?>) value;
        for (Object key : found.keySet()) require(key instanceof String, "string object key required");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) throws IOException {
        require(value instanceof List, "array required");
        return (List<Object>) value;
    }

    public static long number(Object value) throws IOException {
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long,
                "integer required");
        return ((Number) value).longValue();
    }

    private static final class Validation {
        final List<String> notices = new ArrayList<String>();
        final Map<String, List<String>> byFrame = new LinkedHashMap<String, List<String>>();
    }

    private static Validation validateDocument(Map<String, Object> doc) throws IOException {
        fields(doc, "format session frames source losses");
        boolean extendedLabel = PROFILE_EXTENDED_LABEL.equals(doc.get("format"));
        require(PROFILE.equals(doc.get("format")) || extendedLabel, "profile");
        Map<String, Object> session = record(doc.get("session"), "id startedAtMs endedAtMs elapsedMs completed");
        identity(session.get("id")); bool(session.get("completed"));
        for (String name : new String[] { "startedAtMs", "endedAtMs", "elapsedMs" }) time(session.get(name));
        List<Object> frames = array(doc.get("frames"), 1, 100000);
        Set<String> ids = new HashSet<String>();
        Set<String> groups = new HashSet<String>();
        Set<Object> blobRecords = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        Set<Object> extendedLabelRecords = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        String previousGroup = null;
        long previousTime = -1;
        Validation result = new Validation();
        for (Object item : frames) {
            Map<String, Object> frame = record(item,
                    "id operationGroup phase phaseLabel wallTimeMs elapsedMs board display native preview");
            String id = identity(frame.get("id"));
            String group = identity(frame.get("operationGroup"));
            text(frame.get("phaseLabel"), extendedLabel ? MAX_PHASE_LABEL_BYTES : 65535);
            if (extendedLabel) extendedLabelRecords.add(frame);
            require(ids.add(id), "duplicate frame ID");
            if (!group.equals(previousGroup)) {
                require(groups.add(group), "noncontiguous operation group");
                previousGroup = group;
            }
            choice(frame.get("phase"), "input evidence result applied other", "phase");
            time(frame.get("wallTimeMs"));
            long elapsed = time(frame.get("elapsedMs"));
            require(elapsed >= previousTime, "elapsed order");
            previousTime = elapsed;
            validateBoard(frame.get("board"));
            List<Object> objects = array(frame.get("display"), 0, 10000);
            Set<String> objectIds = new HashSet<String>();
            List<String> frameNotices = new ArrayList<String>();
            boolean partial = false;
            for (Object object : objects) {
                Map<String, Object> display = map(object);
                String objectId = identity(display.get("id"));
                String type = identity(display.get("type"));
                boolean required = bool(display.get("required"));
                boolean important = bool(display.get("important"));
                integer(display.get("z"), -32768, 32767);
                require(objectIds.add(objectId), "duplicate object ID");
                if (!DISPLAY_TYPES.contains(type)) {
                    require(!required, "unknown required object");
                    partial |= important;
                    frameNotices.add("UNKNOWN_OPTIONAL:" + objectId);
                } else {
                    validateDisplay(display, type);
                }
            }
            String digest = binding(frame);
            Set<String> namespaces = new HashSet<String>();
            for (Object nativeItem : array(frame.get("native"), 0, 256)) {
                Map<String, Object> nativeRecord = record(nativeItem, "namespace version binding data");
                String namespace = identity(nativeRecord.get("namespace"));
                integer(nativeRecord.get("version"), 1, 65535);
                require(namespaces.add(namespace), "native namespace duplicate");
                digest(nativeRecord.get("binding"));
                require(digest.equals(nativeRecord.get("binding")), "stale native binding");
                blob(nativeRecord.get("data"));
                blobRecords.add(nativeRecord);
            }
            if (frame.get("preview") != null) {
                Map<String, Object> preview = record(frame.get("preview"), "mime coverage binding data");
                require("image/png".equals(preview.get("mime")) && "full-frame".equals(preview.get("coverage")),
                        "preview profile");
                digest(preview.get("binding"));
                require(digest.equals(preview.get("binding")), "stale preview binding");
                validatePng(blob(preview.get("data")));
                blobRecords.add(preview);
            }
            if (partial) frameNotices.add(frame.get("preview") == null ? "PARTIAL_VIEW" : "PREVIEW_FALLBACK");
            result.notices.addAll(frameNotices);
            result.byFrame.put(id, Collections.unmodifiableList(frameNotices));
        }
        require(number(session.get("elapsedMs")) >= previousTime, "session elapsed");
        if (doc.get("source") != null) {
            Map<String, Object> source = record(doc.get("source"), "fileSha256 frameIds");
            digest(source.get("fileSha256"));
            for (Object id : array(source.get("frameIds"), 1, 100000)) identity(id);
        }
        for (Object lossItem : array(doc.get("losses"), 0, 100000)) {
            Map<String, Object> loss = record(lossItem, "frameId namespace reason");
            identity(loss.get("frameId")); identity(loss.get("namespace")); text(loss.get("reason"));
        }
        // Exemption is based on identity of a validated native/preview record, never guessed field names.
        walk(doc, 0, blobRecords, extendedLabelRecords);
        return result;
    }

    private static void validateBoard(Object value) throws IOException {
        Map<String, Object> board = record(value, "values fixed candidates userCandidates");
        for (String key : board.keySet()) {
            List<Object> values = array(board.get(key), 81, 81);
            for (Object item : values) {
                if ("fixed".equals(key)) bool(item);
                else integer(item, 0, "values".equals(key) ? 9 : 511);
            }
        }
        List<Object> values = list(board.get("values"));
        List<Object> fixed = list(board.get("fixed"));
        List<Object> candidates = list(board.get("candidates"));
        for (int i = 0; i < 81; i++) {
            long digit = number(values.get(i));
            require(!bool(fixed.get(i)) || digit != 0, "empty given");
            require(digit == 0 || number(candidates.get(i)) == 0, "filled candidates");
        }
    }

    private static void validateDisplay(Map<String, Object> object, String type) throws IOException {
        String additional;
        if ("mark".equals(type)) additional = "shape bounds anchor color width initial conclusion outline";
        else if ("highlight".equals(type)) additional = "bounds anchor fill";
        else if ("line".equals(type)) additional = "points color width dash arrow";
        else if ("region".equals(type)) additional = "points fill stroke width";
        else additional = "at text color fontSize align";
        fields(object, "id type required important z group " + additional);
        if (object.get("group") != null) identity(object.get("group"));
        if (object.containsKey("bounds")) bounds(object.get("bounds"));
        if (object.containsKey("anchor") && object.get("anchor") != null) {
            Map<String, Object> anchor = record(object.get("anchor"), "cell digit");
            integer(anchor.get("cell"), 0, 80); integer(anchor.get("digit"), 0, 9);
        }
        for (String key : new String[] { "color", "fill", "stroke" }) {
            if (object.containsKey(key)) color(object.get(key));
        }
        if (object.containsKey("width")) integer(object.get("width"), 1, 100000);
        if (object.containsKey("points")) {
            for (Object p : array(object.get("points"), "region".equals(type) ? 3 : 2, 4096)) point(p);
        }
        if ("mark".equals(type)) {
            choice(object.get("shape"), "circle cross", "mark shape");
            bool(object.get("initial")); bool(object.get("conclusion"));
            if (object.get("outline") != null) {
                Map<String, Object> outline = record(object.get("outline"), "color width gap");
                color(outline.get("color")); integer(outline.get("width"), 1, 100000);
                integer(outline.get("gap"), 0, 100000);
            }
        } else if ("line".equals(type)) {
            choice(object.get("dash"), "solid dashed", "dash");
            choice(object.get("arrow"), "none start end both", "arrow");
            List<Object> points = list(object.get("points"));
            boolean nonzero = false;
            for (int i = 1; i < points.size(); i++) {
                List<Object> a = list(points.get(i - 1)), b = list(points.get(i));
                nonzero |= number(a.get(0)) != number(b.get(0)) || number(a.get(1)) != number(b.get(1));
            }
            require(nonzero, "zero length line");
        } else if ("text".equals(type)) {
            point(object.get("at")); text(object.get("text"));
            integer(object.get("fontSize"), 1000, 500000);
            choice(object.get("align"), "left center right", "text align");
        }
    }

    private static void walk(Object value, int depth, Set<Object> blobRecords, Set<Object> extendedLabelRecords) throws IOException {
        require(depth <= 64, "JSON nesting limit");
        if (value instanceof Map) {
            Map<String, Object> object = map(value);
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                text(entry.getKey());
                if ("data".equals(entry.getKey()) && blobRecords.contains(object)) continue;
                if ("phaseLabel".equals(entry.getKey()) && extendedLabelRecords.contains(object)) continue;
                walk(entry.getValue(), depth + 1, blobRecords, extendedLabelRecords);
            }
        } else if (value instanceof List) {
            for (Object item : list(value)) walk(item, depth + 1, blobRecords, extendedLabelRecords);
        } else if (value instanceof String) text(value);
        else if (value != null && !(value instanceof Boolean)) integer(value, -MAX_INTEGER, MAX_INTEGER);
    }

    public static byte[] blob(Object value) throws IOException {
        require(value instanceof String, "base64 string");
        String text = (String) value;
        require(text.length() <= MAX_BASE64_CHARS && text.length() % 4 == 0, "base64 limit/padding");
        int padding = text.endsWith("==") ? 2 : text.endsWith("=") ? 1 : 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            require((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || (c == '=' && i >= text.length() - padding), "invalid base64");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(text);
            require(raw.length <= MAX_BLOB_BYTES, "blob limit");
            return raw;
        } catch (IllegalArgumentException ex) {
            throw error("invalid base64", ex);
        }
    }

    /** Validate the constrained PNG carrier without ImageIO or unbounded decompression. */
    public static void validatePng(byte[] raw) throws IOException {
        require(raw != null && raw.length <= MAX_BLOB_BYTES, "PNG encoded bound");
        byte[] signature = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
        require(raw.length >= 8 && Arrays.equals(Arrays.copyOf(raw, 8), signature), "PNG signature");
        int position = 8, width = 0, height = 0, chunks = 0;
        boolean sawData = false, sawEnd = false;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        while (position < raw.length) {
            require(position <= raw.length - 12, "PNG truncated");
            long count = unsignedInt(raw, position);
            require(count <= MAX_BLOB_BYTES && count <= raw.length - position - 12L, "PNG chunk bound");
            int length = (int) count;
            String tag = new String(raw, position + 4, 4, StandardCharsets.US_ASCII);
            require("IHDR".equals(tag) || "IDAT".equals(tag) || "IEND".equals(tag), "PNG profile chunk");
            CRC32 crc = new CRC32();
            crc.update(raw, position + 4, length + 4);
            require(crc.getValue() == unsignedInt(raw, position + 8 + length), "PNG CRC");
            chunks++;
            if ("IHDR".equals(tag)) {
                require(chunks == 1 && length == 13, "PNG IHDR");
                long w = unsignedInt(raw, position + 8), h = unsignedInt(raw, position + 12);
                require(w >= 1 && w <= 4096 && h >= 1 && h <= 4096 && w * h <= 16777216, "PNG dimensions");
                require(raw[position + 16] == 8 && raw[position + 17] == 6
                        && raw[position + 18] == 0 && raw[position + 19] == 0 && raw[position + 20] == 0, "PNG RGBA8");
                width = (int) w; height = (int) h;
            } else if ("IDAT".equals(tag)) {
                require(!sawEnd && width != 0, "PNG IDAT order");
                require(compressed.size() <= MAX_BLOB_BYTES - length, "PNG compressed bound");
                compressed.write(raw, position + 8, length);
                sawData = true;
            } else {
                require(length == 0 && position + 12 == raw.length, "PNG IEND");
                sawEnd = true;
            }
            position += length + 12;
        }
        require(sawData && sawEnd && width != 0, "PNG completeness");
        int stride = 1 + 4 * width;
        int expected = height * stride;
        require(expected <= 67112960, "PNG inflated bound");
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed.toByteArray());
            byte[] buffer = new byte[16384];
            int total = 0;
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                require(count > 0 || inflater.finished(), "PNG incomplete zlib stream");
                require(total <= expected - count, "PNG inflated bound");
                int nextFilter = total % stride == 0 ? 0 : stride - total % stride;
                for (int i = nextFilter; i < count; i += stride) require((buffer[i] & 255) <= 4, "PNG filters");
                total += count;
            }
            require(total == expected && inflater.getRemaining() == 0, "PNG inflated bound/trailing data");
        } catch (DataFormatException ex) {
            throw error("PNG invalid zlib stream", ex);
        } finally {
            inflater.end();
        }
    }

    private static long unsignedInt(byte[] raw, int at) {
        return ((raw[at] & 255L) << 24) | ((raw[at + 1] & 255L) << 16)
                | ((raw[at + 2] & 255L) << 8) | (raw[at + 3] & 255L);
    }

    private static Map<String, Object> record(Object value, String names) throws IOException {
        Map<String, Object> object = map(value);
        fields(object, names);
        return object;
    }

    private static void fields(Map<String, Object> object, String names) throws IOException {
        require(object != null && object.keySet().equals(new HashSet<String>(Arrays.asList(names.split(" ")))),
                "fields: " + names);
    }

    private static List<Object> array(Object value, int min, int max) throws IOException {
        List<Object> result = list(value);
        require(result.size() >= min && result.size() <= max, "array length");
        return result;
    }

    private static boolean bool(Object value) throws IOException {
        require(value instanceof Boolean, "boolean required");
        return (Boolean) value;
    }

    private static long integer(Object value, long min, long max) throws IOException {
        long number = number(value);
        require(number >= min && number <= max, "integer range");
        return number;
    }

    private static long time(Object value) throws IOException { return integer(value, 0, MAX_INTEGER); }

    private static String text(Object value) throws IOException {
        return text(value, 65535);
    }

    private static String text(Object value, int maximumBytes) throws IOException {
        require(value instanceof String, "string required");
        String result = (String) value;
        // Count UTF-8 bytes without allocating a second potentially large label or blob-shaped value.
        int count = 0;
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < result.length() && Character.isLowSurrogate(result.charAt(i + 1)), "unpaired surrogate");
                i++; count += 4;
            } else {
                require(!Character.isLowSurrogate(c), "unpaired surrogate");
                count += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
            }
            require(count <= maximumBytes, "text limit");
        }
        return result;
    }

    private static String identity(Object value) throws IOException {
        String id = text(value);
        require(id.length() >= 1 && id.length() <= 128, "ID length");
        for (int i = 0; i < id.length(); i++) require(id.charAt(i) >= 0x21 && id.charAt(i) <= 0x7e, "ID character");
        return id;
    }

    private static void choice(Object value, String choices, String label) throws IOException {
        require(value instanceof String && Arrays.asList(choices.split(" ")).contains(value), label);
    }

    private static void color(Object value) throws IOException {
        require(value instanceof String && ((String) value).matches("#[0-9A-Fa-f]{8}"), "ARGB");
    }

    private static void digest(Object value) throws IOException {
        require(value instanceof String && ((String) value).matches("[0-9a-f]{64}"), "SHA-256");
    }

    private static void point(Object value) throws IOException {
        for (Object item : array(value, 2, 2)) integer(item, -500000, 1500000);
    }

    private static void bounds(Object value) throws IOException {
        List<Object> b = array(value, 4, 4);
        for (Object item : b) integer(item, -500000, 1500000);
        require(number(b.get(0)) < number(b.get(2)) && number(b.get(1)) < number(b.get(3)), "bounds");
    }

    private static void checkScalars(String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1)), "unpaired surrogate");
                i++;
            } else require(!Character.isLowSurrogate(c), "unpaired surrogate");
        }
    }

    private static final Comparator<String> SCALAR_ORDER = new Comparator<String>() {
        @Override public int compare(String a, String b) {
            int i = 0, j = 0;
            while (i < a.length() && j < b.length()) {
                int ac = a.codePointAt(i), bc = b.codePointAt(j);
                if (ac != bc) return Integer.compare(ac, bc);
                i += Character.charCount(ac); j += Character.charCount(bc);
            }
            return Integer.compare(a.length() - i, b.length() - j);
        }
    };

    private static void writeJson(Object value, ByteArrayOutputStream out, int depth) throws IOException {
        require(depth <= 64, "JSON nesting limit");
        if (value instanceof Map) {
            Map<String, Object> object = map(value);
            List<String> names = new ArrayList<String>(object.keySet());
            Collections.sort(names, SCALAR_ORDER);
            out.write('{');
            boolean first = true;
            for (String name : names) {
                if (!first) out.write(',');
                string(name, out); out.write(':'); writeJson(object.get(name), out, depth + 1);
                first = false;
            }
            out.write('}');
        } else if (value instanceof List) {
            out.write('[');
            boolean first = true;
            for (Object item : list(value)) {
                if (!first) out.write(',');
                writeJson(item, out, depth + 1); first = false;
            }
            out.write(']');
        } else if (value instanceof String) string((String) value, out);
        else if (value == null) bytes(out, "null");
        else if (value instanceof Boolean) bytes(out, Boolean.TRUE.equals(value) ? "true" : "false");
        else bytes(out, Long.toString(integer(value, -MAX_INTEGER, MAX_INTEGER)));
        require(out.size() <= MAX_FILE_BYTES - 12, "JSON byte limit");
    }

    private static void string(String value, ByteArrayOutputStream out) throws IOException {
        checkScalars(value);
        out.write('"');
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c < 0x20) {
                if (i > start) bytes(out, value.substring(start, i));
                switch (c) {
                    case '"': bytes(out, "\\\""); break;
                    case '\\': bytes(out, "\\\\"); break;
                    case '\b': bytes(out, "\\b"); break;
                    case '\f': bytes(out, "\\f"); break;
                    case '\n': bytes(out, "\\n"); break;
                    case '\r': bytes(out, "\\r"); break;
                    case '\t': bytes(out, "\\t"); break;
                    default: bytes(out, "\\u00" + Character.forDigit(c >>> 4, 16) + Character.forDigit(c & 15, 16));
                }
                start = i + 1;
            }
        }
        if (start < value.length()) bytes(out, value.substring(start));
        out.write('"');
    }

    private static void bytes(ByteArrayOutputStream out, String text) throws IOException {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        require(raw.length <= MAX_FILE_BYTES - 12 - out.size(), "JSON byte limit");
        out.write(raw);
    }

    private static final class JsonReader {
        private final String source;
        private int position;

        JsonReader(String source) { this.source = source; }

        Object parse() throws IOException {
            Object result = value(0);
            whitespace();
            require(position == source.length(), "trailing JSON data");
            return result;
        }

        private Object value(int depth) throws IOException {
            require(depth <= 64, "JSON nesting limit");
            whitespace();
            require(position < source.length(), "truncated JSON");
            char c = source.charAt(position);
            if (c == '{') return object(depth);
            if (c == '[') return array(depth);
            if (c == '"') return string();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            require(c == '-' || (c >= '0' && c <= '9'), "JSON value");
            return integer();
        }

        private Map<String, Object> object(int depth) throws IOException {
            position++;
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            whitespace();
            if (take('}')) return object;
            while (true) {
                whitespace();
                require(position < source.length() && source.charAt(position) == '"', "JSON object key");
                String key = string();
                require(!object.containsKey(key), "duplicate JSON key");
                whitespace(); expect(':');
                object.put(key, value(depth + 1));
                whitespace();
                if (take('}')) return object;
                expect(',');
            }
        }

        private List<Object> array(int depth) throws IOException {
            position++;
            List<Object> array = new ArrayList<Object>();
            whitespace();
            if (take(']')) return array;
            while (true) {
                array.add(value(depth + 1));
                whitespace();
                if (take(']')) return array;
                expect(',');
            }
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') {
                    String text = out.toString();
                    checkScalars(text);
                    return text;
                }
                require(c >= 0x20, "unescaped JSON control");
                if (c == '\\') {
                    require(position < source.length(), "truncated JSON escape");
                    c = source.charAt(position++);
                    switch (c) {
                        case '"': case '\\': case '/': out.append(c); break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            require(position <= source.length() - 4, "truncated Unicode escape");
                            int cp = 0;
                            for (int i = 0; i < 4; i++) {
                                char digit = source.charAt(position++);
                                int hex = digit >= '0' && digit <= '9' ? digit - '0'
                                        : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10
                                        : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10 : -1;
                                require(hex >= 0, "Unicode escape"); cp = (cp << 4) | hex;
                            }
                            out.append((char) cp); break;
                        default: throw error("JSON escape");
                    }
                } else out.append(c);
                // Native data is the only large string; bound it even before schema validation.
                require(out.length() <= Math.max(MAX_BASE64_CHARS, MAX_PHASE_LABEL_BYTES), "JSON string bound");
            }
            throw error("unterminated JSON string");
        }

        private Long integer() throws IOException {
            int start = position;
            take('-');
            require(position < source.length(), "JSON integer");
            if (take('0')) {
                require(position == source.length() || source.charAt(position) < '0' || source.charAt(position) > '9',
                        "leading zero");
            } else {
                require(source.charAt(position) >= '1' && source.charAt(position) <= '9', "JSON integer");
                while (position < source.length() && source.charAt(position) >= '0' && source.charAt(position) <= '9') position++;
            }
            require(position == source.length() || (source.charAt(position) != '.'
                    && source.charAt(position) != 'e' && source.charAt(position) != 'E'), "noninteger JSON number");
            require(position - start <= 17, "JSON integer range");
            try {
                long number = Long.parseLong(source.substring(start, position));
                require(number >= -MAX_INTEGER && number <= MAX_INTEGER, "JSON integer range");
                return number;
            } catch (NumberFormatException ex) {
                throw error("JSON integer", ex);
            }
        }

        private void literal(String text) throws IOException {
            require(source.startsWith(text, position), "JSON literal");
            position += text.length();
        }

        private void whitespace() {
            while (position < source.length()) {
                char c = source.charAt(position);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                position++;
            }
        }

        private boolean take(char c) {
            if (position < source.length() && source.charAt(position) == c) { position++; return true; }
            return false;
        }

        private void expect(char c) throws IOException { require(take(c), "expected " + c); }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw error(message);
    }

    private static IOException error(String message) { return new IOException("Invalid HRPL frame: " + message); }
    private static IOException error(String message, Exception cause) { return new IOException("Invalid HRPL frame: " + message, cause); }
}
