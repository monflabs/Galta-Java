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
package org.monflabs.galtajs.node.literal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.assignop.ASTAssign;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockSplitContext;
import org.monflabs.galtajs.transpiler.util.TranspilerUtil;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;
import org.monflabs.util.generators.GeneratorReturnSignal;


/**
 * Array Literal Node.
 */
public class ASTArrayLiteral extends ASTContainerLiteral {

	// Mutable iterator record used while performing array-pattern destructuring
	// (IteratorBindingInitialization): tracks [[Done]] across the whole pattern so an
	// elision consumes exactly one step, a trailing rest drains what's left, and
	// IteratorClose (the iterator's "return") can be invoked once - either because the
	// pattern doesn't exhaust the iterator (no rest element) or because evaluating an
	// element threw partway through.
	static final class IteratorState {
		private final JSEnvironment env;
		private final Object iterator;
		private boolean done;

		IteratorState(JSEnvironment env, Object value) {
			this.env = env;
			this.iterator = RuntimeUtil.getIterator(env, value);
		}

		// Returns RuntimeUtil.NOT_AVAILABLE once the iterator is exhausted.
		Object step() {
			if(done) {
				return RuntimeUtil.NOT_AVAILABLE;
			}
			Object v;
			try {
				v = RuntimeUtil.iteratorStep(env, iterator);
			} catch(RuntimeException e) {
				// Per spec (IteratorStepValue): if the iterator's OWN next()
				// throws, the iterator record is marked done BEFORE the abrupt
				// completion propagates - so close() (called from assign()'s
				// catch block below) must NOT then ALSO call return() on this
				// same iterator, since its next() just threw, not merely
				// reported "done". Confirmed via destructuring test262 cases
				// asserting a user-supplied next()/return() pair's return()
				// count stays 0 after next() itself throws.
				done = true;
				throw e;
			}
			if(v==RuntimeUtil.NOT_AVAILABLE) {
				done = true;
			}
			return v;
		}
		boolean isDone() {
			return done;
		}
		void close() {
			if(!done) {
				done = true;
				RuntimeUtil.iteratorClose(env, iterator);
			}
		}
	}

	// Pseudo node, not really used as a node
    public abstract static class Initializer extends ASTNode {
    	public Initializer() {
    		super(null);
    	}

    	//
    	// Evaluation
    	//
		public abstract void evaluate(JSInterpretedRuntimeContext context, JSArray list, JSResult result);

