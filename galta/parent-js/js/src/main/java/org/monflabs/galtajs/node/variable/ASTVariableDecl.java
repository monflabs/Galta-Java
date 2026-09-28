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
package org.monflabs.galtajs.node.variable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.node.control.IContextRootContainer;
import org.monflabs.galtajs.node.control.IVarDeclarator;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.node.literal.ASTContainerLiteral;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.literal.ASTObjectLiteral;
import org.monflabs.galtajs.node.literal.DefaultRef;
import org.monflabs.galtajs.optimizer.JSOptimizerContext;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.util.TranspilerUtil;
import org.monflabs.galtajs.types.JSType;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;


/**
 * Scoped variable declaration.
 */
public abstract class ASTVariableDecl extends ASTNode {
	
	public class Entry extends ASTNode {
    	private ASTNode varDecl;
    	private ASTNode initNode;
    	
        public Entry(Token t, ASTNode varDecl, ASTNode init){
        	super(t);
            this.varDecl=assignParent(varDecl);
            this.initNode=assignParent(init);
        }
    	@Override
    	public String getNodeString() {
    		return varDecl.toString();
    	}
    	@Override
    	public void init(InitContext initContext) {
    		// If the right node is a function, then set a name
    		// If the function or class being defined is anonymous, but it is being assigned to a variable or object property, then its .name is inferred from that context.
    		// skipTransparent() unwraps a ParenthesizedExpression wrapper, which
    		// HasName/IsFunctionDefinition propagate through per spec - see ASTAssign.
    		ASTNode initNode = skipTransparent(this.initNode);
    		if(initNode instanceof ASTFunction fd && StringUtil.isEmpty(fd.getFunctionName())) {
    			if(varDecl instanceof ASTIdentifier id) {
    				fd.setFunctionName(id.getId());
    			}
    		} else if(initNode instanceof ASTClassDecl fd && StringUtil.isEmpty(fd.getClassName())) {
    			if(varDecl instanceof ASTIdentifier id) {
    				fd.setClassName(id.getId());
    			}
    		}

    		super.init(initContext);
    	}
    	
        @Override
		public int getChildCount() {
    		return super.getChildCount()+2;
    	}
    	@Override
    	public ASTNode getChild(int index) {
    		switch(index) {
    			case 0 ->	{ return varDecl; }
    			case 1 ->	{ return initNode; }
    			default ->	{ return super.getChild(index-2); }
    		}
    	}
    	@Override
		protected void _setChild(int index, ASTNode node) {
    		switch(index) {
    			case 0 ->	{ this.varDecl = node; }
    			case 1 ->	{ this.initNode  = node; }
    			default ->  { super._setChild(index-2,node); }
    		}
    	}
    	
