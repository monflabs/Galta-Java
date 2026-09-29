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
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.node.ASTArrayMember;
import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTMember;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.node.assignop.ASTAssign;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.control.ASTFunction;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.BaseCallableObject;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
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


/**
 * Object literal Node.
 */
public class ASTObjectLiteral extends ASTContainerLiteral {

	// Pseudo node, not really used as a node
    public abstract static class Initializer extends ASTNode {
    	public Initializer() {
    		super(null);
    	}

    	//
    	// Evaluation
    	//
		public abstract void evaluate(JSInterpretedRuntimeContext context, JSObjectImpl map, JSResult result);

		//
		// Destructuration
		//
    	public abstract void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType);
		// isAssignmentContext: see ASTContainerLiteral.assign()'s comment - only
		// meaningful here for a NESTED array pattern's own empty-pattern case
		// ("{a: []} = x"); an object pattern's own empty case never touches an
		// iterator either way, so implementations besides
		// InitializerFieldNameExpression can ignore this parameter.
		public abstract void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory,JSObject object, Set<Object> consumed, JSResult result, boolean isAssignmentContext);
		public abstract void forEachVarName(Consumer<String> callback);
		// members: property names already consumed by earlier initializers, for
		// a trailing rest to exclude - a computed key contributes a
		// RawJavaExpression (a runtime-evaluated temp-var reference) rather than
		// a plain String, hence Set<Object> rather than Set<String>.
		// jsContext/b: the current generator context and its builder, needed
		// when this property's key is computed (InitializerFieldNameExpression
		// only) - both null only ever reach here from
		// ASTVariableDecl.updateOptimizedContext's optimizer pre-pass.
		public abstract void destructParameters(VariableFactory varFactory, Object[] path, Set<Object> members, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue);
    }

    public static enum AccessorType { GETTER, SETTER };
    public final static class InitializerFieldNameExpression extends Initializer {
    	
    	boolean hasField;
		String _fieldName;
		ASTNode _fieldNode;
		ASTNode node;
		AccessorType accessorType;

		public InitializerFieldNameExpression(Object fieldName, ASTNode node) {
			this(fieldName,node,null);
		}
		public InitializerFieldNameExpression(Object fieldName, ASTNode node, AccessorType accessorType) {
			this.hasField = true;
			if(fieldName instanceof ASTNode _fieldNode) {
			    this._fieldNode = assignParent(_fieldNode);
			} else if(fieldName instanceof String _fieldName) {
				this._fieldName = _fieldName;
			} else if(fieldName==null) {
				this._fieldName = "null";
			} else {	
				throw fillInStackTrace(RuntimeUtil.typeError("Invalid field name type: {0}",fieldName.getClass().getName()));
			}
		    this.node = assignParent(node);
		    this.accessorType = accessorType;
		}
		
		public InitializerFieldNameExpression(ASTNode node) {
		    this.node = assignParent(node);
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
			return super.getChildCount()+2;
		}
		@Override
		public ASTNode getChild(int index) {
			switch(index) {
				case 0 ->	{ return node; }
				case 1 ->	{ return _fieldNode; }
				default ->	{ return super.getChild(index-2); }
			}
		}
		@Override
		protected void _setChild(int index, ASTNode node) {
			switch(index) {
				case 0 ->	{ this.node = node; }
				case 1 ->	{ this._fieldNode = node; }
				default ->  { super._setChild(index-2,node); }
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
		public void evaluate(JSInterpretedRuntimeContext context, JSObjectImpl map, JSResult result) {
			if(!hasField) {
				String fieldName;
				if(node instanceof ASTIdentifier id) {
					fieldName = id.getId();
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
	            Object value = RuntimeUtil.getIdentifierValue(context, fieldName, true);
            	map.litProp(fieldName, value);
            	return;
			}
			Object fieldKey = getObjectFieldKey(context, result);
            Object value = node.evaluateValue(context,result);
        	if(accessorType!=null) {
            	if(accessorType==AccessorType.GETTER) {
            		map.litGetter(fieldKey,value);
            	} else {
            		map.litSetter(fieldKey,value);
            	}
            	return;
        	}
        	// Annex B.3.1's __proto__-as-prototype-setter special case only
        	// ever applies to THIS exact syntactic form (PropertyDefinition :
        	// PropertyName : AssignmentExpression) with a non-computed
        	// PropertyName - _fieldNode==null means non-computed here (see
        	// getObjectFieldKey()). See litProtoProp()'s own doc for the full
        	// list of forms that must NOT trigger it (computed key, shorthand).
        	if(_fieldNode==null && "__proto__".equals(fieldKey)) {
        		map.litProtoProp(value);
        		return;
        	}
        	// BaseCallableObject, not the narrower BuiltinFunction - NamedEvaluation
        	// applies to an anonymous CLASS expression value too (`{id: class {}}`
        	// must get `.name === "id"`), and BuiltinClassConstructor is a
        	// BaseCallableObject but not a BuiltinFunction (test262
        	// fn-name-class.js). litFunction() itself only actually renames when
        	// still unnamed, so an already-named function/class is unaffected.
        	// IsAnonymousFunctionDefinition (spec 8.4.3) is a SYNTACTIC check on
        	// the property's own VALUE expression, not on the resulting runtime
        	// value - "value instanceof BaseCallableObject" alone can't tell
        	// "{xId: (0, function(){})}" (a comma/SequenceExpression that merely
        	// EVALUATES to a function - must NOT be named "xId") apart from
        	// "{id: (function(){})}" (a parenthesized function expression -
        	// transparently still nameable "id", parens don't disqualify it).
        	// skipTransparent() unwraps ONLY paren wrapping (not commas), so
        	// checking the AST shape here mirrors ASTAssign's identical fix.
        	if(value instanceof BaseCallableObject fct
        			&& (skipTransparent(node) instanceof ASTFunction || skipTransparent(node) instanceof ASTClassDecl)) {
            	map.litFunction(fieldKey, fct);
        	} else {
        		map.litProp(fieldKey, value);
        	}
		}
		private Object getObjectFieldKey(JSInterpretedRuntimeContext context, JSResult result) {
			if(_fieldNode!=null) {
	            Object key = _fieldNode.evaluateValue(context,result);
	            // ToPropertyKey: ToPrimitive first, so an object whose toPrimitive
	            // returns a Symbol is keyed by that Symbol (as in transpiled code)
	            return RuntimeUtil.toPropertyKeyString(context.getEnvironment(),key);
			}
			return _fieldName;
		}
		@Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
			if(!hasField) {
				if(node instanceof ASTIdentifier id) {
					String fieldName = id.getId();
					return StringFormat.format(".litProp(\"{0}\",{1})", fieldName, JSTranspiler.asValue(jsContext, id));
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			}
			String fieldName = getObjectFieldName(jsContext);
        	if(accessorType!=null) {
            	if(accessorType==AccessorType.GETTER) {
    				return StringFormat.format(".litGetter({0},{1})", fieldName, JSTranspiler.asValue(jsContext, node));
            	} else {
    				return StringFormat.format(".litSetter({0},{1})", fieldName, JSTranspiler.asValue(jsContext, node));
            	}
        	}
			// See InitializerFieldNameExpression.evaluate()'s identical Annex
			// B.3.1 check (interpreted mode) for the full rationale -
			// _fieldNode==null (non-computed) is known here at TRANSPILE
			// time already, so this is a static check, not a runtime one.
			if(node!=null && _fieldNode==null && "__proto__".equals(_fieldName)) {
				return StringFormat.format(".litProtoProp({0})", JSTranspiler.asValue(jsContext, node));
			}
			if(node!=null) {
            	// skipTransparent(): see the identical AST-shape check in
            	// evaluate() above - a parenthesized function expression is
            	// still nameable (parens are transparent to
            	// IsAnonymousFunctionDefinition), but this check must NOT
            	// itself see through a comma/SequenceExpression. Also
            	// matches an anonymous CLASS expression value (test262
            	// fn-name-class.js), not just ASTFunction - same reasoning
            	// as evaluate()'s own identical check above; asBaseCallableObject
            	// (not the narrower asFunction/toFunction) is needed since
            	// BuiltinClassConstructor isn't a BuiltinFunction.
            	ASTNode transparent = skipTransparent(node);
            	if(transparent instanceof ASTFunction || transparent instanceof ASTClassDecl) {
    				return StringFormat.format(".litFunction({0},{1})", fieldName, JSTranspiler.asBaseCallableObject(jsContext, node));
            	} else {
    				return StringFormat.format(".litProp({0},{1})", fieldName, JSTranspiler.asValue(jsContext, node));
            	}
			} else {
				return StringFormat.format(".litProp({0},{1})", fieldName, ASTIdentifier.transpileJavaExpression(jsContext,fieldName));
			}
		}
		private String getObjectFieldName(JSTranspilerGeneratorContext context) {
			if(_fieldNode!=null) {
				// ToPropertyKey must run BEFORE the value expression is
				// evaluated (spec 13.2.5.5 PropertyDefinitionEvaluation) -
				// a plain ".litProp(rawKeyText,valueText)" call left the
				// coercion to happen lazily inside JSObject.setOwnProperty,
				// which only runs after BOTH call arguments (key AND value)
				// are already evaluated, so a key with a side-effecting
				// toString() ran too late relative to the value expression
				// (test262 computed-property-name-topropertykey-before-
				// value-evaluation.js). Wrapping it here forces the
				// coercion to happen as part of evaluating THIS argument,
				// still before Java evaluates the value argument that
				// follows it - same helper resolveFieldKey() already uses
				// for the destructuring-target codegen path below.
				return StringFormat.format("toPropertyKeyString({0},{1})", JSTranspiler.MAIN_ENVIRONMENT, JSTranspiler.asValue(context, _fieldNode));
			}
			return ASTLiteral.encodeString(_fieldName);
		}
		
		//
		// Destructuration
		//
		@Override
	    public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
			// <expr>
			//    name
			//    name=<expr>
			if(!hasField) {
				if(node instanceof ASTAssign as) {
					ASTNode varNode = as.getLeftNode();
					if(varNode instanceof ASTIdentifier id) {
						id.declareVariables(varContainer, varType);
						return;
					} else {
						throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
					}
				} else if(node instanceof ASTIdentifier id) {
					id.declareVariables(varContainer, varType);
					return;
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			}
			
			// a: <expr>
			//   a: name
			//   a: name=<expr>
			//   a: [] or {}
			if(node instanceof ASTIdentifier id) {
				id.declareVariables(varContainer, varType);
				return;
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					id.declareVariables(varContainer, varType);
					return;
				} else if(varNode instanceof ASTContainerLiteral cl) {
					cl.declareVariables(varContainer, varType);
					return;
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				cl.declareVariables(varContainer, varType);
				return;
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}

		@Override
		public void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSObject object, Set<Object> consumed, JSResult result, boolean isAssignmentContext) {
			// <expr>
			//    name
			//    name=<expr>
			if(!hasField) {
				if(node instanceof ASTAssign as) {
					ASTNode varNode = as.getLeftNode();
					if(varNode instanceof ASTIdentifier id) {
						String fieldName = id.getId();
						if(variableFactory instanceof ASTContainerLiteral.WithScopeResolvingFactory wrf) {
							wrf.resolveBinding(fieldName);
						}
						Object value = object.getProperty(fieldName,RuntimeUtil.NOT_AVAILABLE);
						if(value!=RuntimeUtil.NOT_AVAILABLE && value!=RuntimeUtil.UNDEFINED) {
							variableFactory.accept(fieldName, value);
						} else {
							ASTNode exprNode = as.getRightNode();
							value = exprNode.evaluateValue(context,result);
							variableFactory.accept(fieldName, value);
						}
						consumed.add(fieldName);
						return;
					} else {
						throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
					}
				}

				String fieldName;
				if(node instanceof ASTIdentifier id) {
					fieldName = id.getId();
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
				if(variableFactory instanceof ASTContainerLiteral.WithScopeResolvingFactory wrf) {
					wrf.resolveBinding(fieldName);
				}
				Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
				variableFactory.accept(fieldName, value);
				consumed.add(fieldName);
				return;
			}

			// a: <expr>
			//   a: name
			//   a: name=<expr>
			//   a: [] or {}
			// A computed key ("[expr]: name") can evaluate to a Symbol, not just
			// a String (getObjectFieldKey() returns either) - the whole block
			// below only ever uses fieldKey as an OPAQUE source-object property
			// key (object.getProperty(fieldName,...)), never as a String
			// specifically (the bound variable's own name comes from
			// ASTIdentifier.getId() separately), so accepting both here is
			// enough - test262 language/expressions/object/dstr/object-rest-
			// proxy-gopd-not-called-on-excluded-keys.js: "var {[excludedSymbol]:
			// _, ...} = proxy" was an unconditional SyntaxError since this
			// check only ever matched String.
			Object fieldKey = getObjectFieldKey(context, result);
			if(fieldKey instanceof String || fieldKey instanceof Symbol) {
				Object fieldName = fieldKey;
				consumed.add(fieldName);
				if(node instanceof ASTIdentifier id) {
					String varName = id.getId();
					// No default value here (that's the ASTAssign branch below) - a
					// missing property must resolve to `undefined` (GetV's own
					// behavior), not leak the NOT_AVAILABLE sentinel into the bound
					// variable. See test262 dstr/array-rest-nested-obj-undefined.js:
					// "[...{ 0: x, length }] = []" expects x===undefined, not the
					// sentinel object.
					if(variableFactory instanceof ASTContainerLiteral.WithScopeResolvingFactory wrf) {
						wrf.resolveBinding(varName);
					}
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					variableFactory.accept(varName, value);
					return;
				} else if(isAssignmentContext && node instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
					// Spec order (KeyedDestructuringAssignmentEvaluation): the
					// DestructuringAssignmentTarget's own reference (base +
					// computed key, e.g. "target()[targetKey()]") must be
					// resolved BEFORE GetV(value, propertyName) reads the
					// source property - resolveReference() only evaluates
					// base/key, deferring the actual property-key conversion
					// and write to assignToResolved() below, same two-phase
					// API ASTArrayLiteral's assignIter() already uses for the
					// identical ordering reason. Fixes test262 destructuring/
					// keyed-destructuring-property-reference-target-
					// evaluation-order.js.
					ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					if(ref!=null) {
						arrayMember.assignToResolved(context, ref, value, null, result, null);
					}
					return;
				} else if(isAssignmentContext && node instanceof ASTMember member && member.canResolveReference()) {
					// Same ordering rationale as the ASTArrayMember branch just
					// above (KeyedDestructuringAssignmentEvaluation: the target's
					// own reference must resolve BEFORE GetV(value, propertyName)
					// reads the source) - fixes test262 privatefieldset-
					// evaluation-order-1.js (`{a: this.#field} = object`: `this`
					// must be TDZ-checked, throwing ReferenceError, before
					// `object`'s own `a` getter - which may call super() - ever runs).
					ASTMember.ResolvedReference ref = member.resolveReference(context, result);
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					if(ref!=null) {
						member.assignToResolved(context, ref, value, null, result, null);
					}
					return;
				} else if(node instanceof ASTMember m) {
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					m.evaluateAssign(context, value, null, result, null);
					return;
				} else if(node instanceof ASTAssign as) {
					ASTNode varNode = as.getLeftNode();
					if(varNode instanceof ASTIdentifier id) {
						String varName = id.getId();
						// See ASTContainerLiteral.WithScopeResolvingFactory's own doc
						// comment - spec's KeyedBindingInitialization ResolveBinding
						// (step 2) must run BEFORE GetV (step 3) reads the source
						// property, and before the default value's own evaluation
						// (step 4) too, for a var declaration's binding target. Fixes
						// test262 destructuring/binding/keyed-destructuring-property-
						// reference-target-evaluation-order-with-bindings.js.
						if(variableFactory instanceof ASTContainerLiteral.WithScopeResolvingFactory wrf) {
							wrf.resolveBinding(varName);
						}
						Object value = object.getProperty(fieldName,RuntimeUtil.NOT_AVAILABLE);
						if(value!=RuntimeUtil.NOT_AVAILABLE && value!=RuntimeUtil.UNDEFINED) {
							variableFactory.accept(varName, value);
						} else {
							ASTNode exprNode = as.getRightNode();
							value = exprNode.evaluateValue(context,result);
							variableFactory.accept(varName, value);
						}
						return;
					} else if(varNode instanceof ASTContainerLiteral cl) {
						Object value = object.getProperty(fieldName,RuntimeUtil.NOT_AVAILABLE);
						if(value==RuntimeUtil.NOT_AVAILABLE || value==RuntimeUtil.UNDEFINED) {
							ASTNode exprNode = as.getRightNode();
							value = exprNode!=null ? exprNode.evaluateValue(context,result) : RuntimeUtil.UNDEFINED;
						}
						cl.assign(context, variableFactory, value, result, isAssignmentContext);
						return;
					} else if(isAssignmentContext && varNode instanceof ASTArrayMember arrayMember && arrayMember.canResolveReference()) {
						// Same ordering rationale as the no-default ASTArrayMember
						// branch above, plus the Initializer: the target's reference
						// resolves BEFORE both GetV and (if needed) the default
						// expression's evaluation - test262 destructuring/keyed-
						// destructuring-property-reference-target-evaluation-order-
						// with-bindings.js asserts the default expression's own
						// identifier lookup happens after the target reference is
						// resolved but before the target's property key is actually
						// converted/written (which assignToResolved() defers to).
						ASTArrayMember.ResolvedReference ref = arrayMember.resolveReference(context, result);
						Object value = object.getProperty(fieldName,RuntimeUtil.NOT_AVAILABLE);
						if(value==RuntimeUtil.NOT_AVAILABLE || value==RuntimeUtil.UNDEFINED) {
							ASTNode exprNode = as.getRightNode();
							value = exprNode!=null ? exprNode.evaluateValue(context,result) : RuntimeUtil.UNDEFINED;
						}
						if(ref!=null) {
							arrayMember.assignToResolved(context, ref, value, null, result, null);
						}
						return;
					} else if(isAssignmentContext) {
						// A plain LeftHandSideExpression target with a default value
						// (e.g. "{a: x.y = 5} = obj") - same rationale as the
						// bare-member-target branch above, just with an Initializer.
						Object value = object.getProperty(fieldName,RuntimeUtil.NOT_AVAILABLE);
						if(value==RuntimeUtil.NOT_AVAILABLE || value==RuntimeUtil.UNDEFINED) {
							ASTNode exprNode = as.getRightNode();
							value = exprNode!=null ? exprNode.evaluateValue(context,result) : RuntimeUtil.UNDEFINED;
						}
						varNode.evaluateAssign(context, value, null, result, null);
						return;
					} else {
						throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
					}
				} else if(node instanceof ASTContainerLiteral cl) {
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					cl.assign(context, variableFactory, value, result, isAssignmentContext);
					return;
				} else if(isAssignmentContext) {
					// Any other valid DestructuringAssignmentTarget expression shape
					// with no default value - e.g. a computed member target on a call
					// expression ("{[k]: target()[targetKey()]} = obj", not an
					// ASTMember since the base isn't a plain reference). Same fallback
					// ASTArrayLiteral.InitializerSpread.assignIter() already uses for
					// the identical "any other LeftHandSideExpression" case. Fixes
					// test262 destructuring/keyed-destructuring-property-reference-
					// target-evaluation-order.js.
					Object value = object.getProperty(fieldName,RuntimeUtil.UNDEFINED);
					node.evaluateAssign(context, value, null, result, null);
					return;
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		@Override
		public void forEachVarName(Consumer<String> callback) {
			if(node==null) {
				callback.accept(_fieldName);
			} else if(node instanceof ASTIdentifier id) {
				String varName = id.getId();
				callback.accept(varName);
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					String varName = id.getId();
					callback.accept(varName);
				} else if(varNode instanceof ASTContainerLiteral cl) {
					// A nested pattern with its own default, e.g. `{w: [x,y,z] = [4,5,6]}` -
					// mirrors assign()'s handling of the same shape just above.
					cl.forEachVarName(callback);
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				callback.accept(_fieldName);
				cl.forEachVarName(callback);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
	    // Resolves this property's key for destructuring: the static _fieldName
	    // when present; for a computed key ("[expr]:"), emits (via b) a temp-var
	    // statement evaluating _fieldNode through toPropertyKeyString() - always
	    // a Symbol or String (matching interpreted mode's getObjectFieldKey()
	    // exactly), not toPropertyKey()'s leave-bare-Numbers-as-is fast path,
	    // since this value is also compared via plain .equals() against
	    // ownStringEntries()'s String keys when a trailing rest excludes it -
	    // returning a RawJavaExpression referencing it (so callers splice the
	    // already-evaluated value instead of re-encoding it as a literal);
	    // null when there's no field at all (shorthand) or when b==null (the
	    // ASTVariableDecl.updateOptimizedContext optimizer pre-pass, which has
	    // no builder to emit into and only needs a non-crashing hint, not a
	    // correct runtime key) - callers fall back to the local binding name
	    // in that case, matching this class's pre-existing behavior. jsContext
	    // MUST be the caller's own current context (not recovered via
	    // b.getContext()) - a builder created higher up a split/nested-block
	    // scope chain doesn't necessarily share the current call's actual
	    // variable-resolution context, and _fieldNode may reference a variable
	    // only visible from the current, possibly more deeply nested, scope.
	    private Object resolveFieldKey(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b) {
	    	if(_fieldName!=null) {
	    		return _fieldName;
	    	}
	    	if(_fieldNode!=null && b!=null) {
	    		String keyVar = jsContext.generateUniqueId("key");
	    		b.println("Object {0} = toPropertyKeyString({1}, {2});", keyVar, JSTranspiler.MAIN_ENVIRONMENT, JSTranspiler.asValue(jsContext, _fieldNode));
	    		return new RawJavaExpression(keyVar);
	    	}
	    	return null;
	    }
	    @Override
		public void destructParameters(VariableFactory varFactory, Object[] path, Set<Object> members, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
			if(node==null) {
				ASTIdentifier id = new ASTIdentifier(null, _fieldName);
				varFactory.apply(id,defaultValue,addPath(path,_fieldName),null);
				if(members!=null) {
					members.add(_fieldName);
				}
				return;
			}
			Object fieldKey = resolveFieldKey(jsContext, b);
			if(node instanceof ASTIdentifier id) {
				String varName = id.getId();
				// This is an alias, so the path (and, for a later rest
				// element, the "already consumed" tracking) must use the
				// PROPERTY key (fieldKey), not the local binding name.
				Object subPath = fieldKey!=null ? fieldKey : varName;
				varFactory.apply(node,defaultValue,addPath(path,subPath),null);
				if(members!=null) {
					members.add(subPath);
				}
			} else if(node instanceof ASTMember m) {
				String varName = m.getMemberName();
				// This is an alias, so the path is _fielNode
				Object subPath = fieldKey!=null ? fieldKey : varName;
				varFactory.apply(m,defaultValue,addPath(path,subPath),null);
			} else if(node instanceof ASTArrayMember am) {
				// A computed-member target (e.g. `{ x: base[key] } = obj`) -
				// fieldKey is never null here (unlike the shorthand `{x}` case
				// above): a MemberExpression target always needs its own
				// explicit `propertyName:` before it, shorthand-property
				// syntax only ever allows a bare BindingIdentifier. Mirrors the
				// ASTMember branch just above.
				varFactory.apply(am,defaultValue,addPath(path,fieldKey),null);
			} else if(node instanceof ASTAssign as) {
				ASTNode varNode = as.getLeftNode();
				if(varNode instanceof ASTIdentifier id) {
					String varName = id.getId();
					// Same PROPERTY-key-not-binding-name rule as the plain
					// ASTIdentifier branch above - a renamed property with its
					// own default (e.g. `{poisoned: x = dflt}`) must still
					// read/track the "poisoned" property, not "x".
					Object subPath = fieldKey!=null ? fieldKey : varName;
					Object[] newPath = addPath(path,subPath);
					varFactory.apply(id,new DefaultRef(as.getRightNode(),newPath.length,defaultValue),newPath,null);
					if(members!=null) {
						members.add(subPath);
					}
				} else if(varNode instanceof ASTContainerLiteral cl) {
					Object[] newPath = addPath(path,fieldKey);
					cl.destructParameters(varFactory,newPath,jsContext,b,new DefaultRef(as.getRightNode(),newPath.length,defaultValue));
					if(members!=null) {
						members.add(fieldKey);
					}
				} else {
					// Any other assignment-target-capable expression with its
					// own default (e.g. `{ x: base[key] = dflt } = obj`) -
					// mirrors ASTMember/ASTArrayMember above, only reachable in
					// ASSIGNMENT context (a BindingPattern's elements can never
					// be a MemberExpression).
					Object[] newPath = addPath(path,fieldKey);
					varFactory.apply(varNode,new DefaultRef(as.getRightNode(),newPath.length,defaultValue),newPath,null);
					if(members!=null) {
						members.add(fieldKey);
					}
				}
			} else if(node instanceof ASTContainerLiteral cl) {
				Object[] newPath = addPath(path,fieldKey);
				// No default of its own here - forward whatever ambient default
				// this element itself was passed, instead of discarding it.
				// The depth stays fixed at wherever it was originally
				// introduced, not the depth reached here.
				cl.destructParameters(varFactory,newPath,jsContext,b,defaultValue);
				if(members!=null) {
					members.add(fieldKey);
				}
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
	    }
		
	    @Override
		public String decompileExpression() {
	    	StringBuilder b = new StringBuilder();
    		if(!hasField) {
				if(node instanceof ASTIdentifier) {
					b.append(node.decompileExpression());
				} else if(node instanceof ASTAssign) {
					b.append(node.decompileExpression());
				} else {
					throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
				}
    		} else {
		    	if(accessorType!=null) {
		    		switch(accessorType ) {
		    			case GETTER -> b.append("get ");
		    			case SETTER -> b.append("set ");
		    		}
	    			if(_fieldNode!=null) {
						b.append('[');
						b.append(_fieldNode.decompileExpression());
						b.append(']');
	    			} else {
						b.append(ASTLiteral.encodeString(_fieldName));
	    			}
					String fct = ((ASTFunctionDecl)node).decompileFunction();
					b.append(fct);
		    	} else if(node instanceof ASTFunctionDecl fn && fn.isMethod()) {
		    		// A shorthand method decompiles back to shorthand syntax, like
		    		// the getter/setter branch above - not to `name: function
		    		// name() {...}`, which is illegal when the name is a reserved
		    		// word ({ return() {} }) and would also turn a non-constructible
		    		// method into an ordinary function.
		    		if(fn.isAsync()) {
		    			b.append("async ");
		    		}
		    		if(fn.isGenerator()) {
		    			b.append('*');
		    		}
	    			if(_fieldNode!=null) {
						b.append('[');
						b.append(_fieldNode.decompileExpression());
						b.append(']');
	    			} else {
						b.append(ASTLiteral.encodeString(_fieldName));
	    			}
					b.append(fn.decompileFunction());
		    	} else {
	    			if(_fieldNode!=null) {
						b.append('[');
						b.append(_fieldNode.decompileExpression());
						b.append(']');
	    			} else {
						b.append(ASTLiteral.encodeString(_fieldName));
	    			}
					b.append(": ");
					b.append(node.decompileExpression());
		    	}
    		}
	    	return b.toString();
	    }    
    };

    public final static class InitializerSpread extends Initializer {
		ASTNode node;
		public InitializerSpread(ASTNode node) {
		    this.node = assignParent(node);
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
		public void evaluate(JSInterpretedRuntimeContext context, JSObjectImpl map, JSResult result) {
            Object value = node.evaluateValue(context, result);            
            if(RuntimeUtil.isNotNullOrUndefined(value)) {
            	map.litPutAll(RuntimeUtil.keyValueIterator(context.getEnvironment(),value));
            }
		}
		@Override
	    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
			return StringFormat.format(".litPutAll(keyValueIterator({0}))",JSTranspiler.asValue(jsContext, node));
		}
    	
		//
		// Destructuration
		//
	    @Override
		public void declareVariables(IContextBlockContainer varContainer, VAR_TYPE varType) {
			if(node instanceof ASTIdentifier id) {
				id.declareVariables(varContainer, varType);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
	    }
		@Override
		public void assign(JSInterpretedRuntimeContext context, BiConsumer<String,Object> variableFactory, JSObject object, Set<Object> consumed, JSResult result, boolean isAssignmentContext) {
			if(node instanceof ASTIdentifier id) {
				JSObject o = collectRestObject(context, object, consumed);
				String fieldName = id.getId();
				variableFactory.accept(fieldName, o);
		        return;
			} else if(node instanceof ASTMember m) {
				// AssignmentRestProperty's target is a full
				// DestructuringAssignmentTarget (any LeftHandSideExpression),
				// not just a BindingIdentifier like a rest element in a
				// var/let/const binding pattern - `{...src.y} = vals` must
				// assign the collected rest object onto `src.y`, mirroring
				// the identical ASTMember handling for a NON-rest property
				// target above (test262 obj-rest-to-property.js/
				// obj-rest-to-property-with-setter.js: the rest target was
				// unconditionally rejected as "Invalid destructuration
				// syntax" for anything other than a bare identifier).
				JSObject o = collectRestObject(context, object, consumed);
				m.evaluateAssign(context, o, null, result, null);
				return;
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		// Collects every own enumerable property of `object` not already in
		// `consumed` into a fresh plain object, per CopyDataProperties -
		// shared by both rest-target shapes above. See the symbol-keyed loop
		// below for why symbols are a separate pass rather than merged into
		// one combined-order iteration.
		private JSObject collectRestObject(JSInterpretedRuntimeContext context, JSObject object, Set<Object> consumed) {
			JSObject o = JSObject.create(context.getEnvironment());
			// Single combined-order pass over EVERY own key (strings and
			// symbols together, exactly as [[OwnPropertyKeys]] returned
			// them), mirroring CopyDataProperties precisely: a consumed
			// (excluded) key is skipped with NO [[GetOwnProperty]] call at
			// all, and [[Get]] only fires for a key that turns out
			// enumerable. Replaces a former two-pass (all strings, then all
			// symbols) shape that assumed OrdinaryOwnPropertyKeys' "every
			// symbol after every string" grouping - true for ordinary
			// objects, but a Proxy's "ownKeys" trap may freely interleave
			// them, and CopyDataProperties must preserve whatever order
			// [[OwnPropertyKeys]] actually returned (test262
			// object-rest-proxy-ownkeys-returned-keys-order.js). The old
			// shape also ran the enumerable-only iterator (which internally
			// calls [[GetOwnProperty]] per key to filter) BEFORE checking
			// "consumed", so [[GetOwnProperty]] fired even for excluded
			// keys (object-rest-proxy-gopd-not-called-on-excluded-keys.js).
			for(Iterator<Entry<Object,Object>> it=context.getEnvironment().getAccessor(object).ownPropertyEntries(object,true,true,false); it.hasNext(); ) {
				Object key = it.next().getKey();
				// consumed is Set<Object> (both String and Symbol keys) - see its
				// own doc comment; a Symbol key explicitly destructured earlier in
				// this SAME pattern (e.g. "{[sym]: x, ...rest}") must be excluded
				// here too, exactly like a String one already was, or it gets a
				// SECOND (spec-violating) [[GetOwnProperty]] call from this loop
				// on top of the one the explicit property already triggered.
				if(consumed.contains(key)) {
					continue;
				}
				PropertyDescriptor desc = object.getOwnPropertyDescriptor(key);
				if(desc!=null && desc.isEnumerable()) {
					o.setOwnProperty(key, object.getProperty(key,RuntimeUtil.UNDEFINED));
				}
				consumed.add(key);
			}
			return o;
		}
		@Override
		public void forEachVarName(Consumer<String> callback) {
			if(node instanceof ASTIdentifier id) {
				String fieldName = id.getId();
				callback.accept(fieldName);
			} else {
				throw fillInStackTrace(RuntimeUtil.syntaxError("Invalid destructuration syntax"));
			}
		}
		@Override
		public void destructParameters(VariableFactory varFactory, Object[] path, Set<Object> members, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
			if(node instanceof ASTIdentifier id) {
				String varName = id.getId();
				// A rest element never has an Initializer of its own, but must
				// still forward whatever ambient default this whole pattern was
				// passed (e.g. `{...x} = dflt`'s own default) rather than
				// discarding it - same rule as the ASTContainerLiteral recursion
				// case below, which forwards its ambient default unchanged too.
				varFactory.apply(id,defaultValue,path,members);
				members.add(varName);
			} else {
				// AssignmentRestProperty's target is a full
				// DestructuringAssignmentTarget (any LeftHandSideExpression),
				// not just a BindingIdentifier like a rest element in a
				// var/let/const binding pattern - `{...src.y} = vals` must
				// assign the collected rest object onto `src.y` - mirrors the
				// identical fix already present in this same class's
				// INTERPRETED-mode assign() (see its own comment) and the
				// analogous fallback in ASTArrayLiteral's rest-element
				// transpileJavaIteratorAssign(). Only reachable in ASSIGNMENT
				// context (a BindingPattern's rest element is parser-
				// restricted to a BindingIdentifier, never a MemberExpression).
				varFactory.apply(node,defaultValue,path,members);
			}
		}

	    @Override
		public String decompileExpression() {
	    	return "..."+node.decompileExpression();
	    }
    };

	private ArrayList<Initializer> fieldInitializers = new ArrayList<Initializer>();

	// A shorthand PropertyDefinition is an IdentifierReference ("{x}"), or a
	// CoverInitializedName ("{x = 1}") that only a destructuring pattern
	// accepts: "{0}", "{this}" or "{[x]}" is a SyntaxError.
	public void checkShorthands() {
		for(Initializer init: fieldInitializers) {
			if(init instanceof InitializerFieldNameExpression f && !f.hasField && f.node!=null) {
				ASTNode n = f.node instanceof ASTAssign as ? as.getLeftNode() : f.node;
				if(!(n instanceof ASTIdentifier)) {
					throw new JSParseException(null, this, "Invalid shorthand property initializer");
				}
			}
		}
	}

	@Override
	protected boolean lastIsSpread() {
		return !fieldInitializers.isEmpty() && fieldInitializers.get(fieldInitializers.size()-1) instanceof InitializerSpread;
	}

	@Override
	public void checkPattern(boolean binding, boolean strict) {
		for(int i=0; i<fieldInitializers.size(); i++) {
			Initializer init = fieldInitializers.get(i);
			if(init instanceof InitializerSpread sp) {
				if(i<fieldInitializers.size()-1 || hasCommaAfterSpread()) {
					throw new JSParseException(null, this, "Rest element must be last element");
				}
				// The object rest target is a simple target, never a nested pattern
				if(sp.node instanceof ASTContainerLiteral) {
					throw new JSParseException(null, this, "Invalid rest element");
				}
				checkPatternTarget(sp.node, binding, strict);
			} else if(init instanceof InitializerFieldNameExpression f && f.node!=null) {
				if(f.accessorType!=null) {
					throw new JSParseException(null, this, "Invalid destructuring target");
				}
				ASTNode n = f.node;
				checkPatternTarget(n instanceof ASTAssign as ? as.getLeftNode() : n, binding, strict);
			}
		}
	}

	public ASTObjectLiteral(Token t) {
		super(t);
	}

	// An empty ObjectBindingPattern/ObjectAssignmentPattern (`{}`) still
	// requires RequireObjectCoercible(value) even though it binds nothing -
	// see destructParameters()'s callers for where this matters (a pattern
	// with no properties never triggers any per-property codegen at all).
	public boolean isEmpty() {
		return fieldInitializers.isEmpty();
	}

	// True ONLY for the node produced by MainSourceElements()'s
	// GaltaJS "bare object literal at top level" extension
	// (isSoleTopLevelObjectLiteral() in JSParser.jj) when the source was
	// LITERALLY an empty "{}" - never set for a genuine, explicitly-written
	// ObjectLiteral (`({})`/`x = {}`/etc, even one that also happens to be
	// empty), which parses through the ORDINARY ObjectLiteral() grammar path
	// instead and never reaches the call site that sets this. Lets
	// ASTProgram's eval-completion-value logic distinguish "this program's
	// text was the ambiguous, promoted-to-object-literal bare '{}'" (spec
	// wants an empty Block here, completion value undefined, ONLY for
	// eval() - see that entry in KnownGaps.md) from "the user genuinely
	// wrote an empty object literal as their eval'd program's sole
	// statement" (must still evaluate to {}) - without changing the
	// parser's own promotion decision (and thus without touching
	// GaltaJS's own deliberate extension - ObjectAsExpressionTest -
	// at all, for ANY caller, eval or not).
	private boolean bareTopLevelEmptyPromotion;

	public boolean isBareTopLevelEmptyPromotion() {
		return bareTopLevelEmptyPromotion;
	}

	public void markBareTopLevelEmptyPromotion() {
		bareTopLevelEmptyPromotion = true;
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
	
	public void addExpression(Object name, ASTNode node){
	    fieldInitializers.add(assignParent(new InitializerFieldNameExpression(name,node)));
	}

	public void addExpression(ASTNode node){
	    fieldInitializers.add(assignParent(new InitializerFieldNameExpression(node)));
	}
	
	public void addFunction(Object name, ASTFunction function){
	    fieldInitializers.add(assignParent(new InitializerFieldNameExpression(name,function)));
	}
	
	public void addGetter(Object name, ASTFunction function){
	    fieldInitializers.add(assignParent(new InitializerFieldNameExpression(name,function, AccessorType.GETTER)));
	}
	
	public void addSetter(Object name, ASTFunction function){
	    fieldInitializers.add(assignParent(new InitializerFieldNameExpression(name,function, AccessorType.SETTER)));
	}
	
	public void addSpread(ASTNode node){
	    fieldInitializers.add(assignParent(new InitializerSpread(node)));
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
			JSObjectImpl map = JSObject.create(context.getEnvironment());
	        int count = fieldInitializers.size();
	        for(int i=0;i<count;i++){
	            Initializer n=fieldInitializers.get(i);
	            n.evaluate(context, map, result);
		    }
	        result.setValue(map);
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
    @Override
    public String transpileJavaExpression(JSTranspilerGeneratorContext jsContext) {
    	TranspilerJavaBuilder b = jsContext.createJavaBuilder();
    	b.println("createObject()");
        int count = fieldInitializers.size();
        
        TranspilerCodeSplitter splitter = jsContext.getOptions().getCodeSplitter();
        if(splitter!=null && count>=splitter.ObjectLiteralSplitTheshold()) {
        	for(int i=0; i<count; i+=splitter.ObjectLiteralSplitMax()) {
        		int max = Math.min(count-i, splitter.ObjectLiteralSplitMax());
            	b.incIndent();
            	b.println(".litProps(new Consumer<JSObjectImpl>() { // Object props");
            	b.incIndent();
            	b.println("@Override");
            	b.println("public void accept(JSObjectImpl obj) {");
            	b.incIndent();
            	
				TranspilerGeneratorBlockSplitContext bCtx = new TranspilerGeneratorBlockSplitContext(jsContext);

				b.append("obj");
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
		RuntimeUtil.requireObjectCoercible(value);
		JSObject ol = JSObject.from(context.getEnvironment(),value);
		HashSet<Object> consumed = new HashSet<>();
		int count = fieldInitializers.size();
		for(int i=0; i<count; i++) {
			Initializer initializer = fieldInitializers.get(i);
			if(initializer!=null) {
				initializer.assign(context, variableFactory, ol, consumed, result, isAssignmentContext);
			}
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
	public String transpileJavaAssignment(JSTranspilerGeneratorContext jsContext, ASSIGN_TYPE type, String rightValue, boolean sequence, boolean returnOriginalValue) {
		TranspilerJavaBuilder b = jsContext.createJavaBuilder();
		int vidx = jsContext.generateUniqueId();
		b.println("executeIsolated({0}, () -> {", JSTranspiler.MAIN_CONTEXT);
		b.incIndent();
		b.println("Object v{0} = {1};", vidx, rightValue);
		// RequireObjectCoercible - see ASTVariableDecl's identical check for why.
		b.println("requireObjectCoercible(v{0});", vidx);
        int count = fieldInitializers.size();
        // Shared across the whole loop (matching destructParameters(varFactory,path,
        // defaultValue) below) so a trailing rest element sees every property name
        // already consumed by the preceding initializers - a per-call null crashed
        // InitializerSpread.destructParameters with an NPE.
        Set<Object> members = new LinkedHashSet<>();
        for(int i=0; i<count; i++) {
			Initializer initializer = fieldInitializers.get(i);
			initializer.destructParameters( (container,defaultValue,path,spread) -> {
				StringBuilder bb = new StringBuilder();
		        bb.append(StringFormat.format("VarAccessor.destruct({0},v{1}", JSTranspiler.MAIN_ENVIRONMENT, vidx));
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
        		b.println("{0};", container.transpileJavaAssignment(jsContext, ASSIGN_TYPE.EQUALS, bb.toString(), sequence, returnOriginalValue));
			}, RuntimeUtil.EMPTY_PARAMS, members, jsContext, b, null);
        }
		b.println("return v{0};", vidx);
		b.decIndent();
		b.println("})");
		return b.toString();
    }

	@Override
	public void destructParameters(VariableFactory varFactory, Object[] path, JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, DefaultRef defaultValue) {
		Set<Object> members = new LinkedHashSet<>();
        int count = fieldInitializers.size();
        for(int i=0; i<count; i++) {
			Initializer initializer = fieldInitializers.get(i);
			initializer.destructParameters(varFactory, path, members, jsContext, b, defaultValue);
        }
	}
    
    @Override
	public String decompileExpression() {
    	StringBuilder b = new StringBuilder();
		b.append("{");
        for(int i=0; i<fieldInitializers.size(); i++) {
    		if(i>0) {
    			b.append(", ");
    		}
			b.append(fieldInitializers.get(i).decompileExpression());
    	}
		b.append("}");
		return b.toString();
	}
}
