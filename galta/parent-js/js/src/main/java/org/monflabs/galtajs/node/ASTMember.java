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
package org.monflabs.galtajs.node;

import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.CustomLinkedMap;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.MemberAccessor;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateElementsHolder;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Object Member Node.
 */
public class ASTMember extends ASTNode implements ChainingNode, MemberNode {
	
	// TODO, to simplify the code
	private static final boolean USE_TRANSPILER_RUNTIME = false;
	
	public static boolean isMemberIntegerIndex(JSEnvironment env, String s) {
		if(s==null || s.length()<2 || !s.startsWith(".")) {
			return false;
		}
		for(int i=1; i<s.length(); i++) {
			if(!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}
	public static boolean isIntegerIndex(JSEnvironment env, String s) {
		if(s==null || s.length()<1) {
			return false;
		}
		for(int i=0; i<s.length(); i++) {
			if(!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private ASTNode node;
	private boolean deepscan;
	private String memberName;
	private boolean nullop;

	// Phase 3: monomorphic property cache ("PropIC"). Records a single
	// (owner, own-entry) pair witnessed during the last resolve. Fast path is
	// only entered when the current base is IDENTITY-equal to the cached owner
	// AND the cached entry is still live (not soft-removed). The EntryImpl
	// reference is stable across value writes, descriptor swaps, and rehashes:
	// putEntry on the same key mutates in place, and rehash re-links existing
	// nodes without recreating them. A remove flips `removed=true`; the reader
	// checks that flag and falls back to the slow path. Published via a plain
	// reference write - references are naturally atomic on the JVM, and a
	// stale but self-consistent read is validated by the guard checks below.
	private static final class PropIC {
		final JSObjectImpl                       owner;
		final CustomLinkedMap.EntryImpl<String>  entry;
		PropIC(JSObjectImpl o, CustomLinkedMap.EntryImpl<String> e) { owner=o; entry=e; }
	}
	private volatile PropIC ic;

	// Fast-path eligibility: skip subclasses that override
	// getOwnProperty(String,Object,Object) (BuiltinFunction poison pills,
	// Arguments's magic mapping, GlobalThis fallback, ObjectAccessorWrapper).
	// Their overrides may reinterpret or throw on entries the map still holds
	// (e.g. Function#arguments = null must surface as UNDEFINED via override).
	// Reflection is done once per class and memoized in a ClassValue.
	private static final ClassValue<Boolean> PROPIC_ELIGIBLE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("getOwnProperty", String.class, Object.class, Object.class)
						.getDeclaringClass() == JSObjectImpl.class;
			} catch(NoSuchMethodException nsme) {
				return Boolean.FALSE;
			}
		}
	};

	public ASTMember(Token t, ASTNode node, String memberName, boolean deepscan, boolean nullop) {
		super(t);
		this.node = assignParent(node);
		this.memberName = memberName;
		this.deepscan = deepscan;
		this.nullop = nullop;
	}

	// Pre-resolved LHS reference for correct logical-assignment evaluation order
	// (ECMAScript 13.15.2): the PutValue step must only run when the operator's
	// short-circuit check decides a write is actually needed.
	public record ResolvedReference(Object base, String memberName) {}

	public boolean canResolveReference() {
		return !isSequence();
	}

	public ResolvedReference resolveReference(JSInterpretedRuntimeContext context, JSResult result) {
		node.evaluate(context, result);
		if(result.isChainingNull()) {
			return null;
		}
		Object base = result.getValue();
		if(nullop && (base==null || base==RuntimeUtil.UNDEFINED)) {
			// Optional chain short-circuit: see evaluate() below.
			return null;
		}
		if(base==null || base==RuntimeUtil.UNDEFINED) {
			throw RuntimeUtil.typeError("Left part of member {0} is null or undefined", memberName);
		}
		return new ResolvedReference(base, memberName);
	}

	// Completes an assignment against a reference already resolved by
	// resolveReference() above - mirrors ASTArrayMember.assignToResolved()'s
	// two-phase API (and this class's own evaluateAssign()'s "phase 1a"
	// non-sequence branch, whose base/receiver logic this exactly replays),
	// letting a caller (e.g. destructuring assignment) resolve the TARGET's
	// own reference (evaluating `this`/the base expression - which throws
	// ReferenceError if `this` is still TDZ - here that already happened,
	// inside resolveReference()) BEFORE reading the destructuring SOURCE
	// value, then defer the actual read-modify-write to this method. Fixes
	// test262 language/statements/class/elements/privatefieldset-evaluation-
	// order-1.js: `({a: this.#field} = object)` must throw ReferenceError
	// (`this` still TDZ, pre-super()) before ever invoking `object`'s own `a`
	// getter - previously the getter ran first (via the unconditional
	// m.evaluateAssign() callers used before this method existed), by which
	// point a nested super() call inside the getter had already initialized
	// `this`, so the expected throw never happened.
	public void assignToResolved(JSInterpretedRuntimeContext context, ResolvedReference ref, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		JSEnvironment env = context.getEnvironment();
		Object base = ref.base();
		// SuperProperty: the search starts at the home object's prototype
		// (base), but `this` for an invoked accessor - and the target of a
		// plain create/write - must remain the current `this`, per spec.
		// Fetched fresh here (not pre-captured by resolveReference()), exactly
		// matching evaluateAssign()'s own existing (unchanged) timing for the
		// super case - only the ORDER relative to reading the destructuring
		// source changes for the non-super/private cases above.
		boolean isSuper = node instanceof ASTSuperMember;
		Object receiver = isSuper ? context.getThis() : base;
		if(assigner!=null) {
			Object oldValue = isSuper
					? RuntimeUtil.getPropertyWithReceiver(env, base, memberName, receiver)
					: readProperty(env, base, context);
			Object newValue = assigner.apply(oldValue);
			if(isSuper) {
				RuntimeUtil.setPropertyWithReceiver(env, base, memberName, newValue, receiver);
			} else {
				writeProperty(env, base, newValue, context);
			}
			result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : newValue);
		} else {
			if(isSuper) {
				RuntimeUtil.setPropertyWithReceiver(env, base, memberName, rightValue, receiver);
			} else {
				writeProperty(env, base, rightValue, context);
			}
			result.setValue(rightValue);
		}
	}

	@Override
	protected void init(InitContext initContext) {
		super.init(initContext);

		// This is @, then we force the nullop
		//   @.xxx should not generate an exception
		if(node instanceof ASTIdentifierFilter) {
			nullop = true;
		}
	}


	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public boolean isSequence() {
		return node.isSequence() || deepscan;
	}	
	
	@Override
	public ASTNode getNode() {
		return node;
	}

	@Override
	public boolean isSingleIndex() {
		return true;
	}

	public String getMemberName() {
		return memberName;
	}
	
	@Override
	public boolean isNullOp() {
		return nullop;
	}

	public boolean isDeepscan() {
		return deepscan;
	}

	@Override
	public String getNodeString() {
		if(nullop) {
			return deepscan? "?.."+memberName : "?."+memberName;
		}
		return deepscan? ".."+memberName : "."+memberName;
	}

	@Override
	public int getChildCount() {
		return super.getChildCount()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		switch(index) {
			case 0 ->		{ return node; }
			default ->		{ return super.getChild(index-1); }
		}
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		switch(index) {
			case 0 ->	{ this.node = node; }
			default ->  { super._setChild(index-1,node); }
		}
	}

	
	@Override
	public Object getSingleValue(JSInterpretedRuntimeContext context, Object base) {
		// Used by ASTCall's "member call" fast path (obj.method()) - a
		// separate lookup from readProperty()/evaluate() above, so private
		// access needs the same PrivateName resolution here too. Returns
		// NOT_AVAILABLE (not a thrown brand-check TypeError) when absent,
		// matching the non-private convention - the caller (ASTCall) is
		// responsible for turning that into its own "method does not exist"
		// error or null-chaining short-circuit.
		if(node instanceof ASTSuperMember) {
			// SuperProperty (spec 13.3.7.1 MakeSuperPropertyReference step 2):
			// GetThisBinding() must run - throwing ReferenceError if `this` is
			// still TDZ (i.e. this super access appears before super() has run
			// in a derived constructor) - BEFORE the property is even looked
			// up on the super base, regardless of whether that property
			// exists. readProperty() already has this guard for the plain-read
			// path; this fast "member call" path (ASTCall's obj.method()
			// optimization) was missing it entirely, so a MISSING method on
			// the super base (e.g. `super.method()` where the super class
			// declares no such method) raced past this check and threw this
			// path's own generic "method does not exist" TypeError instead of
			// the ReferenceError required when `this` isn't initialized yet
			// (test262 language/statements/class/definition/
			// this-access-restriction.js).
			context.getThis();
		}
		if(isPrivateMemberName(memberName)) {
			PrivateName pn = context.resolvePrivateName(memberName);
			return base instanceof PrivateElementsHolder holder ? holder.getPrivateElementValue(pn) : RuntimeUtil.NOT_AVAILABLE;
		}
		return RuntimeUtil.getPropertyUnavailable(context.getEnvironment(), base, memberName);
	}

	@Override
	public void forEachEntries(JSInterpretedRuntimeContext context, JSResult sequence, MemberAccessor accessor, boolean forUpdate) {
		if(deepscan) {
			sequence.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(context.getEnvironment(),v,res);
			});
		}
		
