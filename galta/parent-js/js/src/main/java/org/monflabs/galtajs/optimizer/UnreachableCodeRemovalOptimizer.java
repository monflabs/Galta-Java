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
package org.monflabs.galtajs.optimizer;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.control.ASTSynchronized;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTBreak;
import org.monflabs.galtajs.node.control.ASTCase;
import org.monflabs.galtajs.node.control.ASTCatch;
import org.monflabs.galtajs.node.control.ASTContinue;
import org.monflabs.galtajs.node.control.ASTDoWhile;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTIf;
import org.monflabs.galtajs.node.control.ASTReturn;
import org.monflabs.galtajs.node.control.ASTSwitch;
import org.monflabs.galtajs.node.control.ASTThrow;
import org.monflabs.galtajs.node.control.ASTTry;
import org.monflabs.galtajs.node.control.ASTWhile;
import org.monflabs.galtajs.node.control.StatementList;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.util.StringUtil;

/**
 * Remove unreachable code.
 */
public class UnreachableCodeRemovalOptimizer extends NodeOptimizer {

	private static final String TERMINATES_FLOW = "unreachable.return";

	private final boolean deadCodeOnly;

	public UnreachableCodeRemovalOptimizer() {
		this(false);
	}

	public UnreachableCodeRemovalOptimizer(boolean deadCodeOnly) {
		this.deadCodeOnly = deadCodeOnly;
	}

	@Override
	public void optimize(JSOptimizerContext context, ASTNode node) {
		if(node==null) {
			return;
		}

		// Do the children first
		int count = node.getChildCount();
		for(int i=0; i<count; i++) {
			ASTNode c = node.getChild(i);
			optimize(context, c);
		}

		simplifyNode(context, node);
	}

	// Simplifies THIS node's own statement list (StatementList/ASTCase),
	// assuming its own descendant subtree has already been fully processed -
	// extracted so ConstantFoldingAndUnreachableCodeOptimizer can call it
	// directly once ConstantFoldingOptimizer's own per-child loop has
	// finished folding this same node's children, instead of doing a
	// second, separate full-tree pass just to reach this point.
	public void simplifyNode(JSOptimizerContext context, ASTNode node) {
		if(node instanceof StatementList sl) {
			removeUnreachable(context, node, sl.getStatements(), sl::setStatements);
		} else if(node instanceof ASTCase c) {
			removeUnreachable(context, node, c.getStatements(), c::setStatements);
		}
	}

	private void removeUnreachable(JSOptimizerContext context, ASTNode node, ASTNode[] statements, java.util.function.Consumer<ASTNode[]> setter) {
		boolean update = false;
		List<ASTNode> newStatements = new ArrayList<>();
		for(int j=0; j<statements.length; j++) {
			if(deadCodeOnly) {
				newStatements.add(statements[j]);
			} else {
				update |= processStatement(newStatements, statements[j]);
			}
		}

		boolean remove = false;
		for(Iterator<ASTNode> it=newStatements.iterator(); it.hasNext(); ) {
			ASTNode n = it.next();
			// import/export declarations are not reached by the flow: they
			// are linked before the module runs, so they are never dead code
			if(remove && !(ASTNode.skipTransparent(n) instanceof org.monflabs.galtajs.node.control.ASTImpExp)) {
				update = true;
				it.remove();
				continue;
			}
			if(remove) {
				continue;
			}
			remove = terminatesFlow(context, n);
		}

		if(update) {
			ScriptOptimizer so = context.getScriptOptimizer();
			PrintStream ps = so != null ? so.getTraceStream() : null;
			if(ps!=null) {
				ps.println("*** Unreachable code optimization");
				ps.println("* Old node");
				node.dump(ps);
				ps.println();
			}
			setter.accept(newStatements.toArray(new ASTNode[newStatements.size()]));
			if(ps!=null) {
				ps.println("* New node");
				node.dump(ps);
				ps.println();
			}
		}
	}

	private boolean terminatesFlow(JSOptimizerContext context, ASTNode node) {
		if(node==null) {
			return false;
		}
		Boolean ret = context.getNodeProperty(node, TERMINATES_FLOW);
		if(ret!=null) {
			return ret;
		}
		ret = calculateTerminatesFlow(context, node);
		context.setNodeProperty(node, TERMINATES_FLOW, ret);
		return ret;
	}

