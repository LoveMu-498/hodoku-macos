package sudoku;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import static sudoku.AnnotationMenuKeyProbe.*;

/** Real Tab search keeps only the whole authored native path in the chain source. */
public final class TabExactPathInteractionProbe {
    private static Object field(Object owner, String name) throws Exception {
        Field source = owner.getClass().getDeclaredField(name);
        source.setAccessible(true);
        return source.get(owner);
    }

    public static void main(String[] args) throws Exception {
        try {
            Sudoku2 board = new Sudoku2();
            board.setSudoku(CurrentReasoningProbe.PUZZLE);
            List<SolutionStep> nativeChains = new TechniqueStepCatalog().findMatchingSteps(board,
                    step -> step.getType() == SolutionType.X_CHAIN && step.getChains().size() == 1);
            UserChain authored = null;
            for (SolutionStep step : nativeChains) {
                UserChain candidate = CurrentReasoningProbe.fromNative(step.getChains().get(0));
                if (candidate != null && NativeReasoningMatcher.authoredCompletePath(candidate, board) != null) {
                    authored = candidate;
                    break;
                }
            }
            check(authored != null, "no native X-Chain fixture with comparable drawn path");
            final UserChain source = authored;
            edt(() -> {
                f = new MainFrame(null); p = f.getSudokuPanel();
                p.setSudoku(CurrentReasoningProbe.PUZZLE); p.setShowCandidates(true);
                p.resetShowHintCellValues(); p.clearAllCellSelection();
                source.setSourceId(1001L);
                @SuppressWarnings("unchecked")
                List<UserChain> chains = (List<UserChain>) field(p, "userChains");
                chains.add(source);
                f.setVisible(true); f.showCurrentReasoning(true);
                return null;
            });
            AnnotationReasoningScopeProbe.settle();
            final SolutionStep[] chosen = new SolutionStep[1];
            edt(() -> {
                Map<SolutionStep,Integer> matches = AnnotationReasoningScopeProbe.results();
                check(!matches.isEmpty(), "Tab omitted a native proof of the drawn whole path");
                String expected = NativeReasoningMatcher.authoredCompletePath(
                        UserChainAssembly.assemble(p.currentReasoningChains()).chain, p.getSudoku());
                for (Map.Entry<SolutionStep,Integer> entry : matches.entrySet()) {
                    check(entry.getValue() == 2, "chain-only Tab included a non-path source");
                    check(NativeReasoningMatcher.matchesCompletePath(expected, entry.getKey()),
                            "Tab included a candidate-overlap proof with another path");
                    if (chosen[0] == null) chosen[0] = entry.getKey();
                }
                String before = TechniqueStepCatalog.createSignature(p.getSudoku());
                menu().confirm(chosen[0], 0, matches.size());
                check(p.showSelectedReasoningHint(2), "selected full-path step did not preview");
                check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),
                        "Tab preview changed puzzle");
                Object proposal = field(p, "reasoningProposal");
                check(proposal != null && Boolean.TRUE.equals(field(proposal, "authoredChainDisplay"))
                        && Boolean.FALSE.equals(field(proposal, "authoredChainProof")),
                        "native full-path preview did not retain the complete authored drawing");
                return null;
            });
            System.out.println("Tab exact native chain only; selected preview retains drawn chain and does not apply");
        } catch (Throwable failure) {
            failure.printStackTrace(); System.exit(1);
        } finally {
            if (f != null) edt(() -> { f.dispose(); return null; });
        }
        System.exit(0);
    }
}
