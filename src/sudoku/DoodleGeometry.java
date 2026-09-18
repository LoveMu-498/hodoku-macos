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

    static List<DoodleStroke> subtract(List<DoodleStroke> strokes, Shape eraser, int width, int height) {
        return subtract(strokes, eraser, width, height, stroke -> java.awt.geom.AffineTransform.getScaleInstance(width, height));
    }

    static List<DoodleStroke> subtract(List<DoodleStroke> strokes, Shape eraser, int width, int height,
            java.util.function.Function<DoodleStroke, java.awt.geom.AffineTransform> projection) {
        List<double[]> edges = edges(eraser);
        List<DoodleStroke> result = new ArrayList<DoodleStroke>();
        for (DoodleStroke stroke : strokes) {
            java.awt.geom.AffineTransform transform = projection.apply(stroke);
            if (transform == null) { result.add(stroke.copy()); continue; }
            java.awt.geom.AffineTransform inverse;
            try { inverse = transform.createInverse(); }
            catch (java.awt.geom.NoninvertibleTransformException ex) { throw new IllegalArgumentException(ex); }
            int fragmentStart = result.size();
            boolean cutStroke = false;
            DoodleStroke fragment = null;
            List<DoodlePoint> points = stroke.getPoints();
            for (int i = 1; i < points.size(); i++) {
                DoodlePoint a = points.get(i - 1), b = points.get(i);
                Point2D pa = transform.transform(new Point2D.Double(a.getX(),a.getY()),null);
                Point2D pb = transform.transform(new Point2D.Double(b.getX(),b.getY()),null);
                double ax = pa.getX(), ay = pa.getY();
                double dx = pb.getX()-ax, dy = pb.getY()-ay;
                if (Math.hypot(dx, dy) < 1e-9) continue;
                List<Double> cuts = new ArrayList<Double>(); cuts.add(0.0); cuts.add(1.0);
                for (double[] edge : edges) {
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
                    if (eraser.contains(ax + dx * middle, ay + dy * middle)) {cutStroke = true; fragment = null; continue;}
                    Point2D local = inverse.transform(new Point2D.Double(ax+dx*from, ay+dy*from),null);
                    double x = local.getX(), y = local.getY();
                    if (fragment != null) {
                        DoodlePoint last = fragment.getPoints().get(fragment.getPoints().size() - 1);
                        if (Point2D.distance(last.getX(), last.getY(), x, y) > 1e-8) fragment = null;
                    }
                    if (fragment == null) {
                        fragment = new DoodleStroke(stroke.getColor(), stroke.getWidthFactor());
                        fragment.setAnchorCell(stroke.getAnchorCell());
                        fragment.setAnchorDigit(stroke.getAnchorDigit());
                        fragment.getPoints().add(new DoodlePoint(x, y));result.add(fragment);
                    }
                    Point2D end = inverse.transform(new Point2D.Double(ax+dx*to, ay+dy*to),null);
                    fragment.getPoints().add(new DoodlePoint(end.getX(),end.getY()));
                }
            }
            if (stroke.isCandidateAnchored()) {
                if (!cutStroke) {
                    result.subList(fragmentStart,result.size()).clear();
                    result.add(stroke.copy());
                } else {
                    // Once a complete candidate circle is cut, its remaining arcs are freehand ink.
                    for (int i=fragmentStart;i<result.size();i++) {
                        DoodleStroke free = result.get(i);
                        for (DoodlePoint point : free.getPoints()) {
                            Point2D screen = transform.transform(new Point2D.Double(point.getX(),point.getY()),null);
                            point.setX(screen.getX()/width);point.setY(screen.getY()/height);
                        }
                        free.setWidthFactor((float)(stroke.getWidthFactor()*transform.getScaleX()/Math.min(width,height)));
                        free.setAnchorCell(-1);free.setAnchorDigit(0);
                    }
                }
            }
        }
        return result;
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
