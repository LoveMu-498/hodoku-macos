package sudoku;

import java.util.Arrays;

/** Whole authored path versus native single-chain structure, including groups. */
public final class CompletePathMatchProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static UserChain path(boolean closed, UserChainNode[] nodes, boolean... strong) {
        UserChain result = new UserChain();
        result.setClosed(closed);
        result.getNodes().addAll(Arrays.asList(nodes));
        for (boolean link : strong) result.getStrongRelations().add(link);
        return result;
    }

    private static SolutionStep nativeStep(SolutionType type, UserChainNode[] nodes,
            boolean[] strong, boolean repeatStart) {
        int size = nodes.length + (repeatStart ? 1 : 0);
        int[] entries = new int[size];
        for (int i = 0; i < nodes.length; i++) entries[i] = nodes[i].encoded(i > 0 && strong[i - 1]);
        if (repeatStart) entries[size - 1] = nodes[0].encoded(strong[strong.length - 1]);
        else if (strong.length == nodes.length) entries[0] = nodes[0].encoded(strong[strong.length - 1]);
        SolutionStep step = new SolutionStep(type);
        step.addChain(new Chain(0, size - 1, entries));
        step.addCandidateToDelete(40, 1);
        return step;
    }

    public static void main(String[] args) {
        Sudoku2 board = GroupedChainProbe.blank();
        for (int cell = 2; cell < 9; cell++) board.delCandidate(cell, 1);
        UserChainNode a = new UserChainNode(0, 1), b = new UserChainNode(1, 1),
                c = new UserChainNode(10, 1);
        UserChain authored = path(false, new UserChainNode[]{a, b, c}, true, false);
        String key = NativeReasoningMatcher.authoredCompletePath(authored, board);
        check(key != null, "valid simple chain cannot be compared");
        SolutionStep step = nativeStep(SolutionType.X_CHAIN,
                new UserChainNode[]{a, b, c}, new boolean[]{true, false}, false);
        check(NativeReasoningMatcher.matchesCompletePath(key, step), "exact simple path missed");
        check(key.equals(NativeReasoningMatcher.authoredCompletePath(
                path(false, new UserChainNode[]{c, b, a}, false, true), board)), "reversal changed path");
        check(!NativeReasoningMatcher.matchesCompletePath(key, nativeStep(SolutionType.X_CHAIN,
                new UserChainNode[]{a, b, c}, new boolean[]{false, false}, false)), "strong/weak mismatch accepted");
        check(!NativeReasoningMatcher.matchesCompletePath(key, nativeStep(SolutionType.X_CHAIN,
                new UserChainNode[]{a, c, b}, new boolean[]{true, false}, false)), "node order mismatch accepted");

        UserChainNode group = UserChainNode.fromAtoms(new int[]{2, 12}, null);
        UserChainNode single = new UserChainNode(9, 2);
        String groupKey = NativeReasoningMatcher.authoredCompletePath(
                path(false, new UserChainNode[]{group, single}, false), board);
        check(groupKey != null, "native-encodable group rejected");
        check(NativeReasoningMatcher.matchesCompletePath(groupKey, nativeStep(SolutionType.GROUPED_AIC,
                new UserChainNode[]{group, single}, new boolean[]{false}, false)), "exact group path missed");
        check(!NativeReasoningMatcher.matchesCompletePath(groupKey, nativeStep(SolutionType.GROUPED_AIC,
                new UserChainNode[]{new UserChainNode(0, 2), single}, new boolean[]{false}, false)),
                "partial group passed as full group");

        UserChainNode third = new UserChainNode(1, 2);
        UserChain loop = path(true, new UserChainNode[]{group, single, third}, false, false, false);
        String loopKey = NativeReasoningMatcher.authoredCompletePath(loop, board);
        check(loopKey != null, "closed grouped path unavailable");
        check(NativeReasoningMatcher.matchesCompletePath(loopKey,
                nativeStep(SolutionType.GROUPED_CONTINUOUS_NICE_LOOP,
                        new UserChainNode[]{single, third, group}, new boolean[]{false, false, false}, true)),
                "closed-loop rotation missed");
        check(NativeReasoningMatcher.authoredCompletePath(
                path(false, new UserChainNode[]{UserChainNode.fromAtoms(new int[]{2, 13}, null), single}, false), board) == null,
                "mixed-digit group should be unavailable");
        System.out.println("Exact ordinary/grouped paths, reversal, loop rotation, strong/weak and member differences passed");
    }
}
