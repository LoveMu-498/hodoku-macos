/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.beans.PersistenceDelegate;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Exact primitive-array factories, real bean defaults, malformed lengths and old session readers. */
public final class SessionPrimitiveArraysProbe {
    private static final Class<?>[] TYPES = {byte[].class, short[].class, int[].class, long[].class,
            float[].class, double[].class, boolean[].class};
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    /** Existing default arrays are the important Encoder integration case, not just root arrays. */
    public static final class Defaults {
        private int[] integers = {7, 8, 9};
        private int[] alias;
        private double[] decimals = {0.0, 1.0};
        private short[][] nested = {{1, 2}, {3, 4}};
        public Defaults() {}
        public int[] getIntegers() { return integers; }
        public void setIntegers(int[] value) { integers = value; }
        public int[] getAlias() { return alias; }
        public void setAlias(int[] value) { alias = value; }
        public double[] getDecimals() { return decimals; }
        public void setDecimals(double[] value) { decimals = value; }
        public short[][] getNested() { return nested; }
        public void setNested(short[][] value) { nested = value; }
    }

    public static void main(String[] args) throws Exception {
        List<Object> cases = new ArrayList<Object>();
        cases.add(new byte[]{Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE});
        cases.add(new short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE});
        cases.add(new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE});
        cases.add(new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE});
        cases.add(new float[]{-0.0f, 0.0f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.intBitsToFloat(0x7fc00042)});
        cases.add(new double[]{-0.0, 0.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.longBitsToDouble(0x7ff8000000000042L)});
        cases.add(new boolean[]{true, false, true});
        for (Class<?> type : TYPES) cases.add(java.lang.reflect.Array.newInstance(type.getComponentType(), 0));
        for (Object value : cases) {
            byte[] encoded = xml(value, true, false);
            require(SessionPrimitiveArrays.sameBits(value, read(encoded)), "raw primitive bits changed for " + value.getClass());
            require(new String(encoded, StandardCharsets.UTF_8).contains("SessionPrimitiveArrays"), "root array used per-value XML");
        }
        Defaults defaults = new Defaults(); defaults.getIntegers()[1] = 80; defaults.setAlias(defaults.getIntegers());
        defaults.setDecimals(new double[]{-0.0, Double.longBitsToDouble(0x7ff8000000000042L)});
        defaults.getNested()[1][0] = 31;
        Defaults copied = (Defaults) read(xml(defaults, true, false));
        require(SessionPrimitiveArrays.sameBits(defaults.getIntegers(), copied.getIntegers()), "same-length constructor defaults swallowed integer edits");
        require(copied.getAlias() == copied.getIntegers(), "array alias identity changed");
        require(SessionPrimitiveArrays.sameBits(defaults.getDecimals(), copied.getDecimals()), "same-length constructor defaults swallowed signed-zero/NaN bits");
        require(Arrays.deepEquals(defaults.getNested(), copied.getNested()), "nested primitive-array values changed");
        defaults.setIntegers(null); defaults.setAlias(null);
        require(((Defaults)read(xml(defaults, true, false))).getIntegers() == null, "null array replaced by constructor defaults");

        byte[] legacy = xml(new int[]{1, 2, 3}, false, false);
        require(!new String(legacy, StandardCharsets.UTF_8).contains("SessionPrimitiveArrays"), "ordinary XML gained session factories");
        require(Arrays.equals((int[])read(legacy), new int[]{1, 2, 3}), "ordinary XML changed");

        // Exercise the exact oversized fallback path at a small injected bound.
        int[] large = new int[30]; large[3] = 65537; ByteArrayOutputStream fallbackBytes = new ByteArrayOutputStream();
        try (XMLEncoder encoder = new XMLEncoder(fallbackBytes)) {
            encoder.setPersistenceDelegate(int[].class, SessionPrimitiveArrays.delegate(int[].class, encoder.getPersistenceDelegate(int[].class), 64));
            encoder.setExceptionListener(error -> { throw new AssertionError(error); }); encoder.writeObject(large);
        }
        require(!new String(fallbackBytes.toByteArray(), StandardCharsets.UTF_8).contains("SessionPrimitiveArrays"), "oversized input did not retain legacy XML");
        require(Arrays.equals(large, (int[])read(fallbackBytes.toByteArray())), "legacy fallback lost array values");

        malformedPayloads();
        Path directory = Files.createTempDirectory("hodoku-session-arrays-");
        SessionSnapshot snapshot = fixture(); SessionStore current = new SessionStore(directory.resolve("new.xml").toFile());
        current.save(snapshot); assertBoard(snapshot, current.load());
        String written = new String(Files.readAllBytes(directory.resolve("new.xml")), StandardCharsets.UTF_8);
        require(written.contains("SessionPrimitiveArrays"), "real board still used only generic primitive XML");
        Path v1 = directory.resolve("v1.xml"), existingV2 = directory.resolve("existing-v2.xml");
        Files.write(v1, xml(snapshot, false, false));
        Files.write(existingV2, xml(SessionSnapshots.withWireVersion(snapshot, 2), true, true));
        require(!new String(Files.readAllBytes(existingV2), StandardCharsets.UTF_8).contains("SessionPrimitiveArrays"), "old v2 fixture inadvertently used new factories");
        assertBoard(snapshot, new SessionStore(v1.toFile()).load());
        assertBoard(snapshot, new SessionStore(existingV2.toFile()).load());
        System.out.println("PASS session primitive arrays: seven types, all raw bits, bean defaults, nested arrays/aliases/null, bounded malformed payloads, oversized XML fallback, v1 and previous v2 readback");
    }

    private static byte[] xml(Object value, boolean session, boolean restoreOldArrayDelegates) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); List<Exception> failures = new ArrayList<Exception>();
        try (XMLEncoder encoder = new XMLEncoder(bytes)) {
            PersistenceDelegate[] prior = new PersistenceDelegate[TYPES.length];
            for (int i = 0; i < TYPES.length; i++) prior[i] = encoder.getPersistenceDelegate(TYPES[i]);
            if (session) AnnotationXml.configureSession(encoder); else AnnotationXml.configure(encoder);
            if (restoreOldArrayDelegates) for (int i = 0; i < TYPES.length; i++) encoder.setPersistenceDelegate(TYPES[i], prior[i]);
            encoder.setExceptionListener(failures::add); encoder.writeObject(value);
        }
        require(failures.isEmpty(), "XML encoding failed: " + failures); return bytes.toByteArray();
    }
    private static Object read(byte[] bytes) {
        List<Exception> failures = new ArrayList<Exception>(); Object result;
        try (XMLDecoder decoder = new XMLDecoder(new ByteArrayInputStream(bytes), null, failures::add)) { result = decoder.readObject(); }
        require(failures.isEmpty(), "XML decode failed: " + failures); return result;
    }
    private static void malformedPayloads() {
        String valid = SessionPrimitiveArrays.encode(new int[]{7}); byte[] raw = Base64.getDecoder().decode(valid);
        reject(() -> SessionPrimitiveArrays.ints("not base64"));
        reject(() -> SessionPrimitiveArrays.shorts(valid));
        reject(() -> SessionPrimitiveArrays.ints(null));
        byte[] wrongVersion = raw.clone(); wrongVersion[4] = 2; reject(() -> SessionPrimitiveArrays.ints(b64(wrongVersion)));
        byte[] negative = raw.clone(); ByteBuffer.wrap(negative).order(ByteOrder.BIG_ENDIAN).putInt(6, -1); reject(() -> SessionPrimitiveArrays.ints(b64(negative)));
        byte[] enormous = raw.clone(); ByteBuffer.wrap(enormous).order(ByteOrder.BIG_ENDIAN).putInt(6, Integer.MAX_VALUE); reject(() -> SessionPrimitiveArrays.ints(b64(enormous)));
        reject(() -> SessionPrimitiveArrays.ints(b64(Arrays.copyOf(raw, raw.length - 1))));
        reject(() -> SessionPrimitiveArrays.ints(b64(Arrays.copyOf(raw, raw.length + 1))));
        reject(() -> SessionPrimitiveArrays.checkEncodedLength(SessionPrimitiveArrays.MAX_ENCODED_CHARS + 1));
        byte[] booleanPayload = Base64.getDecoder().decode(SessionPrimitiveArrays.encode(new boolean[]{true})); booleanPayload[10] = 2;
        reject(() -> SessionPrimitiveArrays.booleans(b64(booleanPayload)));
    }
    private static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static void reject(Runnable action) { boolean rejected = false; try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; } require(rejected, "malformed compact array was accepted"); }
    private static SessionSnapshot fixture() {
        Sudoku2 board = new Sudoku2(); board.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079"); board.setCell(2, 4);
        GuiState state = new GuiState(); state.setSudoku(board); state.setAnzSteps(new int[]{0, 1, 200, 0});
        SolutionStep step = new SolutionStep(SolutionType.NAKED_SINGLE); step.getChains().add(new Chain(0, 2, new int[]{12, 34, 56})); state.setStep(step);
        SessionSnapshot result = new SessionSnapshot(); result.setGuiState(state); return result;
    }
    private static void assertBoard(SessionSnapshot expected, SessionSnapshot actual) {
        require(expected.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY).equals(actual.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY)), "board/candidates changed");
        require(Arrays.equals(expected.getGuiState().getAnzSteps(), actual.getGuiState().getAnzSteps()), "summary array changed");
        require(Arrays.equals(expected.getGuiState().getStep().getChains().get(0).getChain(), actual.getGuiState().getStep().getChains().get(0).getChain()), "step chain array changed");
    }
}
