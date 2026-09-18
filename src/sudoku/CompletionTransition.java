/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** Tracks one puzzle attempt's first eligible unsolved-to-solved transition. */
final class CompletionTransition {

	private boolean enabled;
	private boolean completedRecorded;
	private boolean previouslySolved;

	void begin(boolean enabled, boolean alreadyCompleted) {
		this.enabled = enabled;
		completedRecorded = alreadyCompleted;
		previouslySolved = alreadyCompleted;
	}

	boolean update(boolean solved) {
		boolean firstCompletion = enabled && !completedRecorded && !previouslySolved && solved;
		if (firstCompletion) {
			completedRecorded = true;
		}
		previouslySolved = solved;
		return firstCompletion;
	}

	boolean isEnabled() {
		return enabled;
	}

	boolean isCompletedRecorded() {
		return completedRecorded;
	}

	boolean wasPreviouslySolved() {
		return previouslySolved;
	}

	void restore(boolean enabled, boolean completedRecorded, boolean previouslySolved) {
		this.enabled = enabled;
		this.completedRecorded = completedRecorded;
		this.previouslySolved = previouslySolved;
	}

	CompletionTransition copy() {
		CompletionTransition copy = new CompletionTransition();
		copy.enabled = enabled;
		copy.completedRecorded = completedRecorded;
		copy.previouslySolved = previouslySolved;
		return copy;
	}

	void restore(CompletionTransition saved) {
		enabled = saved.enabled;
		completedRecorded = saved.completedRecorded;
		previouslySolved = saved.previouslySolved;
	}
}
