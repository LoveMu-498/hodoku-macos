/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.geom.Point2D;
import java.util.List;

/** Shared geometry for generated and user-authored chain relations. */
final class ChainRouteGeometry {
	private ChainRouteGeometry() {
	}

	static Route createLaneZero(Point2D.Double rawStart, Point2D.Double rawEnd,
			double candidateDiameter, List<Point2D.Double> obstacles) {
		Point2D.Double start = copy(rawStart);
		Point2D.Double end = copy(rawEnd);
		double rawLength = rawStart.distance(rawEnd);
		double alpha = Math.atan2(rawEnd.y - rawStart.y, rawEnd.x - rawStart.x);
		double normalX = -Math.sin(alpha);
		double normalY = Math.cos(alpha);
		double inset = candidateDiameter / 2.0 + 4.0;
		int insetX = (int) (inset * Math.cos(alpha));
		int insetY = (int) (inset * Math.sin(alpha));
		start.x += insetX;
		start.y += insetY;
		end.x -= insetX;
		end.y -= insetY;

		boolean obstructed = false;
		double deltaX = rawEnd.x - rawStart.x;
		double deltaY = rawEnd.y - rawStart.y;
		for (Point2D.Double obstacle : obstacles) {
			if (obstacle.equals(rawStart) || obstacle.equals(rawEnd)) {
				continue;
			}
			double obstacleX = obstacle.x - start.x;
			double obstacleY = obstacle.y - start.y;
			if (Math.signum(deltaX) == Math.signum(obstacleX)
					&& Math.signum(deltaY) == Math.signum(obstacleY)
					&& Math.abs(obstacleX) <= Math.abs(deltaX)
					&& Math.abs(obstacleY) <= Math.abs(deltaY)
					&& (deltaX == 0.0 || deltaY == 0.0
							|| Math.abs(deltaX / deltaY - obstacleX / obstacleY) < 0.1)) {
				obstructed = true;
				break;
			}
		}
		if (rawLength < 2.0 * candidateDiameter) {
			obstructed = true;
		}
		if (!obstructed) {
			return new Route(start, end, null, null, rawLength, alpha, normalX, normalY);
		}

		double bezierLength = rawLength < 2.0 * candidateDiameter ? rawLength / 4.0 : 20.0;
		rotate(rawStart, start, -Math.PI / 4.0);
		rotate(rawEnd, end, Math.PI / 4.0);
		double firstAngle = alpha - Math.PI / 4.0;
		Point2D.Double control1 = new Point2D.Double(
				start.x + bezierLength * Math.cos(firstAngle),
				start.y + bezierLength * Math.sin(firstAngle));
		double secondAngle = alpha + Math.PI / 4.0;
		Point2D.Double control2 = new Point2D.Double(
				end.x - bezierLength * Math.cos(secondAngle),
				end.y - bezierLength * Math.sin(secondAngle));
		return new Route(start, end, control1, control2, rawLength, secondAngle, normalX, normalY);
	}

	private static void rotate(Point2D.Double center, Point2D.Double point, double angle) {
		point.x -= center.x;
		point.y -= center.y;
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double x = point.x;
		double y = point.y;
		point.x = x * cos - y * sin + center.x;
		point.y = x * sin + y * cos + center.y;
	}

	private static Point2D.Double copy(Point2D.Double point) {
		return point == null ? null : new Point2D.Double(point.x, point.y);
	}

	/** Assigns stable, distinct coincident lanes while keeping endpoints attached. */
	static double boundedLaneOffset(int ordinal, int count, double spacing,
			double candidateDiameter) {
		if (count <= 1) return 0.0;
		double centered = ordinal - (count - 1) / 2.0;
		double desired = centered * spacing;
		double desiredSpan = (count - 1) / 2.0 * spacing;
		double maximumSpan = Math.max(0.0, candidateDiameter * 0.4);
		if (desiredSpan <= maximumSpan || desiredSpan == 0.0) return desired;
		return desired * maximumSpan / desiredSpan;
	}

	static final class Route {
		private final Point2D.Double start;
		private final Point2D.Double end;
		private final Point2D.Double control1;
		private final Point2D.Double control2;
		private final double rawLength;
		private final double finalAngle;
		private final double normalX;
		private final double normalY;

		private Route(Point2D.Double start, Point2D.Double end, Point2D.Double control1,
				Point2D.Double control2, double rawLength, double finalAngle,
				double normalX, double normalY) {
			this.start = start;
			this.end = end;
			this.control1 = control1;
			this.control2 = control2;
			this.rawLength = rawLength;
			this.finalAngle = finalAngle;
			this.normalX = normalX;
			this.normalY = normalY;
		}

		boolean isCurved() {
			return control1 != null;
		}

		Point2D.Double getStart() {
			return copy(start);
		}

		Point2D.Double getEnd() {
			return copy(end);
		}

		Point2D.Double getControl1() {
			return copy(control1);
		}

		Point2D.Double getControl2() {
			return copy(control2);
		}

		Point2D.Double getArrowBase(double arrowLength) {
			if (isCurved()) {
				// The arrow occupies only the final part of this non-looping route. A
				// bounded binary search is both smoother and much cheaper than flattening
				// the complete cubic at sub-pixel precision on every repaint.
				double low = 0.0;
				double high = 1.0;
				for (int i = 0; i < 28; i++) {
					double middle = (low + high) / 2.0;
					Point2D.Double point = cubicPoint(middle);
					if (point.distance(end) > arrowLength) {
						low = middle;
					} else {
						high = middle;
					}
				}
				return cubicPoint((low + high) / 2.0);
			}
			return new Point2D.Double(end.x - Math.cos(finalAngle) * arrowLength,
					end.y - Math.sin(finalAngle) * arrowLength);
		}

		private Point2D.Double cubicPoint(double t) {
			double inverse = 1.0 - t;
			double startWeight = inverse * inverse * inverse;
			double control1Weight = 3.0 * inverse * inverse * t;
			double control2Weight = 3.0 * inverse * t * t;
			double endWeight = t * t * t;
			return new Point2D.Double(
					startWeight * start.x + control1Weight * control1.x
							+ control2Weight * control2.x + endWeight * end.x,
					startWeight * start.y + control1Weight * control1.y
							+ control2Weight * control2.y + endWeight * end.y);
		}

		double getRawLength() {
			return rawLength;
		}

		Route withLaneOffset(double offset) {
			double x = normalX * offset;
			double y = normalY * offset;
			return new Route(translate(start, x, y), translate(end, x, y),
					translate(control1, x, y), translate(control2, x, y),
					rawLength, finalAngle, normalX, normalY);
		}

		Route reversed() {
			return new Route(copy(end), copy(start), copy(control2), copy(control1),
					rawLength, finalAngle + Math.PI, normalX, normalY);
		}

		private static Point2D.Double translate(Point2D.Double point, double x, double y) {
			return point == null ? null : new Point2D.Double(point.x + x, point.y + y);
		}
	}
}
