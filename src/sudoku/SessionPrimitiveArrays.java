/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.beans.Encoder;
import java.beans.Expression;
import java.beans.PersistenceDelegate;
import java.beans.XMLEncoder;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

/**
 * Compact primitive arrays for private work-session XML only. Public factories
 * are called by XMLDecoder; ordinary puzzle XML and replay formats are untouched.
 */
public final class SessionPrimitiveArrays {
    private static final int MAGIC = 0x48534131; // HSA1
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 10;
    // Bound one new compact payload before Base64 decoding or element allocation.
    // Larger existing arrays retain their legacy XML representation when writing.
    static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    static final int MAX_ENCODED_CHARS = ((MAX_PAYLOAD_BYTES + 2) / 3) * 4;
    private static final Class<?>[] TYPES = {byte[].class, short[].class, int[].class, long[].class,
            float[].class, double[].class, boolean[].class};
    private static final String[] FACTORIES = {"bytes", "shorts", "ints", "longs", "floats", "doubles", "booleans"};
    private static final int[] WIDTHS = {1, 2, 4, 8, 4, 8, 1};

    private SessionPrimitiveArrays() {}

    static void configure(XMLEncoder encoder) {
        for (int index = 0; index < TYPES.length; index++) {
            Class<?> type = TYPES[index];
            encoder.setPersistenceDelegate(type, delegate(type, encoder.getPersistenceDelegate(type), MAX_PAYLOAD_BYTES));
        }
    }

    /** Package-visible small bound permits testing the legacy fallback without a huge allocation. */
    static PersistenceDelegate delegate(final Class<?> type, final PersistenceDelegate legacy, final int payloadLimit) {
        final int tag = tag(type);
        return new PersistenceDelegate() {
            @Override public void writeObject(Object source, Encoder out) {
                if ((long) HEADER_BYTES + (long) Array.getLength(source) * WIDTHS[tag - 1] > payloadLimit)
                    legacy.writeObject(source, out);
                else super.writeObject(source, out);
            }
            @Override protected Expression instantiate(Object source, Encoder out) {
                return new Expression(source, SessionPrimitiveArrays.class, FACTORIES[tag - 1], new Object[]{encode(source)});
            }
            @Override protected boolean mutatesTo(Object source, Object target) {
                // Bean constructors frequently already own same-length default arrays.
                // Class/length alone would incorrectly keep those defaults while our
                // empty initialize method omitted all changed elements.
                return sameBits(source, target);
            }
            @Override protected void initialize(Class<?> sourceType, Object source, Object target, Encoder out) {}
        };
    }

