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
package org.monflabs.galtajs.node.binaryop;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.util.function.TriPredicate;




/**
 * Base class for comparison operators
 * 
 * Note that it does not apply the Lexicographic order like Python (https://en.wikipedia.org/wiki/Lexicographic_order)
 * but it compares the elements one by one.
 * 
 * 
 * http://www.tlcc.com/admin/tips.nsf/tipsview/0988A4C3ACAC737585256E1D007327D9
 * 
 * Pair­wise operators act on two lists in parallel­element fashion. The first element of list 1 pairs with the first element of list 2, 
 * the second element of list 1 pairs with the second element of list 2, and so on. If one list has fewer elements than the other, 
 * the last element in the shorter list is repeated for operations with the remaining elements of the longer list. 
 * For pair­wise comparison tests, only one match is needed for the statement to return True, or 1.
 *    
 */
public abstract class ASTComparisonOp extends ASTBinaryOp {

	// Phase 5a: monotonic type feedback packed in a single int.
	// Same encoding as ASTArithmeticOp — see that class for details.
	private static final int CLASS_UNKNOWN = 0;
	private static final int CLASS_INT     = 1;
	private static final int CLASS_DOUBLE  = 2;
	private static final int CLASS_OTHER   = 3;
	private static final int POISONED      = 1 << 4;
	private static final int BOTH_INT      = CLASS_INT    | (CLASS_INT    << 2);
	private static final int BOTH_DOUBLE   = CLASS_DOUBLE | (CLASS_DOUBLE << 2);

	private volatile int feedback;

	private RuntimeUtil.MODE mode;
	private TriPredicate<JSEnvironment,Object,Object> function;

	public ASTComparisonOp(Token t, TriPredicate<JSEnvironment,Object,Object> function, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		super(t,leftNode,rightNode);
		this.function = function;
		this.mode = mode;
	}

	@Override
	public boolean isSequence() {
		return false;
	}

	public RuntimeUtil.MODE getMode() {
		return mode;
	}

	public TriPredicate<JSEnvironment,Object,Object> getFunction() {
		return function;
	}

	/**
	 * Phase 5a: subclass opts into type-specialized fast paths on int/int and
	 * double/double operands. Default false. Ops with heavier semantics
	 * (in, instanceof, sameValue) stay on the generic path.
	 */
	protected boolean supportsFastPath() {
		return false;
	}

	/** Phase 5a hook: inlined int/int comparison. */
	protected boolean intIntCmp(JSEnvironment env, int l, int r) {
		return function.test(env, l, r);
	}
	/** Phase 5a hook: inlined double/double comparison. */
	protected boolean doubleDoubleCmp(JSEnvironment env, double l, double r) {
		return function.test(env, l, r);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Phase 1a: when the environment does not enable sequence extensions,
			// no sub-expression can produce a Sequence result. Take the direct
			// scalar path unconditionally.
			if(!sequenceEnabled) {
				leftNode.evaluate(context,result);
				Object o1 = result.getValue();
				rightNode.evaluate(context,result);
				Object o2 = result.getValue();
				JSEnvironment env = context.getEnvironment();

				// Phase 5a: monomorphic-type fast paths. Same monotonic feedback
				// as ASTArithmeticOp.
				if(supportsFastPath()) {
					int fb = feedback;
					if((fb & POISONED) == 0) {
						if(fb == BOTH_INT
								&& o1 instanceof Integer i1
								&& o2 instanceof Integer i2
								&& env.getNumberProperties() == null) {
							result.setValue(intIntCmp(env, i1.intValue(), i2.intValue()));
							return Signal.NONE;
						}
						if(fb == BOTH_DOUBLE
								&& o1 instanceof Double d1
								&& o2 instanceof Double d2
								&& env.getNumberProperties() == null) {
							result.setValue(doubleDoubleCmp(env, d1.doubleValue(), d2.doubleValue()));
							return Signal.NONE;
						}
						int observed = classOf(o1) | (classOf(o2) << 2);
						if(fb == CLASS_UNKNOWN) {
							feedback = observed;
						} else if(observed != fb) {
							feedback = fb | POISONED;
						}
					}
				}
				result.setValue(function.test(env,o1,o2));
				return Signal.NONE;
			}

			JSResult r1, r2;
			leftNode.evaluate(context,result);
			if(!result.isSequence()) {
				Object o1 = result.getValue();
				rightNode.evaluate(context,result);
				if(!result.isSequence()) {
					Object o2 = result.getValue();
					result.setValue(function.test(context.getEnvironment(),o1,o2));
					return Signal.NONE;
				}
				r2 = result.ejectAndSequence();
				result.setValue(o1);
				r1 = result;
			} else {
				r1 = result.ejectAndSequence();
				r2 = result;
				rightNode.evaluate(context,r2);
			}
			boolean r = RuntimeUtil.seqCmp(context.getEnvironment(),function, r1, r2, mode);
			result.setValue(r);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}

	private static int classOf(Object o) {
		if(o instanceof Integer) return CLASS_INT;
		if(o instanceof Double)  return CLASS_DOUBLE;
		return CLASS_OTHER;
	}
}