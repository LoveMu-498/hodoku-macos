/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Current manually recorded implications over complete candidate identities.
 * The caller may supply active ink or a saved full list. Archived proof ink is
 * excluded defensively: it is display evidence, never another premise. These
 * results do not certify a Sudoku solver proof.
 */
final class DoodleDerivedBasis {
    private DoodleDerivedBasis() { }

    static final class Evidence {
        private final UserChainNode source;
        private final int sourceGroup;
        private final int branchGroupMask;

        private Evidence(UserChainNode source, int sourceGroup, int branchGroupMask) {
            this.source = source.copy();
            this.sourceGroup = sourceGroup;
            this.branchGroupMask = branchGroupMask;
        }
        UserChainNode getSource() { return source.copy(); }
        int getSourceGroup() { return sourceGroup; }
        int getBranchGroupMask() { return branchGroupMask; }
        int getProofGroupMask() { return branchGroupMask | 1 << sourceGroup; }
        boolean usesBranchGroup(int group) {
            return group >= 0 && group < 6 && (branchGroupMask & 1 << group) != 0;
        }
        private String key() { return sourceGroup + ":" + branchGroupMask + ":" + source.key(); }
    }

    static final class CircleReduction {
        private final UserChainNode result;
        private final List<Evidence> evidence = new ArrayList<Evidence>();

        private CircleReduction(UserChainNode result) { this.result = result.copy(); }
        UserChainNode getResult() { return result.copy(); }
        List<Evidence> getEvidence() { return Collections.unmodifiableList(evidence); }
        Evidence evidenceForGroup(int group) {
            for (Evidence entry : evidence) if (entry.usesBranchGroup(group)) return entry;
            return null;
        }
        private void addEvidence(Evidence entry) { addDistinctEvidence(evidence, entry); }
    }

    static final class WeakRelation {
        private final UserChainNode left;
        private final UserChainNode right;
        private final List<Evidence> evidence = new ArrayList<Evidence>();

        private WeakRelation(UserChainNode left, UserChainNode right) {
            if (left.key().compareTo(right.key()) <= 0) {
                this.left = left.copy(); this.right = right.copy();
            } else {
                this.left = right.copy(); this.right = left.copy();
            }
        }
        UserChainNode getLeft() { return left.copy(); }
        UserChainNode getRight() { return right.copy(); }
        List<Evidence> getEvidence() { return Collections.unmodifiableList(evidence); }
        private String key() { return left.key() + ":" + right.key(); }
        private void addEvidence(Evidence entry) { addDistinctEvidence(evidence, entry); }
    }

    static final class Analysis {
        private final List<CircleReduction> circleReductions;
        private final List<WeakRelation> weakRelations;

        private Analysis(List<CircleReduction> circleReductions, List<WeakRelation> weakRelations) {
            this.circleReductions = Collections.unmodifiableList(circleReductions);
            this.weakRelations = Collections.unmodifiableList(weakRelations);
        }
        List<CircleReduction> getCircleReductions() { return circleReductions; }
        List<WeakRelation> getWeakRelations() { return weakRelations; }
        /** A partial endpoint never inherits a complete group's weak relation. */
        List<UserChainNode> weakTargets(UserChainNode origin) {
            Map<String, UserChainNode> targets = new LinkedHashMap<String, UserChainNode>();
            if (origin == null || !origin.validShape()) return new ArrayList<UserChainNode>();
            String key = origin.key();
            for (WeakRelation relation : weakRelations) {
                if (key.equals(relation.left.key())) targets.put(relation.right.key(), relation.right.copy());
                if (key.equals(relation.right.key())) targets.put(relation.left.key(), relation.left.copy());
            }
            return new ArrayList<UserChainNode>(targets.values());
        }
    }

    private static final class Group {
        final Set<Integer> starts = new TreeSet<Integer>();
        final Set<Integer> falseConclusions = new TreeSet<Integer>();
        final Set<Integer> trueConclusions = new TreeSet<Integer>();
        int startKind;
        boolean valid = true;
        boolean usable() { return valid && !starts.isEmpty(); }
    }

    private static final class NegativeBranch {
        final Set<Integer> representatives;
        final Set<Integer> coverage;
        final int group;

