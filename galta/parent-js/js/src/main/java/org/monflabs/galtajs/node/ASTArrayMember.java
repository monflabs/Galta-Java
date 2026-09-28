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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.MemberAccessor;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.util.StringFormat;


/**
 * Array Member Node.
 */
public class ASTArrayMember extends ASTNode implements ChainingNode, MemberNode {
	
	// TODO, to simplify the code
	private static final boolean USE_TRANSPILER_RUNTIME = false;

	// Pre-resolved LHS reference for correct assignment evaluation order (ECMAScript 13.15.2).
	// superThis is only meaningful when the base is a SuperProperty (see resolveReference()):
	// it's GetThisBinding()'s result, captured BEFORE the property-key expression is evaluated
	// (per spec MakeSuperPropertyReference), so a nested super() call in the key expression is
	// never reached when `this` is still uninitialized/TDZ'd.
	public record ResolvedReference(Object base, Object index, Object superThis) {}

	public boolean canResolveReference() {
		return singleIndexNode()!=null && !deepscan && !node.isSequence();
	}

	public ResolvedReference resolveReference(JSInterpretedRuntimeContext context, JSResult result) {
		getNode().evaluate(context, result);
		if(result.isChainingNull()) {
			return null;
		}
		Object base = result.getValue();
		if(nullop && (base==null || base==RuntimeUtil.UNDEFINED)) {
			// Optional chain short-circuit: see ASTArrayMember.evaluate().
			return null;
		}
		// Per spec (MakeSuperPropertyReference 13.3.7.1), GetThisBinding() runs
		// BEFORE the computed property-key expression is evaluated - so an
		// uninitialized `this` (TDZ, in a derived-class constructor before
		// super() runs) must throw before the key expression's own side effects
		// (e.g. a nested super() call) are ever observed. See test262
		// language/expressions/super/prop-expr-uninitialized-this-putvalue(-
		// compound-assign).js.
		Object superThis = (getNode() instanceof ASTSuperMember) ? context.getThis() : null;
		// The property key expression is evaluated (and can throw) before the base
		// is checked for null/undefined. Neither the null/undefined base check NOR
		// ToPropertyKey coercion of the result happens here: plain assignment (=)
		// must defer BOTH until after the right-hand side is evaluated (they're
		// part of PutValue, which runs last - 6.2.4.5 PutValue step 5a's
		// ToObject(base), test262 target-member-computed-reference-null(-undefined)
		// .js/target-super-computed-reference-null.js), while compound assignment
		// (*=, +=, etc.) must do both before the right-hand side (they're part of
		// the initial GetValue, 6.2.4.6 GetValue step 5a). See ASTAssign (plain -
		// defers to assignToResolved(), called after the RHS) vs. the compound
		// operators, which call coerceKey() explicitly right after this method
		// returns (still before their own RHS evaluation).
		Object idx = context.executeWithFilterContext(base, singleIndexNode(), result);
		return new ResolvedReference(base, idx, superThis);
	}

	// Coerces a resolved reference's index to a property key now (ToPropertyKey),
	// and validates the base is not null/undefined (ToObject) now too - both run
	// immediately for compound assignment (see resolveReference()'s comment),
	// unlike plain assignment where assignToResolved() performs the equivalent
	// check after the right-hand side has already been evaluated.
	public static ResolvedReference coerceKey(JSEnvironment env, ResolvedReference ref) {
		if(ref==null) {
			return null;
		}
		requireNonNullBase(ref);
		return new ResolvedReference(ref.base(), RuntimeUtil.toPropertyKey(env, ref.index()), ref.superThis());
	}

	private static void requireNonNullBase(ResolvedReference ref) {
		if(ref.base()==null || ref.base()==RuntimeUtil.UNDEFINED) {
			throw RuntimeUtil.typeError("Left part of index is null, {0}", ref.base());
		}
	}

