/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.transpiler.context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTBreak;
import org.monflabs.galtajs.node.control.ASTCase;
import org.monflabs.galtajs.node.control.ASTContinue;
import org.monflabs.galtajs.node.control.ASTDoWhile;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTForIn;
import org.monflabs.galtajs.node.control.ASTForOf;
import org.monflabs.galtajs.node.control.ASTFor_;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.ASTSwitch;
import org.monflabs.galtajs.node.control.ASTWhile;
import org.monflabs.galtajs.node.control.ILabeledNode;

/**
 * Keeps the Java methods generated for a JavaScript function small enough to be
 * compiled by the JIT, which never compiles a method larger than 8000 bytes of
 * bytecode: a large function would otherwise run interpreted forever.
 * <p>
 * Before a function body is emitted, its AST is weighed (one unit per node, a
 * nested function counting for little as it is emitted as a method of its own)
 * and, when it exceeds the budget, some of its statement lists - the statements of
 * a block or of a switch case, or a run of consecutive statements - are chosen to
 * be emitted as regions: anonymous classes run at once, whose run() method javac
 * compiles separately. The JavaScript variables live in arrays, so a region needs no
 * capture beyond those final arrays. A region returns {@code NORMAL}, the value
 * of a JavaScript {@code return}, or a jump marker for a {@code break} or
 * {@code continue} that leaves it, which the caller then performs.
 */
public class TranspilerMethodSplitter {

	/**
	 * The size, in AST nodes, a generated method should not exceed (about 15 bytes
	 * of bytecode per node: a margin below the JIT's 8000 bytes). It can be set
	 * with the galtajs.transpiler.methodBudget system property - a tiny value
	 * splits every statement list, which the tests use to exercise the regions.
	 */
	public static final int DEFAULT_BUDGET = Integer.getInteger("galtajs.transpiler.methodBudget", 400);

	// The weight of a region call left in its caller, and of a nested function
	private static final int REGION_CALL_WEIGHT = 12;
	private static final int NESTED_FUNCTION_WEIGHT = 3;
	// The smallest run of statements worth a region of its own
	private static final int MIN_REGION_WEIGHT = 8;
	// The jumps a region can report (see JSTranspiledRegion.JUMPS)
	public static final int MAX_JUMPS = 64;

	/**
	 * A run of statements of a list, emitted as a region.
	 */
	public static final class Region {
		private final int from;
		private final int to;
		// The break/continue statements of the region that leave it, by jump index
		private final Map<ASTNode,Integer> escapes;

		Region(int from, int to, Map<ASTNode,Integer> escapes) {
			this.from = from;
			this.to = to;
			this.escapes = escapes;
		}
		public int getFrom() {
			return from;
		}
		public int getTo() {
			return to;
		}
		public Map<ASTNode,Integer> getEscapes() {
			return escapes;
		}
	}

	private final int budget;
	// The regions of each statement list, by the node owning the list
	private final Map<ASTNode,List<Region>> regions = new IdentityHashMap<>();
	// The statement lists emitted whole as a region (their own runs, if any, are
	// regions inside it)
	private final Map<ASTNode,Region> wholeLists = new IdentityHashMap<>();

	private TranspilerMethodSplitter(int budget) {
		this.budget = budget;
	}

	/**
	 * Plans the regions of a function body, or returns null when it needs none.
	 */
	public static TranspilerMethodSplitter plan(ASTFunction function, int budget) {
		if(budget<=0) {
			return null;
		}
		TranspilerMethodSplitter s = new TranspilerMethodSplitter(budget);
		ASTNode[] statements = function.getStatements();
		if(statements==null) {
			return null;
		}
		s.reduceList(function, statements, 0, statements.length, s.budget);
		return s.regions.isEmpty() && s.wholeLists.isEmpty() ? null : s;
	}

	/**
	 * The runs of statements of a list emitted as regions, in order, or null.
	 */
	public List<Region> getRegions(ASTNode owner) {
		return regions.get(owner);
	}

