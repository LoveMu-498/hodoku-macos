/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copies the complete session data graph before a worker can see it. The only
 * shared mutable-looking objects are DoodleStroke's immutable coordinate pages.
 * Unknown data types fail closed instead of silently escaping to the worker.
 */
final class SessionSnapshots {
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<Class<?>, List<Field>>();
    private SessionSnapshots() {}

    /** Must be called while the source graph is stable (normally on the EDT). */
    static SessionSnapshot detached(SessionSnapshot source) {
        return (SessionSnapshot) copy(source, new IdentityHashMap<Object, Object>());
    }

    /** Cheap root copy so the wire version never mutates the caller's snapshot. */
    static SessionSnapshot withWireVersion(SessionSnapshot source, int version) {
        SessionSnapshot result = new SessionSnapshot();
        try {
            for (Field field : fields(SessionSnapshot.class)) field.set(result, field.get(source));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Unable to copy session header", failure);
        }
        result.setFormatVersion(version);
        return result;
    }

    /** Exact fallback comparison: O(changed graph size), O(1) for shared coordinates. */
    static boolean sameContent(SessionSnapshot first, SessionSnapshot second) {
        return same(first, second, new IdentityHashMap<Object, IdentityHashMap<Object, Boolean>>());
    }

    /**
     * The independent replay checkpoint owns a ticking elapsed clock. Reusing a
     * session may ignore that clock only; replay ID/frame count and all actual
     * working state still have to match. Quit must still flush the replay clock.
     */
    static boolean sameWorkingContent(SessionSnapshot first, SessionSnapshot second) {
        if (first == null || second == null) return first == second;
        SessionSnapshot normalized = withWireVersion(second, second.getFormatVersion());
        normalized.setReplayElapsedMillis(first.getReplayElapsedMillis());
        return sameContent(first, normalized);
    }

    private static boolean immutable(Object value) {
        return value instanceof String || value instanceof Enum<?> || value instanceof Color
                || value instanceof Boolean || value instanceof Character || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double;
    }