    	public ASTNode getVarDecl(){
            return varDecl;
        }
        public ASTNode getInitNode(){
            return initNode;
        }
		public void assign(JSInterpretedRuntimeContext context, JSRuntimeContext declContext, JSResult result) {
			// ResolveBinding happens before the Initializer is evaluated, per
			// spec (VariableDeclaration : BindingIdentifier Initializer,
			// LexicalBinding : BindingIdentifier Initializer). Normally
			// unobservable, but a `with` block plus a `delete` inside the
			// Initializer that removes the very property the `with` had
			// bound this identifier to needs the ORIGINAL binding resolved
			// first - resolving after evaluation would fall through to an
			// outer/global binding instead. Only VAR/AUTO needs this: a var's
			// binding is already hoisted (so it exists to be resolved right
			// now, ahead of time), and it's the only kind resolution can walk
			// past a `with` object to reach in the first place - a lexical
			// `let`/`const` is always declared directly in the innermost
			// environment and can never be shadowed by an enclosing `with`.
			if(varDecl instanceof ASTIdentifier id) {
				VAR_TYPE varType = getVarType();
				if(varType==VAR_TYPE.VAR || varType==VAR_TYPE.AUTO) {
					VarAccessor acc = resolveVarWithinDeclContext(context, declContext, id.getId());
					Object value =  initNode!=null ? initNode.evaluateValue(context,result) : RuntimeUtil.UNDEFINED;
					if(acc!=null) {
						acc.setValue(value);
					} else {
						declContext.createVariable(id.getId(), value, varType);
					}
					return;
				}
			}
			Object value =  initNode!=null ? initNode.evaluateValue(context,result) : RuntimeUtil.UNDEFINED;
			assign(context, declContext, value, result);
		}
		public void assign(JSInterpretedRuntimeContext context, JSRuntimeContext declContext, Object value, JSResult result) {
			if(varDecl instanceof ASTIdentifier id) {
				VAR_TYPE varType = getVarType();
				if(varType==VAR_TYPE.VAR || varType==VAR_TYPE.AUTO) {
					// Resolve the normal way (which a `with` object property of the
					// same name can shadow), but bounded to declContext - unlike a
					// full unbounded chain walk, this must NOT continue past
					// declContext into an unrelated outer scope. For an ordinary
					// (non-eval) var, the binding is already hoisted exactly at
					// declContext, so this always finds it there (or earlier, via
					// `with`), identical to the old unbounded-walk behavior. Only a
					// direct eval's OWN var declaration (whose static hoisting ran
					// against its own disconnected program root, never touching the
					// real runtime chain - see JSInterpretedUnit.executeForEval)
					// can fail to find anything within this bound - falling through
					// to declContext.createVariable() lets BaseEvalContext's own
					// override (StandardLibrary.java) correctly implement
					// EvalDeclarationInstantiation (reuse a local binding, else
					// check the immediately-enclosing scope, else create fresh
					// there) instead of silently reusing an unrelated outer binding.
					VarAccessor acc = resolveVarWithinDeclContext(context, declContext, id.getId());
					if(acc!=null) {
						acc.setValue(value);
					} else {
						declContext.createVariable(id.getId(), value, varType);
					}
				} else {
					createVariable(declContext, id.getId(), value);
				}
	            return;
			} else if(varDecl instanceof ASTContainerLiteral lit) {
				// See ASTContainerLiteral.WithScopeResolvingFactory's own doc
				// comment: only a var-declaration's own binding target needs
				// the extra resolveBinding() probe (a with-shadowable ambient
				// chain walk, same as the simple-identifier case above) -
				// reuses the SAME resolveVarWithinDeclContext() bounded walk,
				// purely for its observable side effect (a `with` object's
				// Proxy `has` trap firing at the right point), discarding the
				// VarAccessor it returns since the actual write still goes
				// through createVariable()/declContext exactly as before.
	            lit.assign(context, new ASTContainerLiteral.WithScopeResolvingFactory() {
	            	@Override public void accept(String k, Object v) { createVariable(declContext, k, v); }
	            	@Override public void resolveBinding(String name) { resolveVarWithinDeclContext(context, declContext, name); }
	            }, value, result, false);
	            return;
			} else {
				throw RuntimeUtil.illegalState();
			}
		}
		// Bounded identifier resolution for VAR/AUTO declarations: walks from
		// `context` up to (and including) `declContext`, checking each level's
		// own bindings only (mirrors AbstractRuntimeContext.getVariableEntry's
		// per-level check: its own VariableMap, then resolveOwnIdentifierEntry()
		// - the hook `with` and a few other special contexts use) - but, unlike
		// that method, never continues past declContext. See the call sites
		// above for why this bound matters.
		private static VarAccessor resolveVarWithinDeclContext(JSRuntimeContext context, JSRuntimeContext declContext, String varName) {
			for(JSRuntimeContext ctx = context; ctx!=null; ctx = ctx.getParent()) {
				VarAccessor a;
				if(ctx==declContext && ctx instanceof org.monflabs.galtajs.rt.JSEvalRuntimeContext evalCtx && evalCtx.isStrictMode()) {
					// A STRICT eval's own var declaration always targets a
					// binding genuinely local to the eval's own
					// VariableEnvironment (EvalDeclarationInstantiation's
					// strict path never consults any outer scope at all) -
					// unlike the general getLocalVariableEntry() check below,
					// this must NOT match a transpiled eval's free-variable
					// VarAccessor bundle (StandardLibrary.TranspiledEvalContext
					// .resolveOwnIdentifierEntry() - needed for ordinary reads
					// of outer identifiers inside eval'd text, but not a
					// genuine local declaration of this eval's own). See
					// StandardLibrary.BaseEvalContext.createVariable()'s
					// matching fix (same reasoning, for the hoisting/
					// no-initializer path this method's own caller falls
					// through to when it returns null here).
					org.monflabs.galtajs.rt.interpreter.VariableMap ownMap = ctx.getVariableMap(false);
					a = ownMap!=null ? ownMap.getEntry(varName) : null;
				} else {
					a = ctx.getLocalVariableEntry(varName);
				}
				if(a!=null) {
					return a;
				}
				if(ctx==declContext) {
					return null;
				}
			}
			return null;
		}
		public void forEachVarName(Consumer<String> callback) {
			if(varDecl instanceof ASTIdentifier id) {
				callback.accept(id.getId());
			} else if(varDecl instanceof ASTContainerLiteral lit) {
				lit.forEachVarName(callback);
			} else {
				throw RuntimeUtil.illegalState();
			}
		}