        NegativeBranch(Set<Integer> representatives, Set<Integer> coverage, int group) {
            this.representatives = representatives; this.coverage = coverage; this.group = group;
        }
    }

    static Analysis analyze(Sudoku2 board, Iterable<DoodleStroke> activeMarks) {
        Map<String, CircleReduction> reductions = new LinkedHashMap<String, CircleReduction>();
        Map<String, WeakRelation> weak = new LinkedHashMap<String, WeakRelation>();
        if (board != null && activeMarks != null) {
            Group[] groups = collect(board, activeMarks);
            for (int sourceGroup = 0; sourceGroup < groups.length; sourceGroup++) {
                Group source = groups[sourceGroup];
                if (source == null || !source.usable()
                        || source.startKind != DoodleStroke.MARK_TRUE_CIRCLE) continue;
                List<List<NegativeBranch>> branches = negativeBranches(source, sourceGroup, groups);
                reduce(source, sourceGroup, branches, 0, new TreeSet<Integer>(),
                        new TreeSet<Integer>(), 0, reductions, new HashSet<String>());
                weakRelations(source, sourceGroup, groups, weak);
            }
        }
        List<CircleReduction> result = new ArrayList<CircleReduction>(reductions.values());
        Collections.sort(result, new Comparator<CircleReduction>() {
            @Override public int compare(CircleReduction a, CircleReduction b) {
                int count = Integer.compare(a.result.atoms().length, b.result.atoms().length);
                return count != 0 ? count : a.result.key().compareTo(b.result.key());
            }
        });
        return new Analysis(result, new ArrayList<WeakRelation>(weak.values()));
    }

    private static Group[] collect(Sudoku2 board, Iterable<DoodleStroke> marks) {
        Group[] groups = new Group[6];
        for (DoodleStroke mark : marks) {
            if (mark == null || mark.isProofOnly() || mark.isRefutationResult() || mark.getThoughtGroup() < 0
                    || mark.getThoughtGroup() >= groups.length) continue;
            int group = mark.getThoughtGroup();
            if (groups[group] == null) groups[group] = new Group();
            Group entry = groups[group];
            boolean current = mark.isStandardCandidateMark()
                    && board.getValue(mark.getAnchorCell()) == 0
                    && board.isCandidate(mark.getAnchorCell(), mark.getAnchorDigit());
            if (mark.isHypothesisStart()) {
                if (!current) { entry.valid = false; continue; }
                int kind = mark.getCandidateMarkKind();
                if (entry.startKind != 0 && entry.startKind != kind) entry.valid = false;
                entry.startKind = kind;
                entry.starts.add(atom(mark));
            } else if (current && mark.getCandidateMarkKind() == DoodleStroke.MARK_FALSE_CROSS) {
                entry.falseConclusions.add(atom(mark));
            } else if (current && mark.getCandidateMarkKind() == DoodleStroke.MARK_TRUE_CIRCLE) {
                entry.trueConclusions.add(atom(mark));
            }
        }
        return groups;
    }

    private static List<List<NegativeBranch>> negativeBranches(Group source, int sourceGroup, Group[] groups) {
        List<List<NegativeBranch>> result = new ArrayList<List<NegativeBranch>>();
        for (int group = 0; group < groups.length; group++) {
            Group branch = groups[group];
            if (group == sourceGroup || branch == null || !branch.usable()) continue;
            List<NegativeBranch> alternatives = new ArrayList<NegativeBranch>();
            if (branch.startKind == DoodleStroke.MARK_FALSE_CROSS) {
                Set<Integer> coverage = intersection(source.starts, branch.falseConclusions);
                if (!coverage.isEmpty()) alternatives.add(new NegativeBranch(branch.starts, coverage, group));
            } else if (branch.startKind == DoodleStroke.MARK_TRUE_CIRCLE
                    && source.starts.containsAll(branch.starts)) {
                // (OR G) => X is exactly the contrapositive !X => all G false.
                // Different true conclusions are alternative representatives for
                // the same coverage; choosing several only adds redundant atoms.
                for (int target : branch.trueConclusions) {
                    Set<Integer> representative = new TreeSet<Integer>(); representative.add(target);
                    alternatives.add(new NegativeBranch(representative, branch.starts, group));
                }
            }
            if (!alternatives.isEmpty()) result.add(alternatives);
        }
        return result;
    }