    static String encode(Object array) {
        int tag = tag(array.getClass()), count = Array.getLength(array);
        long size = HEADER_BYTES + (long) count * WIDTHS[tag - 1];
        if (size > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Compact session array exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        ByteBuffer bytes = ByteBuffer.allocate((int) size).order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(MAGIC).put((byte) VERSION).put((byte) tag).putInt(count);
        switch (tag) {
            case 1: bytes.put((byte[]) array); break;
            case 2: for (short value : (short[]) array) bytes.putShort(value); break;
            case 3: for (int value : (int[]) array) bytes.putInt(value); break;
            case 4: for (long value : (long[]) array) bytes.putLong(value); break;
            case 5: for (float value : (float[]) array) bytes.putInt(Float.floatToRawIntBits(value)); break;
            case 6: for (double value : (double[]) array) bytes.putLong(Double.doubleToRawLongBits(value)); break;
            case 7: for (boolean value : (boolean[]) array) bytes.put((byte) (value ? 1 : 0)); break;
            default: throw new AssertionError(tag);
        }
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    public static byte[] bytes(String encoded) { return (byte[]) decode(encoded, 1); }
    public static short[] shorts(String encoded) { return (short[]) decode(encoded, 2); }
    public static int[] ints(String encoded) { return (int[]) decode(encoded, 3); }
    public static long[] longs(String encoded) { return (long[]) decode(encoded, 4); }
    public static float[] floats(String encoded) { return (float[]) decode(encoded, 5); }
    public static double[] doubles(String encoded) { return (double[]) decode(encoded, 6); }
    public static boolean[] booleans(String encoded) { return (boolean[]) decode(encoded, 7); }

    static void checkEncodedLength(int length) {
        if (length < 0 || length > MAX_ENCODED_CHARS)
            throw new IllegalArgumentException("Compact session array Base64 length exceeds " + MAX_ENCODED_CHARS + " characters");
    }

    private static Object decode(String encoded, int expectedTag) {
        if (encoded == null) throw new IllegalArgumentException("Missing compact session array");
        checkEncodedLength(encoded.length());
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length < HEADER_BYTES || raw.length > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("Invalid compact session array byte length: " + raw.length);
        ByteBuffer bytes = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        if (bytes.getInt() != MAGIC) throw new IllegalArgumentException("Invalid compact session array signature");
        int version = bytes.get() & 255;
        if (version != VERSION) throw new IllegalArgumentException("Unsupported compact session array version: " + version);
        int actualTag = bytes.get() & 255;
        if (actualTag != expectedTag) throw new IllegalArgumentException("Compact session array type mismatch: " + actualTag + "/" + expectedTag);
        int count = bytes.getInt();
        long required = (long) count * WIDTHS[expectedTag - 1];
        if (count < 0 || required != bytes.remaining())
            throw new IllegalArgumentException("Invalid compact session array element count/length: " + count + "/" + bytes.remaining());
        switch (expectedTag) {
            case 1: { byte[] result = new byte[count]; bytes.get(result); return result; }
            case 2: { short[] result = new short[count]; for (int i = 0; i < count; i++) result[i] = bytes.getShort(); return result; }
            case 3: { int[] result = new int[count]; for (int i = 0; i < count; i++) result[i] = bytes.getInt(); return result; }
            case 4: { long[] result = new long[count]; for (int i = 0; i < count; i++) result[i] = bytes.getLong(); return result; }
            case 5: { float[] result = new float[count]; for (int i = 0; i < count; i++) result[i] = Float.intBitsToFloat(bytes.getInt()); return result; }
            case 6: { double[] result = new double[count]; for (int i = 0; i < count; i++) result[i] = Double.longBitsToDouble(bytes.getLong()); return result; }
            case 7: {
                boolean[] result = new boolean[count];
                for (int i = 0; i < count; i++) {
                    int value = bytes.get() & 255;
                    if (value > 1) throw new IllegalArgumentException("Invalid compact session boolean at index " + i + ": " + value);
                    result[i] = value == 1;
                }
                return result;
            }
            default: throw new AssertionError(expectedTag);
        }
    }

    static boolean sameBits(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null || first.getClass() != second.getClass()) return false;
        if (first instanceof byte[]) return Arrays.equals((byte[]) first, (byte[]) second);
        if (first instanceof short[]) return Arrays.equals((short[]) first, (short[]) second);
        if (first instanceof int[]) return Arrays.equals((int[]) first, (int[]) second);
        if (first instanceof long[]) return Arrays.equals((long[]) first, (long[]) second);
        if (first instanceof boolean[]) return Arrays.equals((boolean[]) first, (boolean[]) second);
        if (first instanceof float[]) {
            float[] left = (float[]) first, right = (float[]) second; if (left.length != right.length) return false;
            for (int i = 0; i < left.length; i++) if (Float.floatToRawIntBits(left[i]) != Float.floatToRawIntBits(right[i])) return false;
            return true;
        }
        if (first instanceof double[]) {
            double[] left = (double[]) first, right = (double[]) second; if (left.length != right.length) return false;
            for (int i = 0; i < left.length; i++) if (Double.doubleToRawLongBits(left[i]) != Double.doubleToRawLongBits(right[i])) return false;
            return true;
        }
        return false;
    }

    private static int tag(Class<?> type) {
        for (int i = 0; i < TYPES.length; i++) if (type == TYPES[i]) return i + 1;
        throw new IllegalArgumentException("Unsupported compact session array type: " + type.getName());
    }
}
