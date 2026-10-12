/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Cuts projected stroke paths; partially erased candidate circles become ordinary normalized ink. */
final class DoodleGeometry {
    private DoodleGeometry() {}

    /** Both sides of the same cut, so the ghost predicts the release result. */
    static final class ErasurePreview {
        final List<DoodleStroke> retained;
        final List<DoodleStroke> removed;
        ErasurePreview(List<DoodleStroke> retained, List<DoodleStroke> removed) {
            this.retained = retained; this.removed = removed;
        }
    }

    static ErasurePreview previewSubtract(List<DoodleStroke> strokes, Shape eraser, int width, int height,
            java.util.function.Function<DoodleStroke, java.awt.geom.AffineTransform> projection) {
        List<DoodleStroke> removed = new ArrayList<DoodleStroke>();
        return new ErasurePreview(subtract(strokes, eraser, width, height, projection, removed, null, false), removed);
    }

    static ErasurePreview previewSubtract(List<DoodleStroke> strokes, Shape eraser, int width, int height,
            java.util.function.Function<DoodleStroke, java.awt.geom.AffineTransform> projection,
            java.util.function.ToDoubleFunction<DoodleStroke> pixelWidth, boolean wholeStroke) {
        List<DoodleStroke> removed = new ArrayList<DoodleStroke>();
        return new ErasurePreview(subtract(strokes, eraser, width, height, projection, removed, pixelWidth, wholeStroke), removed);
    }

    static List<DoodleStroke> subtract(List<DoodleStroke> strokes, Shape eraser, int width, int height) {
        return subtract(strokes, eraser, width, height, stroke -> java.awt.geom.AffineTransform.getScaleInstance(width, height));
    }

    static List<DoodleStroke> subtract(List<DoodleStroke> strokes, Shape eraser, int width, int height,
            java.util.function.Function<DoodleStroke, java.awt.geom.AffineTransform> projection) {
        return subtract(strokes, eraser, width, height, projection, null, null, false);
    }