	public void assignToResolved(JSInterpretedRuntimeContext context, ResolvedReference ref, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		// PutValue/GetValue's ToObject(base) check (6.2.4.5/6.2.4.6 step 5a): for
		// compound assignment this is a harmless repeat of coerceKey()'s already-
		// performed check; for plain assignment (assigner==null, called from
		// ASTAssign with an uncoerced ref) this is the FIRST time base is
		// validated, correctly deferred until after the right-hand side ran.
		requireNonNullBase(ref);
		JSEnvironment env = context.getEnvironment();
		// SuperProperty: the search starts at the home object's prototype
		// (ref.base), but `this` for an invoked accessor - and the target of a
		// plain create/write - must remain the current `this`, per spec. Reuse
		// the `this` already captured (and TDZ-checked) by resolveReference()
		// BEFORE the property key was evaluated - re-fetching it here would be
		// too late (and, if the RHS itself calls super(), could observe a
		// DIFFERENT, now-initialized `this` than the one the Reference was
		// actually formed with).
		boolean isSuper = getNode() instanceof ASTSuperMember;
		Object receiver = isSuper ? ref.superThis() : ref.base;
		if(assigner!=null) {
			Object oldValue = isSuper
					? RuntimeUtil.getPropertyWithReceiver(env, ref.base, ref.index, receiver)
					: RuntimeUtil.getProperty(env, ref.base, ref.index);
			Object newValue = assigner.apply(oldValue);
			if(isSuper) {
				RuntimeUtil.setPropertyWithReceiver(env, ref.base, ref.index, newValue, receiver);
			} else {
				RuntimeUtil.setProperty(env, ref.base, ref.index, newValue);
			}
			result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : newValue);
		} else {
			if(isSuper) {
				RuntimeUtil.setPropertyWithReceiver(env, ref.base, ref.index, rightValue, receiver);
			} else {
				RuntimeUtil.setProperty(env, ref.base, ref.index, rightValue);
			}
			result.setValue(rightValue);
		}
	}

	// Maybe this node is not necessary - we just need start
	public static class Index extends ASTNode {
		ASTNode start;
		Index(Token t, ASTNode start) {
			super(t);
			this.start = assignParent(start);
		}
		
		@Override
		public int getChildCount() {
			return super.getChildCount()+1;
		}
		@Override
		public ASTNode getChild(int index) {
			switch(index) {
				case 0 ->	{ return start; }
				default ->	{ return super.getChild(index-1); }
			}
		}
		@Override
		protected void _setChild(int index, ASTNode node) {
			switch(index) {
				case 0 ->	{ this.start = node; }
				default ->  { super._setChild(index-1,node); }
			}
		}

	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}

	    @Override
		public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
	    	return start.evaluateValue(context, result);
	    }

	    @Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    		return JSTranspiler.asValue(jsContext, start);
	    }
	    @Override
		public String decompileExpression() {
			return start.decompileExpression();
		}
	}
	public static class Range extends Index {
		ASTNode end;
		ASTNode step;
		Range(Token t, ASTNode start, ASTNode end, ASTNode step) {
			super(t,start);
			this.end = assignParent(end);
			this.step = assignParent(step);
		}
		
		@Override
		public int getChildCount() {
			return super.getChildCount() + 2;
		}
		@Override
		public ASTNode getChild(int index) {
			switch(index) {
				case 0 ->	{ return end; }
				case 1 ->	{ return step; }
				default ->	{ return super.getChild(index-2); }
			}
		}
		@Override
		protected void _setChild(int index, ASTNode node) {
			switch(index) {
				case 0 ->	{ this.end = node; }
				case 1 ->	{ this.step  = node; }
				default ->  { super._setChild(index-2,node); }
			}
		}	
		
		@Override
		public Object evaluateValue(JSInterpretedRuntimeContext context, JSResult result) {
			Integer startValue = start!=null ? RuntimeUtil.toInt32(context.getEnvironment(), start.evaluateValue(context, result)) : null;
			Integer endValue = end!=null ? RuntimeUtil.toInt32(context.getEnvironment(), end.evaluateValue(context, result)) : null;
			Integer stepValue = step!=null ? RuntimeUtil.toInt32(context.getEnvironment(), step.evaluateValue(context, result)) : null;
			return JSTranspiledUnit.range(startValue, endValue, stepValue);
		}
	    
	    @Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
	    	return StringFormat.format("range({0},{1},{2})", 
	    				start!=null ? JSTranspiler.asInt32(jsContext, start) : "null", 
	    				end!=null ? JSTranspiler.asInt32(jsContext, end) : "null", 
	    				step!=null ? JSTranspiler.asInt32(jsContext, step) : "null");
	    }
	    @Override
		public String decompileExpression() {
	    	StringBuilder b = new StringBuilder();
	    	if(start!=null) {
				b.append(start.decompileExpression());
	    	}
			b.append(":");
	    	if(end!=null) {
				b.append(end.decompileExpression());
	    	}
	    	if(step!=null) {
				b.append(":");
				b.append(step.decompileExpression());
	    	}
			return b.toString();
		}
	}

	private ASTNode node;
	private boolean deepscan;
	private boolean nullop;
	private List<Index> indexes = new ArrayList<>();
		
	// optimizations
	// Whether this is a single plain index: the index node itself is read from
	// indexes on each use, since an optimizer can replace it (constant folding)
	private boolean singleIndex;
	private ASTNode singleIndexNode() {
		return singleIndex ? indexes.get(0).start : null;
	}

	public ASTArrayMember(Token t, ASTNode node, boolean deepscan, boolean nullop) {
		super(t);
		this.node = assignParent(node);
		this.deepscan = deepscan;
		this.nullop = nullop;
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return STATEMENT_TYPE.EXPRESSION;
	}	

	@Override
	public boolean isSequence() {
		return node.isSequence() || deepscan || singleIndexNode()==null;
	}	

	@Override
	public String getNodeString() {
		if(nullop) {
			return deepscan? "..?[]" : "?[]";
		}
		return deepscan? "..[]" : "[]";
	}


	@Override
	public int getChildCount() {
		return super.getChildCount()+indexes.size()+1;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index==0) {
			return node;
		}
		if(index<=indexes.size()) {
			return indexes.get(index-1);
		}
		return super.getChild(index-indexes.size()-1);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index==0) {
			this.node = node;
			return;
		}
		if(index<=indexes.size()) {
			indexes.set(index-1,(Index)node);
			return;
		}
		super._setChild(index-indexes.size()-1, node);
	}
	
	@Override
	public ASTNode getNode() {
		return node;
	}
	
	@Override
	public boolean isSingleIndex() {
		return singleIndexNode()!=null;
	}

	@Override
	public Object getSingleValue(JSInterpretedRuntimeContext context, Object base) {
		Object singleIndex;
		if(singleIndexNode() instanceof ASTLiteral lit) {
			singleIndex = lit.getValue();
		} else if(singleIndexNode() instanceof ASTIdentifier ident) {
			singleIndex = RuntimeUtil.getIdentifierValue(context, ident.getId(), true);
		} else {
			singleIndex = context.executeWithFilterContext(base, singleIndexNode(), new JSResult());
		}
		return RuntimeUtil.getPropertyUnavailable(context.getEnvironment(), base, singleIndex); 
	}

	@Override
	public void forEachEntries(JSInterpretedRuntimeContext context, JSResult sequence, MemberAccessor accessor, boolean forUpdate) {
		if(deepscan) {
			sequence.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(context.getEnvironment(),v,res);
			});
		}

		// Currently we force the filter context, but the JSON path specs says that the filter is only set
		// when the brackets are like [(expression)].
		// In GaltaJS, we also support [expression] like Java script
		
		// TODO: Can we use the code in GaltaJSClass? (addSequenceMember)

		sequence.forEach( (base) -> {
			JSEnvironment env = context.getEnvironment();
			if(base==null) { // Silent null chaining
				return;
			}
			context.executeWithFilterContext(base, () -> {
				if(singleIndexNode()!=null) {
					Object singleIndex;
					if(singleIndexNode() instanceof ASTLiteral lit) {
						singleIndex = lit.getValue();
					} else if(singleIndexNode() instanceof ASTIdentifier ident) {
						singleIndex = RuntimeUtil.getIdentifierValue(context, ident.getId(), true);
					} else {
						singleIndex = singleIndexNode().evaluateValue(context, new JSResult());
					}
					if(singleIndex instanceof Number n) {
						long longIndex = n.longValue();
						if(longIndex<0) {
							JSArray list = RuntimeUtil.getArrayLike(env, base);
							singleIndex = longIndex + list.arrayLength();
						}
					}
					final Object staticIndex = singleIndex;
		    		accessor.apply(base, staticIndex,
	    				() -> RuntimeUtil.getPropertyUnavailable(env, base, staticIndex), 
	    				forUpdate ? (val) -> RuntimeUtil.setProperty(env, base, staticIndex, val) : null,
	    				forUpdate ? ()  -> RuntimeUtil.deleteProperty(env, base, staticIndex) : null
	    		    );
				} else {
					JSResult temp = new JSResult();
					int nIndex = indexes.size();
					for(int i=0; i<nIndex; i++) {
						Index idx = indexes.get(i);
						if(idx instanceof Range range) {
							Object start=null, end=null, step=null;
							if(range.start!=null) { // start can be null with [:]
								start = range.start.evaluateValue(context,temp);
								if(start!=null && !(start instanceof Number)) {
									throw RuntimeUtil.typeError("Start range is not a number, {0}", start);
								}
							}
							if(range.end!=null) {
								end = range.end.evaluateValue(context,temp);
								if(end!=null && !(end instanceof Number)) {
									throw RuntimeUtil.typeError("End range is not a number, {0}", end);
								}
							}
							if(range.step!=null) {
								step = range.step.evaluateValue(context,temp);
								if(step!=null && !(step instanceof Number)) {
									throw RuntimeUtil.typeError("Step is not a number, {0}", end);
								}
							}
							
							JSArray list = RuntimeUtil.getArrayLikeUnchecked(env, base, true);
							if(list!=null) {
								long ti = step!=null ? RuntimeUtil.toLong(env,step) : 1;
								if(ti==0) {
									throw RuntimeUtil.rangeError("Step cannot be zero, {0}", step);
								}
								// Note: the default value of si and ei depends on ti
								// If ti<0, then we should inverse them as ut starts from the end
								long size = list.arrayLength();
								long si = start!=null ? RuntimeUtil.toLong(env,start) : (ti>0?0:size);
								if(si<0) {
									si = Math.max( 0, size + si);
								}
								long ei = end!=null ? RuntimeUtil.toLong(env,end) : (ti>0?size:0);
								if(ei<0) {
									ei = Math.max( 0, size + ei);
								}
								if(!forUpdate) {
									// We optimize read as there is no need to check out side of the boundaries
									// For update, we can create indexes outside of the existing range
									si = Math.min(si, size);
									ei = Math.min(ei, size);
								}
								if(ti>0) {
									for(long n=si; n<ei; n+=ti) {
										final long memberIndex = n;
							    		accessor.apply(base, memberIndex,
							    				() -> (memberIndex>=0 && memberIndex<list.arrayLength()) ? list.getProperty(memberIndex,RuntimeUtil.NOT_AVAILABLE) : RuntimeUtil.NOT_AVAILABLE, 
							    				forUpdate ? (val) -> { 
							    					if(memberIndex>=0) { list.setOwnProperty(memberIndex,val); } 
							    				} : null,
							    				forUpdate ? ()  -> { 
							    					if(memberIndex>=0 && memberIndex<list.arrayLength()) {return list.arrayRemove(memberIndex);} return true; 
							    				} : null
							    		    );
									}
								} else {
									for(long n=si; n>=ei; n+=ti) {
										final long memberIndex = n;
							    		accessor.apply(base, memberIndex,
							    				() -> (memberIndex>=0 && memberIndex<list.arrayLength()) ? list.getProperty(memberIndex,RuntimeUtil.NOT_AVAILABLE) : RuntimeUtil.NOT_AVAILABLE, 
							    				forUpdate ? (val) -> { 
							    					if(memberIndex>=0) { list.setOwnProperty(memberIndex,val); } 
							    				} : null,
							    				forUpdate ? ()  -> { 
							    					if(memberIndex>=0 && memberIndex<list.arrayLength()) {return list.arrayRemove(memberIndex);} return true; 
							    				} : null
							    		    );
									}
								}
							}
						} else {
							Object memberIndex = idx.start.evaluateValue(context,temp);
							if(memberIndex instanceof Number n) {
								long longIndex = n.longValue();
								if(longIndex<0) {
									JSArray list = RuntimeUtil.getArrayLike(env, base);
									memberIndex = longIndex + list.arrayLength();
								}
							}
							final Object staticIndex = memberIndex;
				    		accessor.apply(base, staticIndex,
				    				() -> RuntimeUtil.getPropertyUnavailable(env, base, staticIndex), 
				    				forUpdate ? (val) -> RuntimeUtil.setProperty(env, base, staticIndex, val) : null,
				    				forUpdate ? ()  -> RuntimeUtil.deleteProperty(env, base, staticIndex) : null
				    		    );
						}
					}
				}
				return null;
			});
		});
	}

	@Override
	public boolean isNullOp() {
		return nullop;
	}

	public boolean isDeepscan() {
		return deepscan;
	}

	public List<Index> getIndexes() {
		return indexes;
	}
	
	public void addIndex(Token t, ASTNode start) {
		Index idx = assignParent(new Index(t,start)); 
		indexes.add(idx);
		singleIndex = indexes.size()==1;
	}
	
	public void addIndex(Token t, ASTNode start, ASTNode end, ASTNode step) {
		Range idx = assignParent(new Range(t,start,end,step));
		indexes.add(idx);
		singleIndex = false;
	}

	// GaltaJS's grammar shares the exact `[a, b, c]` syntax between its own
	// multi-index/sequence extension (ArrayIndexList() building one Index per
	// comma-separated item) and plain ECMAScript's comma operator inside a
	// computed member expression (`obj[a, b]` === `obj[(a, b)]`, i.e. a single
	// property key: all evaluated left-to-right, only the last value used).
	// When GaltaJS extensions are disabled, the multi-index/sequence meaning
	// isn't available at all - so a plain (no ":" slicing, no deep-scan)
	// comma-separated index list must fall back to the standard ECMAScript
	// reading instead of unconditionally throwing "requires Galta extensions"
	// (see test262 language/expressions/optional-chaining/
	// optional-chain-prod-expression.js: `arr?.[0, 1]`). Collapse the indexes
	// into a single synthetic ASTExpression (the same node the parser already
	// builds for a real comma operator, e.g. Expression()) and reduce to one
	// singleIndexNode(), so the rest of this class's single-index code paths
	// handle it exactly like any other non-sequence member access - a slice
	// (Range) is always GaltaJS-only regardless, so it's left alone (still
	// throws when extensions are off, same as before).
	private void collapseCommaIndexesWhenExtensionsDisabled(InitContext initContext) {
		if(deepscan || indexes.size()<=1 || initContext.getEnvironment().supportSequenceExtensions()) {
			return;
		}
		for(Index idx : indexes) {
			if(idx instanceof Range) {
				return;
			}
		}
		List<ASTNode> starts = new ArrayList<>();
		for(Index idx : indexes) {
			starts.add(idx.start);
		}
		ASTNode commaExpr = new ASTExpression(starts);
		Index combined = assignParent(new Index(null, commaExpr));
		indexes = new ArrayList<>();
		indexes.add(combined);
		singleIndex = true;
	}

	@Override
	protected void init(InitContext initContext) {
		collapseCommaIndexesWhenExtensionsDisabled(initContext);
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSEnvironment env = context.getEnvironment();
			getNode().evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}

			// Simple member access (no sequence)
			if(!result.isSequence() && singleIndexNode()!=null && !deepscan) {
				Object leftValue = result.getValue();
				if(nullop && (leftValue==null || leftValue==RuntimeUtil.UNDEFINED)) {
					// Optional chain short-circuit (spec: OptionalChain ?. [Expression]):
					// once the base is null/undefined, the property key expression must
					// NOT be evaluated at all.
					result.setChainingNull();
					return Signal.NONE;
				}
				// Per spec (MakeSuperPropertyReference 13.3.7.1), GetThisBinding()
				// runs BEFORE the computed property-key expression is evaluated -
				// so an uninitialized `this` (TDZ, in a derived-class constructor
				// before super() runs) must throw before the key expression's own
				// side effects (e.g. a nested super() call) are ever observed. See
				// test262 language/expressions/super/prop-expr-uninitialized-
				// this-getvalue.js.
				boolean isSuper = getNode() instanceof ASTSuperMember;
				Object superThis = isSuper ? context.getThis() : null;
				// The property key expression is evaluated (and can throw) before the
				// base is checked for null/undefined - per spec, that check only
				// happens when the resulting Reference is actually dereferenced.
				Object idx = context.executeWithFilterContext(leftValue, singleIndexNode(), result );
				if(leftValue==null || leftValue==RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("Left part of index is null, {0}", getNode().getNodeString());
				}
				// SuperProperty: the search starts at the home object's prototype
				// (leftValue), but `this` for an invoked getter must remain the
				// current `this`, per spec.
				Object v = isSuper
						? RuntimeUtil.getPropertyWithReceiver(env, leftValue, idx, superThis)
						: RuntimeUtil.getProperty(env, leftValue, idx);
				result.setValue(v);
				return Signal.NONE;
			}

			if(USE_TRANSPILER_RUNTIME) {
//				Function<Object,Object>[] indexValues = indexValues(context);
//				RuntimeUtilTranspiler.memberSeq(env, result, deepscan, indexValues);
				throw new IllegalStateException();
			} else {
				JSResult leftValue = result.ejectAndSequence();
				forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
					// Don't access the string with an index if in a sequence
					if((base instanceof CharSequence) && (index instanceof Number) ) {
						return;
					}
					Object v = getter.get();
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						result.addToSequence(context.getEnvironment(),v);
					}
				},false);
			}

			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