    private static void reduce(Group source, int sourceGroup, List<List<NegativeBranch>> branches,
            int index, Set<Integer> representatives, Set<Integer> coverage, int branchMask,
            Map<String, CircleReduction> targets, Set<String> visited) {
        // Representatives only accumulate. Once they cannot reduce the source,
        // no later coverage can make this combination useful.
        if (representatives.size() >= source.starts.size()) return;
        // A selected group has one fixed coverage. Exchanging equivalent true
        // targets between selected groups can reach the same complete state;
        // its result and full group evidence are identical, so visit it once.
        if (!visited.add(index + ":" + branchMask + ":" + representatives)) return;
        if (index == branches.size()) {
            if (branchMask == 0) return;
            Set<Integer> result = new TreeSet<Integer>(source.starts);
            result.removeAll(coverage); result.addAll(representatives);
            if (result.isEmpty() || result.size() >= source.starts.size()) return;
            UserChainNode node = node(result);
            CircleReduction reduction = targets.get(node.key());
            if (reduction == null) { reduction = new CircleReduction(node); targets.put(node.key(), reduction); }
            reduction.addEvidence(new Evidence(node(source.starts), sourceGroup, branchMask));
            return;
        }
        reduce(source, sourceGroup, branches, index + 1, representatives, coverage, branchMask, targets, visited);
        for (NegativeBranch branch : branches.get(index)) {
            Set<Integer> nextRepresentatives = new TreeSet<Integer>(representatives);
            nextRepresentatives.addAll(branch.representatives);
            Set<Integer> nextCoverage = new TreeSet<Integer>(coverage); nextCoverage.addAll(branch.coverage);
            reduce(source, sourceGroup, branches, index + 1, nextRepresentatives, nextCoverage,
                    branchMask | 1 << branch.group, targets, visited);
        }
    }

    private static void weakRelations(Group source, int sourceGroup, Group[] groups,
            Map<String, WeakRelation> targets) {
        for (int leftGroup = 0; leftGroup < groups.length; leftGroup++) {
            Group left = groups[leftGroup];
            if (!trueBranch(left, leftGroup, sourceGroup)) continue;
            for (int rightGroup = leftGroup + 1; rightGroup < groups.length; rightGroup++) {
                Group right = groups[rightGroup];
                if (!trueBranch(right, rightGroup, sourceGroup)) continue;
                Set<Integer> coverage = new TreeSet<Integer>(left.falseConclusions);
                coverage.addAll(right.falseConclusions);
                if (!coverage.containsAll(source.starts)) continue;
                UserChainNode leftNode = node(left.starts), rightNode = node(right.starts);
                if (leftNode.key().equals(rightNode.key())) continue;
                WeakRelation relation = new WeakRelation(leftNode, rightNode);
                WeakRelation existing = targets.get(relation.key());
                if (existing == null) { existing = relation; targets.put(relation.key(), existing); }
                existing.addEvidence(new Evidence(node(source.starts), sourceGroup,
                        1 << leftGroup | 1 << rightGroup));
            }
        }
    }

    private static boolean trueBranch(Group group, int index, int sourceGroup) {
        return index != sourceGroup && group != null && group.usable()
                && group.startKind == DoodleStroke.MARK_TRUE_CIRCLE
                && !group.falseConclusions.isEmpty();
    }
    private static Set<Integer> intersection(Set<Integer> a, Set<Integer> b) {
        Set<Integer> result = new TreeSet<Integer>(a); result.retainAll(b); return result;
    }
    private static int atom(DoodleStroke mark) { return mark.getAnchorCell() * 10 + mark.getAnchorDigit(); }
    private static UserChainNode node(Set<Integer> atoms) {
        int[] members = new int[atoms.size()]; int index = 0;
        for (int atom : atoms) members[index++] = atom;
        return UserChainNode.fromAtoms(members, null);
    }
    private static void addDistinctEvidence(List<Evidence> entries, Evidence entry) {
        for (Evidence existing : entries) if (existing.key().equals(entry.key())) return;
        entries.add(entry);
    }
}