	private boolean calculateTerminatesFlow(JSOptimizerContext context, ASTNode node) {
		if(node instanceof ASTReturn) {
			return true;
		}
		if(node instanceof ASTThrow) {
			return true;
		}
		if(node instanceof ASTBreak) {
			return true;
		}
		if(node instanceof ASTContinue) {
			return true;
		}
		// synchronized(o){...} completes like its block (javac rejects code
		// after a synchronized block that always returns)
		if(node instanceof ASTSynchronized sy) {
			return terminatesFlow(context, sy.getBodyNode());
		}
		if(node instanceof ASTBlock bl) {
			String blockLabel = bl.getLabel();
			if(blockLabel != null && hasLabeledBreak(bl, blockLabel)) {
				return false;
			}
			ASTNode[] stats = bl.getStatements();
			for(int i=0; i<stats.length; i++) {
				if(terminatesFlow(context, stats[i])) {
					return true;
				}
			}
			return false;
		}
		if(node instanceof ASTIf n) {
			ASTNode thenNode = n.getThenNode();
			ASTNode elseNode = n.getElseNode();
			if(elseNode != null && terminatesFlow(context, thenNode) && terminatesFlow(context, elseNode)) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTSwitch sw) {
			ASTNode[] cases = sw.getCases();
			if(cases==null || cases.length==0) {
				return false;
			}
			boolean hasDefault = false;
			for(int i=0; i<cases.length; i++) {
				ASTCase c = (ASTCase)cases[i];
				if(c.getExprNode()==null) {
					hasDefault = true;
				}
				if(!caseTerminates(context, c, sw.getLabel())) {
					return false;
				}
			}
			return hasDefault;
		}
		if(node instanceof ASTTry tr) {
			ASTBlock finallyNode = tr.getFinallyNode();
			if(finallyNode != null && terminatesFlow(context, finallyNode)) {
				return true;
			}
			ASTBlock bodyNode = tr.getBodyNode();
			ASTCatch catchNode = tr.getCatchNode();
			if(catchNode == null) {
				return terminatesFlow(context, bodyNode);
			}
			return terminatesFlow(context, bodyNode) && terminatesFlow(context, catchNode.getBodyNode());
		}
		if(node instanceof ASTWhile wh) {
			if(isConstantTrue(wh.getTestNode()) && !hasBreakTargeting(wh.getBodyNode(), wh.getLabel())) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTDoWhile dw) {
			if(isConstantTrue(dw.getTestNode()) && !hasBreakTargeting(dw.getBodyNode(), dw.getLabel())) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTFor fr) {
			if((fr.getTestNode() == null || isConstantTrue(fr.getTestNode())) && !hasBreakTargeting(fr.getBodyNode(), fr.getLabel())) {
				return true;
			}
			return false;
		}
		return false;
	}

	private boolean caseTerminates(JSOptimizerContext context, ASTCase c, String switchLabel) {
		ASTNode[] stats = c.getStatements();
		if(stats==null || stats.length==0) {
			return false;
		}
		// A labeled break only leaves the enclosing flow when its target is outside the
		// switch: "break L" aimed at the switch itself, or at a labeled block inside the
		// case, just continues after that statement
		Set<String> innerLabels = new HashSet<>();
		if(StringUtil.isNotEmpty(switchLabel)) {
			innerLabels.add(switchLabel);
		}
		for(int i=0; i<stats.length; i++) {
			if(terminatesOuterFlow(context, stats[i], innerLabels)) {
				return true;
			}
		}
		return false;
	}

	private boolean terminatesOuterFlow(JSOptimizerContext context, ASTNode node, Set<String> innerLabels) {
		if(node instanceof ASTBreak br) {
			String label = br.getNodeString();
			return StringUtil.isNotEmpty(label) && !innerLabels.contains(label);
		}
		if(node instanceof ASTBlock bl) {
			if(StringUtil.isNotEmpty(bl.getLabel())) {
				innerLabels = new HashSet<>(innerLabels);
				innerLabels.add(bl.getLabel());
			}
			ASTNode[] stats = bl.getStatements();
			for(int i=0; i<stats.length; i++) {
				if(terminatesOuterFlow(context, stats[i], innerLabels)) {
					return true;
				}
			}
			return false;
		}
		if(node instanceof ASTIf n) {
			ASTNode thenNode = n.getThenNode();
			ASTNode elseNode = n.getElseNode();
			if(elseNode != null && terminatesOuterFlow(context, thenNode, innerLabels) && terminatesOuterFlow(context, elseNode, innerLabels)) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTReturn || node instanceof ASTThrow) {
			return true;
		}
		if(node instanceof ASTContinue) {
			return true;
		}
		if(node instanceof ASTSynchronized sy) {
			return terminatesOuterFlow(context, sy.getBodyNode(), innerLabels);
		}
		return false;
	}