	/**
	 * The region of a statement list emitted whole as a region, or null.
	 */
	public Region getWholeListRegion(ASTNode owner) {
		return wholeLists.get(owner);
	}

	/**
	 * Whether the last statement of a list is emitted in a region (the method then
	 * still needs its own closing return).
	 */
	public boolean endsInRegion(ASTNode owner, int length) {
		List<Region> l = regions.get(owner);
		return l!=null && !l.isEmpty() && l.get(l.size()-1).to==length;
	}

	//
	// Planning
	//

	// Reduces statements[from,to) - part of the method being sized - to at most
	// `limit` units, choosing regions; returns the remaining weight in the method
	private int reduceList(ASTNode owner, ASTNode[] statements, int from, int to, int limit) {
		int n = to-from;
		int[] w = new int[n];
		int total = 0;
		for(int i=0; i<n; i++) {
			w[i] = reduceStatement(statements[from+i], limit);
			total += w[i];
		}
		if(total<=limit || !canSplitList(owner)) {
			return total;
		}
		// Group consecutive statements in runs of at most `limit` units, and move
		// the heaviest runs to regions until the method fits
		List<int[]> runs = new ArrayList<>();
		int start = 0, acc = 0;
		for(int i=0; i<n; i++) {
			if(acc>0 && acc+w[i]>limit) {
				runs.add(new int[] {start, i, acc});
				start = i;
				acc = 0;
			}
			acc += w[i];
		}
		runs.add(new int[] {start, n, acc});
		runs.sort((a,b) -> Integer.compare(b[2], a[2]));
		List<Region> chosen = new ArrayList<>();
		for(int[] run: runs) {
			if(total<=limit || run[2]<MIN_REGION_WEIGHT) {
				break;
			}
			Map<ASTNode,Integer> escapes = findEscapingJumps(statements, from+run[0], from+run[1]);
			if(escapes==null) {
				continue;
			}
			// Its statements were each reduced to the budget, and the run fits in it
			chosen.add(new Region(from+run[0], from+run[1], escapes));
			total += REGION_CALL_WEIGHT - run[2];
		}
		if(!chosen.isEmpty()) {
			List<Region> l = regions.computeIfAbsent(owner, k -> new ArrayList<>());
			l.addAll(chosen);
			l.sort((a,b) -> Integer.compare(a.from, b.from));
		}
		return total;
	}