    private static Object copy(Object source, IdentityHashMap<Object, Object> seen) {
        if (source == null || immutable(source)) return source;
        Object existing = seen.get(source);
        if (existing != null) return existing;
        if (source instanceof DoodleStroke) {
            DoodleStroke result = ((DoodleStroke) source).copy();
            seen.put(source, result);
            return result;
        }
        if (source instanceof Date) return new Date(((Date) source).getTime());
        Class<?> type = source.getClass();
        if (type.isArray()) {
            int count = Array.getLength(source);
            Object result = Array.newInstance(type.getComponentType(), count);
            seen.put(source, result);
            if (type.getComponentType().isPrimitive()) System.arraycopy(source, 0, result, 0, count);
            else for (int index = 0; index < count; index++) Array.set(result, index, copy(Array.get(source, index), seen));
            return result;
        }
        if (source instanceof Map<?, ?>) {
            Map<Object, Object> result;
            if (source instanceof SortedMap<?, ?>) {
                if (((SortedMap<?, ?>) source).comparator() != null)
                    throw new IllegalArgumentException("Unsupported custom session map comparator");
                result = new TreeMap<Object, Object>();
            } else result = new LinkedHashMap<Object, Object>();
            seen.put(source, result);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet())
                result.put(copy(entry.getKey(), seen), copy(entry.getValue(), seen));
            return result;
        }
        if (source instanceof Collection<?>) {
            Collection<Object> result;
            if (source instanceof Stack<?>) result = new Stack<Object>();
            else if (source instanceof List<?>) result = new ArrayList<Object>(((List<?>) source).size());
            else if (source instanceof SortedSet<?>) {
                if (((SortedSet<?>) source).comparator() != null)
                    throw new IllegalArgumentException("Unsupported custom session set comparator");
                result = new TreeSet<Object>();
            } else if (source instanceof Set<?>) result = new LinkedHashSet<Object>();
            else throw new IllegalArgumentException("Unsupported session collection " + type.getName());
            seen.put(source, result);
            for (Object value : (Collection<?>) source) result.add(copy(value, seen));
            return result;
        }
        try {
            List<Field> properties = fields(type);
            Object result = type.getDeclaredConstructor().newInstance();
            seen.put(source, result);
            for (Field property : properties) property.set(result, copy(property.get(source), seen));
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to detach session data " + type.getName(), failure);
        }
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> cached = FIELDS.get(type);
        if (cached != null) return cached;
        if (!type.getName().startsWith("sudoku.") && !type.getName().equals("solver.RestrictedCommon"))
            throw new IllegalArgumentException("Unsupported session data " + type.getName());
        List<Field> result = new ArrayList<Field>();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (current == GuiState.class && (field.getName().equals("sudokuPanel")
                        || field.getName().equals("sudokuSolver") || field.getName().equals("solutionPanel"))) continue;
                field.setAccessible(true);
                result.add(field);
            }
        }
        FIELDS.put(type, result);
        return result;
    }

    private static boolean same(Object first, Object second,
            IdentityHashMap<Object, IdentityHashMap<Object, Boolean>> seen) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof Double && second instanceof Double)
            return Double.doubleToRawLongBits((Double) first) == Double.doubleToRawLongBits((Double) second);
        if (first instanceof Float && second instanceof Float)
            return Float.floatToRawIntBits((Float) first) == Float.floatToRawIntBits((Float) second);
        if (immutable(first) || first instanceof Date) return first.equals(second);
        // Native set identity is the two membership masks (SudokuSetBase.equals).
        // XMLEncoder calls getValues(), which lazily populates values/anz/initialized
        // on an otherwise unchanged saved snapshot. Comparing that iteration cache
        // would make every subsequent idle/quit capture appear dirty. Limit this
        // rule to the known data types so future subclasses keep strict fields.
        // The final short candidate set has the same native mask/cache contract.
        if ((first.getClass() == SudokuSet.class || first.getClass() == SudokuSetBase.class
                || first.getClass() == SudokuSetShort.class)
                && first.getClass() == second.getClass()) return first.equals(second);
        IdentityHashMap<Object, Boolean> matches = seen.get(first);
        if (matches != null && matches.containsKey(second)) return true;
        if (matches == null) { matches = new IdentityHashMap<Object, Boolean>(); seen.put(first, matches); }
        matches.put(second, Boolean.TRUE);
        if (first instanceof DoodleStroke && second instanceof DoodleStroke) {
            DoodleStroke left = (DoodleStroke) first, right = (DoodleStroke) second;
            return SessionInkCodec.metadata(left).equals(SessionInkCodec.metadata(right))
                    && same(left.coordinateSnapshot(), right.coordinateSnapshot(), seen);
        }
        if (first instanceof DoodleCoordinates && second instanceof DoodleCoordinates) {
            DoodleCoordinates left = (DoodleCoordinates) first, right = (DoodleCoordinates) second;
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) {
                if (Double.doubleToRawLongBits(left.x(index)) != Double.doubleToRawLongBits(right.x(index))
                        || Double.doubleToRawLongBits(left.y(index)) != Double.doubleToRawLongBits(right.y(index))) return false;
            }
            return true;
        }
        if (first.getClass().isArray()) {
            if (first.getClass() != second.getClass() || Array.getLength(first) != Array.getLength(second)) return false;
            for (int index = 0; index < Array.getLength(first); index++)
                if (!same(Array.get(first, index), Array.get(second, index), seen)) return false;
            return true;
        }
        if (first instanceof List<?> && second instanceof List<?>) {
            List<?> left = (List<?>) first, right = (List<?>) second;
            if (left.size() != right.size()) return false;
            for (int index = 0; index < left.size(); index++) if (!same(left.get(index), right.get(index), seen)) return false;
            return true;
        }
        if (first instanceof Map<?, ?> && second instanceof Map<?, ?>) {
            Map<?, ?> left = (Map<?, ?>) first, right = (Map<?, ?>) second;
            if (!left.keySet().equals(right.keySet())) return false;
            for (Object key : left.keySet()) if (!same(left.get(key), right.get(key), seen)) return false;
            return true;
        }
        if (first.getClass() != second.getClass()) return false;
        try {
            for (Field field : fields(first.getClass())) {
                // The format tag is a storage concern; v1 restored data can be compared with new captures.
                if (field.getDeclaringClass() == SessionSnapshot.class && field.getName().equals("formatVersion")) continue;
                if (!same(field.get(first), field.get(second), seen)) return false;
            }
            return true;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Unable to compare session data", failure);
        }
    }
}
