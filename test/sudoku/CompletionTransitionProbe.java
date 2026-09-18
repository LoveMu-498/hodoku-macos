/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** Checks that one attempt records only the first unsolved-to-solved transition. */
public final class CompletionTransitionProbe {

	private CompletionTransitionProbe() {
	}

	public static void main(String[] args) {
		CompletionTransition transition = new CompletionTransition();
		transition.begin(true, false);
		require(!transition.update(false), "unsolved state recorded a completion");
		require(transition.update(true), "first completion was not recorded");
		require(!transition.update(true), "same solved state recorded twice");
		require(!transition.update(false), "undo recorded a completion");
		require(!transition.update(true), "re-completing the same attempt recorded twice");

		transition.begin(false, false);
		require(!transition.update(true), "excluded source recorded a completion");

		transition.begin(true, true);
		require(!transition.update(true), "already completed history row recorded again");

		transition.begin(true, false);
		require(transition.update(true), "new attempt did not reset transition tracking");

		transition.restore(true, true, false);
		require(!transition.update(true), "restored completed attempt recorded twice");
		System.out.println("Completion transition checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