		sequence.forEach( (base) -> {
			if(base==null) { // Silent null chaining
				return;
			}
    		accessor.apply(base, memberName,
				() -> RuntimeUtil.getPropertyUnavailable(context.getEnvironment(), base, memberName), 
				forUpdate ? (val) -> RuntimeUtil.setProperty(context.getEnvironment(), base, memberName, val) : null,
				forUpdate ? ()  -> RuntimeUtil.deleteProperty(context.getEnvironment(), base, memberName) : null
		    );
			return;
		});
	}
	
	// Phase 3 fast path shared by evaluate() and evaluateAssign()'s read leg -
	// also called directly by the short-circuit-aware logical-assignment
	// operators (ASTAssignAnd/Or/NullCoalescing), which resolve the
	// reference once via resolveReference() then need the SAME private-
	// aware read/write logic, not the plain RuntimeUtil.getProperty/
	// setProperty(env,base,memberName) they'd otherwise reach for (those
	// don't know about PrivateName resolution at all - see
	// ResolvedReference/resolveReference above). Handles the ordinary
	// (non-super, non-sequence, base-not-null) property read. SuperProperty
	// needs a distinct receiver (spec) and stays on the slow path.
	public Object readProperty(JSEnvironment env, Object base, JSInterpretedRuntimeContext context) {
		if(node instanceof ASTSuperMember) {
			return RuntimeUtil.getPropertyWithReceiver(env, base, memberName, context.getThis());
		}
		// Private (#name) access resolves the enclosing class evaluation's
		// PrivateName token via the ambient closure chain, then goes straight
		// to [[PrivateElements]] - bypassing JSAccessor/PropIC/prototype
		// chain/Proxy traps entirely, per spec.
		if(isPrivateMemberName(memberName)) {
			PrivateName pn = context.resolvePrivateName(memberName);
			return RuntimeUtil.getPrivateField(base, pn);
		}
		// PropIC fast path: only for JSObjectImpl subclasses that do NOT override
		// getOwnProperty (see PROPIC_ELIGIBLE). Overrides on BuiltinFunction
		// (arguments/caller poison pill), Arguments (mapped args), GlobalThis,
		// and ObjectAccessorWrapper can reinterpret or throw on entries the
		// map still holds, so those bases must stay on the slow path.
		if(base instanceof JSObjectImpl jo && PROPIC_ELIGIBLE.get(jo.getClass())) {
			PropIC snap = ic;
			if(snap!=null && snap.owner==jo && !snap.entry.isRemoved()) {
				return snap.entry.resolveValue(jo);
			}
			// Slow path + publish on first miss for the own-property case.
			CustomLinkedMap.EntryImpl<String> ownEntry = jo.getEntry(memberName);
			if(ownEntry!=null) {
				ic = new PropIC(jo, ownEntry);
				return ownEntry.resolveValue(jo);
			}
			// Own-miss (prototype lookup or Java-fallback): don't cache; walk chain.
			return jo.getProperty(memberName, RuntimeUtil.UNDEFINED);
		}
		return RuntimeUtil.getProperty(env, base, memberName);
	}
	private static boolean isPrivateMemberName(String memberName) {
		return memberName!=null && !memberName.isEmpty() && memberName.charAt(0)=='#';
	}

	// Non-super write leg shared by evaluateAssign()'s plain and compound
	// assignment paths (and, like readProperty above, the short-circuit-
	// aware logical-assignment operators) - mirrors readProperty()'s
	// private-vs-generic branch.
	public void writeProperty(JSEnvironment env, Object base, Object value, JSInterpretedRuntimeContext context) {
		if(isPrivateMemberName(memberName)) {
			PrivateName pn = context.resolvePrivateName(memberName);
			RuntimeUtil.setPrivateField(base, pn, value);
			return;
		}
		RuntimeUtil.setProperty(env, base, memberName, value);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSEnvironment env = context.getEnvironment();
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}
			
			// Phase 1a: when sequence extensions are disabled, deepscan is provably
			// false (init() rejects it) and result.isSequence() can never be true,
			// so short-circuit the volatile-ish sequence checks.
			if(!sequenceEnabled || (!result.isSequence() && !deepscan)) {
				Object base = result.getValue();
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member {0} is null or undefined, {1}", memberName, node.getNodeString());
					}
					result.setChainingNull();
				} else {
					result.setValue(readProperty(env, base, context));
				}
				return Signal.NONE;
			}

			JSResult sequence = result.ejectAndSequence();
			forEachEntries(context, sequence, (base,index,getter,setter,remover) -> {
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member {0} is null or undefined, {1}", memberName, node.getNodeString());
					}
				} else {
					Object v = getter.get();
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						result.addToSequence(env,v);
					}
				}
			}, false);			
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

	
	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSEnvironment env = context.getEnvironment();

			// Propagate null chaining...
			if(result.isChainingNull()) {
				result.setNull();
				return Signal.NONE;
			}
			
			// Phase 1a: when sequence extensions are disabled, deepscan is provably
			// false (init() rejects it) and result.isSequence() can never be true,
			// so short-circuit the volatile-ish sequence checks.
			if(!sequenceEnabled || (!result.isSequence() && !deepscan)) {
				Object v = node.evaluateValue(context,result);
				// Must go through readProperty() (private-name-aware), not
				// the plain string-keyed RuntimeUtil.getProperty() - that
				// overload's private-member branch is a TRANSPILED-mode-only
				// fallback (see its own comment) and doesn't reach the
				// PrivateName token this interpreted `#name` reference
				// already resolved elsewhere (readProperty/evaluate()) -
				// `typeof this.#m` was throwing "class did not declare it"
				// even when `#m` genuinely was declared and registered, by
				// looking it up under the wrong key (test262
				// prod-private-method.js/prod-private-method-initialize-
				// order.js).
				result.setValue( RuntimeUtil.typeof(env,readProperty(env, v, context)) );
				return Signal.NONE;

			}
			
			if(USE_TRANSPILER_RUNTIME) {
//				RuntimeUtilTranspiler.memberTypeofSeq(env, result, deepscan, memberName);
				throw new IllegalStateException();
			} else {
				JSResult leftValue = result.ejectAndSequence();
				forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
					Object v = getter.get();
					result.addToSequence(env,RuntimeUtil.typeof(context.getEnvironment(),v));
				},false);
			}

			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		try {
			getNode().evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return;
			}
			
			// Phase 1a: when sequence extensions are disabled, deepscan is provably
			// false (init() rejects it) and result.isSequence() can never be true,
			// so short-circuit the volatile-ish sequence checks.
			if(!sequenceEnabled || (!result.isSequence() && !deepscan)) {
				Object base = result.getValue();
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member {0} is null or undefined",memberName);
					}
					result.setChainingNull();
				} else {
					// SuperProperty: the search starts at the home object's
					// prototype (base), but `this` for an invoked accessor - and
					// the target of a plain create/write - must remain the
					// current `this`, per spec.
					boolean isSuper = node instanceof ASTSuperMember;
					JSEnvironment jsEnv = context.getEnvironment();
					Object receiver = isSuper ? context.getThis() : base;
					if(assigner!=null) {
						Object oldValue = isSuper
								? RuntimeUtil.getPropertyWithReceiver(jsEnv, base, memberName, receiver)
								: readProperty(jsEnv, base, context);
						Object newValue = assigner.apply(oldValue);
						if(isSuper) {
							RuntimeUtil.setPropertyWithReceiver(jsEnv, base, memberName, newValue, receiver);
						} else {
							writeProperty(jsEnv, base, newValue, context);
						}
						result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue)  : newValue);
					} else {
						if(isSuper) {
							RuntimeUtil.setPropertyWithReceiver(jsEnv, base, memberName, rightValue, receiver);
						} else {
							writeProperty(jsEnv, base, rightValue, context);
						}
						result.setValue(rightValue);
					}
				}
			} else {
				JSResult sequence = result.ejectAndSequence();
				forEachEntries(context, sequence, (base,index,getter,setter,remover) -> {
					if(base==null || base==RuntimeUtil.UNDEFINED) {
						if(!nullop) {
							throw RuntimeUtil.typeError("Left part of member {0} is null or undefined, {1}", memberName, node.getNodeString());
						}
					} else {
						if(assigner!=null) {
							Object oldValue = getter.get();
							if(deepscan) {
								if(oldValue!=RuntimeUtil.NOT_AVAILABLE) {
									Object newValue = assigner.apply(oldValue);
									setter.accept(newValue);
									result.addToSequence(context.getEnvironment(),returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : newValue);
								}
							} else {
								if(oldValue==RuntimeUtil.NOT_AVAILABLE) {
									oldValue = RuntimeUtil.UNDEFINED;
								}
								Object newValue = assigner.apply(oldValue);
								setter.accept(newValue);
								result.addToSequence(context.getEnvironment(),returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : newValue);
							}
						} else {
							Object oldValue = getter.get();
							if(deepscan) {
								if(oldValue!=RuntimeUtil.NOT_AVAILABLE) {
									setter.accept(rightValue);
									result.addToSequence(context.getEnvironment(),rightValue);
								}
							} else {
								setter.accept(rightValue);
								result.addToSequence(context.getEnvironment(),rightValue);
							}
						}
					}
				}, true);
			}
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(node instanceof ASTSuperMember) {
				// SuperReferences may never be deleted - this must be checked before
				// anything else is evaluated (the super base, the property key, etc.)
				throw RuntimeUtil.referenceError("Unsupported reference to 'delete' super property");
			}
			JSEnvironment env = context.getEnvironment();
			node.evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return true;
			}
			
			// Phase 1a: when sequence extensions are disabled, deepscan is provably
			// false (init() rejects it) and result.isSequence() can never be true,
			// so short-circuit the volatile-ish sequence checks.
			if(!sequenceEnabled || (!result.isSequence() && !deepscan)) {
				Object base = result.getValue();
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member {0} is null or undefined, {1}", memberName, node.getNodeString());
					}
					return true;
				} else {
					boolean v = RuntimeUtil.deleteProperty(env, base, memberName);
					return v;
				}
			}
			
			JSResult sequence = result.ejectAndSequence();
			result.setValue(false);
			forEachEntries(context, sequence, (base,index,getter,setter,remover) -> {
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member {0} is null or undefined, {1}", memberName, node.getNodeString());
					}
				} else {
					boolean v = remover.getAsBoolean();
					if(v) {
						result.setValue(true);
					}
				}
			}, true);
			return (Boolean)result.getValue();
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	if(!isSequence()) {
        	return transpileChain(jsContext, this);
    	} else {
       		return StringFormat.format("memberSeq({0},{1},{2})", 
       				JSTranspiler.asResult(jsContext, node), 
       				deepscan,
       				ASTLiteral.encodeString(getMemberName()));
    	}
    }
    
    @Override
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	if(!isSequence()) {
    		// Private (#name) targets resolve their PrivateName through the
    		// current context, same as transpileChainingNode's privateGet -
    		// the generic RuntimeUtil.typeof(env,base,index) overload only
    		// knows ordinary string-keyed properties, never [[PrivateElements]]
    		// storage, so it always read back undefined for a private member.
    		if(isPrivateMemberName(memberName)) {
    			return StringFormat.format("privateTypeof({0},{1},{2})",
    					jsContext.getContextJavaName(),
    					JSTranspiler.asValue(jsContext,node),
    					ASTLiteral.encodeString(getMemberName()));
    		}
    		return StringFormat.format("typeof({0},{1})",
    				JSTranspiler.asValue(jsContext,node),
    				ASTLiteral.encodeString(memberName));
    	} else {
       		return StringFormat.format("memberTypeofSeq({0},{1},{2})", 
       				JSTranspiler.asResult(jsContext, node), 
       				deepscan,
       				ASTLiteral.encodeString(getMemberName()));
    	}
    }
    
    @Override
    public String transpileDeleteExpression(JSTranspilerGeneratorContext jsContext) {
    	if(node instanceof ASTSuperMember) {
    		// SuperReferences may never be deleted.
    		return "throwSuperDeleteReferenceError()";
    	}
    	if(!isSequence()) {
    		return StringFormat.format("delete({0},{1})", 
    				JSTranspiler.asValue(jsContext,node), 
    				ASTLiteral.encodeString(memberName));
    	} else {
       		return StringFormat.format("memberDeleteSeq({0},{1},{2})", 
       				JSTranspiler.asResult(jsContext, node), 
       				deepscan,
       				ASTLiteral.encodeString(getMemberName()));
    	}
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	if(!isSequence()) {
	    	String leftValue = JSTranspiler.asValue(jsContext, node);
	    	String memberName = ASTLiteral.encodeString(getMemberName());
	    	// Logical assignment operators (||=, &&=, ??=) must not evaluate the RHS
	    	// (nor attempt the PutValue step) at all when their short-circuit check
	    	// means the assignment never happens - pass it as a lazy Supplier.
	    	String rightArg = (type==ASSIGN_TYPE.EQUALS_OR || type==ASSIGN_TYPE.EQUALS_AND || type==ASSIGN_TYPE.EQUALS_NULLCOALESCING)
	    			? StringFormat.format("()->{0}", rightValue)
	    			: rightValue;
	    	// Private (#name) targets resolve their PrivateName through the
	    	// current context (see RuntimeUtil.private*), so they route through
	    	// a distinct, ctx-aware runtime function - one per ASSIGN_TYPE,
	    	// mirroring the ordinary ones (e.g. "assignAdd" -> "privateAssignAdd").
	    	if(isPrivateMemberName(getMemberName())) {
	    		String fnName = type.assignmentRuntimeFunction();
	    		String privateFnName = "private" + Character.toUpperCase(fnName.charAt(0)) + fnName.substring(1);
	    		return StringFormat.format("{0}({1},{2},{3},{4})",
	    				privateFnName,
	    				jsContext.getContextJavaName(),
	    				leftValue,
	    				memberName,
	    				rightArg);
	    	}
	    	// Every OTHER compound operator (+=, *=, ...) must also defer RHS
	    	// evaluation until after PutValue/GetValue's own null-base check has
	    	// run (spec 13.15.2: lval = GetValue(lref) happens BEFORE the RHS is
	    	// evaluated) - a plain Object argument here would let Java's eager
	    	// left-to-right call-argument evaluation run the RHS's side effects
	    	// (and observe them) before assignXXX's own body ever checks the
	    	// base for null, wrongly surfacing the RHS's own exception instead
	    	// of the spec-mandated TypeError (test262 S11.13.2_A7.*_T1/T3/T4 -
	    	// "Compound Assignment Operator evaluates its operands from left to
	    	// right"). EQUALS itself is unaffected: plain assignment's PutValue
	    	// null-check is correctly deferred until AFTER the RHS already runs
	    	// (ASTArrayMember.assignToResolved()'s identical distinction).
	    	boolean isIncDec = type==ASSIGN_TYPE.PREINC || type==ASSIGN_TYPE.POSTINC || type==ASSIGN_TYPE.PREDEC || type==ASSIGN_TYPE.POSTDEC;
	    	String memberRightArg = (type==ASSIGN_TYPE.EQUALS || isIncDec) ? rightArg : StringFormat.format("()->{0}", rightValue);
	    	if(node instanceof ASTSuperMember && type==ASSIGN_TYPE.EQUALS) {
	    		// SuperProperty PutValue (spec 6.2.5.6 step 6b/13.3.7.1): [[Set]]
	    		// must target the actual `this`, not leftValue (the home
	    		// object's prototype, used only to locate an existing property)
	    		// - mirrors evaluateAssign()'s isSuper branch above. Only plain
	    		// "=" is covered here; super's compound-assignment forms (+=,
	    		// ...) aren't exercised by any currently-passing test.
	    		return StringFormat.format("assignWithThis({0},{1},{2},{3})",
	    				leftValue,
	    				memberName,
	    				memberRightArg,
	    				JSTranspiler.thisRef(this));
	    	}
    		return StringFormat.format("{0}({1},{2},{3})",
    				type.assignmentRuntimeFunction(),
    				leftValue,
    				memberName,
    				memberRightArg);
    	} else {
       		return StringFormat.format("assignMemberSeq({0},{1},RuntimeUtil::{2},{3},{4})", 
       				JSTranspiler.asResult(jsContext, node), 
       				rightValue, 
       				type.assignmentRuntimeFunction(),
       				deepscan,
       				ASTLiteral.encodeString(getMemberName())
       		);
    	}
    }