	// Reduces one statement to at most `limit` units by moving its heaviest nested
	// statement lists to regions; returns its remaining weight
	private int reduceStatement(ASTNode node, int limit) {
		if(node==null) {
			return 0;
		}
		if(node instanceof ASTFunction) {
			return NESTED_FUNCTION_WEIGHT;
		}
		// The nested lists (or the statement itself, a block), each first reduced
		// to fit the method
		List<ASTNode> lists = new ArrayList<>();
		int own;
		if(isList(node)) {
			lists.add(node);
			own = 0;
		} else {
			collectLists(node, lists);
			own = ownWeight(node);
		}
		int total = own;
		int[] lw = new int[lists.size()];
		for(int i=0; i<lists.size(); i++) {
			ASTNode l = lists.get(i);
			ASTNode[] st = listStatements(l);
			lw[i] = reduceList(l, st, 0, st.length, limit);
			total += lw[i];
		}
		if(total<=limit) {
			return total;
		}
		// Too heavy for the method: move whole nested lists, heaviest first (a list
		// keeps the runs it already has in regions, inside its own)
		Integer[] order = new Integer[lists.size()];
		for(int i=0; i<order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a,b) -> Integer.compare(lw[b], lw[a]));
		for(int i: order) {
			if(total<=limit || lw[i]<MIN_REGION_WEIGHT) {
				break;
			}
			ASTNode l = lists.get(i);
			if(!canSplitList(l)) {
				continue;
			}
			ASTNode[] st = listStatements(l);
			Map<ASTNode,Integer> escapes = findEscapingJumps(st, 0, st.length);
			if(escapes==null) {
				continue;
			}
			wholeLists.put(l, new Region(0, st.length, escapes));
			total += REGION_CALL_WEIGHT - lw[i];
		}
		return total;
	}

	// The weight of a node without its nested statement lists
	private static int ownWeight(ASTNode node) {
		if(node==null) {
			return 0;
		}
		if(node instanceof ASTFunction) {
			return NESTED_FUNCTION_WEIGHT;
		}
		if(isList(node)) {
			return 0;
		}
		int w = 1;
		for(int i=0; i<node.getChildCount(); i++) {
			w += ownWeight(node.getChild(i));
		}
		return w;
	}

	// The statement lists directly nested in a node (not inside a nested list)
	private static void collectLists(ASTNode node, List<ASTNode> lists) {
		for(int i=0; i<node.getChildCount(); i++) {
			ASTNode c = node.getChild(i);
			if(c==null || c instanceof ASTFunction) {
				continue;
			}
			if(isList(c)) {
				lists.add(c);
			} else {
				collectLists(c, lists);
			}
		}
	}

	private static boolean isList(ASTNode node) {
		return node instanceof ASTBlock || node instanceof ASTCase;
	}

	private static ASTNode[] listStatements(ASTNode node) {
		ASTNode[] st = node instanceof ASTBlock b ? b.getStatements() : node instanceof ASTCase c ? c.getStatements() : null;
		return st!=null ? st : new ASTNode[0];
	}

	// A list wrapped in a using-disposal boundary keeps its statements together
	private static boolean canSplitList(ASTNode owner) {
		if(owner instanceof ASTVarContainer vc && ASTBlock.hasUsingDeclarations(vc)) {
			return false;
		}
		return true;
	}

	//
	// Jumps leaving a region
	//

	// The break/continue statements of statements[from,to) that leave the run, by
	// jump index - or null when there are too many to report
	private static Map<ASTNode,Integer> findEscapingJumps(ASTNode[] statements, int from, int to) {
		Map<ASTNode,Integer> escapes = new IdentityHashMap<>();
		for(int i=from; i<to; i++) {
			collectEscapes(statements[i], 0, 0, new HashSet<>(), escapes);
			if(escapes.size()>MAX_JUMPS) {
				return null;
			}
		}
		return escapes;
	}

	// loops/breakables: the loops and the loops+switches enclosing the node inside
	// the region; labels: the labels declared inside the region
	private static void collectEscapes(ASTNode node, int loops, int breakables, Set<String> labels, Map<ASTNode,Integer> escapes) {
		if(node==null || node instanceof ASTFunction) {
			return;
		}
		if(node instanceof ASTBreak br) {
			boolean leaves = br.getLabel()==null || br.getLabel().isEmpty() ? breakables==0 : !labels.contains(br.getLabel());
			if(leaves) {
				escapes.put(node, escapes.size());
			}
			return;
		}
		if(node instanceof ASTContinue co) {
			boolean leaves = co.getLabel()==null || co.getLabel().isEmpty() ? loops==0 : !labels.contains(co.getLabel());
			if(leaves) {
				escapes.put(node, escapes.size());
			}
			return;
		}
		boolean loop = node instanceof ASTFor || node instanceof ASTFor_ || node instanceof ASTForIn || node instanceof ASTForOf
				|| node instanceof ASTWhile || node instanceof ASTDoWhile;
		boolean breakable = loop || node instanceof ASTSwitch;
		String label = node instanceof ILabeledNode ln ? ln.getLabel() : null;
		Set<String> inner = labels;
		if(label!=null && !label.isEmpty()) {
			inner = new HashSet<>(labels);
			inner.add(label);
		}
		for(int i=0; i<node.getChildCount(); i++) {
			collectEscapes(node.getChild(i), loop ? loops+1 : loops, breakable ? breakables+1 : breakables, inner, escapes);
		}
	}
}