//	private Function<Object,Object>[] indexValues(JSInterpretedRuntimeContext context) {
//		JSResult temp = new JSResult();
//		@SuppressWarnings("unchecked")
//		Function<Object,Object>[] indexValues = new Function[indexes.size()];
//		for(int i=0; i<indexValues.length; i++) {
//			final int ii = i;
//			indexValues[i] = (base) -> context.executeWithFilterContext(base, indexes.get(ii), temp);
//		}
//		return indexValues;
//	}
	
	@Override
	public Signal evaluateTypeof(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			JSEnvironment env = context.getEnvironment();
			getNode().evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				result.setNull();
				return Signal.NONE;
			}

			if(!result.isSequence() && singleIndexNode()!=null && !deepscan) {
				Object base = result.getValue();
				Object idx = context.executeWithFilterContext(base, singleIndexNode(), result );
				Object v = RuntimeUtil.getProperty(env, base, idx);
				result.setValue(RuntimeUtil.typeof(env,v));
				return Signal.NONE;
			}

			if(USE_TRANSPILER_RUNTIME) {
//				Function<Object,Object>[] indexValues = indexValues(context);
//				RuntimeUtilTranspiler.memberTypeofSeq(env, result, deepscan, indexValues);
				throw new IllegalStateException();
			} else {
				JSResult leftValue = result.ejectAndSequence();
				forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
					if((base instanceof CharSequence) && (index instanceof Number) ) {
						return;
					}
					Object v = getter.get();
					result.addToSequence(context.getEnvironment(),RuntimeUtil.typeof(env,v));
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
			JSEnvironment env = context.getEnvironment();
			getNode().evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return;
			}

			// Simple member access (no sequence)
			if(!result.isSequence() && singleIndexNode()!=null && !deepscan) {
				Object base = result.getValue();
				// Per spec (MakeSuperPropertyReference 13.3.7.1), GetThisBinding()
				// runs BEFORE the computed property-key expression is evaluated -
				// so an uninitialized `this` (TDZ, in a derived-class constructor
				// before super() runs) must throw before the key expression's own
				// side effects (e.g. a nested super() call) are ever observed. See
				// test262 language/expressions/super/prop-expr-uninitialized-
				// this-putvalue-increment.js (the only caller reaching this
				// method for a SuperProperty target - see ASTPreInc/ASTPostInc).
				boolean isSuper = getNode() instanceof ASTSuperMember;
				Object superThis = isSuper ? context.getThis() : null;
				// The property key expression is evaluated (and can throw) before the
				// base is checked for null/undefined - per spec, that check only
				// happens when the resulting Reference is actually dereferenced.
				Object idx = context.executeWithFilterContext(base, singleIndexNode(), result );
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of index is null, {0}");
					}
					result.setChainingNull();
					return;
				}
				// SuperProperty: the search starts at the home object's prototype
				// (base), but `this` for an invoked accessor - and the target of
				// a plain create/write - must remain the current `this`, per spec.
				Object receiver = isSuper ? superThis : base;
				if(assigner!=null) {
					// Convert to a property key once: getProperty/setProperty each do
					// this conversion internally, and calling it twice would evaluate
					// a side-effecting key (e.g. toString()) twice.
					Object key = RuntimeUtil.toPropertyKey(env, idx);
					Object oldValue = isSuper
							? RuntimeUtil.getPropertyWithReceiver(env, base, key, receiver)
							: RuntimeUtil.getProperty(env, base, key);
					Object newValue = assigner.apply(oldValue);
					if(isSuper) {
						RuntimeUtil.setPropertyWithReceiver(env, base, key, newValue, receiver);
					} else {
						RuntimeUtil.setProperty(env, base, key, newValue);
					}
					result.setValue(returnOriginalValue!=null ? returnOriginalValue.apply(oldValue) : newValue);
				} else {
					if(isSuper) {
						RuntimeUtil.setPropertyWithReceiver(env, base, idx, rightValue, receiver);
					} else {
						RuntimeUtil.setProperty(env, base, idx, rightValue);
					}
					result.setValue(rightValue);
				}
				return;
			}