	    @Override
		public JSType getReturnedType() {
	    	return JSType.UNKNOWN;
		}
	    
	    @Override
		public String decompileExpression() {
	    	StringBuilder b = new StringBuilder();
	    	b.append(varDecl.decompileExpression());
	    	if(initNode!=null) {
		    	b.append("=");
		    	b.append(initNode.decompileExpression());
	    	}
	    	return b.toString();
	    }
    }


    private ArrayList<Entry> entries = new ArrayList<Entry>();

    public ASTVariableDecl(Token t) {
    	super(t);
    }
    
    public abstract VAR_TYPE getVarType();
    
    public List<Entry> getEntries() {
    	return entries;
    }
    
	@Override
	public int getChildCount() {
		return super.getChildCount() + entries.size();
	}
	@Override
	public ASTNode getChild(int index) {
		if(index<entries.size()) {
			return entries.get(index);
		}
		return super.getChild(index-entries.size());
	}
	@Override
	protected void _setChild(int index, ASTNode node) {
		if(index<entries.size()) {
			entries.set(index,(Entry)node);
			return;
		}
		super._setChild(index-entries.size(), node);
	}

    public void add(ASTNode varDecl, ASTNode initialize){
        Entry e = new Entry(null,varDecl,initialize);
        assignParent(e);
        entries.add(e);
    }
   