	private boolean isConstantTrue(ASTNode node) {
		if(node instanceof ASTLiteral lit) {
			return toBoolean(lit.getValue());
		}
		return false;
	}

	private boolean hasBreakTargeting(ASTNode node, String loopLabel) {
		if(node == null) return false;
		if(node instanceof ASTBreak br) {
			String breakLabel = br.getNodeString();
			if(StringUtil.isEmpty(breakLabel)) {
				return true;
			}
			if(StringUtil.equals(breakLabel, loopLabel)) {
				return true;
			}
			return false;
		}
		if(node instanceof ASTWhile || node instanceof ASTDoWhile || node instanceof ASTFor || node instanceof ASTSwitch) {
			if(StringUtil.isNotEmpty(loopLabel)) {
				return hasLabeledBreak(node, loopLabel);
			}
			return false;
		}
		int count = node.getChildCount();
		for(int i = 0; i < count; i++) {
			if(hasBreakTargeting(node.getChild(i), loopLabel)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLabeledBreak(ASTNode node, String label) {
		if(node == null) return false;
		if(node instanceof ASTBreak br) {
			return StringUtil.equals(br.getNodeString(), label);
		}
		int count = node.getChildCount();
		for(int i = 0; i < count; i++) {
			if(hasLabeledBreak(node.getChild(i), label)) {
				return true;
			}
		}
		return false;
	}

	protected boolean processStatement(List<ASTNode> statements, ASTNode node) {
		if(node instanceof ASTIf n) {
			ASTNode testNode = n.getTestNode();
			if(testNode instanceof ASTLiteral lit) {
				boolean b = toBoolean(lit.getValue());
				ASTNode branch = b ? n.getThenNode() : n.getElseNode();
				if(producesNoStatements(branch) || endsInBreakOrContinue(branch)) {
					// Either splicing this branch in would contribute zero
					// statements, or its last (possibly abrupt) statement is a
					// bare break/continue - both are spec cases where
					// IfStatement's own evaluation performs UpdateEmpty(...,
					// undefined) (or the no-else/false-test shortcut), which
					// inlining the branch's raw statements would silently skip.
					// Keep the original ASTIf so its evaluate() (which already
					// does this correctly) still runs, instead of eliminating
					// the completion-value side effect along with the dead code.
					statements.add(node);
					return false;
				}
				addNode(statements,branch);
				return true;
			}
		}
		statements.add(node);
		return false;
	}

	private boolean producesNoStatements(ASTNode node) {
		if(node==null) {
			return true;
		}
		if(node instanceof ASTBlock bl) {
			if(!bl.hasDeclaredVariables() && !bl.hasFunctionDeclarations()) {
				return bl.getStatements().length==0;
			}
		}
		return false;
	}

	private boolean endsInBreakOrContinue(ASTNode node) {
		if(node instanceof ASTBreak || node instanceof ASTContinue) {
			return true;
		}
		if(node instanceof ASTBlock bl) {
			ASTNode[] stats = bl.getStatements();
			if(stats.length==0) {
				return false;
			}
			return endsInBreakOrContinue(stats[stats.length-1]);
		}
		return false;
	}

	protected void addNode(List<ASTNode> statements, ASTNode node) {
		if(node==null) {
			return;
		}
		if(node instanceof ASTBlock bl) {
			if(!bl.hasDeclaredVariables() && !bl.hasFunctionDeclarations()) {
				ASTNode[] st = bl.getStatements();
				for(int i=0; i<st.length; i++) {
					statements.add(st[i]);
				}
				return;
			}
		}
		statements.add(node);
	}

	public static boolean toBoolean(Object v) {
		if(v==null) {
			return false;
		}
		if(v==RuntimeUtil.UNDEFINED) {
			return false;
		}
		if(v instanceof Boolean b) {
			return b;
		}
		if(v instanceof Number n) {
			if(n instanceof Integer || n instanceof Short || n instanceof Byte) {
				return n.intValue()!=0;
			}
			if(n instanceof Long) {
				return n.longValue()!=0;
			}
			double d = n.doubleValue();
			return d!=0.0 && !Double.isNaN(d);
		}
		if(v instanceof CharSequence s) {
			return s.length()>0;
		}
		return true; // not null (include symbols)
	}
}
