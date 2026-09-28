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
package org.monflabs.galtajs.node.call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTIdentifierFilter;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTSuperMember;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.ChainingNode;
import org.monflabs.galtajs.node.MemberNode;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.unaryop.ASTParen;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;


/**
 * Function call.
 */
public class ASTCall extends ASTBaseCall implements ChainingNode {
	
	private ASTNode node;
	private boolean nullop;

	public ASTCall(Token t, ASTNode node, List<ASTNode> parameters, boolean nullop) {
		super(t,parameters);
		this.node = assignParent(node);
		this.nullop = nullop;
	}
	
	@Override
	public boolean isSequence() {
		return this.node.isSequence();
	}

	// Parens preserve a Reference (spec: CoverParenthesizedExpression does not strip it),
	// so (a.b)() must still bind `this` to `a`, same as a.b().
	private static ASTNode unwrapParen(ASTNode n) {
		while(n instanceof ASTParen p) {
			n = p.getNode();
		}
		return n;
	}
	
	@Override
	public ASTNode getNode() {
		return node;
	}

	@Override
	public boolean isNullOp() {
		return nullop;
	}

	@Override
	public String getNodeString() {
		return nullop ? "?()" : "()";
	}

	@Override
	public int getChildCount() {
		return super.getChildCount() +1;
	}
	@Override
	public ASTNode getChild(int index) {
		if(index==0) {
			return node;
		}
		return super.getChild(index-1);
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index==0) {
			this.node = node;
			return;
		}
		super._setChild(index-1,node);
	}
	
	@Override
	public void init(InitContext initContext) {
		// This is @, then we force the nullop
		//   @.xxx should not generate an exception
		if(node instanceof ASTIdentifierFilter) {
			nullop = true;
		}
		
		super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			context.getGlobalContext().checkInterrupted();
			
			// Propagate null chaining...
			if(result.isChainingNull()) {
				return Signal.NONE;
			}

	    	if(unwrapParen(node) instanceof MemberNode m) {
	    		// Member call
				m.getNode().evaluate(context,result);
				if(!result.isSequence() && m.isSingleIndex()) {
					Object base = result.getValue();
					if(m.isNullOp() && (base==null || base==RuntimeUtil.UNDEFINED)) {
						// OptionalChain short-circuit: e.g. o?.bar(x) - once the base of
						// the (optional) member access is null/undefined, the whole
						// chain (including this call and its arguments) short-circuits.
						result.setChainingNull();
						return Signal.NONE;
					}
					Object callable = m.getSingleValue(context, base);
					if(callable==null || callable==RuntimeUtil.UNDEFINED || callable==RuntimeUtil.NOT_AVAILABLE) {
						if(!nullop) {
							// Arguments are evaluated (for side effects) before the
							// TypeError is thrown, per spec.
							evaluateParams(context, result);
							throw RuntimeUtil.typeError("Method does not exist, {0}, object class {1}",node.getNodeString(), base.getClass());
						}
						result.setChainingNull();
					} else {
						// SuperProperty: the lookup base is the [[HomeObject]]'s prototype,
						// but `this` for the call must remain the current `this`, per spec.
						Object thisArg = (m.getNode() instanceof ASTSuperMember) ? context.getThis() : base;
						result.setValue(call(context,callable,thisArg,result));
					}
				} else {
					JSResult clone = result.ejectAndSequence();
					JSResult temp = new JSResult();
					m.forEachEntries(context, clone, (base,index,getter,setter,remover) -> {
						Object callable = getter.get();
						if(callable==null || callable==RuntimeUtil.UNDEFINED || callable==RuntimeUtil.NOT_AVAILABLE) {
							if(!nullop) {
								throw RuntimeUtil.typeError("Method does not exist, {0}, object class {1}",node.getNodeString(), base.getClass());
							}
						} else {
							result.addToSequence(context.getEnvironment(),call(context,callable,base,temp));
						}
					},false);
				}
	    	} else {
	    		// Function call
				node.evaluate(context,result);
				if(!result.isSequence()) {
					Object callable = result.getValue();
					if(callable==null || callable==RuntimeUtil.UNDEFINED || callable==RuntimeUtil.NOT_AVAILABLE) {
						if(!nullop) {
							// Arguments are evaluated (for side effects) before the
							// TypeError is thrown, per spec.
							evaluateParams(context, result);
							throw RuntimeUtil.typeError("Function does not exist, {0}", node.getNodeString());
						}
						result.setChainingNull();
					} else {
						result.setValue(call(context,callable,RuntimeUtil.UNDEFINED,result));
					}
				} else {
					JSResult temp = new JSResult();
					result.reduceToSequence((callable,res) -> {
						if(callable==null || callable==RuntimeUtil.UNDEFINED || callable==RuntimeUtil.NOT_AVAILABLE) {
							if(!nullop) {
								throw RuntimeUtil.typeError("Function does not exist, {0}", node.getNodeString());
							}
						} else {
							res.addToSequence(context.getEnvironment(),call(context,callable,RuntimeUtil.UNDEFINED,temp));
						}
					});
				}
	    	}
			
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

	
	
	//
	// TODO With CTOR
	//
	
    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	ASTNode[] parameters = getParameters();
    	if(!node.isSequence()) {
    		return transpileChain(jsContext, this);
    	} else {
    		// In a sequence, it can only be a method call
        	if(node instanceof ASTMember m) {
	    		String base = JSTranspiler.asResult(jsContext, m.getNode());
		    	StringBuilder b = new StringBuilder(128);
		    	b.append("seqInvokeMethod(");
		    	b.append(JSTranspiler.MAIN_CONTEXT);
		    	b.append(",");
		    	b.append(base);
		    	b.append(",\"");
		    	b.append(m.getMemberName());
		    	b.append("\"");
		    	if(parameters.length>0) {
		    		b.append(",");
		    		b.append(transpileParams(jsContext,parameters,hasSpread(),null));
		    	}
		    	b.append(")");
		    	return b.toString();
        	} else {
        		throw new IllegalStateException();
        	}
    	}
    }
    
    private String transpileSpecialFunctions(JSTranspilerGeneratorContext jsContext) {
    	ASTNode[] parameters = getParameters();
    	if(node instanceof ASTIdentifier ident) {
    		String id = ident.getId();
    		boolean unitTests = jsContext.getTranspiler().getProperty(JSTranspiler.PROP_UNITTESTS, Boolean.FALSE);
    		if(unitTests && parameters.length==1) {
    			if(id.equals("assertDeclared")||id.equals("assertNotDeclared")) {
    				return getVariableNames(jsContext.getAllVariables(new HashMap<>(), true));
    			} else if(id.equals("assertDeclaredInScope") || id.equals("assertNotDeclaredInScope")) {
    				return getVariableNames(jsContext.getAllVariables(new HashMap<>(), false));
    			}
    		}
    		if(id.equals("eval") && !nullop) {
    			// Only a bare, literal `eval(...)` call is a DIRECT eval per
    			// spec - `eval?.(...)` (this same ASTIdentifier callee, just
    			// reached through an optional-chain call) must be INDIRECT
    			// (global-scope-only) instead. Falling through here (returning
    			// null, same as any other non-eval identifier) means no
    			// direct-eval VarAccessor[]/scope-flags metadata gets bundled,
    			// so StandardLibrary's eval handler correctly takes its
    			// existing indirect-eval branch instead - same as any other
    			// indirect form (`(0,eval)(...)`, `var e=eval; e(...)`).
    			// Confirmed via test262 eval-optional-call.js.
    			// Adding all the variables in not the most optimal, as we can get the variables
    			// using a linked in context. Vut eval() is bad and mostly used by the tests
    			// so the impact is negligible. And we save the linked list.
    			// The calling context's genuine strictness (never JSEnvironment's parse-dialect
    			// toggle) is baked in alongside the VarAccessor[] - StandardLibrary's eval handler
    			// reads it to force the eval'd text to parse as strict too, regardless of its own
    			// directive prologue. Bundled into a single Object[] expression (rather than a bare
    			// comma-separated 3rd argument) because this extraParam text is also spliced, as
    			// ONE argument, into transpileParams()'s spread-call codegen (paramBuilder(...)
    			// .value(extraParam)) - a top-level comma there would be parsed as two arguments to
    			// a single-arg method call, not two array elements.
    			// The next five booleans (whether THIS eval call site sits lexically inside the
    			// nearest enclosing function's own default-parameter-expression scope, inside a
    			// class field's own Initializer, inside ANY non-arrow function at all, inside a
    			// method, or inside a derived class's own constructor) are purely static AST
    			// properties of the call site itself - computed here, at transpile time, via the
    			// SAME AST-walking helpers StandardLibrary's interpreted-caller branch calls at
    			// runtime (made public there for this reuse).
    			String callerStrict = findParentNodeByClass(IContextRootContainer.class).isGenuinelyStrictMode() ? "true" : "false";
    			String callerInParamScope = StandardLibrary.isCallerInParameterExpressionScope(this) ? "true" : "false";
    			// Broader than callerInParamScope above: no arrow/"arguments"
    			// exemption - see StandardLibrary.isCallerInAnyParameterExpressionScope()'s
    			// own doc. Bundled at the END of evalArgs (index 9) alongside
    			// the lexical-context element, for the same "don't disturb
    			// existing fixed-index reads" reason as that element's own
    			// comment below.
    			String callerInAnyParamScope = StandardLibrary.isCallerInAnyParameterExpressionScope(this) ? "true" : "false";
    			String callerInFieldInit = StandardLibrary.isCallerInFieldInitializer(this) ? "true" : "false";
    			String callerHasNewTarget = StandardLibrary.isCallerInFunctionScope(this) ? "true" : "false";
    			String callerIsMethod = StandardLibrary.isCallerInMethod(this) ? "true" : "false";
    			String callerIsDerivedCtor = StandardLibrary.isCallerInDerivedClassConstructor(this) ? "true" : "false";
    			// The set of #name private names declared by any class lexically
    			// enclosing this eval call site - another purely static AST fact
    			// (PrivateBoundNames per declaring class never varies across
    			// evaluations, only the minted PrivateName TOKEN identity does -
    			// see PrivateNameValidator.collectEnclosingPrivateNames' own doc),
    			// baked in as a literal String[] for AllPrivateNamesValid's early-
    			// SyntaxError pre-scan of the eval'd source (ASTProgram.__init's
    			// PrivateNameValidator.check call) to actually run for a
    			// transpiled caller instead of being skipped entirely.
    			StringBuilder privateNamesLit = new StringBuilder("new String[]{");
    			boolean firstPn = true;
    			for(String pn: org.monflabs.galtajs.node.PrivateNameValidator.collectEnclosingPrivateNames(this)) {
    				if(!firstPn) {
    					privateNamesLit.append(",");
    				}
    				firstPn = false;
    				privateNamesLit.append(ASTLiteral.encodeString(pn));
    			}
    			privateNamesLit.append("}");
    			// The caller's own LEXICAL context - the literal `_ctx` Java
    			// variable in scope at this exact call site, per
    			// JSTranspiler.MAIN_CONTEXT - is needed so a direct eval can
    			// resolve `this`/[[HomeObject]] the same way ASTSuperMember/
    			// arrow-closure codegen does. `JSRuntimeContext.get()` (what
    			// StandardLibrary's eval handler used exclusively before)
    			// reads the AMBIENT/thread-local context instead, which for a
    			// static field initializer's own eval call is NOT updated by
    			// TranspiledFieldInitializerRuntimeContext's shadowing (a
    			// pure Java-local-variable rename, not an ambient-context
    			// push) - so `eval('this.g')` inside `static h = eval(...)`
    			// read the ENCLOSING initClass() method's own ambient `this`
    			// (the class, coincidentally, for a STATIC field - but never
    			// resolved through the correct per-field context, and simply
    			// wrong for the analogous instance-field case) instead of the
    			// class the static field initializer is actually running
    			// against. Bundled as this array's LAST element so existing
    			// fixed-index reads of the earlier elements are unaffected.
    			// Enclosing with-object(s) (nearest-first) visible at THIS eval call
			// site - see collectEnclosingWithJavaNames' own doc. Only appended (as
			// one more trailing element, index 10) when non-empty, so an eval call
			// NOT lexically inside any `with` emits byte-for-byte the same evalArgs
			// literal as before this existed.
			List<String> enclosingWiths = collectEnclosingWithJavaNames(jsContext);
			String withObjectsElem = "";
			if(!enclosingWiths.isEmpty()) {
				StringBuilder wb = new StringBuilder(",new Object[]{");
				for(int wi=0; wi<enclosingWiths.size(); wi++) {
					if(wi>0) {
						wb.append(",");
					}
					wb.append(enclosingWiths.get(wi));
				}
				wb.append("}");
				withObjectsElem = wb.toString();
			}
			String evalArgs = "new Object[]{"+getVariableJavaReferences(jsContext, jsContext.getAllVariables(new HashMap<>(), true))+","+callerStrict+","+callerInParamScope+","+callerInFieldInit+","+callerHasNewTarget+","+callerIsMethod+","+callerIsDerivedCtor+","+privateNamesLit+","+jsContext.getContextJavaName()+","+callerInAnyParamScope+withObjectsElem+"}";
        		if(parameters.length==0) {
        			// eval() with genuinely zero arguments: spec 12.3.4.1
        			// step ii "If argList has no elements, return undefined" -
        			// the synthesized "value" placeholder must be UNDEFINED,
        			// not Java null (which represents JS null, a different
        			// value). Confirmed via test262 eval-no-args.js.
        			return "UNDEFINED,"+evalArgs;
        		}
        		// Any number of real arguments (1, or more - e.g. eval("x=0;",
        		// ...iter), whose extra spread-expanded values eval() itself
        		// simply ignores per spec, using only argList's first
        		// element): the metadata is appended as one more trailing
        		// element regardless of how many real values precede it (see
        		// ASTBaseCall.transpileParams and StandardLibrary's matching
        		// args[args.length-1] read). Previously only length==1 was
        		// handled, so e.g. eval("x=0;", ...iter) fell through to
        		// ordinary (indirect) call codegen entirely. Confirmed via
        		// test262 eval-spread-empty-trailing.js.
        		return evalArgs;
    		}
    	}
    	return null;
    }
    private String getVariableNames(Map<String,VariableDef> vars) {
    	StringBuilder b = new StringBuilder();
    	int varCount = 0;;
    	b.append("createArray(");
    	for(VariableDef v: vars.values()) {
    		if(v.getVarType()!=VAR_TYPE.SYSTEM) {
    			if(varCount++>0) {
    				b.append(",");
    			}
	    		b.append(ASTLiteral.encodeString(v.getName()));
    		}
    	}
    	b.append(")");
    	return b.toString();
    }
    private String getVariableJavaReferences(JSTranspilerGeneratorContext jsContext, Map<String,VariableDef> vars) {
    	java.util.Set<String> ownScope = collectOwnScopeNames(jsContext);
    	StringBuilder b = new StringBuilder();
    	b.append("new VarAccessor[]{");
    	boolean first = true;
    	for(VariableDef v: vars.values()) {
    		if(v.getVarType()!=VAR_TYPE.SYSTEM) {
	    		if(first) {
	    			first = false;
	    		} else {
	    			b.append(",");
	    		}
	    		b.append("JSVarRef.of(");
	    		b.append(JSTranspiler.literal(v.getName()));
	    		b.append(",");
	    		b.append(v.getJavaVariableArray());
	    		b.append(",");
	    		b.append(v.getJavaVariableIndex());
	    		b.append(",VAR_TYPE.");
	    		b.append(v.getVarType().name());
	    		b.append(",");
	    		b.append(ownScope.contains(v.getName()));
	    		b.append(")");
    		}
    	}
    	b.append("}");
    	return b.toString();
    }

    // Names declared in the SAME function (walking OUT through any nested block
    // contexts within it, e.g. a `{ let y; eval(...) }` block - `var`/`function`
    // declarations always hoist to the nearest function's own context regardless
    // of block nesting, so including every block context's own names along the
    // way is still exactly "this function's own scope") that the eval call site
    // (`jsContext`) lexically sits in - or, if the eval call site is itself at
    // top level (no enclosing function at all), the whole global scope. Stops at
    // (but includes) the first function-boundary context reached (an actual new
    // Java method scope - see TranspilerGeneratorFunctionContext's own doc) or
    // the root context (getParent()==null). Used only to tag each bundled
    // free-variable entry with VarAccessor.isOwnScope() for the direct-eval
    // var-declaration-reuse decision - see that method's own doc for why the
    // distinction matters. Never affects ordinary identifier read/write codegen.
    private static java.util.Set<String> collectOwnScopeNames(JSTranspilerGeneratorContext jsContext) {
    	java.util.Set<String> names = new java.util.HashSet<>();
    	for(JSTranspilerGeneratorContext ctx=jsContext; ctx!=null; ctx=ctx.getParent()) {
    		names.addAll(ctx.getVariables().keySet());
    		if(ctx instanceof org.monflabs.galtajs.transpiler.context.TranspilerGeneratorFunctionContext || ctx.getParent()==null) {
    			break;
    		}
    	}
    	return names;
    }

    // Java local variable names (e.g. "with_7") of every `with`-object
    // LEXICALLY enclosing this eval call site, nearest-first - walks the full
    // CODEGEN context chain (unlike collectOwnScopeNames above, this does NOT
    // stop at a function boundary: a `with` can lexically enclose a nested
    // function, and a closure defined inside that function still resolves
    // free identifiers through the with-object, exactly the same cross-
    // function capture ASTIdentifier.getIdentifierReadAccessor()'s own
    // withParams walk already relies on for an ordinary identifier read/write
    // reached through such a closure - see ASTWith's own recent closure-
    // capture fix for why with_N stays visible to such a closure at all).
    // Unlike that per-identifier walk, this collects EVERY enclosing with
    // unconditionally, since an eval's own free identifiers aren't known
    // until its text is parsed at runtime - see StandardLibrary.
    // TranspiledEvalContext.resolveOwnIdentifierEntry() for how the runtime
    // checks these, nearest first, before its static free-variable bundle.
    // Empty (the overwhelming common case: an eval not lexically inside any
    // `with`) whenever no context in the chain is ever a with-context -
    // see transpileSpecialFunctions' own use of this result for why that
    // keeps codegen byte-for-byte unchanged for that case.
    private static java.util.List<String> collectEnclosingWithJavaNames(JSTranspilerGeneratorContext jsContext) {
    	java.util.List<String> names = null;
    	for(JSTranspilerGeneratorContext ctx=jsContext; ctx!=null; ctx=ctx.getParent()) {
    		String withVar = ctx.getWithJavaName();
    		if(withVar!=null) {
    			if(names==null) {
    				names = new java.util.ArrayList<>();
    			}
    			names.add(withVar);
    		}
    	}
    	return names!=null ? names : java.util.Collections.emptyList();
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
    	ASTNode[] parameters = getParameters();
    	ASTNode unwrapped = unwrapParen(node);
    	if(unwrapped instanceof MemberNode m) {
    		String base = JSTranspiler.asValue(jsContext,m.getNode());
    		//String base = JSTranspiler.asResult(jsContext,m.getNode());
    		boolean isSuper = m.getNode() instanceof ASTSuperMember;
    		// SuperProperty never reaches a PrivateIdentifier (not valid grammar),
    		// so isPrivateCall and isSuper are mutually exclusive.
    		boolean isPrivateCall = !isSuper && unwrapped instanceof ASTMember privMn && RuntimeUtil.isPrivateMemberName(privMn.getMemberName());
	    	// Only String-key invokeMethod has arity-N direct-arg overloads; the
	    	// Object-index form stays on the Object[] path, and so does a private
	    	// method call (privateInvokeMethod has only the Object[] overload).
	    	boolean allowDirect = (unwrapped instanceof ASTMember) && !isPrivateCall;

	    	// Plain o.bar(...) (dot-notation, not super, not private): per spec,
	    	// the property resolution that locates `bar` on `base` (which may
	    	// itself throw for a null/undefined base, or run arbitrary JS via a
	    	// Proxy `get` trap / accessor getter) must complete BEFORE the call's
	    	// own argument list is evaluated - only the *IsCallable* check is
	    	// allowed to run after the arguments (test262 language/expressions/
	    	// call/11.2.3-3_3.js: `o.bar.gar(foo())` must never call foo() when
	    	// `o.bar` is undefined). See JSTranspiledUnit.resolveMethodTarget's
	    	// own doc for the mechanism. resolveMethodTarget(...) is nested
	    	// directly as the first positional argument (not wrapped in a
	    	// lambda/executeIsolated) - Java's own left-to-right argument
	    	// evaluation already runs it before the arguments below, with no
	    	// shared mutable state of any kind involved, so this has none of
	    	// the reentrancy hazard the rejected TEMP_VAR design had (a single
	    	// shared field, clobbered whenever nested execution triggered by
	    	// THIS resolution itself performed another method call before the
	    	// field was read back) - nor the per-call lambda-allocation cost
	    	// wrapping it in executeIsolated used to pay for zero added safety
	    	// (executeIsolated is just `return supplier.get();`, RuntimeUtil.java).
	    	if(unwrapped instanceof ASTMember mn && !isSuper && !isPrivateCall) {
		    	StringBuilder b = new StringBuilder(128);
		    	b.append("invokeResolvedMethod(");
		    	b.append(jsContext.getContextJavaName());
		    	b.append(",resolveMethodTarget(");
		    	b.append(jsContext.getContextJavaName());
		    	b.append(",");
		    	b.append(base);
		    	b.append(",\"");
		    	b.append(mn.getMemberName());
		    	b.append("\"),\"");
		    	b.append(mn.getMemberName());
		    	b.append("\"");
		    	if(parameters.length>0) {
		    		b.append(",");
		    		b.append(transpileParams(jsContext,getParameters(),hasSpread(),null,allowDirect));
		    	} else if(!allowDirect) {
			    	b.append(",EMPTY_PARAMS");
		    	}
		    	b.append(")");
		    	return b.toString();
	    	}

	    	StringBuilder b = new StringBuilder(128);
	    	b.append(isPrivateCall ? "privateInvokeMethod(" : (isSuper ? "invokeMethodWithThis(" : "invokeMethod("));
	    	b.append(jsContext.getContextJavaName());
	    	b.append(",");
	    	b.append(base);
	    	if(isSuper) {
	    		b.append(",");
	    		// thisRef() not the local `_this`: a super.method() call can sit
	    		// after super() in a derived constructor, where that parameter has
	    		// been reassigned - see JSTranspiler.thisRef().
	    		b.append(JSTranspiler.thisRef(this));
	    	}
	    	if(unwrapped instanceof ASTMember mn) {
		    	b.append(",\"");
	    		b.append(mn.getMemberName());
		    	b.append("\"");
	    	} else if(unwrapped instanceof ASTArrayMember am) {
	    		if(!am.isSingleIndex()) {
	    			throw new IllegalStateException("Only a simple index is supported for now with call()");
	    		}
		    	b.append(",");
	    		// invokeMethod has both a String-key and an Object-index overload;
	    		// a bare `null` literal must be disambiguated to the Object one
	    		// (same fix as ASTArrayMember.transpileChainingNode), otherwise
	    		// javac silently binds it to the String overload and
	    		// RuntimeUtil.isPrivateMemberName's unguarded memberName.length()
	    		// NPEs instead of ToPropertyKey(null) coercing to the string
	    		// "null" (test262 cpn-class-decl-computed-property-name-from-
	    		// null.js and its fields-methods sibling).
	    		String index = JSTranspiler.asValue(jsContext,am.getIndexes().get(0));
	    		if(index.equals("null")) {
	    			index = "(Object)null";
	    		}
	    		b.append(index);
	    	} else {
    			throw new IllegalStateException("Internal error: should not be here");
	    	}
	    	if(parameters.length>0) {
	    		b.append(",");
	    		b.append(transpileParams(jsContext,getParameters(),hasSpread(),null,allowDirect));
	    	} else if(!allowDirect) {
		    	b.append(",EMPTY_PARAMS");
	    	}
	    	b.append(")");
	    	return b.toString();

    	} else {
    		String extraParam = null;
   			extraParam = transpileSpecialFunctions(jsContext);
	    	StringBuilder b = new StringBuilder(128);
	    	b.append("invokeFunction(");
	    	b.append(JSTranspiler.MAIN_CONTEXT);
	    	b.append(",");
	    	b.append(JSTranspiler.asValue(jsContext,node));

	    	// eval() special-case injects a VarAccessor[] as the second argument,
	    	// which StandardLibrary.GlobalFunction picks up via `args[1]` from the
	    	// Object[]; direct-arg would break that contract. All other function
	    	// calls can use the direct-arg fast path.
	    	boolean allowDirect = (extraParam == null);
	    	if(parameters.length>0 || extraParam!=null) {
	    		b.append(",");
	    		b.append(transpileParams(jsContext,getParameters(),hasSpread(),extraParam,allowDirect));
	    	} else if(!allowDirect) {
		    	b.append(",EMPTY_PARAMS");
	    	}
	    	b.append(")");
	    	return b.toString();
	    }
    }
    
    @Override
	public String decompileExpression() {
    	ASTNode[] parameters = getParameters();
    	StringBuilder b= new StringBuilder();
    	b.append(node.decompileExpression());
    	b.append(nullop?"?.(":"(");
    	for(int i=0; i<parameters.length; i++) {
    		if(i>0) {
    	    	b.append(", ");
    		}
        	b.append(parameters[i].decompileExpression());
    	}
    	b.append(")");
    	return b.toString();
	}    
}
