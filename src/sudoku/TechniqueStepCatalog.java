/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import solver.SudokuSolver;
import solver.SearchCancellation;
import solver.NativeProofCollector;
import solver.SudokuSolverFactory;

/**
 * Board-versioned source of the raw list produced by HoDoKu's native
 * "Find All Steps" path, before progress scores are calculated.
 */
final class TechniqueStepCatalog {
	private static final class BoardCache {
		private List<SolutionStep> steps = new ArrayList<SolutionStep>();
		private boolean complete;
		private boolean inFlight;
	}

	private final java.util.concurrent.locks.ReentrantLock scanLock = new java.util.concurrent.locks.ReentrantLock();
	private final Map<String, BoardCache> boardCaches = new HashMap<String, BoardCache>();
	private String currentSignature;
	private int enumerationRunCount;

	static String createSignature(Sudoku2 sudoku) {
		Options options = Options.getInstance();
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < 81; i++) value.append(sudoku.getValue(i)).append(':')
				.append(sudoku.getCell(i)).append(':').append(sudoku.getUserCells()[i]).append(':').append(sudoku.isFixed(i)).append(';');
		for (StepConfig config : options.solverSteps) {
			value.append('|').append(config.getType().name())
					.append(':').append(config.isAllStepsEnabled())
					.append(':').append(config.getIndex());
		}
		// Solver and All Steps settings that can change the enumerated instances.
		value.append('|').append(options.getRestrictChainLength())
				.append(':').append(options.getRestrictNiceLoopLength())
				.append(':').append(options.isRestrictChainSize())
				.append(':').append(options.getMaxTableEntryLength())
				.append(':').append(options.getAnzTableLookAhead())
				.append(':').append(options.isOnlyOneChainPerStep())
				.append(':').append(options.isAllowAlsInTablingChains())
				.append(':').append(options.isOnlyOneAlsPerStep())
				.append(':').append(options.isAllowAlsOverlap())
				.append(':').append(options.getAllStepsAlsChainLength())
				.append(':').append(options.isAllStepsAlsChainForwardOnly())
				.append(':').append(options.isAllStepsAllowAlsOverlap())
				.append(':').append(options.isAllStepsOnlyOneAlsPerStep())
				.append(':').append(options.isAllStepsAllowAlsInTablingChains())
				.append(':').append(options.isAllStepsSearchFish())
				.append(':').append(options.getAllStepsMinFishSize())
				.append(':').append(options.getAllStepsMaxFishSize())
				.append(':').append(options.getAllStepsMaxFins())
				.append(':').append(options.getAllStepsMaxEndoFins())
				.append(':').append(options.getAllStepsMaxFishType())
				.append(':').append(options.isAllStepsCheckTemplates())
				.append(':').append(options.getAllStepsFishCandidates())
				.append(':').append(options.getAllStepsKrakenMinFishSize())
				.append(':').append(options.getAllStepsKrakenMaxFishSize())
				.append(':').append(options.getAllStepsMaxKrakenFins())
				.append(':').append(options.getAllStepsMaxKrakenEndoFins())
				.append(':').append(options.getAllStepsKrakenMaxFishType())
				.append(':').append(options.getAllStepsKrakenFishCandidates());
		return value.toString();
	}

    List<SolutionStep> findBoxSteps(Sudoku2 board,java.util.Set<Integer> cells) {
        if (cells.isEmpty()) return findAllRawSteps(board, null);
        return findMatchingSteps(board, step -> {
            ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
            java.util.Set<Integer> involved = new java.util.HashSet<>(index.premiseCells);
            involved.addAll(index.conclusionCells);
            return index.supported && involved.containsAll(cells);
        });
    }

    /** Search every enabled native family without letting equal eliminations hide a proof.
     * Only matching proofs are retained. Canceled scans never publish partial results.
     */
    List<SolutionStep> findMatchingSteps(Sudoku2 board, java.util.function.Predicate<SolutionStep> matches) {
        try { scanLock.lockInterruptibly(); }
        catch (InterruptedException canceled) { Thread.currentThread().interrupt(); return new ArrayList<>(); }
        SudokuSolver solver = SudokuSolverFactory.getInstance();
        boolean completed = false;
        final String signature = createSignature(board);
        java.util.Map<String,SolutionStep> proofs = new java.util.LinkedHashMap<>();
        java.util.function.Consumer<SolutionStep> collect = step -> {
            if (matches.test(step)) {
                String key = ReasoningStepIndex.identity(step);
                if (!proofs.containsKey(key)) proofs.put(key, NativeProofCollector.copy(step));
            }
        };
        try (SearchCancellation.Scope scope = SearchCancellation.enable();
                NativeProofCollector collector = new NativeProofCollector(collect)) {
            SearchCancellation.check();
            List<SolutionStep> ordinary = new ArrayList<>();
            FindAllSteps finder = new FindAllSteps(ordinary, board.clone(), null, solver.getStepFinder());
            finder.setCalculateProgressScores(false);
            finder.setCloseDialogWhenDone(false);
            finder.run();
            for (SolutionStep step : ordinary) { SearchCancellation.check(); collect.accept(step); }
            // Basic size 2–4 fish are also checked for manual verification, as in Windows.
            // This is independent of recommendation-list fish visibility settings.
            for (SolutionStep step : solver.getStepFinder().getAllFishes(board.clone(), 2, 4, 5, 2, null, -1, 0))
                collect.accept(step);
            SearchCancellation.check();
            completed = signature.equals(createSignature(board));
            return completed ? new ArrayList<>(proofs.values()) : new ArrayList<>();
        } catch (java.util.concurrent.CancellationException canceled) { return new ArrayList<>(); }
        finally {
            if (completed) SudokuSolverFactory.giveBack(solver); else SudokuSolverFactory.discard(solver);
            scanLock.unlock();
        }
    }

	List<SolutionStep> findAllRawSteps(Sudoku2 sudoku, FindAllStepsProgressDialog progress) {
		return findSteps(sudoku, progress, false);
	}

	List<SolutionStep> findSteps(Sudoku2 sudoku, FindAllStepsProgressDialog progress, boolean singleDigit) {
        try { scanLock.lockInterruptibly(); }
        catch (InterruptedException canceled) { Thread.currentThread().interrupt(); return new ArrayList<SolutionStep>(); }
        try { return enumerateSteps(sudoku, progress, singleDigit); }
        finally { scanLock.unlock(); }
    }

    private List<SolutionStep> enumerateSteps(Sudoku2 sudoku, FindAllStepsProgressDialog progress, boolean singleDigit) {
		String signature = createSignature(sudoku) + "|single=" + singleDigit;
		BoardCache cache;
		synchronized (this) {
			cache = getBoardCache(signature);
			while (cache.inFlight) {
				try {
					wait();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return new ArrayList<SolutionStep>();
				}
			}
			if (cache.complete) return cloneSteps(cache.steps);
			cache.inFlight = true;
			enumerationRunCount++;
		}

		List<SolutionStep> found = new ArrayList<SolutionStep>();
		boolean completed = false;
		SudokuSolver solver = SudokuSolverFactory.getInstance();
		try (SearchCancellation.Scope scope=SearchCancellation.enable()) {
            SearchCancellation.check();
			solver.setSudoku(sudoku);
			FindAllSteps finder = new FindAllSteps(found, sudoku.clone(), progress,
					solver.getStepFinder());
			if (singleDigit) {
				List<SolutionType> types = new ArrayList<SolutionType>();
				for (StepConfig config : Options.getInstance().solverSteps)
					if (config.isAllStepsEnabled() && ReasoningStepIndex.SINGLE_DIGIT.contains(config.getType())) types.add(config.getType());
				finder.setTestType(types);
			}
			finder.setCalculateProgressScores(false);
			finder.setCloseDialogWhenDone(false);
			finder.run();
			completed = !Thread.currentThread().isInterrupted()
                    && signature.equals(createSignature(sudoku) + "|single=" + singleDigit);
		} catch(java.util.concurrent.CancellationException canceled) {
            completed=false;
        } finally {
			if(completed)SudokuSolverFactory.giveBack(solver);else SudokuSolverFactory.discard(solver);
			synchronized (this) {
				cache.inFlight = false;
				if (completed) {
					cache.steps = cloneSteps(found);
					cache.complete = true;
				}
				notifyAll();
				pruneOldCaches();
			}
		}
		return completed ? cloneSteps(found) : new ArrayList<SolutionStep>();
	}

	private BoardCache getBoardCache(String signature) {
		currentSignature = signature;
		BoardCache cache = boardCaches.get(signature);
		if (cache == null) {
			cache = new BoardCache();
			boardCaches.put(signature, cache);
		}
		return cache;
	}

	private void pruneOldCaches() {
		List<String> obsolete = new ArrayList<String>();
		for (Map.Entry<String, BoardCache> entry : boardCaches.entrySet()) {
			if (!entry.getKey().substring(0, entry.getKey().lastIndexOf("|single=")).equals(
                    currentSignature.substring(0, currentSignature.lastIndexOf("|single="))) && !entry.getValue().inFlight) {
				obsolete.add(entry.getKey());
			}
		}
		for (String signature : obsolete) boardCaches.remove(signature);
	}

	private static List<SolutionStep> cloneSteps(List<SolutionStep> source) {
		List<SolutionStep> copy = new ArrayList<SolutionStep>(source.size());
		for (SolutionStep step : source) copy.add((SolutionStep) step.clone());
		return copy;
	}

	synchronized int getEnumerationRunCount() {
		return enumerationRunCount;
	}
}