//			Function<Object,Object>[] indexValues = indexValues(context);
//			MemberAssigner acc = RuntimeUtil::assign;
//			RuntimeUtilTranspiler.assignMemberSeq(env, result, rightValue, acc, deepscan, indexValues);

			JSResult leftValue = result.ejectAndSequence();
			forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					if(!nullop) {
						throw RuntimeUtil.typeError("Left part of member is null, {0}", node.getNodeString());
					}
					return;
				}
				// Don't access the string with an index if in a sequence
				if((base instanceof CharSequence) && (index instanceof Number) ) {
					return;
				}
				if(assigner!=null) {
					Object oldValue = getter.get();
					if(oldValue==RuntimeUtil.NOT_AVAILABLE) {
						oldValue = RuntimeUtil.UNDEFINED;
					}
					Object newValue = assigner.apply(oldValue);
					setter.accept(newValue);
					result.addToSequence(context.getEnvironment(),returnOriginalValue!=null ? returnOriginalValue.apply(oldValue)  : newValue);
				} else {
					setter.accept(rightValue);
					result.addToSequence(context.getEnvironment(),rightValue);
				}
			},true);

		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	@Override
	public boolean evaluateDelete(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			if(getNode() instanceof ASTSuperMember) {
				// SuperReferences may never be deleted - this must be checked before
				// anything else is evaluated (the super base, the property key, etc.)
				throw RuntimeUtil.referenceError("Unsupported reference to 'delete' super property");
			}
			JSEnvironment env = context.getEnvironment();
			getNode().evaluate(context,result);
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return true;
			}

			// Simple member access (no sequence)
			if(!result.isSequence() && singleIndexNode()!=null && !deepscan) {
				Object base = result.getValue();
				if(nullop && (base==null || base==RuntimeUtil.UNDEFINED)) {
					// Optional chain short-circuit: see evaluate() above.
					return true;
				}
				// The property key expression is evaluated (and can throw) before the
				// base is checked for null/undefined - per spec, that check only
				// happens when the resulting Reference is actually dereferenced.
				Object idx = context.executeWithFilterContext(base, singleIndexNode(), result );
				if(base==null || base==RuntimeUtil.UNDEFINED) {
					throw RuntimeUtil.typeError("Left part of index is null, {0}", getNode().getNodeString());
				}
				return RuntimeUtil.deleteProperty(env, base, idx);
			}
			
			if(USE_TRANSPILER_RUNTIME) {
//				Function<Object,Object>[] indexValues = indexValues(context);
//				return RuntimeUtilTranspiler.memberDeleteSeq(env, result, deepscan, indexValues);
				throw new IllegalStateException();
			} else {
				JSResult leftValue = result.ejectAndSequence();
				result.setValue(true);
					forEachEntries(context, leftValue, (base,index,getter,setter,remover) -> {
	//				if((base instanceof CharSequence) && (index instanceof Number) ) {
	//					result.setValue(true);
	//					return;
	//				}
						boolean v = remover.getAsBoolean();
						if(!v) {
							result.setValue(false);
						}
					},true);
					return (Boolean)result.getValue();
			}
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
       					transpileAllIndexes(jsContext));
    	}
    }
    
    @Override
    public String transpileTypeofExpression(JSTranspilerGeneratorContext jsContext) {
    	if(!isSequence()) {
    		return StringFormat.format("typeof({0},{1})", 
    				JSTranspiler.asValue(jsContext,node), 
    				JSTranspiler.asValue(jsContext,getUniqueIndex()));
    	} else {
       		return StringFormat.format("memberTypeofSeq({0},{1},{2})", 
   					JSTranspiler.asResult(jsContext, node),
   					deepscan,
   					transpileAllIndexes(jsContext));
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
    				JSTranspiler.asValue(jsContext,getUniqueIndex()));
    	} else {
       		return StringFormat.format("memberDeleteSeq({0},{1},{2})", 
       				JSTranspiler.asResult(jsContext, node), 
   					deepscan,
   					transpileAllIndexes(jsContext));
    	}
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	if(!isSequence()) {
			String leftValue = JSTranspiler.asValue(jsContext, node);
			String rawIndex = JSTranspiler.asValue(jsContext,getUniqueIndex());
	    	if(type==ASSIGN_TYPE.EQUALS) {
	    		// Plain assignment defers BOTH the null-base check and ToPropertyKey
	    		// coercion until after the right-hand side runs (they're part of
	    		// PutValue, which executes last) - see ASTArrayMember.
	    		// assignToResolved()'s identical comment. rawIndex's own side
	    		// effects (e.g. calling a function key) still happen here,
	    		// unconditionally, matching resolveReference()'s ordering.
	    		if(node instanceof ASTSuperMember) {
	    			// SuperProperty PutValue - see ASTMember.
	    			// transpileJavaAssignment's identical isSuper branch.
	    			return StringFormat.format("assignWithThis({0},{1},{2},{3})",
	    						leftValue,
	    						rawIndex,
	    						rightValue,
	    						JSTranspiler.thisRef(this));
	    		}
	    		return StringFormat.format("{0}({1},{2},{3})",
	    					type.assignmentRuntimeFunction(),
	    					leftValue,
	    					rawIndex,
	    					rightValue);
	    	}
	    	// Compound assignment (+=, *=, ...) must resolve the FULL reference -
	    	// null-base check THEN ToPropertyKey coercion of the key, in that
	    	// order (see RuntimeUtil.toPropertyKeyChecked()'s comment) - and read
	    	// the old value, all BEFORE the right-hand side is ever evaluated
	    	// (spec 13.15.2: lval = GetValue(lref) precedes "rref = Evaluation of
	    	// AssignmentExpression"). rawIndex is passed through UNCOERCED here -
	    	// the shared runtime assignXXX(Object,Object,Supplier) method does
	    	// the null-check+coercion itself, using its own already-evaluated
	    	// leftValue/member parameters, so `node` (the base) is only ever
	    	// transpiled/evaluated once (embedding a second toPropertyKeyChecked(
	    	// ...,leftValue,...) call INLINE here would re-evaluate `leftValue`'s
	    	// own Java source text a second time, wrongly re-running any of its
	    	// side effects, e.g. a base that's itself a function call). RHS is
	    	// always deferred to a lazy Supplier, so it never runs before that
	    	// check (test262 S11.13.2_A7.*_T1/T3/T4).
	    	// preInc/postInc/preDec/postDec take a plain (unused) 3rd argument,
	    	// not an RHS to defer - leave them as a raw value, not a Supplier.
	    	boolean isIncDec = type==ASSIGN_TYPE.PREINC || type==ASSIGN_TYPE.POSTINC || type==ASSIGN_TYPE.PREDEC || type==ASSIGN_TYPE.POSTDEC;
	    	String rightArg = isIncDec ? rightValue : StringFormat.format("()->{0}", rightValue);
    		return StringFormat.format("{0}({1},{2},{3})",
    					type.assignmentRuntimeFunction(),
    					leftValue,
    					rawIndex,
    					rightArg);
    	} else {
       		return StringFormat.format("assignMemberSeq({0},{1},RuntimeUtil::{2},{3},{4})", 
       				JSTranspiler.asResult(jsContext, node), 
       				rightValue, 
       				type.assignmentRuntimeFunction(),
       				deepscan,
       				transpileAllIndexes(jsContext));
    	}
    }
    
    // Public (not just private) so array-destructuring codegen elsewhere
    // (ASTArrayLiteral's iterator-assign fallback, a different package) can
    // resolve this target's own key expression separately from its base, to
    // preserve the spec-mandated "resolve target reference before stepping
    // the source iterator" ordering for a computed-member destructuring
    // element.
    public ASTNode getUniqueIndex() {
    	if(indexes.size()==1) {
    		Index index = indexes.get(0);
    		return index;
    	}
    	throw new JSTranspilerException(null,this,"Array access construct is not supported by the compiler");
    }
    
	public String transpileAllIndexes(JSTranspilerGeneratorContext jsContext) {
    	StringBuilder b = new StringBuilder();
    	for(int i=0; i<indexes.size(); i++) {
    		if(i>0) {
    			b.append(',');
    		}
    		Index idx = indexes.get(i);
        	int valueId = jsContext.generateUniqueId();
    		b.append(StringFormat.format("({0}{1}) -> {2}", 
    				JSTranspiler.CURRENT_VALUE, valueId, 
    				JSTranspiler.asValue(jsContext, valueId, idx)));
    	}
  		return b.toString();
    }


    
    //
    // Chaining node implementation
    //
    
    @Override
	public String transpileChainedNode(JSTranspilerGeneratorContext jsContext) {
		return JSTranspiler.asValue(jsContext, node);
    }
    
    @Override
	public String transpileChainingNode(JSTranspilerGeneratorContext jsContext, String chain) {
    	// TODO: Could optimize this for simpler expressions
    	int valueId = jsContext.generateUniqueId();
// 		return StringFormat.format("memberGet({0},({1}{2}) -> {3})", 
// 				chain, 
//				JSTranspiler.CURRENT_VALUE, valueId,
//				JSTranspiler.asValue(jsContext, valueId, getUniqueIndex()));
    	String value = JSTranspiler.asValue(jsContext, valueId, getUniqueIndex());
    	if(value.equals("null")) {
    		value = "(Object)null";
    	}
    	if(node instanceof ASTSuperMember) {
    		// SuperProperty [[Get]] (spec 13.3.7.1/6.2.5.5) - see ASTMember.
    		// transpileChainingNode's identical isSuper branch.
    		return StringFormat.format("memberGetWithThis({0},{1},{2})",
    				chain,
    				value,
    				JSTranspiler.thisRef(this));
    	}
 		return StringFormat.format("memberGet({0},{1})",
 				chain,
				value);
    }

    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append(node.decompileExpression());
		if(nullop) {
			b.append(deepscan ? "?..[" : "?.[");
		} else {
			b.append(deepscan ? "..[" : "[");
		}
    	for(int i=0; i<indexes.size(); i++) {
    		if(i>0) {
    			b.append(", ");
    		}
    		b.append(indexes.get(i).decompileExpression());
    	}
		b.append(']');
		return b.toString();
    }
}
