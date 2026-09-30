/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.util.*;

/** Structural counts only: a repeated relation is not a validated deduction. */
final class ChainJunctionSummary {
    final List<UserChainNode> strong = new ArrayList<UserChainNode>();
    final List<UserChainNode> weak = new ArrayList<UserChainNode>();
    final boolean closed;
    final UserChainValidator.Problem problem;

    private ChainJunctionSummary(boolean closed, UserChainValidator.Problem problem) {
        this.closed = closed;
        this.problem = problem;
    }

    /** First segment is the active chain, or the most recently completed chain. */
    static ChainJunctionSummary from(List<UserChain> segments) {
        if (segments.isEmpty()) return new ChainJunctionSummary(false, UserChainValidator.Problem.NONE);
        Set<String> identities = new HashSet<String>();
        List<UserChain> connected = new ArrayList<UserChain>();
        Set<UserChain> included = Collections.newSetFromMap(new IdentityHashMap<UserChain, Boolean>());
        UserChain seed = segments.get(0);
        included.add(seed); connected.add(seed);
        for (UserChainNode node : seed.getNodes()) identities.add(node.key());
        boolean changed;
        do {
            changed = false;
            for (UserChain segment : segments) {
                if (included.contains(segment)) continue;
                boolean touches = false;
                for (UserChainNode node : segment.getNodes()) if (identities.contains(node.key())) { touches = true; break; }
                if (!touches) continue;
                included.add(segment); connected.add(segment); changed = true;
                for (UserChainNode node : segment.getNodes()) identities.add(node.key());
            }
        } while (changed);
        UserChainAssembly.Result assembled = UserChainAssembly.assemble(connected);
        UserChain chain = assembled.chain;
        ChainJunctionSummary result = new ChainJunctionSummary(chain != null && chain.isClosed(), assembled.problem);
        if (chain == null) return result;
        int n = chain.getNodes().size();
        for (int i = chain.isClosed() ? 0 : 1; i < (chain.isClosed() ? n : n - 1); i++) {
            boolean before = chain.getStrongRelations().get((i + n - 1) % n);
            boolean after = chain.getStrongRelations().get(i);
            if (before == after) (before ? result.strong : result.weak).add(chain.getNodes().get(i).copy());
        }
        return result;
    }

    boolean available() { return problem == UserChainValidator.Problem.NONE; }

    String description() {
        if (!available()) return "当前连通结构无法按单链统计（分支或关系冲突）；不代表推理结论";
        return "当前连通链：双强 " + strong.size() + " 处，双弱 " + weak.size() + " 处；"
                + (closed ? "已闭环（含首尾交接）" : "尚未闭环")
                + "。仅统计结构，未验证不连续环结论；悬停圈出接点（实圈双强，虚圈双弱）。";
    }
}
