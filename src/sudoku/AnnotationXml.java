/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.beans.*;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Uses existing public constructors, keeping ordinary JavaBeans XML and all annotation metadata. */
final class AnnotationXml {
    private AnnotationXml(){}
    static void configure(XMLEncoder encoder){
        // Ordinary puzzle XML continues to use only the old ArrayList/point vocabulary.
        encoder.setPersistenceDelegate(DoodlePoints.class, new PersistenceDelegate() {
            @Override protected Expression instantiate(Object source, Encoder out) {
                return new Expression(source, ArrayList.class, "new", new Object[0]);
            }
            @Override protected boolean mutatesTo(Object source, Object target) { return target instanceof List<?>; }
            @Override protected void initialize(Class<?> type, Object source, Object target, Encoder out) {
                DoodlePoints points = (DoodlePoints) source;
                for (int index = ((List<?>) target).size(); index < points.size(); index++)
                    out.writeStatement(new Statement(source, "add", new Object[]{points.get(index)}));
            }
        });
        encoder.setPersistenceDelegate(DoodlePoint.class,new PersistenceDelegate(){
            @Override protected Expression instantiate(Object source,Encoder out){
                DoodlePoint point=(DoodlePoint)source;
                return new Expression(source,DoodlePoint.class,"new",new Object[]{point.getX(),point.getY()});
            }
            // The constructor initializes the entire point; no reflective setter replay is needed.
            @Override protected void initialize(Class<?> type,Object source,Object target,Encoder out){}
        });
        encoder.setPersistenceDelegate(DoodleStroke.class,new DefaultPersistenceDelegate(new String[]{"color","widthFactor"}));
    }

    /** Session v2: encode each distinct stroke once, with no per-coordinate bean graph. */
    static void configureSession(XMLEncoder encoder) {
        configure(encoder);
        SessionPrimitiveArrays.configure(encoder);
        final Map<DoodleCoordinates, DoodleCoordinates> identities = new IdentityHashMap<DoodleCoordinates, DoodleCoordinates>();
        final Map<CoordinateKey, DoodleCoordinates> geometries = new HashMap<CoordinateKey, DoodleCoordinates>();
        final Map<DoodleCoordinates, Map<String, DoodleStroke>> strokes = new IdentityHashMap<DoodleCoordinates, Map<String, DoodleStroke>>();
        encoder.setPersistenceDelegate(DoodleCoordinates.class, new PersistenceDelegate() {
            @Override protected Expression instantiate(Object source, Encoder out) {
                return new Expression(source, SessionInkCodec.class, "coordinates",
                        new Object[]{SessionInkCodec.encodeCoordinates((DoodleCoordinates) source)});
            }
            @Override protected void initialize(Class<?> type, Object source, Object target, Encoder out) {}
        });
        encoder.setPersistenceDelegate(DoodleStroke.class, new PersistenceDelegate() {
            @Override protected Expression instantiate(Object source, Encoder out) {
                DoodleStroke stroke = (DoodleStroke) source;
                DoodleCoordinates own = stroke.coordinateSnapshot();
                DoodleCoordinates canonical = identities.get(own);
                if (canonical == null) {
                    CoordinateKey key = new CoordinateKey(own);
                    canonical = geometries.get(key);
                    if (canonical == null) { canonical = own; geometries.put(key, own); }
                    identities.put(own, canonical);
                }
                Map<String, DoodleStroke> dictionary = strokes.get(canonical);
                if (dictionary == null) { dictionary = new HashMap<String, DoodleStroke>(); strokes.put(canonical, dictionary); }
                String encoded = SessionInkCodec.metadata(stroke);
                DoodleStroke original = dictionary.get(encoded);
                if (original != null && original != source)
                    return new Expression(source, original, "copy", new Object[0]);
                dictionary.put(encoded, stroke);
                return new Expression(source, SessionInkCodec.class, "decode", new Object[]{encoded, canonical});
            }
            @Override protected void initialize(Class<?> type, Object source, Object target, Encoder out) {}
        });
    }

    /** Exact equality after a linear hash; hash collisions never merge different coordinates. */
    private static final class CoordinateKey {
        final DoodleCoordinates coordinates;
        final int hash;
        CoordinateKey(DoodleCoordinates value) {
            coordinates = value;
            int result = value.size();
            for (int index = 0; index < value.size(); index++) {
                long x = Double.doubleToRawLongBits(value.x(index)), y = Double.doubleToRawLongBits(value.y(index));
                result = 31 * result + (int) (x ^ (x >>> 32));
                result = 31 * result + (int) (y ^ (y >>> 32));
            }
            hash = result;
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof CoordinateKey)) return false;
            DoodleCoordinates right = ((CoordinateKey) other).coordinates;
            if (coordinates == right) return true;
            if (coordinates.size() != right.size()) return false;
            for (int index = 0; index < right.size(); index++) {
                if (Double.doubleToRawLongBits(coordinates.x(index)) != Double.doubleToRawLongBits(right.x(index))
                        || Double.doubleToRawLongBits(coordinates.y(index)) != Double.doubleToRawLongBits(right.y(index))) return false;
            }
            return true;
        }
    }
}