    private static List<DoodleStroke> subtract(List<DoodleStroke> strokes, Shape eraser, int width, int height,
            java.util.function.Function<DoodleStroke, java.awt.geom.AffineTransform> projection,
            List<DoodleStroke> removed, java.util.function.ToDoubleFunction<DoodleStroke> pixelWidth, boolean wholeStroke) {
        List<double[]> edges = edges(eraser);
        List<DoodleStroke> result = new ArrayList<DoodleStroke>();
        java.awt.geom.Rectangle2D eraserBounds = eraser.getBounds2D();
        java.util.Map<Float,Shape> expandedRegions = new java.util.HashMap<Float,Shape>();
        java.util.Map<Float,List<double[]>> expandedEdges = new java.util.HashMap<Float,List<double[]>>();
        for (DoodleStroke stroke : strokes) {
            // Invisible source snapshots are revealed read-only; board erasers
            // must not change evidence the user cannot see or edit.
            if (stroke.isProofOnly()) { result.add(stroke.copy()); continue; }
            java.awt.geom.AffineTransform transform = projection.apply(stroke);
            if (transform == null) { result.add(stroke.copy()); continue; }
            java.awt.geom.Rectangle2D bounds = projectedBounds(stroke, transform);
            float inkWidth = pixelWidth == null ? 0f : (float)pixelWidth.applyAsDouble(stroke);
            java.awt.geom.Rectangle2D visibleBounds = new java.awt.geom.Rectangle2D.Double(
                    bounds.getX()-inkWidth/2, bounds.getY()-inkWidth/2,
                    bounds.getWidth()+inkWidth, bounds.getHeight()+inkWidth);
            if (!visibleBounds.intersects(eraserBounds)) { result.add(stroke.copy()); continue; }
            Shape hitRegion = eraser;
            List<double[]> hitEdges = edges;
            if (inkWidth > 0 && !eraserBounds.isEmpty()) {
                hitRegion = expandedRegions.get(inkWidth);
                if (hitRegion == null) {
                    java.awt.geom.Area expanded = new java.awt.geom.Area(eraser);
                    expanded.add(new java.awt.geom.Area(new java.awt.BasicStroke(inkWidth,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND).createStrokedShape(eraser)));
                    hitRegion = expanded;
                    expandedRegions.put(inkWidth, hitRegion); expandedEdges.put(inkWidth, edges(hitRegion));
                }
                hitEdges = expandedEdges.get(inkWidth);
            }
            java.awt.geom.AffineTransform inverse;
            try { inverse = transform.createInverse(); }
            catch (java.awt.geom.NoninvertibleTransformException ex) { throw new IllegalArgumentException(ex); }
            int fragmentStart = result.size();
            int removedStart = removed == null ? 0 : removed.size();
            boolean cutStroke = false;
            DoodleStroke fragment = null;
            DoodleStroke erasedFragment = null;
            segments: for (int i = 1; i < stroke.pointCount(); i++) {
                // A standard cross stores two independent strokes in four points.
                if (stroke.getCandidateMarkKind() == DoodleStroke.MARK_FALSE_CROSS && i == 2) continue;
                Point2D pa = transform.transform(new Point2D.Double(stroke.pointX(i-1),stroke.pointY(i-1)),null);
                Point2D pb = transform.transform(new Point2D.Double(stroke.pointX(i),stroke.pointY(i)),null);
                double ax = pa.getX(), ay = pa.getY();
                double dx = pb.getX()-ax, dy = pb.getY()-ay;
                if (Math.hypot(dx, dy) < 1e-9) continue;
                List<Double> cuts = new ArrayList<Double>(); cuts.add(0.0); cuts.add(1.0);
                for (double[] edge : hitEdges) {
                    double ex = edge[2] - edge[0], ey = edge[3] - edge[1];
                    double denominator = dx * ey - dy * ex;
                    if (Math.abs(denominator) < 1e-10) continue;
                    double qx = edge[0] - ax, qy = edge[1] - ay;
                    double t = (qx * ey - qy * ex) / denominator;
                    double u = (qx * dy - qy * dx) / denominator;
                    if (t > 0 && t < 1 && u >= 0 && u <= 1) cuts.add(t);
                }
                Collections.sort(cuts);
                for (int j = 1; j < cuts.size(); j++) {
                    double from = cuts.get(j - 1), to = cuts.get(j);
                    if (to - from < 1e-10) continue;
                    double middle = (from + to) / 2;
                    if (hitRegion.contains(ax + dx * middle, ay + dy * middle)) {
                        cutStroke = true; fragment = null;
                        // Atomic deletion needs only the first exact hit, not the remaining intersections.
                        if (stroke.isStandardCandidateMark() || wholeStroke) break segments;
                        if (removed != null && !stroke.isStandardCandidateMark()) {
                            Point2D start = inverse.transform(new Point2D.Double(ax+dx*from, ay+dy*from),null);
                            if (erasedFragment != null) {
                                int last = erasedFragment.pointCount()-1;
                                if (Point2D.distance(erasedFragment.pointX(last),erasedFragment.pointY(last),start.getX(),start.getY()) > 1e-8)
                                    erasedFragment = null;
                            }
                            if (erasedFragment == null) {
                                erasedFragment = stroke.copy();
                                erasedFragment.getPoints().clear();
                                erasedFragment.addPoint(start.getX(),start.getY());
                                removed.add(erasedFragment);
                            }
                            Point2D end = inverse.transform(new Point2D.Double(ax+dx*to, ay+dy*to),null);
                            erasedFragment.addPoint(end.getX(),end.getY());
                        }
                        continue;
                    }
                    erasedFragment = null;
                    Point2D local = inverse.transform(new Point2D.Double(ax+dx*from, ay+dy*from),null);
                    double x = local.getX(), y = local.getY();
                    if (fragment != null) {
                        int last = fragment.pointCount() - 1;
                        if (Point2D.distance(fragment.pointX(last), fragment.pointY(last), x, y) > 1e-8) fragment = null;
                    }
                    if (fragment == null) {
                        fragment = new DoodleStroke(stroke.getColor(), stroke.getWidthFactor());
                        fragment.setThoughtGroup(stroke.getThoughtGroup());
                        fragment.setAnchorCell(stroke.getAnchorCell());
                        fragment.setAnchorDigit(stroke.getAnchorDigit());
                        fragment.addPoint(x, y);result.add(fragment);
                    }
                    Point2D end = inverse.transform(new Point2D.Double(ax+dx*to, ay+dy*to),null);
                    fragment.addPoint(end.getX(),end.getY());
                }
            }
            if (stroke.isStandardCandidateMark() || wholeStroke) {
                if (cutStroke) {
                    if (removed != null) removed.subList(removedStart, removed.size()).clear();
                    // A structured candidate mark is atomic; never turn a partial
                    // erasure into a freehand fragment that retains no meaning.
                    result.subList(fragmentStart,result.size()).clear();
                    if (removed != null) removed.add(stroke.copy());
                } else {
                    result.subList(fragmentStart,result.size()).clear();
                    result.add(stroke.copy());
                }
            } else if (stroke.isCandidateAnchored()) {
                if (!cutStroke) {
                    result.subList(fragmentStart,result.size()).clear();
                    result.add(stroke.copy());
                } else {
                    // Once a complete candidate circle is cut, its remaining arcs are freehand ink.
                    for (int i=fragmentStart;i<result.size();i++) {
                        DoodleStroke free = result.get(i);
                        for (int j = 0; j < free.pointCount(); j++) {
                            Point2D screen = transform.transform(new Point2D.Double(free.pointX(j),free.pointY(j)),null);
                            free.setPoint(j,screen.getX()/width,screen.getY()/height);
                        }
                        free.setWidthFactor((float)(stroke.getWidthFactor()*transform.getScaleX()/Math.min(width,height)));
                        free.setAnchorCell(-1);free.setAnchorDigit(0);
                    }
                    if (removed != null) for (int i=removedStart;i<removed.size();i++) {
                        DoodleStroke free = removed.get(i);
                        for (int j = 0; j < free.pointCount(); j++) {
                            Point2D screen = transform.transform(new Point2D.Double(free.pointX(j),free.pointY(j)),null);
                            free.setPoint(j,screen.getX()/width,screen.getY()/height);
                        }
                        free.setWidthFactor((float)(stroke.getWidthFactor()*transform.getScaleX()/Math.min(width,height)));
                        free.setAnchorCell(-1);free.setAnchorDigit(0);
                    }
                }
            }
        }
        return result;
    }

