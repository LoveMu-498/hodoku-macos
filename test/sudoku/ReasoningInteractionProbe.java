/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import solver.SudokuSolver;

/** End-to-end checks for native Box Analyze/Preview/Apply ownership. */
public final class ReasoningInteractionProbe {
	private static final String[] PUZZLES = {
		"530070000600195000098000060800060003400803001700020006060000280000419005000080079"
	};

	private ReasoningInteractionProbe() {
	}

	public static void main(String[] args) throws Exception {
		final AtomicReference<MainFrame> frameRef = new AtomicReference<MainFrame>();
		try {
			MainFrame frame = onEdt(new Callable<MainFrame>() {
				@Override public MainFrame call() {
					MainFrame created = new MainFrame(null);
					created.getSudokuPanel().setSize(600, 600);
					frameRef.set(created);
					return created;
				}
			});
			verifyBoxReasoningTransaction(frame);
			verifyFreeChainReasoningTransaction(frame);
		} finally {
			final MainFrame frame = frameRef.get();
			if (frame != null) onEdt(new Callable<Void>() {
				@Override public Void call() {
					frame.dispose();
					return null;
				}
			});
		}
		System.out.println("Reasoning interaction checks passed");
		System.exit(0);
	}

	private static void verifyBoxReasoningTransaction(final MainFrame frame) throws Exception {
		final SudokuPanel panel = frame.getSudokuPanel();
		SudokuSet footprint = null;
		for (final String puzzle : PUZZLES) {
			onEdt(new Callable<Void>() {
				@Override public Void call() {
					panel.setSudoku(puzzle);
					panel.paint(new java.awt.image.BufferedImage(600, 600,
							java.awt.image.BufferedImage.TYPE_INT_ARGB).getGraphics());
					return null;
				}
			});
			Sudoku2 snapshot = onEdt(new Callable<Sudoku2>() {
				@Override public Sudoku2 call() { return panel.getSudoku().clone(); }
			});
			List<SolutionStep> steps = frame.getTechniqueStepCatalog().findAllRawSteps(snapshot, null);
			footprint = findMatchableBoxFootprint(steps, snapshot);
			if (footprint != null) break;
		}
		require(footprint != null, "fixtures exposed no exact native Box premise");
		final SudokuSet selected = footprint.clone();
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
				for (int i = 0; i < selected.size(); i++) clickCell(panel, selected.get(i));
				return null;
			}
		});
		final String before = onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		});
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.undoPossible(); }
		}), "fixture began with Sudoku undo history");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_ENTER);
				clickFrameButton(frame, "hinweisAbbrechenButton");
				require(!panel.isReasoningAnalysisInProgress() && panel.getStep() == null,
						"hint-panel X did not cancel an active reasoning analysis");
				require(panel.getBoxReasoningFootprint().size() == selected.size(),
						"canceling reasoning analysis consumed its Box source");
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() {
						return panel.getStep() != null || !panel.isReasoningAnalysisInProgress();
					}
				});
			}
		}, 20000L, "Box analysis did not finish");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.getStep() != null; }
		}), "first Enter did not publish an exact native preview");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return frame.isDisplayedStepExecutionEnabled()
						&& frame.getRootPane().getDefaultButton() != null;
			}
		}), "reasoning preview did not expose the unified revalidating execute command");
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Analyze changed the Sudoku board");
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.undoPossible(); }
		}), "Analyze created a Sudoku undo transaction");
		require(onEdt(new Callable<Integer>() {
			@Override public Integer call() { return panel.getBoxReasoningFootprint().size(); }
		}).intValue() == selected.size(), "Analyze removed the source Box regions before Apply");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.doStep();
				return null;
			}
		});
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "the low-level legacy executor bypassed reasoning revalidation");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.getStep() != null; }
		}), "blocked legacy execution dismissed the reasoning preview");
		final java.awt.image.BufferedImage proposalExport = onEdt(
				new Callable<java.awt.image.BufferedImage>() {
			@Override public java.awt.image.BufferedImage call() {
				return panel.getSudokuImage(360);
			}
		});

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.shutdownReasoning();
				return null;
			}
		});
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				GuiState state = new GuiState(panel, null, null);
				state.setIncludeAnnotations(true);
				state.get(true);
				return panel.getStep() == null && state.getStep() == null;
			}
		}), "reasoning shutdown left a proposal overlay executable or persistable");
		final java.awt.image.BufferedImage ordinaryExport = onEdt(
				new Callable<java.awt.image.BufferedImage>() {
			@Override public java.awt.image.BufferedImage call() {
				return panel.getSudokuImage(360);
			}
		});
		require(sameImage(proposalExport, ordinaryExport),
				"screen-only reasoning proposal leaked into image export");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() != null; }
				});
			}
		}, 20000L, "Box source could not be analyzed again after reasoning shutdown");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				clickFrameButton(frame, "abortStepToggleButton");
				require(panel.getStep() == null && !panel.isReasoningAnalysisInProgress(),
						"toolbar X did not cancel the reasoning preview");
				require(panel.getBoxReasoningFootprint().size() == selected.size(),
						"canceling a reasoning preview consumed its Box source");
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() != null; }
				});
			}
		}, 20000L, "Box source could not be analyzed again after toolbar cancellation");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_DELETE);
				return null;
			}
		});
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Delete applied a reasoning proposal");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.getStep() != null; }
		}), "Delete dismissed the reasoning preview");

		GuiState captured = onEdt(new Callable<GuiState>() {
			@Override public GuiState call() {
				GuiState state = new GuiState(panel, null, null);
				state.setIncludeAnnotations(true);
				state.get(true);
				return state;
			}
		});
		require(captured.getStep() == null,
				"reasoning-origin preview leaked into persisted solution-step state");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				clickFrameButton(frame, "hinweisAusfuehrenButton");
				require(panel.isReasoningAnalysisInProgress(),
						"checkmark did not enter reasoning revalidation");
				Object revalidationWorker = readField(panel, "reasoningWorker");
				press(panel, KeyEvent.VK_ENTER);
				require(readField(panel, "reasoningWorker") == revalidationWorker,
						"repeated Enter started a second reasoning revalidation worker");
				Options.getInstance().setShowHintButtonsInToolbar(true);
				invokeFrameNoArg(frame, "setShowHintButtonsInToolbar");
				require(!frame.isDisplayedStepExecutionEnabled()
						&& frameButtonEnabled(frame, "abortStepToggleButton"),
						"toolbar refresh coupled reasoning cancel availability to Execute");
				clickFrameButton(frame, "hinweisAbbrechenButton");
				require(panel.getStep() == null && !panel.isReasoningAnalysisInProgress(),
						"hint-panel X did not cancel reasoning revalidation");
				require(panel.getBoxReasoningFootprint().size() == selected.size(),
						"canceling reasoning revalidation consumed its Box source");
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() != null; }
				});
			}
		}, 20000L, "Box source could not be analyzed again after revalidation cancellation");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				clickFrameButton(frame, "hinweisAusfuehrenButton");
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() == null && panel.undoPossible(); }
				});
			}
		}, 20000L, "hint-panel checkmark did not revalidate and apply the native proposal");
		require(!before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Apply made no native Sudoku change");
		require(onEdt(new Callable<Integer>() {
			@Override public Integer call() { return panel.getBoxReasoningFootprint().size(); }
		}).intValue() == 0, "Apply retained the consumed source Box regions");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.getAnnotationTool() == AnnotationTool.BOX_SELECTION; }
		}), "Apply switched away from Box selection");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
				panel.undo();
				return null;
			}
		});
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Sudoku undo did not reverse exactly the applied native step");
		require(onEdt(new Callable<Integer>() {
			@Override public Integer call() { return panel.getBoxReasoningFootprint().size(); }
		}).intValue() == 0, "Sudoku undo restored an already-consumed Box annotation source");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
				for (int i = 0; i < selected.size(); i++) clickCell(panel, selected.get(i));
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() != null; }
				});
			}
		}, 20000L, "restored exact Box source did not produce another preview");
		final int extraCell = onEdt(new Callable<Integer>() {
			@Override public Integer call() {
				for (int index = 0; index < Sudoku2.LENGTH; index++) {
					if (!selected.contains(index) && panel.getSudoku().getValue(index) == 0) return index;
				}
				return -1;
			}
		});
		require(extraCell >= 0, "fixture exposed no extra empty Box cell");

		final SudokuSolver originalSolver = onEdt(new Callable<SudokuSolver>() {
			@Override public SudokuSolver call() { return panel.getSolver(); }
		});
		onEdt(new Callable<Void>() {
			@Override public Void call() throws Exception {
				setPanelSolver(panel, new FailingSudokuSolver());
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() {
						return panel.getStep() == null && !panel.isReasoningAnalysisInProgress();
					}
				});
			}
		}, 20000L, "failed native apply did not settle cleanly");
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "failed native apply did not roll back the Sudoku board");
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.undoPossible(); }
		}), "failed native apply created a Sudoku undo transaction");
		require(onEdt(new Callable<Integer>() {
			@Override public Integer call() { return panel.getBoxReasoningFootprint().size(); }
		}).intValue() == selected.size(), "failed native Apply consumed its Box source");
		onEdt(new Callable<Void>() {
			@Override public Void call() throws Exception {
				setPanelSolver(panel, originalSolver);
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() { return panel.getStep() != null; }
				});
			}
		}, 20000L, "Box source could not be analyzed again after an apply failure");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				clickCell(panel, extraCell);
				return null;
			}
		});
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.getStep() == null; }
		}), "editing the proposal's Box source did not dismiss its generated overlay");
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "source invalidation changed the Sudoku board");
	}

	private static void verifyFreeChainReasoningTransaction(final MainFrame frame) throws Exception {
		final SudokuPanel panel = frame.getSudokuPanel();
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.setSudoku(PUZZLES[0]);
				panel.setShowCandidates(true);
				panel.paint(new java.awt.image.BufferedImage(600, 600,
						java.awt.image.BufferedImage.TYPE_INT_ARGB).getGraphics());
				return null;
			}
		});
		Sudoku2 snapshot = onEdt(new Callable<Sudoku2>() {
			@Override public Sudoku2 call() { return panel.getSudoku().clone(); }
		});
		List<SolutionStep> steps = frame.getTechniqueStepCatalog().findAllRawSteps(snapshot, null);
		final UserChain authored = findMatchableUserChain(steps, snapshot);
		require(authored != null, "fixture exposed no exact native Free-chain premise");
		final String before = onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		});

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
				panel.getCellZoomPanel().selectPaletteGroup(0);
				int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
				List<UserChainNode> nodes = authored.getNodes();
				clickCandidate(panel, nodes.get(0), cellSize);
				boolean pendingStrong = true;
				for (int relation = 0; relation < authored.getStrongRelations().size(); relation++) {
                    if(relation==1) {
                        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),
                                InputEvent.BUTTON3_DOWN_MASK,10,10,1,false,MouseEvent.BUTTON3));
                        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),
                                0,10,10,1,false,MouseEvent.BUTTON3));
                        clickCandidate(panel,nodes.get(relation),cellSize);pendingStrong=true;
                    }
					boolean desiredStrong = authored.getStrongRelations().get(relation).booleanValue();
					if (pendingStrong != desiredStrong) press(panel, KeyEvent.VK_SPACE);
					UserChainNode target = relation + 1 < nodes.size()
							? nodes.get(relation + 1) : nodes.get(0);
					clickCandidate(panel, target, cellSize);
					pendingStrong = !desiredStrong;
				}
				require(panel.getStep() == null,
						"completing a Free chain started analysis without Enter");
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() {
						return panel.getStep() != null || !panel.isReasoningAnalysisInProgress();
					}
				});
			}
		}, 20000L, "Free-chain analysis did not finish");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return panel.getStep() != null && panel.getUserChainCount() == 2;
			}
		}), "first Free-chain Enter did not publish an exact native preview");
		final java.awt.image.BufferedImage chainProposalWithSource = onEdt(
				new Callable<java.awt.image.BufferedImage>() {
			@Override public java.awt.image.BufferedImage call() {
				return paintPanel(panel);
			}
		});
		final java.awt.image.BufferedImage chainProposalWithoutSource = onEdt(
				new Callable<java.awt.image.BufferedImage>() {
			@Override public java.awt.image.BufferedImage call() {
				panel.setUserChainsVisible(false);
				try {
					return paintPanel(panel);
				} finally {
					panel.setUserChainsVisible(true);
				}
			}
		});
		require(sameImage(chainProposalWithSource, chainProposalWithoutSource),
				"native Free-chain preview retained its replaced source drawing");
		require(before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Free-chain Analyze changed the Sudoku board");
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.undoPossible(); }
		}), "Free-chain Analyze created a Sudoku undo transaction");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				clickFrameButton(frame, "executeStepToggleButton");
				return null;
			}
		});
		waitFor(new Callable<Boolean>() {
			@Override public Boolean call() throws Exception {
				return onEdt(new Callable<Boolean>() {
					@Override public Boolean call() {
						return panel.getStep() == null && panel.undoPossible();
					}
				});
			}
		}, 20000L, "toolbar checkmark did not revalidate and apply the Free-chain proposal");
		require(!before.equals(onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		})), "Free-chain Apply made no native Sudoku change");
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return panel.getUserChainCount() == 0
						&& panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN;
			}
		}), "Free-chain Apply retained its consumed source chain or switched tools");
		final String afterApply = onEdt(new Callable<String>() {
			@Override public String call() { return panel.getSudokuString(ClipboardMode.PM_GRID); }
		});
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
				return null;
			}
		});
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return panel.getUserChainCount() == 2
						&& afterApply.equals(panel.getSudokuString(ClipboardMode.PM_GRID));
			}
		}), "Free-chain undo did not restore the source independently of Sudoku history");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_Y, InputEvent.META_DOWN_MASK);
				return null;
			}
		});
		require(onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return panel.getUserChainCount() == 0
						&& afterApply.equals(panel.getSudokuString(ClipboardMode.PM_GRID));
			}
		}), "Free-chain redo did not re-consume the source independently of Sudoku history");

		onEdt(new Callable<Void>() {
			@Override public Void call() {
				int[] available = firstVisibleCandidate(panel);
				clickCandidate(panel, new UserChainNode(available[0], available[1]),
						panel.getX(0, 1) - panel.getX(0, 0));
				return null;
			}
		});
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() { return panel.hasSelectedUserChainForReasoning(); }
		}), "starting a new Free-chain draft retained the old confirmed analysis source");
		onEdt(new Callable<Void>() {
			@Override public Void call() {
				press(panel, KeyEvent.VK_ESCAPE);
				press(panel, KeyEvent.VK_ENTER);
				return null;
			}
		});
		require(!onEdt(new Callable<Boolean>() {
			@Override public Boolean call() {
				return panel.isReasoningAnalysisInProgress() || panel.getStep() != null;
			}
		}), "canceling a new draft implicitly reselected an older completed chain");
	}

	private static int[] firstVisibleCandidate(SudokuPanel panel) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (panel.getSudoku().getValue(index) != 0) continue;
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				if (panel.getSudoku().isCandidate(index, candidate)) {
					return new int[] { index, candidate };
				}
			}
		}
		throw new AssertionError("fixture exposed no visible candidate for a new draft");
	}

	private static void setPanelSolver(SudokuPanel panel, SudokuSolver solver) throws Exception {
		Field field = SudokuPanel.class.getDeclaredField("solver");
		field.setAccessible(true);
		field.set(panel, solver);
	}

	private static void clickFrameButton(MainFrame frame, String fieldName) {
		try {
			Field field = MainFrame.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			javax.swing.JButton button = (javax.swing.JButton) field.get(frame);
			require(button != null && button.isEnabled(), fieldName + " was not enabled");
			button.doClick();
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not activate " + fieldName, ex);
		}
	}

	private static boolean frameButtonEnabled(MainFrame frame, String fieldName) {
		try {
			Field field = MainFrame.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			return ((javax.swing.JButton) field.get(frame)).isEnabled();
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not inspect " + fieldName, ex);
		}
	}

	private static Object readField(Object target, String fieldName) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not inspect " + fieldName, ex);
		}
	}

	private static void invokeFrameNoArg(MainFrame frame, String methodName) {
		try {
			java.lang.reflect.Method method = MainFrame.class.getDeclaredMethod(methodName);
			method.setAccessible(true);
			method.invoke(frame);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("could not invoke " + methodName, ex);
		}
	}

	private static final class FailingSudokuSolver extends SudokuSolver {
		@Override public void doStep(Sudoku2 board, SolutionStep step) {
			Candidate deletion = step.getCandidatesToDelete().get(0);
			board.delCandidate(deletion.getIndex(), deletion.getValue());
			throw new AssertionError("intentional reasoning apply failure");
		}
	}

	private static UserChain findMatchableUserChain(List<SolutionStep> steps, Sudoku2 board) {
		for (SolutionStep step : steps) {
			UserChain authored = authoredChainFor(step);
			if (authored != null
					&& NativeReasoningMatcher.matchChain(steps, board, authored) != null) {
				return authored;
			}
		}
		return null;
	}

	private static UserChain authoredChainFor(SolutionStep step) {
		if (step == null || step.getChains().size() != 1) return null;
		SolutionType type = step.getType();
		boolean nativeLoop = type == SolutionType.CONTINUOUS_NICE_LOOP
				|| type == SolutionType.DISCONTINUOUS_NICE_LOOP;
		if (!nativeLoop && type != SolutionType.X_CHAIN && type != SolutionType.AIC) return null;
		Chain nativeChain = step.getChains().get(0);
		if (nativeChain == null || nativeChain.getChain() == null
				|| nativeChain.getEnd() <= nativeChain.getStart()) return null;
		java.util.ArrayList<UserChainNode> nodes = new java.util.ArrayList<UserChainNode>();
		for (int i = nativeChain.getStart(); i <= nativeChain.getEnd(); i++) {
			int entry = nativeChain.getChain()[i];
			if (entry <= 0 || Chain.getSNodeType(entry) != Chain.NORMAL_NODE) return null;
			nodes.add(new UserChainNode(Chain.getSCellIndex(entry), Chain.getSCandidate(entry)));
		}
		UserChainNode first = nodes.get(0);
		UserChainNode last = nodes.get(nodes.size() - 1);
		boolean repeatedStart = sameNode(first, last);
		boolean closed = nativeLoop || (type == SolutionType.X_CHAIN && repeatedStart);
		if (closed && repeatedStart) nodes.remove(nodes.size() - 1);
		UserChain authored = new UserChain();
		authored.setClosed(closed);
		authored.getNodes().addAll(nodes);
		int nativeEdgeCount = nativeChain.getEnd() - nativeChain.getStart();
		for (int edge = 1; edge <= nativeEdgeCount; edge++) {
			authored.getStrongRelations().add(Boolean.valueOf(
					nativeChain.isStrong(nativeChain.getStart() + edge)));
		}
		if (closed && !repeatedStart) {
			authored.getStrongRelations().add(Boolean.valueOf(
					nativeChain.isStrong(nativeChain.getStart())));
		}
		return authored;
	}

	private static boolean sameNode(UserChainNode first, UserChainNode second) {
		return first.getCellIndex() == second.getCellIndex()
				&& first.getCandidate() == second.getCandidate();
	}

	private static SudokuSet findMatchableBoxFootprint(List<SolutionStep> steps, Sudoku2 board) {
		for (SolutionStep step : steps) {
			SudokuSet selected = new SudokuSet();
			for (Integer index : step.getIndices()) {
				if (index != null) selected.add(index.intValue());
			}
			NativeReasoningMatcher.Match match =
					NativeReasoningMatcher.matchBox(steps, board, selected);
			if (match != null) return selected;
		}
		return null;
	}

	private static void clickCell(SudokuPanel panel, int index) {
		int row = Sudoku2.getRow(index);
		int col = Sudoku2.getCol(index);
		int cellSize = Math.max(1, panel.getX(row, Math.min(8, col + 1)) - panel.getX(row, col));
		int x = panel.getX(row, col) + cellSize / 2;
		int y = panel.getY(row, col) + cellSize / 2;
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
	}

	private static void clickCandidate(SudokuPanel panel, UserChainNode node, int cellSize) {
		int candidate = node.getCandidate();
		double third = cellSize / 3.0;
		int row = Sudoku2.getRow(node.getCellIndex());
		int col = Sudoku2.getCol(node.getCellIndex());
		int x = (int) Math.round(panel.getX(row, col)
				+ ((candidate - 1) % 3) * third + third / 2.0);
		int y = (int) Math.round(panel.getY(row, col)
				+ ((candidate - 1) / 3) * third + third / 2.0);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
	}

	private static MouseEvent mouse(SudokuPanel panel, int id, int x, int y) {
		return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
				MouseEvent.BUTTON1);
	}

	private static void press(SudokuPanel panel, int keyCode) {
		press(panel, keyCode, 0);
	}

	private static void press(SudokuPanel panel, int keyCode, int modifiers) {
		KeyEvent pressed = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		KeyEvent released = new KeyEvent(panel, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) listener.keyPressed(pressed);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) listener.keyReleased(released);
	}

	private static <T> T onEdt(final Callable<T> callable) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) return callable.call();
		final AtomicReference<T> result = new AtomicReference<T>();
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override public void run() {
				try { result.set(callable.call()); }
				catch (Throwable ex) { failure.set(ex); }
			}
		});
		if (failure.get() != null) throw new AssertionError(failure.get());
		return result.get();
	}

	private static void waitFor(Callable<Boolean> condition, long timeoutMillis,
			String failureMessage) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (condition.call().booleanValue()) return;
			Thread.sleep(20L);
		}
		throw new AssertionError(failureMessage);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static boolean sameImage(java.awt.image.BufferedImage first,
			java.awt.image.BufferedImage second) {
		if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) return false;
		for (int y = 0; y < first.getHeight(); y++) {
			for (int x = 0; x < first.getWidth(); x++) {
				if (first.getRGB(x, y) != second.getRGB(x, y)) return false;
			}
		}
		return true;
	}

	private static java.awt.image.BufferedImage paintPanel(SudokuPanel panel) {
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
				panel.getWidth(), panel.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
		return image;
	}
}
