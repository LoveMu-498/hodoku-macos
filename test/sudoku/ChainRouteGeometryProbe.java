/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.geom.Point2D;
import java.util.Collections;

/** Verifies the shared, deterministic geometry used for chain relations. */
public final class ChainRouteGeometryProbe {
	private static final double EPSILON = 0.000001;

	private ChainRouteGeometryProbe() {
	}

	public static void main(String[] args) {
		verifyClearHorizontalRoute();
		verifyCandidateObstacleRoute();
		verifyLaneOffsetAndReverseTraversal();
		verifyBoundedLaneAssignment();
		System.out.println("Chain route geometry checks passed");
	}

	private static void verifyClearHorizontalRoute() {
		ChainRouteGeometry.Route route = ChainRouteGeometry.createLaneZero(
				new Point2D.Double(10.0, 10.0), new Point2D.Double(110.0, 10.0),
				12.0, Collections.<Point2D.Double>emptyList());
		require(!route.isCurved(), "clear horizontal relation did not stay straight");
		requirePoint(route.getStart(), 20.0, 10.0,
				"straight relation start no longer matches the generated-chain inset");
		requirePoint(route.getEnd(), 100.0, 10.0,
				"straight relation end no longer matches the generated-chain inset");
		requirePoint(route.getArrowBase(10.0), 90.0, 10.0,
				"straight arrow base no longer follows the final route tangent");
		require(Math.abs(route.getRawLength() - 100.0) < EPSILON,
				"route lost the raw candidate-center distance");
	}

	private static void verifyCandidateObstacleRoute() {
		ChainRouteGeometry.Route route = ChainRouteGeometry.createLaneZero(
				new Point2D.Double(10.0, 10.0), new Point2D.Double(110.0, 10.0), 12.0,
				Collections.singletonList(new Point2D.Double(60.0, 10.0)));
		require(route.isCurved(), "candidate on the relation did not select a cubic route");
		requirePoint(route.getStart(), 17.071067811865476, 2.9289321881345254,
				"curved relation start no longer matches generated-chain rotation");
		requirePoint(route.getControl1(), 31.213203435596427, -11.213203435596425,
				"curved relation first control point changed");
		requirePoint(route.getControl2(), 88.78679656440357, -11.213203435596425,
				"curved relation second control point changed");
		requirePoint(route.getEnd(), 102.92893218813452, 2.9289321881345254,
				"curved relation end no longer matches generated-chain rotation");
		Point2D.Double arrowBase = route.getArrowBase(10.0);
		require(Math.abs(arrowBase.distance(route.getEnd()) - 10.0) < 0.00001
				&& arrowBase.x < route.getEnd().x,
				"curved arrow base no longer follows the final route tangent: " + arrowBase);
	}

	private static void verifyLaneOffsetAndReverseTraversal() {
		ChainRouteGeometry.Route route = ChainRouteGeometry.createLaneZero(
				new Point2D.Double(10.0, 10.0), new Point2D.Double(110.0, 10.0),
				12.0, Collections.<Point2D.Double>emptyList()).withLaneOffset(5.0);
		requirePoint(route.getStart(), 20.0, 15.0,
				"lane offset did not move the route along its stable normal");
		requirePoint(route.getArrowBase(10.0), 90.0, 15.0,
				"lane offset did not move the arrow geometry with the route");

		ChainRouteGeometry.Route reversed = route.reversed();
		requirePoint(reversed.getStart(), 100.0, 15.0,
				"reverse traversal did not start at the lane's opposite endpoint");
		requirePoint(reversed.getEnd(), 20.0, 15.0,
				"reverse traversal did not end at the lane's opposite endpoint");
		requirePoint(reversed.getArrowBase(10.0), 30.0, 15.0,
				"reverse traversal did not reverse the arrow direction");
	}

	private static void verifyBoundedLaneAssignment() {
		double first = ChainRouteGeometry.boundedLaneOffset(0, 21, 5.0, 12.0);
		double middle = ChainRouteGeometry.boundedLaneOffset(10, 21, 5.0, 12.0);
		double last = ChainRouteGeometry.boundedLaneOffset(20, 21, 5.0, 12.0);
		require(Math.abs(first) <= 4.8 + EPSILON && Math.abs(last) <= 4.8 + EPSILON,
				"dense coincident lanes escaped the candidate attachment radius");
		require(first < middle && middle < last,
				"bounded coincident lanes lost deterministic ordering");
		require(Math.abs(ChainRouteGeometry.boundedLaneOffset(0, 1, 5.0, 12.0)) < EPSILON,
				"a single segment did not remain on lane zero");
	}

	private static void requirePoint(Point2D.Double actual, double x, double y, String message) {
		require(Math.abs(actual.x - x) < EPSILON && Math.abs(actual.y - y) < EPSILON,
				message + ": " + actual);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