    private static java.awt.geom.Rectangle2D projectedBounds(DoodleStroke stroke,
            java.awt.geom.AffineTransform transform) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<stroke.pointCount();i++) {
            minX=Math.min(minX,stroke.pointX(i));minY=Math.min(minY,stroke.pointY(i));
            maxX=Math.max(maxX,stroke.pointX(i));maxY=Math.max(maxY,stroke.pointY(i));
        }
        if(stroke.pointCount()==0)return new java.awt.geom.Rectangle2D.Double();
        java.awt.geom.Rectangle2D bounds=transform.createTransformedShape(
                new java.awt.geom.Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY)).getBounds2D();
        // Horizontal/vertical paths have zero-area bounds but are still erasable.
        return new java.awt.geom.Rectangle2D.Double(bounds.getX()-1e-7,bounds.getY()-1e-7,
                bounds.getWidth()+2e-7,bounds.getHeight()+2e-7);
    }

    private static List<double[]> edges(Shape shape) {
        List<double[]> result = new ArrayList<double[]>();double[] v = new double[6];
        double x = 0, y = 0, startX = 0, startY = 0;
        for (PathIterator it = shape.getPathIterator(null, 0.2); !it.isDone(); it.next()) {
            int type = it.currentSegment(v);
            if (type == PathIterator.SEG_MOVETO) {x = startX = v[0];y = startY = v[1];}
            else if (type == PathIterator.SEG_LINETO) {result.add(new double[]{x,y,v[0],v[1]});x=v[0];y=v[1];}
            else if (type == PathIterator.SEG_CLOSE) {result.add(new double[]{x,y,startX,startY});x=startX;y=startY;}
        }
        return result;
    }
}