		//
		// Destructuration
		//
        public abstract void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType);
		// Positional access into a plain values array - used for function parameter
		// binding, which per spec walks a non-observable list iterator (no user-visible
		// Symbol.iterator call, so plain indexed access is behaviorally identical).
		public abstract void assignPositional(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSArray array, long index, JSResult result);
		// Real destructuring against a shared iterator record - used for variable
		// declarations, plain assignment, and (recursively) nested patterns.
		// isAssignmentContext: see ASTContainerLiteral.assign()'s own comment -
		// threaded through to a nested pattern's own assign() call unchanged,
		// since a nested element stays in the same Assignment/Binding grammar
		// context as its enclosing pattern.
		public abstract void assignIter(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, IteratorState state, JSResult result, boolean isAssignmentContext);
		public abstract void forEachVarName(Consumer<String> callback);
		// jsContext/b: relay-only for this class (array positions have no
		// computed keys) - forwarded into a nested ASTObjectLiteral pattern's
		// own destructParameters, which may need them to evaluate a computed
		// property key. See ASTContainerLiteral.destructParameters's own comment.
		public abstract void destructParameters(VariableFactory varFactory, Object[] path, int index, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue);
		// Transpiler counterpart of assignIter(): emits Java statements that
		// consume this position from the shared IteratorRecord itVar, mirroring
		// assignIter()'s control flow exactly - see ASTArrayLiteral.
		// transpileJavaIteratorDestructure() for the top-level driver.
		public abstract void transpileJavaIteratorAssign(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder);
    }

    public final static class InitializerEmptySlot extends Initializer {
		public InitializerEmptySlot() {
		}
		
    	//
    	// Evaluation
    	//
	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}
		@Override
		public void evaluate(JSInterpretedRuntimeContext context, JSArray list, JSResult result) {
			list.arrayAddLength(1);
		}
	    @Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
			return StringFormat.format(".arrayAddLength(1)");
	    }
		
		//
		// Destructuration
		//
		@Override
		public void assignPositional(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSArray array, long index, JSResult result) {
		}
		@Override
		public void assignIter(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, IteratorState state, JSResult result, boolean isAssignmentContext) {
			// An elision still consumes one iterator step, it just discards the value.
			state.step();
		}
		@Override
		public void forEachVarName(Consumer<String> callback) {
		}
	    @Override
		public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
	    }
	    @Override
		public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
	    }
	    @Override
		public void destructParameters(VariableFactory varFactory, Object[] path, int index, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
	    }
		@Override
		public void transpileJavaIteratorAssign(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder) {
			// An elision still consumes one iterator step, it just discards the value.
			b.println("{0}.step();", itVar);
		}

	    @Override
		public String decompileExpression() {
	    	return "";
	    }
    }
    public final static class InitializerExpression extends Initializer {
		ASTNode node;
		public InitializerExpression(ASTNode node) {
		    this.node = assignParent(node);
		}
		public ASTNode getNode() {
			return node;
		}
    	@Override
    	public void init(InitContext initContext) {
    		if(node instanceof ASTAssign as && as.getLeftNode() instanceof ASTIdentifier id) {
    			ASTContainerLiteral.inferAnonymousName(as.getRightNode(), id.getId());
    		}
    		super.init(initContext);
    	}
    	@Override
		public int getChildCount() {
    		return super.getChildCount()+1;
    	}
    	@Override
    	public ASTNode getChild(int index) {
    		switch(index) {
    			case 0 ->	{ return node; }
    			default ->	{ return super.getChild(index-1); }
    		}
    	}
    	@Override
		protected void _setChild(int index, ASTNode node) {
    		switch(index) {
    			case 0 ->	{ this.node = node; }
    			default ->  { super._setChild(index-1,node); }
    		}
    	}
    	
    	//
    	// Evaluation
    	//
	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}
    	@Override
		public void evaluate(JSInterpretedRuntimeContext context, JSArray list, JSResult result) {
            node.evaluate(context,result);
            result.derefArray(list);
		}
		@Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
			if(!node.isSequence()) {
				return StringFormat.format(".arrayAdd({0})", JSTranspiler.asValue(jsContext, node));
			} else {
				return StringFormat.format(".arrayAddAll({0})", JSTranspiler.asResult(jsContext, node));
			}
		}
    	
		//
		// Destructuration
		//
		@Override
		public void assignPositional(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSArray array, long index, JSResult result) {
			if(node==null) {
				// nothing
				return;
			} else if(node instanceof ASTIdentifier id) {
				String varName = id.getId();
				Object value = array.getProperty(index,RuntimeUtil.UNDEFINED);
				variableFactory.accept(varName, value);
				return;
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					String varName = id.getId();
					ASTNode exprNode = as.getRightNode();
					Object value = (array!=null && index<array.arrayLength()) ? array.getProperty(index,RuntimeUtil.UNDEFINED) : RuntimeUtil.UNDEFINED;
					if(value==RuntimeUtil.UNDEFINED) {
						value = exprNode.evaluateValue(context,result);
					}
					variableFactory.accept(varName, value);
					return;
				} else if(varNode instanceof ASTContainerLiteral cl) {
					Object value = array.getProperty(index,RuntimeUtil.NOT_AVAILABLE);
					if(value==RuntimeUtil.NOT_AVAILABLE || value==RuntimeUtil.UNDEFINED) {
						ASTNode exprNode = as.getRightNode();
						if(exprNode!=null) {
							value = exprNode.evaluateValue(context,result);
						} else {
							value = RuntimeUtil.UNDEFINED;
						}
					}
					// Function-parameter destructuring is always a BindingPattern,
					// never AssignmentPattern - see ASTContainerLiteral.assign()'s comment.
					cl.assign(context, variableFactory,  value, result, false);
					return;
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				Object value = array.getProperty(index,RuntimeUtil.UNDEFINED);
				cl.assign(context, variableFactory,  value, result, false);
				return;
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		@Override
		public void assignIter(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, IteratorState state, JSResult result, boolean isAssignmentContext) {
			if(node==null) {
				// nothing, but the slot still occupies one iterator step
				state.step();
				return;
			} else if(node instanceof ASTIdentifier id) {
				Object value = state.step();
				if(value==RuntimeUtil.NOT_AVAILABLE) {
					value = RuntimeUtil.UNDEFINED;
				}
				variableFactory.accept(id.getId(), value);
				return;
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					variableFactory.accept(id.getId(), stepValue(context, state, as.getRightNode(), result));
					return;
				} else if(varNode instanceof ASTContainerLiteral cl) {
					cl.assign(context, variableFactory, stepValue(context, state, as.getRightNode(), result), result, isAssignmentContext);
					return;
				} else if(isAssignmentContext) {
					// DestructuringAssignmentTarget Initializer, target NOT an
					// identifier/nested pattern - a plain LeftHandSideExpression
					// (e.g. "[x.y = 5] = vals") is a genuinely valid assignment
					// target here (AssignmentElement, unlike BindingElement,
					// allows any DestructuringAssignmentTarget - confirmed via
					// dstr/array-elem-put-obj-literal-prop-ref-init.js).
					assignTarget(context, varNode, state, as.getRightNode(), result);
					return;
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				Object value = state.step();
				if(value==RuntimeUtil.NOT_AVAILABLE) {
					value = RuntimeUtil.UNDEFINED;
				}
				cl.assign(context, variableFactory, value, result, isAssignmentContext);
				return;
			} else if(isAssignmentContext) {
				// Same rationale as above, for a DestructuringAssignmentTarget
				// with no Initializer (e.g. "[x.y] = vals") - confirmed via
				// dstr/array-elem-put-prop-ref.js.
				//
				// Spec order (IteratorDestructuringAssignmentEvaluation for
				// AssignmentElement): the DestructuringAssignmentTarget's
				// reference must be resolved BEFORE the iterator is stepped
				// for this element - so if resolving a computed member
				// target throws, the iterator must never have been advanced
				// (test262 dstr/array-elem-iter-thrw-close.js:
				// "[ {}[thrower()] ] = iterable" asserts nextCount stays 0
				// after the throw). Reuse the same resolveReference() two-
				// phase API already used by plain assignment (ASTAssign)
				// for this exact ordering reason.
				assignTarget(context, node, state, null, result);
				return;
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		// The next iterator value, or the initializer's value when it is undefined
		private static Object stepValue(JSInterpretedRuntimeContext context, IteratorState state, ASTNode initializer, JSResult result) {
			Object value = state.step();
			if(value==RuntimeUtil.NOT_AVAILABLE) {
				value = RuntimeUtil.UNDEFINED;
			}
			if(value==RuntimeUtil.UNDEFINED && initializer!=null) {
				value = initializer.evaluateValue(context,result);
			}
			return value;
		}
		// AssignmentElement whose target is not a pattern: the target's reference is
		// resolved before the iterator is stepped (and before the initializer runs).
		private static void assignTarget(JSInterpretedRuntimeContext context, ASTNode target, IteratorState state, ASTNode initializer, JSResult result) {
			if(target instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
				ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
				Object value = stepValue(context, state, initializer, result);
				if(ref!=null) {
					arrayMember.assignToResolved(context, ref, value, null, result, null);
				}
			} else if(target instanceof ASTMember member && member.canResolveReference()) {
				ASTMember.ResolvedReference ref = member.resolveReference(context, result);
				Object value = stepValue(context, state, initializer, result);
				if(ref!=null) {
					member.writeProperty(context.getEnvironment(), ref.base(), value, context);
				}
			} else {
				target.evaluateAssign(context, stepValue(context, state, initializer, result), null, result, null);
			}
		}
		@Override
		public void forEachVarName(Consumer<String> callback) {
			if(node==null) {
				// nothing
			} else if(node instanceof ASTIdentifier id) {
				callback.accept(id.getId());
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					callback.accept(id.getId());
				} else if(varNode instanceof ASTContainerLiteral cl) {
					cl.forEachVarName(callback);
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.forEachVarName(callback);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
	    @Override
		public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
			if(node==null) {
				// nothing
			} else if(node instanceof ASTIdentifier id) {
				id.declareVariables(varContainer, varType);
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					id.declareVariables(varContainer, varType);
				} else if(varNode instanceof ASTContainerLiteral cl) {
					cl.declareVariables(varContainer,varType);
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.declareVariables(varContainer,varType);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
	    }
		@Override
		public void destructParameters(VariableFactory varFactory, Object[] path, int index, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
			if(node==null) {
				// nothing
			} else if(node instanceof ASTIdentifier) {
				varFactory.apply(node,defaultValue,addPath(path,index),null);
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					ASTNode exprNode = as.getRightNode();
					Object[] newPath = addPath(path,index);
					varFactory.apply(id,new DefaultRef(exprNode,newPath.length,defaultValue),newPath,null);
				} else if(varNode instanceof ASTContainerLiteral cl) {
					ASTNode exprNode = as.getRightNode();
					Object[] newPath = addPath(path,index);
					cl.destructParameters(varFactory,newPath,jsContext,b,new DefaultRef(exprNode,newPath.length,defaultValue));
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				// No default of its own here - forward whatever ambient default
				// this element itself was passed (e.g. from an enclosing
				// `[... ] = default` one level up), instead of discarding it.
				// The depth stays fixed at wherever the ambient default was
				// originally introduced, NOT the depth reached here - a
				// deeper leaf resolving to undefined for an unrelated reason
				// must not spuriously re-trigger an outer default.
				cl.destructParameters(varFactory,addPath(path,index),jsContext,b,defaultValue);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		@Override
		public void transpileJavaIteratorAssign(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder) {
			if(node==null) {
				// nothing, but the slot still occupies one iterator step
				b.println("{0}.step();", itVar);
				return;
			}
			if(node instanceof ASTIdentifier id) {
				String v = jsContext.generateUniqueId("v");
				b.println("Object {0} = {1}.step();", v, itVar);
				b.println("if({0}==NOT_AVAILABLE) {0} = UNDEFINED;", v);
				leafBinder.accept(id, v);
				return;
			}
			if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				ASTNode exprNode = as.getRightNode();
				if(!(varNode instanceof ASTIdentifier) && !(varNode instanceof ASTContainerLiteral)
						&& transpileJavaMemberTargetAssign(jsContext, b, itVar, varNode, exprNode)) {
					return;
				}
				String v = jsContext.generateUniqueId("v");
				b.println("Object {0} = {1}.step();", v, itVar);
				b.println("if({0}==NOT_AVAILABLE || {0}==UNDEFINED) {0} = {1};", v, JSTranspiler.asValue(jsContext,exprNode));
				if(varNode instanceof ASTIdentifier id) {
					leafBinder.accept(id, v);
					return;
				} else if(varNode instanceof ASTContainerLiteral cl) {
					emitContainerTarget(jsContext, b, cl, v, isAssignmentContext, leafBinder);
					return;
				} else {
					// Any other assignment-target-capable expression (e.g. a
					// MemberExpression: `[ base[key] = dflt ] = iterable`) - only
					// reachable in ASSIGNMENT context (a BindingPattern's elements
					// are parser-restricted to identifiers/patterns, never a
					// MemberExpression), where leafBinder calls
					// target.transpileJavaAssignment(...) generically - see the
					// identical fallback below.
					leafBinder.accept(varNode, v);
					return;
				}
			}
			if(node instanceof ASTContainerLiteral cl) {
				String v = jsContext.generateUniqueId("v");
				b.println("Object {0} = {1}.step();", v, itVar);
				b.println("if({0}==NOT_AVAILABLE) {0} = UNDEFINED;", v);
				emitContainerTarget(jsContext, b, cl, v, isAssignmentContext, leafBinder);
				return;
			}
			// A computed-member target (base[key], no default) must have its
			// OWN reference (base + RAW key, side effects and all) resolved
			// BEFORE the source iterator is ever stepped - spec
			// IteratorDestructuringAssignmentEvaluation's AssignmentElement:
			// step 1 (resolve DestructuringAssignmentTarget) precedes step 2
			// (IteratorStepValue), regardless of what the target turns out to
			// be. Mirrors the interpreter's identical resolveReference()-
			// before-step()-before-assignToResolved()/writeProperty()
			// sequence (see its own comment) - test262 dstr/array-elem-iter-
			// thrw-close.js/array-elem-trlg-iter-list-thrw-close.js: "[ {}[
			// thrower()] ] = iterable" must never step the iterator at all
			// once the key throws. Only ASTArrayMember/ASTMember get this
			// treatment (matching the interpreter, which only special-cases
			// those two) - anything else falls through to the plain
			// step-then-write fallback below, unaffected.
			if(transpileJavaMemberTargetAssign(jsContext, b, itVar, node, null)) {
				return;
			}
			// Any other assignment-target-capable expression with no default
			// (e.g. the `{}[yield]` MemberExpression target in test262
			// S11.13.2-shaped for-of/dstr tests) - same ASSIGNMENT-context-
			// only reasoning as above.
			String v = jsContext.generateUniqueId("v");
			b.println("Object {0} = {1}.step();", v, itVar);
			b.println("if({0}==NOT_AVAILABLE) {0} = UNDEFINED;", v);
			leafBinder.accept(node, v);
		}
		// A member target (base[key] or base.name), with an optional default:
		// its reference is resolved before the iterator is stepped (see above).
		// Returns false when the target is not such a member.
		private static boolean transpileJavaMemberTargetAssign(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, ASTNode target, ASTNode defaultValue) {
			if(target instanceof ASTArrayMember am && am.canResolveReference()) {
				String baseVar = jsContext.generateUniqueId("base");
				String keyVar = jsContext.generateUniqueId("key");
				b.println("Object {0} = {1};", baseVar, JSTranspiler.asValue(jsContext, am.getNode()));
				b.println("Object {0} = {1};", keyVar, JSTranspiler.asValue(jsContext, am.getUniqueIndex()));
				String v = transpileJavaStepValue(jsContext, b, itVar, defaultValue);
				b.println("assign({0},toPropertyKeyChecked({1},{0},{2}),{3});", baseVar, JSTranspiler.MAIN_ENVIRONMENT, keyVar, v);
				return true;
			}
			if(target instanceof ASTMember m && m.canResolveReference()) {
				String baseVar = jsContext.generateUniqueId("base");
				b.println("Object {0} = requireNonNullMemberBase({1},{2});", baseVar, JSTranspiler.asValue(jsContext, m.getNode()), ASTLiteral.encodeString(m.getMemberName()));
				String v = transpileJavaStepValue(jsContext, b, itVar, defaultValue);
				// Private (#name) targets resolve their PrivateName through
				// the current context and must go through PrivateFieldSet's
				// "already exists" check (RuntimeUtil.setPrivateField, via
				// privateAssign) - the generic assign(Object,Object,Object)
				// call below is statically Object-keyed all the way down to
				// RuntimeUtil.setProperty, so it can never reach the
				// String-keyed overload's private-brand check, silently
				// creating an ordinary "#field"-named property instead of
				// throwing. Mirrors ASTMember.transpileJavaAssignment's
				// identical branch.
				if(RuntimeUtil.isPrivateMemberName(m.getMemberName())) {
					b.println("privateAssign({0},{1},{2},{3});", jsContext.getContextJavaName(), baseVar, ASTLiteral.encodeString(m.getMemberName()), v);
				} else {
					b.println("assign({0},{1},{2});", baseVar, ASTLiteral.encodeString(m.getMemberName()), v);
				}
				return true;
			}
			return false;
		}
		// Steps the iterator into a new variable, applying the default (if any) to undefined
		private static String transpileJavaStepValue(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, ASTNode defaultValue) {
			String v = jsContext.generateUniqueId("v");
			b.println("Object {0} = {1}.step();", v, itVar);
			if(defaultValue!=null) {
				b.println("if({0}==NOT_AVAILABLE || {0}==UNDEFINED) {0} = {1};", v, JSTranspiler.asValue(jsContext,defaultValue));
			} else {
				b.println("if({0}==NOT_AVAILABLE) {0} = UNDEFINED;", v);
			}
			return v;
		}

	    @Override
		public String decompileExpression() {
	    	return node.decompileExpression();
	    }    
	};

    public final static class InitializerSpread extends Initializer {
		ASTNode node;
		public InitializerSpread(ASTNode node) {
		    this.node = assignParent(node);
		}
		public ASTNode getNode() {
			return node;
		}
    	@Override
		public int getChildCount() {
    		return super.getChildCount()+1;
    	}
    	@Override
    	public ASTNode getChild(int index) {
    		switch(index) {
    			case 0 ->	{ return node; }
    			default ->	{ return super.getChild(index-1); }
    		}
    	}
    	@Override
		protected void _setChild(int index, ASTNode node) {
    		switch(index) {
    			case 0 ->	{ this.node = node; }
    			default ->  { super._setChild(index-1,node); }
    		}
    	}

    	//
    	// Evaluation
    	//
	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}
    	@Override
		public void evaluate(JSInterpretedRuntimeContext context, JSArray list, JSResult result) {
            Object value = node.evaluateValue(context, result);            
            // Spreading null/undefined is a TypeError (valueIterator throws it)
            {
            	Iterator<Object> it = RuntimeUtil.valueIterator(context.getEnvironment(),value);
	            if(it!=null) {
	            	while(it.hasNext()) {
		            	list.arrayAdd(it.next());
	            	}
	            } else {
	            	throw RuntimeUtil.typeError("Expression is not an array");
	            }
            }
		}
		@Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
			return StringFormat.format(".arrayAddAll(valueIterator({0}))",JSTranspiler.asValue(jsContext, node));
		}
    	
		//
		// Destructuration
		//
		@Override
		public void assignPositional(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSArray array, long index, JSResult result) {
			JSArray a = JSArray.create(context.getEnvironment());
			if(array!=null) {
				for(long i=index; i<array.arrayLength(); i++) {
					Object value = array.getProperty(i,RuntimeUtil.UNDEFINED);
					a.arrayAdd(value);
				}
			}
			if(node instanceof ASTIdentifier id) {
				String fieldName = id.getId();
				variableFactory.accept(fieldName, a);
				return;
			}
			if(node instanceof ASTContainerLiteral cl) {
				// Function-parameter destructuring is always a BindingPattern,
				// never AssignmentPattern - see ASTContainerLiteral.assign()'s comment.
				cl.assign(context, variableFactory, a, result, false);
				return;
			}
			throw RuntimeUtil.error("Expression must be a variable name");
		}
		@Override
		public void assignIter(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, IteratorState state, JSResult result, boolean isAssignmentContext) {
			// Spec order (IteratorDestructuringAssignmentEvaluation for
			// AssignmentRestElement): when the target is a plain
			// DestructuringAssignmentTarget (not a nested Object/ArrayLiteral
			// pattern), its reference must be resolved BEFORE draining the
			// iterator - so if resolving a computed member target throws
			// (test262 dstr/array-rest-lref-err.js: "[...{}[thrower()]] =
			// iterable" asserts nextCount stays 0), the iterator is never
			// stepped. Reuse the same resolveReference() two-phase API
			// already used by plain assignment (ASTAssign) for this exact
			// ordering reason; a throw here propagates to assign()'s catch
			// block below, which still closes the (unstepped, not-done)
			// iterator correctly.
			ASTArrayMember.ResolvedReference arrayMemberRef = null;
			ASTArrayMember arrayMember = null;
			ASTMember.ResolvedReference memberRef = null;
			ASTMember member = null;
			if(isAssignmentContext && node instanceof ASTArrayMember am && am.canResolveReference()) {
				arrayMember = am;
				arrayMemberRef = am.resolveReference(context, result);
			} else if(isAssignmentContext && node instanceof ASTMember m && m.canResolveReference()) {
				member = m;
				memberRef = m.resolveReference(context, result);
			}
			// A rest element drains the rest of the iterator; being the last element of
			// the pattern, this also means the pattern exhausts the iterator, so no
			// IteratorClose is needed for it.
			JSArray a = JSArray.create(context.getEnvironment());
			while(true) {
				Object value = state.step();
				if(value==RuntimeUtil.NOT_AVAILABLE) {
					break;
				}
				a.arrayAdd(value);
			}
			if(arrayMember!=null) {
				if(arrayMemberRef!=null) {
					arrayMember.assignToResolved(context, arrayMemberRef, a, null, result, null);
				}
				return;
			}
			if(member!=null) {
				if(memberRef!=null) {
					member.writeProperty(context.getEnvironment(), memberRef.base(), a, context);
				}
				return;
			}
			if(node instanceof ASTIdentifier id) {
				String fieldName = id.getId();
				variableFactory.accept(fieldName, a);
				return;
			}
			if(node instanceof ASTContainerLiteral cl) {
				cl.assign(context, variableFactory, a, result, isAssignmentContext);
				return;
			}
			if(isAssignmentContext) {
				// Same rationale as the regular-element case above (e.g.
				// "[...x.y]" already handled via ASTArrayMember/ASTMember
				// above; this is the fallback for any other valid
				// DestructuringAssignmentTarget expression shape).
				node.evaluateAssign(context, a, null, result, null);
				return;
			}
			throw RuntimeUtil.error("Expression must be a variable name");
		}
		@Override
		public void forEachVarName(Consumer<String> callback) {
			if(node instanceof ASTIdentifier id) {
				String fieldName = id.getId();
				callback.accept(fieldName);
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.forEachVarName(callback);
			} else {
				throw RuntimeUtil.syntaxError("Spread argument must be a variable name or binding pattern");
			}
		}
	    @Override
		public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
			if(node instanceof ASTIdentifier id) {
				id.declareVariables(varContainer, varType);
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.declareVariables(varContainer, varType);
			} else {
				throw RuntimeUtil.syntaxError("Spread argument must be a variable name or binding pattern");
			}
	    }
		@Override
		public void destructParameters(VariableFactory varFactory, Object[] path, int index, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
			if(node instanceof ASTIdentifier) {
				varFactory.apply(node,defaultValue,path,index);
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.destructParameters(varFactory, addPath(path,index), jsContext, b, defaultValue);
			} else {
				throw RuntimeUtil.syntaxError("Spread argument must be a variable name or binding pattern");
			}
		}
		@Override
		public void transpileJavaIteratorAssign(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String itVar, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder) {
			// Spec order (IteratorDestructuringAssignmentEvaluation for
			// AssignmentRestElement): when the target is a computed-member
			// expression, its OWN reference (base + RAW key, side effects and
			// all) must be resolved BEFORE the iterator is drained - mirrors
			// the interpreter's identical resolveReference()-before-drain
			// sequence (see its own comment) and the non-rest element's
			// identical fix above - test262 dstr/array-rest-lref-err.js:
			// "[...{}[thrower()]] = iterable" must never step the iterator at
			// all once the key throws.
			String baseVar = null, keyVar = null;
			String memberName = null;
			if(node instanceof ASTArrayMember am && am.canResolveReference()) {
				baseVar = jsContext.generateUniqueId("base");
				keyVar = jsContext.generateUniqueId("key");
				b.println("Object {0} = {1};", baseVar, JSTranspiler.asValue(jsContext, am.getNode()));
				b.println("Object {0} = {1};", keyVar, JSTranspiler.asValue(jsContext, am.getUniqueIndex()));
			} else if(node instanceof ASTMember m && m.canResolveReference()) {
				memberName = m.getMemberName();
				baseVar = jsContext.generateUniqueId("base");
				b.println("Object {0} = requireNonNullMemberBase({1},{2});", baseVar, JSTranspiler.asValue(jsContext, m.getNode()), ASTLiteral.encodeString(memberName));
			}
			// A rest element drains the rest of the iterator; being the last element of
			// the pattern, this also means the pattern exhausts the iterator, so no
			// IteratorClose is needed for it.
			String restVar = jsContext.generateUniqueId("rest");
			String stepVar = jsContext.generateUniqueId("v");
			b.println("JSArrayImpl {0} = createArray();", restVar);
			b.println("while(true) {");
			b.incIndent();
			b.println("Object {0} = {1}.step();", stepVar, itVar);
			b.println("if({0}==NOT_AVAILABLE) break;", stepVar);
			b.println("{0}.arrayAdd({1});", restVar, stepVar);
			b.decIndent();
			b.println("}");
			if(baseVar!=null && keyVar!=null) {
				b.println("assign({0},toPropertyKeyChecked({1},{0},{2}),{3});", baseVar, JSTranspiler.MAIN_ENVIRONMENT, keyVar, restVar);
				return;
			}
			if(baseVar!=null) {
				// See the non-rest element's identical branch above for why
				// a private (#name) target needs privateAssign instead of
				// the generic assign(Object,Object,Object).
				if(RuntimeUtil.isPrivateMemberName(memberName)) {
					b.println("privateAssign({0},{1},{2},{3});", jsContext.getContextJavaName(), baseVar, ASTLiteral.encodeString(memberName), restVar);
				} else {
					b.println("assign({0},{1},{2});", baseVar, ASTLiteral.encodeString(memberName), restVar);
				}
				return;
			}
			if(node instanceof ASTIdentifier id) {
				leafBinder.accept(id, restVar);
				return;
			}
			if(node instanceof ASTContainerLiteral cl) {
				emitContainerTarget(jsContext, b, cl, restVar, isAssignmentContext, leafBinder);
				return;
			}
			// Any other assignment-target-capable expression (e.g.
			// `[...base[key]] = iterable`) - mirrors assignIter()'s identical
			// isAssignmentContext fallback (node.evaluateAssign(...)) on the
			// interpreter side above; only reachable in ASSIGNMENT context, same
			// reasoning as transpileJavaIteratorAssign()'s fallback above.
			leafBinder.accept(node, restVar);
		}

		@Override
		public String decompileExpression() {
	    	return "..."+ node.decompileExpression();
	    }
    };
    
    
	
	private ArrayList<Initializer> fieldInitializers = new ArrayList<Initializer>();

	public ASTArrayLiteral(Token t) {
		super(t);
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+fieldInitializers.size();
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<fieldInitializers.size()) {
			return fieldInitializers.get(index);
		}
		return super.getChild(index-fieldInitializers.size());
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<fieldInitializers.size()) {
			fieldInitializers.set(index,(Initializer)node);
			return;
		}
		super._setChild(index-fieldInitializers.size(), node);
	}

	public void fixParametersForFunction() {
		if(fieldInitializers.size()>=2) {
			Set<String> list = new HashSet<String>();
			for(int i=fieldInitializers.size()-1; i>=0; i--) {
				Initializer init = fieldInitializers.get(i);
				if(init instanceof InitializerExpression exp) {
					if(exp.node instanceof ASTIdentifier id) {
						if(list.contains(id.getId())) {
							InitializerEmptySlot slot = new InitializerEmptySlot(); 
							fieldInitializers.set(i,assignParent(slot));
						} else {
							list.add(id.getId());
						}
					}
				}
			}
		}
	}
	
	public void addEmptySlot() {
	    fieldInitializers.add(assignParent(new InitializerEmptySlot()));
	}

	/**
	 * True if used as a parameter list with no rest, default or destructured parameter
	 * (spec: IsSimpleParameterList), a prerequisite for a mapped arguments object.
	 */
	public boolean isSimpleParameterList() {
		for(Initializer init: fieldInitializers) {
			if(init instanceof InitializerSpread) {
				return false;
			}
			if(init instanceof InitializerExpression exp) {
				ASTNode n = exp.getNode();
				if(n!=null && !(n instanceof ASTIdentifier)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Parameter name for each position, or {@code null} if that position isn't a plain
	 * identifier (only meaningful when {@link #isSimpleParameterList()} is true).
	 */
	public String[] getSimpleParameterNames() {
		String[] names = new String[fieldInitializers.size()];
		for(int i=0; i<names.length; i++) {
			Initializer init = fieldInitializers.get(i);
			if(init instanceof InitializerExpression exp && exp.getNode() instanceof ASTIdentifier id) {
				names[i] = id.getId();
			}
		}
		return names;
	}

	// Split that into different objects?
	public void addExpression(ASTNode node){
	    fieldInitializers.add(assignParent(new InitializerExpression(node)));
	}

	public void addSpread(ASTNode node){
	    fieldInitializers.add(assignParent(new InitializerSpread(node)));
	}

	@Override
	protected boolean lastIsSpread() {
		return !fieldInitializers.isEmpty() && fieldInitializers.get(fieldInitializers.size()-1) instanceof InitializerSpread;
	}

	@Override
	public void checkPattern(boolean binding, boolean strict) {
		if(hasCommaAfterSpread()) {
			throw new JSParseException(null, this, "Rest element must be last element");
		}
		for(Initializer init: fieldInitializers) {
			if(init instanceof InitializerSpread sp) {
				if(sp.getNode() instanceof ASTAssign) {
					throw new JSParseException(null, this, "Rest element may not have a default initializer");
				}
				checkPatternTarget(sp.getNode(), binding, strict);
			} else if(init instanceof InitializerExpression exp && exp.getNode()!=null) {
				ASTNode n = exp.getNode();
				checkPatternTarget(n instanceof ASTAssign as ? as.getLeftNode() : n, binding, strict);
			}
		}
	}
	
	
	//
	// Evaluation
	//

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSArray list = JSArray.create(context.getEnvironment());
			int count = fieldInitializers.size();
			for(int i=0; i<count; i++) {
				fieldInitializers.get(i).evaluate(context, list, result);
			}
			result.setValue(list);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
    
	@Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	TranspilerJavaBuilder b = jsContext.createJavaBuilder();
    	b.append(StringFormat.format("createArray()"));
        int count = fieldInitializers.size();
        
        TranspilerCodeSplitter splitter = jsContext.getOptions().getCodeSplitter();
        if(splitter==null && count>TranspilerCodeSplitter.DEFAULT_ARRAY_SPLIT_MAX) {
        	// The general code-splitter feature is opt-in (JSTranspilerOptions.
        	// Builder.splitCode) and not exercised by the test262 harness or
        	// other general transpiled-mode callers - but an array literal well
        	// past DEFAULT_ARRAY_SPLIT_MAX (e.g. an exhaustive ~3968-entry
        	// per-codepoint-sequence listing) compiles to a deeply nested Java
        	// expression tree that can overflow javac's OWN compiler stack
        	// (StackOverflowError in TreeScanner.scan/visitApply/visitSelect),
        	// independent of whether code-splitting was requested. Constructing
        	// a standalone splitter here (its constructor has no side effects)
        	// only ever activates the existing, already-exercised split codegen
        	// path below - never touched for normal small/medium literals.
        	splitter = new TranspilerCodeSplitter(jsContext.getOptions());
        }
        if(splitter!=null && count>=splitter.ArrayLiteralSplitTheshold()) {
        	for(int i=0; i<count; i+=splitter.ObjectLiteralSplitMax()) {
        		int max = Math.min(count-i, splitter.ObjectLiteralSplitMax());
            	b.incIndent();
            	b.println(".litValues(new Consumer<JSArrayImpl>() { // Array values");
            	b.incIndent();
            	b.println("@Override");
            	b.println("public void accept(JSArrayImpl arr) {");
            	b.incIndent();
            	
				TranspilerGeneratorBlockSplitContext bCtx = new TranspilerGeneratorBlockSplitContext(jsContext);

				b.append("arr");
        		for(int j=0; j<max; j++) {
                	b.incIndent();
    				Initializer initializer = fieldInitializers.get(i+j);
    				String s = JSTranspiler.asValue(bCtx, initializer);
    				if(j==max-1) {
    					b.print(s);
    					b.println(";");
    				} else {
    					b.println(s);
    				}
                	b.decIndent();
        		}
        		
            	b.decIndent();
            	b.println("}");

            	JSTranspiler.createConstantPool(bCtx,b);

            	b.decIndent();
            	b.println("})");
            	b.decIndent();
        	}
        } else {
        	b.incIndent();
	        for(int i=0; i<count; i++) {
				Initializer initializer = fieldInitializers.get(i);
				String s = JSTranspiler.asValue(jsContext, initializer);
				b.println(s);
	        }
        	b.decIndent();
        }
    	return b.toString();
	}

	
	//
	// Destructuration
	//
	@Override
    public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
		if(varType!=VAR_TYPE.AUTO) {
			int count = fieldInitializers.size();
			for(int i=0; i<count; i++) {
				fieldInitializers.get(i).declareVariables(varContainer,varType);
			}
		}
	}
	@Override
	public void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, Object value, JSResult result, boolean isAssignmentContext) {
		int count = fieldInitializers.size();
		JSEnvironment env = context.getEnvironment();
		if(count==0) {
			if(isAssignmentContext) {
				// Spec: ArrayAssignmentPattern : [ ] still calls GetIterator+IteratorClose
				// even though it binds nothing - unlike ArrayBindingPattern below.
				new IteratorState(env, value).close();
			}
			// Spec: ArrayBindingPattern : [ ] doesn't call GetIterator at all - an
			// empty pattern matches anything, iterable or not, without touching it.
			return;
		}
		IteratorState state = new IteratorState(env, value);
		try {
			for(int i=0; i<count; i++) {
				fieldInitializers.get(i).assignIter(context, variableFactory, state, result, isAssignmentContext);
			}
		} catch(RuntimeException e) {
			try {
				state.close();
			} catch(RuntimeException closeEx) {
				// Per spec (IteratorClose), a failure while closing on a "throw"
				// abrupt completion doesn't replace the original exception - but
				// on a "return" completion (a generator forcibly terminated via
				// .return() while suspended mid-destructuring, surfacing here as
				// a GeneratorReturnSignal unwind) the close failure - e.g. a
				// non-Object return() result producing a TypeError - IS the
				// completion, overriding the return. See test262
				// array-elem-iter-rtrn-close-{err,null}.js /
				// array-rest-iter-rtrn-close-{err,null}.js /
				// array-elem-trlg-iter-{list,rest}-rtrn-close-{err,null}.js.
				if(e instanceof GeneratorReturnSignal) {
					throw closeEx;
				}
			}
			throw e;
		}
		state.close();
	}

	// Function parameter binding: `values` is the (non-observable) raw arguments array,
	// walked positionally rather than through an iterator - see Initializer.assignPositional.
	public void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, Object[] values, JSResult result) {
		JSArray al = RuntimeUtil.getArrayLike(context.getEnvironment(),values);
		int count = fieldInitializers.size();
		for(int i=0; i<count; i++) {
			fieldInitializers.get(i).assignPositional(context, variableFactory, al, i, result);
		}
	}

	@Override
	public void forEachVarName(Consumer<String> callback) {
		int count = fieldInitializers.size();
		for(int i=0; i<count; i++) {
			Initializer initializer = fieldInitializers.get(i);
			if(initializer!=null) {
				initializer.forEachVarName(callback);
			}
		}
	}
    
	@Override
	public void destructParameters(VariableFactory varFactory, Object[] path, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
        int count = fieldInitializers.size();
        for(int i=0; i<count; i++) {
			Initializer initializer = fieldInitializers.get(i);
			initializer.destructParameters(varFactory, path, i, jsContext, b, defaultValue);
        }
	}

	@Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		TranspilerJavaBuilder b = jsContext.createJavaBuilder();
		int vidx = jsContext.generateUniqueId();
		b.println("executeIsolated({0}, () -> {", JSTranspiler.MAIN_CONTEXT);
		b.incIndent();
		b.println("Object v{0} = {1};", vidx, rightValue);
		// RequireObjectCoercible-equivalent is subsumed by IteratorRecord.begin's
		// own GetIterator call (throws on null/undefined) for the non-empty case;
		// the empty-pattern case is handled by transpileJavaIteratorDestructure's
		// own isAssignmentContext branch below (ArrayAssignmentPattern : [ ] still
		// calls GetIterator+IteratorClose even though it binds nothing).
		transpileJavaIteratorDestructure(jsContext, b, "v"+vidx, /*isAssignmentContext*/ true, (target,valueExpr) -> {
			b.println("{0};", target.transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS, valueExpr, sequence, returnOriginalValue));
		});
		b.println("return v{0};", vidx);
		b.decIndent();
		b.println("})");
		return b.toString();
	}

	// Emits Java statements that destructure sourceExpr's single value against
	// this array pattern using the real iterator protocol - the transpiler's
	// counterpart of assign() above. isAssignmentContext/leafBinder generalize
	// assign()'s own isAssignmentContext/variableFactory: leafBinder is invoked
	// once per bound identifier with the identifier node and a Java expression
	// (a bare temp-var name) already holding its fully-resolved value.
	// sourceExpr must be a side-effect-free expression (a bare temp-var name),
	// since IteratorRecord.begin reads it exactly once.
	public void transpileJavaIteratorDestructure(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, String sourceExpr, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder) {
		int count = fieldInitializers.size();
		if(count==0) {
			if(isAssignmentContext) {
				// Spec: ArrayAssignmentPattern : [ ] still calls GetIterator+IteratorClose
				// even though it binds nothing - unlike ArrayBindingPattern below.
				b.println("IteratorRecord.begin({0},{1}).close();", JSTranspiler.MAIN_ENVIRONMENT, sourceExpr);
			}
			// Spec: ArrayBindingPattern : [ ] doesn't call GetIterator at all - an
			// empty pattern matches anything, iterable or not, without touching it.
			return;
		}
		String itVar = jsContext.generateUniqueId("it");
		String excVar = jsContext.generateUniqueId("iterEx");
		b.println("IteratorRecord {0} = IteratorRecord.begin({1},{2});", itVar, JSTranspiler.MAIN_ENVIRONMENT, sourceExpr);
		b.println("try {");
		b.incIndent();
		for(int i=0; i<count; i++) {
			fieldInitializers.get(i).transpileJavaIteratorAssign(jsContext, b, itVar, isAssignmentContext, leafBinder);
		}
		b.decIndent();
		b.println("} catch(RuntimeException {0}) {", excVar);
		b.incIndent();
		String closeExVar = jsContext.generateUniqueId("closeEx");
		b.println("try {");
		b.incIndent();
		b.println("{0}.close();", itVar);
		b.decIndent();
		b.println("} catch(RuntimeException {0}) {", closeExVar);
		b.incIndent();
		// Per spec (IteratorClose), a failure while closing on a "throw" abrupt
		// completion doesn't replace the original exception - but on a "return"
		// completion (a generator forcibly terminated via .return() while
		// suspended mid-destructuring, surfacing here as a GeneratorReturnSignal
		// unwind) the close failure IS the completion, overriding the return -
		// see the interpreted-mode assign() counterpart above for the same fix
		// and the test262 files it targets.
		b.println("if({0} instanceof GeneratorReturnSignal) throw {1};", excVar, closeExVar);
		b.decIndent();
		b.println("}");
		b.println("throw {0};", excVar);
		b.decIndent();
		b.println("}");
		b.println("{0}.close();", itVar);
	}

	// Hand-off point for a nested pattern reached while destructuring against a
	// shared iterator: a nested array pattern recurses into the same mechanism
	// (a fresh IteratorRecord sourced from this single value); a nested object
	// pattern crosses into the existing, unchanged property-access-based
	// mechanism, rooted at this single value - exactly mirroring how
	// ASTContainerLiteral.assign()'s cl.assign(...) dispatches polymorphically
	// in interpreted mode.
	static void emitContainerTarget(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, ASTContainerLiteral cl, String valueVar, boolean isAssignmentContext, BiConsumer<ASTNode,String> leafBinder) {
		if(cl instanceof ASTArrayLiteral nestedArray) {
			nestedArray.transpileJavaIteratorDestructure(jsContext, b, valueVar, isAssignmentContext, leafBinder);
		} else if(cl instanceof ASTObjectLiteral nestedObject) {
			b.println("requireObjectCoercible({0});", valueVar);
			nestedObject.destructParameters( (container,defaultValue,path,spread) -> {
				StringBuilder bb = new StringBuilder();
				bb.append(StringFormat.format("VarAccessor.destruct({0},{1}", JSTranspiler.MAIN_ENVIRONMENT, valueVar));
	    		if(path.length==0) {
	        		bb.append(",null");
	    		} else if(path.length==1) {
	        		bb.append(StringFormat.format(",{0}", ASTLiteral.encodeLiteral(jsContext,path[0])));
	    		} else {
			        bb.append(",new Object[]{");
		        	for(int j=0; j<path.length; j++) {
		        		if(j>0) {
			        		bb.append(",");
		        		}
		        		bb.append(StringFormat.format("{0}", ASTLiteral.encodeLiteral(jsContext,path[j])));
		        	}
	        		bb.append("}");
	    		}
	        	if(spread!=null) {
	        		bb.append(StringFormat.format(",{0}",TranspilerUtil.encodeSpreadValue(jsContext,spread)));
	        	} else {
	        		bb.append(",null");
	        	}
	    		if(defaultValue!=null) {
	    		if(path.length>1) {
	    			bb.append(StringFormat.format(",{0}", DefaultRef.transpileChain(jsContext,defaultValue)));
	    		} else {
	    			bb.append(StringFormat.format(",()->{0}", JSTranspiler.asValue(jsContext,defaultValue.node())));
	    		}
	    		}
	    		bb.append(")");
	    		// Delegate to the caller-supplied leafBinder (not a hardcoded
	    		// container.transpileJavaAssignment(...) call) so a declaration-
	    		// context leaf (var/let/const, or a function parameter - whose
	    		// leafBinder writes the Java local directly, bypassing any
	    		// assignment-specific semantics) and a true assignment-expression
	    		// leaf (whose leafBinder calls target.transpileJavaAssignment(...),
	    		// e.g. triggering the const-reassignment check) each get the
	    		// behavior appropriate to their own context - a nested object
	    		// pattern's leaf is always a FRESH BINDING when reached from a
	    		// declaration (for(const [{x}]=dflt] of ...)'s "x" is a brand new
	    		// per-iteration binding, never a reassignment, even though "x"
	    		// happens to be const), never a mutation of a pre-existing one.
	    		leafBinder.accept(container, bb.toString());
			}, RuntimeUtil.EMPTY_PARAMS, jsContext, b, null);
		}
	}


    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append("[");
		if(!fieldInitializers.isEmpty()) {
	        for(int i=0; i<fieldInitializers.size(); i++) {
	    		if(i>0) {
	    			b.append(", ");
	    		}
				b.append(fieldInitializers.get(i).decompileExpression());
	    	}
	        if(fieldInitializers.get(fieldInitializers.size()-1) instanceof InitializerEmptySlot) {
    			b.append(",");
	        }
		}
		b.append("]");
		return b.toString();
	}
	public String decompileParameters() {
    	StringBuilder b = new StringBuilder();
        for(int i=0; i<fieldInitializers.size(); i++) {
    		if(i>0) {
    			b.append(", ");
    		}
			b.append(fieldInitializers.get(i).decompileExpression());
    	}
		return b.toString();
	}
}