//	@Override
//	public void compileForDeclStatement(JSOptimizerContext jsContext, String initNode) {
//		throw new IllegalStateException();
//	}
    
    //
    // Chaining node implementation
    //
    
//    obj.d?.a.e
//
//    ident('obj')
//    getMember(ident('obj'),d)
//    (tmp=getMember(ident('obj'),d),!=null) ? getMember(tmp,'a') : null
//    (tmp=getMember(ident('obj'),d),!=null) ? getMember(getMember(tmp,'a'),'e') : null
    
    @Override
	public String transpileChainedNode(JSTranspilerGeneratorContext jsContext) {
		return JSTranspiler.asValue(jsContext, node);
    }
    
    @Override
	public String transpileChainingNode(JSTranspilerGeneratorContext jsContext, String chain) {
    	if(isPrivateMemberName(memberName)) {
    		return StringFormat.format("privateGet({0},{1},{2})",
    				jsContext.getContextJavaName(),
    				chain,
    				ASTLiteral.encodeString(getMemberName()));
    	}
    	if(node instanceof ASTSuperMember) {
    		// SuperProperty [[Get]] (spec 13.3.7.1/6.2.5.5): the search starts
    		// at the home object's prototype (chain), but the receiver passed
    		// to an invoked getter must remain the actual `this` - mirrors
    		// readProperty()'s isSuper branch above. Needed even for a plain
    		// member read (not just a call) - e.g. `super.getter` invokes the
    		// parent's getter with the WRONG receiver otherwise.
    		return StringFormat.format("memberGetWithThis({0},{1},{2})",
    				chain,
    				ASTLiteral.encodeString(getMemberName()),
    				JSTranspiler.thisRef(this));
    	}
		return StringFormat.format("memberGet({0},{1})",
				chain,
				ASTLiteral.encodeString(getMemberName()));
    }
    
    @Override
	public String decompileExpression() {
    	StringBuilder b= new StringBuilder();
    	b.append(node.decompileExpression());
    	// ASTIdentifierFilter forces nullop internally, remove it
    	String op = (nullop && !(node instanceof ASTIdentifierFilter)) ? (deepscan? "?.." : "?.") : (deepscan? ".." : ".");
    	b.append(op);
    	b.append(memberName);
    	return b.toString();
	}
}
