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
import org.monflabs.util.function.TriFunction;




/**
 * Base arithmetic operation Node.
 *
 * Pair­wise operators act on two lists in parallel­element fashion. The first element of list 1 pairs with the first element of list 2,
 * the second element of list 1 pairs with the second element of list 2, and so on. If one list has fewer elements than the other,
 * the last element in the shorter list is repeated for operations with the remaining elements of the longer list.
 */
public abstract class ASTArithmeticOp extends ASTBinaryOp {

	// Phase 5a: monotonic type feedback packed in a single int.
	// bits 0-1 = lhs class, bits 2-3 = rhs class, bit 4 = poisoned (never recover).
	// class codes: 0=unknown, 1=Integer, 2=Double, 3=other.
	// The word is volatile so a torn read across threads is impossible: a poisoned
	// value is never observed as clean, and benign specialize races converge to
	// poisoned on the second write.
	private static final int CLASS_UNKNOWN = 0;
	private static final int CLASS_INT     = 1;
	private static final int CLASS_DOUBLE  = 2;
	private static final int CLASS_OTHER   = 3;
	private static final int POISONED      = 1 << 4;
	private static final int BOTH_INT      = CLASS_INT    | (CLASS_INT    << 2);
	private static final int BOTH_DOUBLE   = CLASS_DOUBLE | (CLASS_DOUBLE << 2);

	private volatile int feedback;

	private TriFunction<JSEnvironment,Object,Object,Object>  function;

	protected ASTArithmeticOp(Token t, TriFunction<JSEnvironment,Object,Object,Object> function, ASTNode leftNode, ASTNode rightNode) {
		super(t,leftNode,rightNode);
		this.function = function;
	}

	public TriFunction<JSEnvironment,Object,Object,Object>  getFunction() {
		return function;
	}

	/**
	 * Phase 5a: subclass opts into type-specialized fast paths on int/int and
	 * double/double operands. Default false — only ASTAdd, ASTSub, ASTMul, ASTMod
	 * currently override, since their operator semantics collapse to a JVM
	 * primitive op (plus the existing RuntimeUtil.*Exact overflow guards) once
	 * the operand types are known. Ops with heavier semantics (ASTDiv's `-0` /
	 * `Infinity` fanout, ASTPower's transcendentals, bit-shift coercion of
	 * non-integers) don't benefit and stay on the generic path.
	 */
	protected boolean supportsFastPath() {
		return false;
	}

	/**
	 * Phase 5a hook: inlined int/int operation. Only called when the subclass
	 * has opted in via {@link #supportsFastPath()}.
	 */
	protected Object intIntOp(JSEnvironment env, int l, int r) {
		return function.apply(env, l, r);
	}
	/**
	 * Phase 5a hook: inlined double/double operation. Same rationale as
	 * {@link #intIntOp}. Subclasses that care about BigDecimal-forced mode
	 * must delegate back to the generic path when configured.
	 */
	protected Object doubleDoubleOp(JSEnvironment env, double l, double r) {
		return function.apply(env, l, r);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Phase 1a: when the environment does not enable sequence extensions,
			// no sub-expression can produce a Sequence result, so the two
			// isSequence() branches below are provably dead. Take the direct
			// scalar path unconditionally.
			if(!sequenceEnabled) {
				leftNode.evaluate(context,result);
				Object o1 = result.getValue();
				rightNode.evaluate(context,result);
				Object o2 = result.getValue();
				JSEnvironment env = context.getEnvironment();

				// Phase 5a: monomorphic-type fast paths. One volatile read; if the
				// node has stably observed int/int or double/double AND the current
				// operands match, dispatch directly through the subclass hook. This
				// bypasses the megamorphic TriFunction apply that otherwise mixes
				// add/sub/mul/mod across every arithmetic node in the program.
				if(supportsFastPath()) {
					int fb = feedback;
					if((fb & POISONED) == 0) {
						if(fb == BOTH_INT
								&& o1 instanceof Integer i1
								&& o2 instanceof Integer i2
								&& env.getNumberProperties() == null) {
							result.setValue(intIntOp(env, i1.intValue(), i2.intValue()));
							return Signal.NONE;
						}
						if(fb == BOTH_DOUBLE
								&& o1 instanceof Double d1
								&& o2 instanceof Double d2
								&& env.getNumberProperties() == null) {
							result.setValue(doubleDoubleOp(env, d1.doubleValue(), d2.doubleValue()));
							return Signal.NONE;
						}
						// Update feedback based on observed classes. Any disagreement
						// with the recorded state poisons the node forever.
						int observed = classOf(o1) | (classOf(o2) << 2);
						if(fb == CLASS_UNKNOWN) {
							feedback = observed;
						} else if(observed != fb) {
							feedback = fb | POISONED;
						}
					}
				}
				result.setValue(function.apply(env,o1,o2));
				return Signal.NONE;
			}

			JSResult r1, r2;
			leftNode.evaluate(context,result);
			if(!result.isSequence()) {
				Object o1 = result.getValue();
				rightNode.evaluate(context,result);
				if(!result.isSequence()) {
					Object o2 = result.getValue();
					result.setValue(function.apply(context.getEnvironment(),o1,o2));
					return Signal.NONE;
				}
				r1 = new JSResult(o1);
				r2 = result.ejectAndSequence();
			} else {
				r1 = result.ejectAndSequence();
				r2 = new JSResult();
				rightNode.evaluate(context,r2);
			}
			RuntimeUtil.seq(context.getEnvironment(),function, r1, r2, result);
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