    @Override
	protected void init(InitContext initContext) {
    	IContextBlockContainer varContainer = getVarType()==VAR_TYPE.VAR
				? findParentNodeByClass(IContextRootContainer.class)
				: findParentNodeByClass(IContextBlockContainer.class);
    	for(Entry e: entries) {
    		if(e.varDecl instanceof IVarDeclarator vd) {
    			vd.declareVariables(varContainer,getVarType());
    		}
    		e.forEachVarName((name) -> checkStrictBindingName(initContext,name,e));
    	}
    	super.init(initContext);
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
	        int count = entries.size();
	        if(count>0) {
	        	VAR_TYPE varType = getVarType();
	        	JSRuntimeContext declContext = getDeclContext(context);
	        	// A variable/lexical declaration's own completion is always empty
	        	// (UpdateEmpty semantics): evaluating each initializer expression
	        	// sets `result` as a side effect, so restore whatever value
	        	// `result` already had, rather than leaking the initializer's
	        	// value or forcing it to undefined.
	        	Object priorValue = result.deref();
		        for(int i=0; i<count; i++) {
		            Entry e = entries.get(i);
		            if(varType==VAR_TYPE.VAR || varType==VAR_TYPE.AUTO) {
		            	if(e.initNode!=null) {
							e.assign(context, declContext, result);
		            	}
		            } else {
						e.assign(context, declContext, result);
		            }
		        }
				result.setValue(priorValue);
	        }
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}
	}
	
	public List<String> getDeclaredVariables() {
		List<String> vars = new ArrayList<>();
        int count = entries.size();
	    for(int i=0; i<count; i++) {
	    	Entry e = entries.get(i);
	    	e.forEachVarName( (s) -> vars.add(s) );
	    }
	    return vars;
	}
	
	@Override
	public void evaluateAssign(JSInterpretedRuntimeContext context, Object rightValue, Function<Object, Object> assigner, JSResult result, Function<Object, Object> returnOriginalValue) {
		try {
			JSRuntimeContext declContext = getDeclContext(context);
            Entry e = entries.get(0);
            e.assign(context, declContext, rightValue, result);
            //createVariable(declContext, e.varName, value);
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}

	protected void createVariable(JSRuntimeContext declContext, String name, Object value) {
		// With 'var' keyword, we can override the variable if already created
		declContext.createVariable(name, value, VAR_TYPE.VAR);
	}

	protected abstract JSRuntimeContext getDeclContext(JSRuntimeContext context);
	

    @Override
	public JSType getReturnedType() {
    	return JSType.UNKNOWN;
	}

    
    
	//
	// Optimizer
	//
	
	@Override
	public void updateOptimizedContext(JSOptimizerContext context) {
        int count = entries.size();
        if(count>0) {
	        for(int i=0; i<count; i++) {
	            Entry e = entries.get(i);
	            ASTNode varDecl = e.varDecl;
				if(varDecl instanceof ASTIdentifier id) {
		            VariableDef variable = findVariable(id.getId());
		            if(variable!=null && !variable.getVarType().isDeclaredGlobally()) {
			            if(e.initNode!=null) {
			            	JSOptimizerContext.ContextVariable cv = resolveContextVariable(context, variable.getName());
			            	if(cv!=null) {
			            		cv.setInitNode(e.initNode);
			            	}
			            }
		            }
				} else if(varDecl instanceof ASTContainerLiteral lit) {
					lit.destructParameters( (container,defaultValue,path,spread) -> {
						String varName = ((ASTIdentifier)container).getId();
				        VariableDef variable = findVariable(varName);
			            if(variable!=null && !variable.getVarType().isDeclaredGlobally()) {
				            if(e.initNode!=null) {
				            	JSOptimizerContext.ContextVariable cv = resolveContextVariable(context, variable.getName());
				            	if(cv!=null) {
				            		cv.setInitNode(e.initNode);
				            	}
				            }
			            }
					}, RuntimeUtil.EMPTY_PARAMS, null, null, null);
				} else {
					throw RuntimeUtil.illegalState();
				}
	        }
        }
	}

	private static JSOptimizerContext.ContextVariable resolveContextVariable(JSOptimizerContext context, String name) {
		for(JSOptimizerContext ctx = context; ctx != null; ctx = ctx.getParent()) {
			JSOptimizerContext.ContextVariable cv = ctx.getVariables().get(name);
			if(cv != null) {
				return cv;
			}
		}
		return null;
	}


	//
	// Transpiler
	//
	
    @Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
        int count = entries.size();
        if(count>0) {
	        for(int i=0; i<count; i++) {
	            Entry e = entries.get(i);
	            ASTNode varDecl = e.varDecl;
				if(varDecl instanceof ASTIdentifier id) {
		            VariableDef variable = findVariable(id.getId());
		            // Var and system are already declared
		            if(!variable.getVarType().isDeclaredGlobally()) {
		            	variable.setTranspilerDeclared(true);
		            }
		            if(e.initNode!=null) {
		            	if(variable.getVarType()==VAR_TYPE.VAR) {
		            		// A `var` declaration's initializer is an ordinary
		            		// identifier assignment (spec: BindingInitialization
		            		// for var goes through ResolveBinding/PutValue, same
		            		// as a plain assignment expression) - unlike the
		            		// declaration/hoisting itself, this CAN be
		            		// intercepted by an enclosing `with` object that
		            		// happens to have its own same-named property.
		            		// Route through the same with-aware dynamic accessor
		            		// a plain assignment expression already uses,
		            		// instead of always writing directly to this var's
		            		// own static slot - a no-op difference when no
		            		// `with` is in scope (id.transpileJavaAssignment's
		            		// own fast path already reduces to the identical
		            		// direct write below in that case). test262
		            		// language/statements/with/S12.10_A1.10_T1.js and
		            		// neighbors: `with(myObj) { var value = 'value'; }`
		            		// where myObj has its own `value` property must
		            		// write THAT property, leaving the hoisted global
		            		// `value` untouched.
		            		b.println("{0};", id.transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS, JSTranspiler.asValue(jsContext,e.initNode), false, false));
		            	} else {
			            	b.println(StringFormat.format("{0} = {1};", variable.getJavaVariableValue(), JSTranspiler.asValue(jsContext,e.initNode)));
		            	}
		            } else if(variable.getVarType()==VAR_TYPE.LET) {
		            	// A bare `let x;` still clears the TDZ sentinel that
		            	// transpilerDeclareStatement pre-filled this slot with -
		            	// `const x;` is unreachable here (syntactically requires
		            	// an initializer).
		            	b.println("{0} = UNDEFINED;", variable.getJavaVariableValue());
		            }
				} else if(varDecl instanceof ASTObjectLiteral lit) {
					if(e.initNode==null) {
						// Parser gap (see JSParser.jj isLetDeclaration()'s own
						// comment): "let {"/"let [" is accepted as a
						// LexicalDeclaration even in bare-Statement-only
						// positions (for/while/with/if/labeled bodies) where a
						// Declaration isn't grammatically valid at all, so a
						// destructuring BindingPattern's normally-mandatory
						// Initialiser can end up null here. Every test262 file
						// hitting this constructs it as unreachable dead code
						// (e.g. `for (var x of []) let {}`), so codegen just
						// skips it - matching interpreted mode's own
						// (accidental) non-crash for these files, rather than
						// NPEing on the codegen below.
						continue;
					}
					String tmpVarName = jsContext.generateUniqueId("tmp");
					// 1. We calculate the value and store it temporarily
		            b.println(StringFormat.format("Object {0}={1};", tmpVarName, JSTranspiler.asValue(jsContext,e.initNode)));
	            	// RequireObjectCoercible: must reject null/undefined even
	            	// when the pattern is empty (`{}`), so this can't just be
	            	// left to fall out of the per-property destructuring below.
	            	b.println("requireObjectCoercible({0});", tmpVarName);
		            // 2. We assign the variables
					lit.destructParameters( (container,defaultValue,path,spread) -> {
						String varName = ((ASTIdentifier)container).getId();
				        VariableDef variable = findVariable(varName);
			            // Var and system are already declared
			            if(!variable.getVarType().isDeclaredGlobally()) {
			            	variable.setTranspilerDeclared(true);
			            }
			            // Spec's KeyedBindingInitialization (14.3.3.3) requires
			            // ResolveBinding (step 2) to run BEFORE GetV reads the
			            // source property (step 3) - for a `var` target
			            // specifically, ResolveBinding walks the running
			            // execution context's ambient scope chain, which an
			            // enclosing `with` object can intercept (observable via
			            // a Proxy `has` trap). Mirrors interpreted mode's
			            // ASTContainerLiteral.WithScopeResolvingFactory/
			            // resolveVarWithinDeclContext() - see its own doc - by
			            // calling the SAME with-aware accessor lookup a plain
			            // "var x = value" write already uses
			            // (getIdentifierWriteAccessor()'s runtime counterpart),
			            // purely for its has()-probing side effect, discarding
			            // the result (the actual write below still goes
			            // directly to this var's own declared slot, exactly as
			            // before). Only emitted when this declaration is
			            // lexically inside a `with` block - a no-op, zero extra
			            // codegen otherwise. `let`/`const` targets never need
			            // this (their binding always resolves directly to its
			            // own lexical slot, never through an ambient `with`
			            // chain), hence the VAR-only gate. Fixes test262
			            // destructuring/binding/keyed-destructuring-property-
			            // reference-target-evaluation-order-with-bindings.js.
			            if(variable.getVarType()==VAR_TYPE.VAR) {
			            	List<Object> withParams = null;
			            	for(JSTranspilerGeneratorContext ctx = jsContext; ctx!=null; ctx = ctx.getParent()) {
			            		if(ctx.getOwnVariable(varName)!=null) {
			            			break;
			            		}
			            		String withVar = ctx.getWithJavaName();
			            		if(withVar!=null) {
			            			if(withParams==null) {
			            				withParams = new ArrayList<>();
			            			}
			            			withParams.add(withVar);
			            		}
			            	}
			            	if(withParams!=null) {
			            		b.print("getIdentifierAccessor({0},\"{1}\",{2},{3},false", JSTranspiler.MAIN_CONTEXT, varName, variable.getJavaVariable(), variable.getJavaVariableIndex());
			            		for(Object w: withParams) {
			            			b.print(",{0}", w);
			            		}
			            		b.println(");");
			            	}
			            }
				        b.print("{0} = VarAccessor.destruct({1},{2}", variable.getJavaVariableValue(), JSTranspiler.MAIN_ENVIRONMENT, tmpVarName);
		        		if(path.length==0) {
			        		b.append(",null");
		        		} else if(path.length==1) {
			        		b.append(StringFormat.format(",{0}", ASTLiteral.encodeLiteral(jsContext,path[0])));
		        		} else {
					        b.print(",new Object[]{");
				        	for(int j=0; j<path.length; j++) {
				        		if(j>0) {
					        		b.print(",");
				        		}
				        		b.print("{0}", JSTranspiler.asValue(jsContext,path[j]));
				        	}
			        		b.print("}");
		        		}

			        	if(spread!=null) {
			        		b.print(",{0}", TranspilerUtil.encodeSpreadValue(jsContext,spread));
			        	} else {
			        		b.print(",null");
			        	}

		        		if(defaultValue!=null) {
		        		if(path.length>1) {
		        			b.print(",{0}", DefaultRef.transpileChain(jsContext,defaultValue));
		        		} else {
		        			b.print(",()->{0}", JSTranspiler.asValue(jsContext,defaultValue.node()));
		        		}
		        		}
		        		b.println(");");
					}, RuntimeUtil.EMPTY_PARAMS, jsContext, b, null);
				} else if(varDecl instanceof ASTArrayLiteral arr) {
					if(e.initNode==null) {
						// Same parser gap as the ASTObjectLiteral branch above
						// (`let [` variant) - see its comment.
						continue;
					}
					String tmpVarName = jsContext.generateUniqueId("tmp");
					// 1. We calculate the value and store it temporarily
		            b.println(StringFormat.format("Object {0}={1};", tmpVarName, JSTranspiler.asValue(jsContext,e.initNode)));
		            // 2. We assign the variables using real iterator protocol -
		            // always a BindingPattern here (var/let/const), never an
		            // AssignmentPattern.
		            arr.transpileJavaIteratorDestructure(jsContext, b, tmpVarName, /*isAssignmentContext*/ false, (target,valueExpr) -> {
						String varName = ((ASTIdentifier)target).getId();
				        VariableDef variable = findVariable(varName);
			            if(!variable.getVarType().isDeclaredGlobally()) {
			            	variable.setTranspilerDeclared(true);
			            }
			            b.println("{0} = {1};", variable.getJavaVariableValue(), valueExpr);
		            });
				} else {
					throw RuntimeUtil.illegalState();
				}
	        }
        }
    }

    @Override
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
    	// Used by for...of/in loops
		TranspilerJavaBuilder b = jsContext.createJavaBuilder();
		int count = entries.size();
		if(count>0) {
	        for(int i=0; i<count; i++) {
	            Entry e = entries.get(i);
	            ASTNode varDecl = e.varDecl;
				if(varDecl instanceof ASTIdentifier id) {
		            VariableDef variable = findVariable(id.getId());
		            // Var and system are already declared
		            if(!variable.getVarType().isDeclaredGlobally()) {
		            	variable.setTranspilerDeclared(true);
		            }
	            	b.println(StringFormat.format("{0} = {1};", variable.getJavaVariableValue(), rightValue));
				} else if(varDecl instanceof ASTObjectLiteral lit) {
					String tmpVarName = jsContext.generateUniqueId("tmp");
					// 1. We calculate the value and store it temporarily
		            b.println(StringFormat.format("Object {0}={1};", tmpVarName, rightValue));
	            	// RequireObjectCoercible - see transpileJavaStatement's
	            	// identical check above for why.
	            	b.println("requireObjectCoercible({0});", tmpVarName);
		            // 2. We assign the variables
					lit.destructParameters( (container,defaultValue,path,spread) -> {
						String varName = ((ASTIdentifier)container).getId();
				        VariableDef variable = findVariable(varName);
			            // Var and system are alreaady declared
			            if(!variable.getVarType().isDeclaredGlobally()) {
			            	variable.setTranspilerDeclared(true);
			            }
				        b.print("{0} = VarAccessor.destruct({1},{2}", variable.getJavaVariableValue(),JSTranspiler.MAIN_ENVIRONMENT,tmpVarName);
		        		if(path.length==0) {
			        		b.append(",null");
		        		} else if(path.length==1) {
			        		b.append(StringFormat.format(",{0}", ASTLiteral.encodeLiteral(jsContext,path[0])));
		        		} else {
					        b.print(",new Object[]{");
				        	for(int j=0; j<path.length; j++) {
				        		if(j>0) {
					        		b.print(",");
				        		}
				        		b.print("{0}", JSTranspiler.asValue(jsContext,path[j]));
				        	}
			        		b.print("}");
		        		}
			        	if(spread!=null) {
			        		b.print(",{0}", TranspilerUtil.encodeSpreadValue(jsContext,spread));
			        	} else {
			        		b.print(",null");
			        	}
		        		if(defaultValue!=null) {
		        		if(path.length>1) {
		        			b.print(",{0}", DefaultRef.transpileChain(jsContext,defaultValue));
		        		} else {
		        			b.print(",()->{0}", JSTranspiler.asValue(jsContext,defaultValue.node()));
		        		}
		        		}
		        		b.println(");");
					}, RuntimeUtil.EMPTY_PARAMS, jsContext, b, null);
				} else if(varDecl instanceof ASTArrayLiteral arr) {
					String tmpVarName = jsContext.generateUniqueId("tmp");
					// 1. We calculate the value and store it temporarily
		            b.println(StringFormat.format("Object {0}={1};", tmpVarName, rightValue));
		            // 2. We assign the variables using real iterator protocol -
		            // always a BindingPattern here (var/let/const for-of/for-in
		            // loop variable), never an AssignmentPattern.
		            arr.transpileJavaIteratorDestructure(jsContext, b, tmpVarName, /*isAssignmentContext*/ false, (target,valueExpr) -> {
						String varName = ((ASTIdentifier)target).getId();
				        VariableDef variable = findVariable(varName);
			            if(!variable.getVarType().isDeclaredGlobally()) {
			            	variable.setTranspilerDeclared(true);
			            }
			            b.println("{0} = {1};", variable.getJavaVariableValue(), valueExpr);
		            });
				} else {
					throw RuntimeUtil.illegalState();
				}
	        }
      	}
		return b.toString();
    }
    
    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	return transpileJava(jsContext, false);
    }

    // Inline declaration, like in for loops
    protected String transpileJava(JSTranspilerGeneratorContext jsContext, boolean statement) {
    	StringBuilder b = new StringBuilder();
    	int nexpr = 0;
        int count = entries.size();
        if(count>0) {
        	boolean shouldInsertComma = false;
	        for(int i=0; i<count; i++) {
	        	if(shouldInsertComma) {
	        		b.append(", ");
	        	}
	        	shouldInsertComma = false;
	            Entry e = entries.get(i);
	            ASTNode varDecl = e.varDecl;
				if(varDecl instanceof ASTIdentifier id) {
		            VariableDef variable = findVariable(id.getId());
		            // Var, system, functions are already declared
		            // But not let or const (see ASTVarContainer)
		            if(!variable.getVarType().isDeclaredGlobally()) {
		            	variable.setTranspilerDeclared(true);
		            }
		            if(e.initNode!=null) {
	            		b.append(StringFormat.format("{0} = {1}", variable.getJavaVariableValue(), JSTranspiler.asValue(jsContext,e.initNode)));
			            shouldInsertComma = true;
			            nexpr++;
		            } else {
		            	// In expressions like for( v of ... ) it should present the value
		            	// In statement, it should not just appear
		            	if(!statement) {
		            		b.append(variable.getJavaVariableValue());
				            shouldInsertComma = true;
				            nexpr++;
		            	}
		            }
				} else if(varDecl instanceof ASTContainerLiteral) {
					
				} else {
					throw RuntimeUtil.illegalState();
				}
	        }
        }
        String expr =  b.toString();
        // The comma operator doesn't exist in Java so we use a function to simulate it
        if(nexpr>1) {
            return StringFormat.format("comma({0})", expr);
        }
        return expr;
    }
    
	@Override
	public void decompileStatement(JavaBuilder b) {
		b.append(decompileExpression());
		b.append(";");
	}
    @SuppressWarnings("incomplete-switch")
	@Override
    public String decompileExpression() {
		StringBuilder b = new StringBuilder();
    	switch(getVarType()) {
			case CONST -> b.append("const ");
			case LET -> b.append("let ");
			case VAR -> b.append("var ");
		}
		for(int i=0; i<entries.size(); i++) {
			if(i>0) {
				b.append(", ");
			}
			b.append(entries.get(i).decompileExpression());
		}
		return b.toString();
	}
}
