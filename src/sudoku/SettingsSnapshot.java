/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import generator.GeneratorPattern;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Detached settings graph, copied without invoking setters or XML encoding. */
final class SettingsSnapshot {
    private static final Map<Class<?>, Field[]> FIELDS;
    static {
        Map<Class<?>, Field[]> fields = new HashMap<Class<?>, Field[]>();
        for (Class<?> type : Arrays.<Class<?>>asList(Options.class, DifficultyLevel.class,
                StepConfig.class, GeneratorPattern.class, AnnotationPalettePreferences.class,
                AnnotationPaletteSelection.class)) {
            List<Field> entries = new ArrayList<Field>();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                // These public arrays are derived solver views, not XML bean properties.
                if (type == Options.class && (field.getName().equals("solverSteps")
                        || field.getName().equals("solverStepsProgress"))) continue;
                field.setAccessible(true);
                entries.add(field);
            }
            fields.put(type, entries.toArray(new Field[entries.size()]));
        }
        FIELDS = Collections.unmodifiableMap(fields);
    }

    final Options options;
    SettingsSnapshot(Options source) { options = (Options) copy(source); }
    boolean sameContent(SettingsSnapshot other) { return other != null && same(options, other.options); }

    private static boolean immutable(Object value) {
        return value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>
                || value instanceof Color || value instanceof Font;
    }

    private static Object copy(Object value) {
        if (immutable(value)) return value;
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object result = Array.newInstance(type.getComponentType(), length);
            if (type.getComponentType().isPrimitive()) System.arraycopy(value, 0, result, 0, length);
            else for (int i = 0; i < length; i++) Array.set(result, i, copy(Array.get(value, i)));
            return result;
        }
        if (value instanceof List<?>) {
            List<Object> result = new ArrayList<Object>(((List<?>) value).size());
            for (Object element : (List<?>) value) result.add(copy(element));
            return result;
        }
        Field[] fields = fields(type);
        try {
            Object result = type.getDeclaredConstructor().newInstance();
            for (Field field : fields) field.set(result, copy(field.get(value)));
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to capture settings type " + type.getName(), failure);
        }
    }

    private static Field[] fields(Class<?> type) {
        Field[] fields = FIELDS.get(type);
        if (fields == null) throw new IllegalStateException("Unsupported mutable settings type " + type.getName());
        return fields;
    }

    private static boolean same(Object first, Object second) {
        if (first == second) return true;
        if (first == null || second == null || first.getClass() != second.getClass()) return false;
        if (immutable(first)) return first.equals(second);
        if (first.getClass().isArray()) {
            int length = Array.getLength(first);
            if (length != Array.getLength(second)) return false;
            for (int i = 0; i < length; i++) if (!same(Array.get(first, i), Array.get(second, i))) return false;
            return true;
        }
        if (first instanceof List<?>) {
            List<?> a = (List<?>) first, b = (List<?>) second;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!same(a.get(i), b.get(i))) return false;
            return true;
        }
        try {
            for (Field field : fields(first.getClass())) if (!same(field.get(first), field.get(second))) return false;
            return true;
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
}
