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
package org.monflabs.galtajs.node.clazz;

import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.node.ASTIdentifier;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.node.ASTSuperCtor;
import org.monflabs.galtajs.node.ASTVarContainer.VariableDef;
import org.monflabs.galtajs.node.NodeFactory;
import org.monflabs.galtajs.node.control.ASTFunctionMethod;
import org.monflabs.galtajs.node.control.IContextBlockContainer;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinFunction;
import org.monflabs.galtajs.rt.interpreter.InterpretedBlockRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedFieldInitializerRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedClassPrivateScopeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedUnitRuntimeContext.Signal;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.TranspilerJavaBuilder;
import org.monflabs.galtajs.transpiler.context.JSTranspilerGeneratorContext;
import org.monflabs.galtajs.transpiler.context.TranspilerClassSelfNameContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorBlockSplitContext;
import org.monflabs.galtajs.transpiler.context.TranspilerCodeSplitter;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorConstantPoolContext;
import org.monflabs.galtajs.transpiler.context.TranspilerGeneratorFunctionContext;
import org.monflabs.galtajs.util.JavaBuilder;
import org.monflabs.util.StringUtil;


/**
 * Class declaration.
 * 
 *   Class A {
 *   }
 *   
 *   Class B extends A {
 *   }
 *   
 *   
 *   A: BuiltinClassContructor
 *        A.prototype contains the methods for A (new Object)
 *   
 *   B: BuiltinClassContructor
 *        B.prototype contains the methods for B (new Object)
 *        B.prototype.__proto__ = A.prototype
 *    
 *   
 *   new B()
 *   	  o.__proto__ = B.prototype
 *   
 */
public class ASTClassDecl extends ASTBaseClass /*implements HoistableNode*/ { // Not Hoistable

	private boolean statement;
	private ASTClassMethod ctor;

	// TRANSPILER ONLY (see KnownGaps.md "a class's own name isn't bound in
	// its own separate, immutable inner scope"): a genuinely separate,
	// immutable (VAR_TYPE.CONST) inner binding for this NAMED class's own
	// name, registered - in init() - into the SAME enclosing container/array
	// as everything else in scope, under a synthetic name that can never be
	// typed as a real JS identifier (so it never collides, and is never
	// found by an ordinary name-keyed lookup) - just a genuinely separate
	// Java array SLOT within the exact same array every class-element body
	// already captures via ordinary Java local-class closure capture. Null
	// for an unnamed class (nothing to bind). See transpileJavaExpression's
	// TranspilerClassSelfNameContext for how class-body codegen is routed to
	// it instead of whatever OUTER same-named binding might already exist.
	private VariableDef selfBindingVarDef;
	private static final java.util.concurrent.atomic.AtomicLong SELF_BINDING_COUNTER = new java.util.concurrent.atomic.AtomicLong();

	// Used by ASTVarContainer.wrapForEnclosingClasses (see its own doc): a
	// class METHOD/getter/setter/constructor (unlike a field initializer or
	// static block's own top-level statements, which are transpiled inline
	// via transpileJavaExpression's own already-wrapped `jsContext`) is
	// pre-declared as a sibling Java local class by the ENCLOSING container's
	// transpilerDeclareFunctionClasses - a completely separate, earlier
	// codegen pass that never sees this class's own selfNameContext wrapping
	// at all. getNestedFunctionParentContext is that container's own hook for
	// routing a specific nested function's body codegen through a different
	// parent context; ASTVarContainer's default implementation walks up from
	// the function being declared, and for any enclosing NAMED class found
	// along the way, wraps with THAT class's own self-binding - hence this
	// getter.
	public VariableDef getSelfBindingVarDef() {
		return selfBindingVarDef;
	}

	public ASTClassDecl(Token t, String className, ASTNode superClass, List<ASTNode> elements) {
		this(t,className,superClass,elements,Collections.emptyList());
	}
	public ASTClassDecl(Token t, String className, ASTNode superClass, List<ASTNode> elements, List<ASTNode> decorators) {
		super(t,StringUtil.nonNull(className), superClass, withConstructor(superClass,elements), decorators);
	}
	private static List<ASTNode> withConstructor(ASTNode superClass, List<ASTNode> elements) {
		// Make sure that there is a constructor in the class
		for(int i=0; i<elements.size(); i++) {
			if(elements.get(i) instanceof ASTClassMethod m && m.isConstructor()) {
				return elements;
			}
		}
		
		// If there is no constructor, add one
		// If there is a super class, we need to call the super constructor
		NodeFactory f = NodeFactory.DEFAULT;
		ASTArrayLiteral args = f.createArrayLiteral(null);
		if(superClass!=null) {
			ASTSuperCtor superCtor = superClass!=null ? f.createSuperCtor(null, List.of(f.createFunctionCallSpread(null, f.createIdentifier(null, Arguments.ARGUMENTS)) ) ) : null;
			ASTFunctionMethod fctor = f.createFunctionMethod(null, Constructor.CONSTRUCTOR, args, List.of(superCtor));
			ASTClassMethod ctor = f.createClassMethod(null, Constructor.CONSTRUCTOR, fctor, false, false, true);
			elements.add(ctor);
		} else {
			ASTFunctionMethod fctor = f.createFunctionMethod(null, Constructor.CONSTRUCTOR, args,  Collections.emptyList());
			ASTClassMethod ctor = f.createClassMethod(null, Constructor.CONSTRUCTOR, fctor, false, false, true);
			elements.add(ctor);
		}
		return elements;
	}

	@Override
	public STATEMENT_TYPE getStatementType() {
		return statement ? STATEMENT_TYPE.STATEMENT : STATEMENT_TYPE.EXPRESSION;
	}

	public boolean isStatement() {
		return statement;
	}

	public void setStatement(boolean statement) {
		this.statement = statement;
	}
	
	@Override
	protected void init(InitContext initContext) {
		// A ClassDeclaration is a LexicallyScopedDeclaration, block-scoped
		// exactly like `let`/`const` (spec: CreateMutableBinding + TDZ, never
		// hoisted/initialized ahead of time, never installed as a global
		// object property at script/global scope) - NOT a
		// VarScopedDeclaration like a function declaration. Registers into
		// the nearest BLOCK container (like ASTVariableDecl's let/const
		// branch), not the nearest ROOT (function/program) container - was
		// previously using IContextRootContainer/VAR_TYPE.FUNCTION, which
		// wrongly (a) leaked the binding out of its declaring block to the
		// enclosing function/global scope and (b) installed it as a global
		// property + skipped TDZ (test262 language/global-code/decl-lex.js,
		// language/eval-code/*/lex-env-no-init-cls.js).
		IContextBlockContainer varContainer = findParentNodeByClass(IContextBlockContainer.class);
    	if(isStatement() && StringUtil.isNotEmpty(getClassName())) {
    		// The class's own name binding is a BindingIdentifier in the
    		// ENCLOSING scope (like a function declaration's name) - checked
    		// against the enclosing (parameter) initContext, not the class
    		// body's own always-strict ChildContext constructed below.
    		checkStrictBindingName(initContext, getClassName(), this);
    		varContainer.addVarDeclaration(getClassName(), VAR_TYPE.LET, null);
    	}
    	// TRANSPILER ONLY - see the `selfBindingVarDef` field's own doc. Any
    	// NAMED class (declaration OR expression - unlike the outer LET just
    	// above, which is statement-only) gets this separate inner binding;
    	// an expression has no outer binding at all but its OWN self-
    	// reference is still spec-required. VAR_TYPE.CONST (not FUNCTION_SELF)
    	// for the always-throws-on-write semantics test262 requires even from
    	// sloppy-mode code (name-binding/const.js) - FUNCTION_SELF's write
    	// handling is strict-mode-conditional, which is wrong here.
    	if(StringUtil.isNotEmpty(getClassName())) {
    		selfBindingVarDef = varContainer.addVarDeclaration(
    				"9classSelf$"+SELF_BINDING_COUNTER.incrementAndGet(), VAR_TYPE.CONST, null);
    	}

		ctor = null;
		ASTNode[] nodes = getElements();
		int count = nodes.length;
		for(int i=0; i<count; i++) {
			if(nodes[i] instanceof ASTClassMethod meth) {
				if(meth.isConstructor()) {
					ctor = meth;
					break;
				}
			}
		}
		if(ctor==null) {
			throw RuntimeUtil.referenceError("Class must have a constructor");
		}

		checkDuplicatePrivateNames(nodes);

		// A class body (constructor, methods, fields) is always strict mode code,
		// regardless of whether the enclosing script/function declared "use strict".
    	super.init(new ASTNode.ChildContext(initContext, true, true));
	}

	// ClassBody Early Errors: "It is a Syntax Error if PrivateBoundIdentifiers
	// of ClassElementList contains any duplicate entries, unless the name is
	// used once for a getter and once for a setter and in no other entries,
	// and the getter and setter are either both static or both non-static."
	//
	// A getter/setter PAIR is the single legal repetition; everything else -
	// two fields, a field and a method, a field and an accessor, two getters,
	// a static and a non-static of the same name - is a SyntaxError. An
	// auto-accessor (`accessor #x`) binds its name as BOTH a getter and a
	// setter, so it can never pair with anything, not even a lone setter.
	//
	// Checked here rather than in the grammar because a private name's
	// declaration kind is only known once the element nodes exist; init() is
	// early enough that `eval("class C { #x; #x; }")` throws before the class
	// is ever evaluated, which is what the error is for.
	private static final int PRIV_FIELD  = 1;
	private static final int PRIV_METHOD = 2;
	private static final int PRIV_GET    = 4;
	private static final int PRIV_SET    = 8;
	private void checkDuplicatePrivateNames(ASTNode[] nodes) {
		java.util.Map<String,int[]> seen = null;   // name -> { kindMask, isStatic }
		for(ASTNode node: nodes) {
			if(!(node instanceof ASTClassMember member) || !member.isPrivate()
					|| StringUtil.isEmpty(member.getName())) {
				continue;
			}
			int kind;
			if(node instanceof ASTClassField f) {
				kind = f.isAccessor() ? (PRIV_GET|PRIV_SET) : PRIV_FIELD;
			} else if(node instanceof ASTClassGetter) {
				kind = PRIV_GET;
			} else if(node instanceof ASTClassSetter) {
				kind = PRIV_SET;
			} else {
				kind = PRIV_METHOD;
			}
			String name = member.getName();
			if(seen==null) {
				seen = new java.util.HashMap<>();
			}
			int[] prev = seen.get(name);
			if(prev==null) {
				seen.put(name, new int[] { kind, member.isStatic() ? 1 : 0 });
				continue;
			}
			boolean pairable = (prev[0]==PRIV_GET && kind==PRIV_SET) || (prev[0]==PRIV_SET && kind==PRIV_GET);
			if(!pairable || prev[1]!=(member.isStatic() ? 1 : 0)) {
				throw RuntimeUtil.syntaxError("Duplicate private name #{0} in class body", name);
			}
			prev[0] |= kind;
		}
	}

	@Override
	public Signal evaluate(JSInterpretedRuntimeContext context, JSResult result) {
		try {
			// Per spec (ClassTail), a NAMED class - expression or declaration -
			// creates its own declarative environment with an immutable
			// (TDZ'd) binding for its own name BEFORE the ClassHeritage
			// ("extends") expression is evaluated, so a self-reference there
			// (`class x extends x {}`) must see the TDZ placeholder and throw
			// ReferenceError - never whatever the ENCLOSING scope's own
			// same-named binding happens to hold. This scope used to be
			// created later, inside initClass() below (after superClass had
			// ALREADY been evaluated against the plain enclosing `context`) -
			// correct by accident for a class DECLARATION statement (whose
			// SEPARATE, outer VAR_TYPE.LET binding - see init() above -
			// already has its own container-entry TDZ placeholder that
			// "extends" naturally resolves through instead), but wrong for a
			// class EXPRESSION, which has no such outer binding at all (test262
			// language/statements/class/name-binding/in-extends-expression-
			// assigned.js: `var x = (class x extends x {})` incorrectly saw
			// the enclosing (hoisted, still-undefined) `var x` instead).
			// Reused below (inside initClass()) as classScope's base instead
			// of creating a second, separate self-reference scope - the only
			// observable ordering change is that the private-name scope (if
			// any) now wraps this scope rather than the reverse, which is
			// safe since private-name resolution (InterpretedClassPrivateScope
			// Context.resolvePrivateName()) is a completely separate lookup
			// mechanism from plain identifier resolution.
			// Uses FUNCTION_SELF (not LET) so the SAME immutability semantics
			// this binding always had (assignment silently no-ops in sloppy
			// code / throws TypeError in strict - see ASTIdentifier.
			// evaluateAssign()) still apply once the class body runs -
			// RuntimeUtil.checkTDZ() on the plain-READ path checks the raw
			// TDZ sentinel value regardless of VAR_TYPE, so this still
			// correctly throws ReferenceError for the extends-clause
			// self-reference despite not being LET/CONST/USING.
			boolean named = StringUtil.isNotEmpty(getClassName());
			JSInterpretedRuntimeContext nameScopeContext = context;
			if(named) {
				nameScopeContext = new InterpretedBlockRuntimeContext(context);
				nameScopeContext.createVariable(getClassName(), RuntimeUtil.TDZ, VAR_TYPE.FUNCTION_SELF);
			}
			final JSInterpretedRuntimeContext heritageContext = nameScopeContext;
			Object superClass = getSuperClass() != null ? getSuperClass().evaluateValue(heritageContext, result) : RuntimeUtil.NO_SUPERCLASS;

			// Only a class that actually declares a private member pays for
			// the InterpretedClassPrivateScopeContext scope-chain hop below -
			// an ordinary class parents blockContext directly to the
			// enclosing context instead, adding no extra hop to every
			// method/getter/setter/field-initializer closure's lookup chain
			// (checked once here, at evaluation time, not per-access).
			boolean hasPrivateMembers = false;
			ASTNode[] scanElements = getElements();
			for(int i=0; i<scanElements.length; i++) {
				if(scanElements[i] instanceof ASTClassMember m && m.isPrivate()) {
					hasPrivateMembers = true;
					break;
				}
			}
			boolean finalHasPrivateMembers = hasPrivateMembers;

			BuiltinClassConstructor _clazz = RuntimeUtil.createClass(context.getEnvironment(),getClassName(), superClass, new BuiltinClassConstructor.Initializer() {
				// Created once (during initClass, the first callback to run
				// for any class evaluation) and reused by every later
				// initInstance call for THIS SAME evaluation - see
				// InterpretedClassPrivateScopeContext. Only actually a
				// private scope when finalHasPrivateMembers; otherwise this
				// is just `context` itself.
				private JSInterpretedRuntimeContext classScope;
				// Cached instance field-initializer [[HomeObject]] carrier - see
				// initInstance() below. Keyed on identity of the looked-up
				// prototype (not computed once at class-definition time) so a
				// prototype reassigned between two `new` calls is still picked
				// up exactly as before this cache was added; only the (rare)
				// unchanged case is now free of a redundant allocation.
				private Object cachedInstanceProto;
				private BuiltinFunction cachedInstanceHomeObjectCarrier;
				@Override
				public void initClass(BuiltinClassConstructor clazz) {
					// Base is heritageContext (== context when unnamed), NOT
					// context directly - it already carries the class's own
					// self-reference binding (created, TDZ'd, in evaluate()
					// above, before superClass was evaluated). Initializing it
					// here (rather than creating a brand-new binding the way
					// this used to) is what makes the SAME binding visible to
					// BOTH the extends-clause (already evaluated, TDZ-checked)
					// and the class body below.
					JSInterpretedRuntimeContext scope = heritageContext;
					if(finalHasPrivateMembers) {
						scope = new InterpretedClassPrivateScopeContext(scope, clazz);
						// Pre-mint every private name declared anywhere in this
						// class body UPFRONT: private names are lexically visible
						// throughout the WHOLE class body regardless of
						// declaration order (e.g. an earlier field's initializer
						// referencing a later field's #name), unlike their values.
						ASTNode[] elements = getElements();
						for(int i=0; i<elements.length; i++) {
							if(elements[i] instanceof ASTClassMember m && m.isPrivate()) {
								clazz.getOrCreatePrivateName("#"+m.getName());
							}
						}
					}
					// The class's own name is ALSO bound, immutably, as a
					// BindingIdentifier visible throughout the WHOLE class body
					// (constructor, every method/getter/setter, every field
					// initializer, static or instance) - a SEPARATE binding from
					// the outer, mutable, statement-only VAR_TYPE.LET
					// declaration set up in init()/evaluate() below, exactly
					// mirroring a named function EXPRESSION's own FUNCTION_SELF
					// binding (same immutability, same "own dedicated scope so
					// methods close over it" mechanism) - except this applies to
					// BOTH declaration and expression forms, since spec makes a
					// class's inner self-reference always immutable regardless.
					// Transitions the EXISTING (TDZ) binding to the real class
					// value, rather than creating a second, separate one.
					if(StringUtil.isNotEmpty(getClassName())) {
						heritageContext.setVariable(getClassName(), clazz);
					}
					classScope = scope;
					// [[HomeObject]] for a static field initializer (or any closure
					// created within it, e.g. an arrow function or eval'd code) is
					// the class constructor itself - a static method/field's `super`
					// resolves via the constructor's own [[Prototype]] (the super
					// class's constructor), not via the instance prototype chain.
					InterpretedFieldInitializerRuntimeContext blockContext =
							new InterpretedFieldInitializerRuntimeContext(classScope, clazz, clazz);
					// Must run BEFORE any class element - see
					// initClassConstructorLength()'s own doc for why a static
					// element literally named "length" needs to be able to
					// override this afterward, unconditionally.
					clazz.initClassConstructorLength(ctor.getFunctionDecl().getParamLength());
					blockContext.run( () -> {
						ASTNode[] nodes = getElements();
						// A dedicated, throwaway JSResult - NOT the outer evaluate()
						// call's own `result` (which tracks THIS class DECLARATION's
						// completion value, per spec always empty/untouched for a
						// statement - see the `statement` branch below) - each class
						// element (constructor/method/field) only ever WRITES its own
						// value here as scratch space (e.g. ASTClassMethod.evaluate()
						// stashes the compiled function into its own field, never
						// reads `result` back), so reusing the outer `result` merely
						// leaked the LAST element's value into it as a side effect,
						// corrupting `eval('class C {}')`'s completion value with the
						// class's own (default) constructor function instead of
						// leaving it empty (test262 language/statements/class/
						// cptn-decl.js).
						JSResult elementResult = new JSResult();
						for(int i=0; i<nodes.length; i++) {
							nodes[i].evaluate(blockContext,elementResult);
							if(nodes[i] instanceof ASTClassMember m) {
								m.initClass(blockContext, clazz);
							}
						}
						// A static field's VALUE initializer AND a static block's
						// BODY both run in a SEPARATE, source-ordered second pass,
						// strictly AFTER every element above (static or instance,
						// field or method) has already had its own key/name
						// computed and every method/getter/setter fully installed
						// - see ASTClassField.initClass()'s and
						// ASTClassStaticBlock.runStaticBlock()'s own comments for
						// the full spec citation and the test262 files
						// (intercalated-static-non-static-computed-fields.js,
						// static-init-sequence.js, static-init-scope-private.js)
						// this fixes. Static fields and static blocks share ONE
						// ordered list per spec (ClassDefinitionEvaluation step
						// 34) - iterating `nodes` in source order here keeps them
						// correctly interleaved.
						for(int i=0; i<nodes.length; i++) {
							if(nodes[i] instanceof ASTClassField f && f.isStatic()) {
								f.initStaticFieldValue(blockContext, clazz);
							} else if(nodes[i] instanceof ASTClassStaticBlock b) {
								b.runStaticBlock(blockContext, clazz);
							}
						}
					});
				}
				@Override
				public void initInstance(BuiltinClassConstructor clazz, Object instance) {
					// [[HomeObject]] for an instance field initializer (or any closure
					// created within it) is the class's prototype - same object a real
					// instance method's [[HomeObject]] is set to by
					// BuiltinClassConstructor.addClassMethod.
					Object proto = clazz.getProperty(Constructor.PROTOTYPE);
					if(proto!=cachedInstanceProto) {
						cachedInstanceHomeObjectCarrier = InterpretedFieldInitializerRuntimeContext.createHomeObjectCarrier(classScope, proto);
						cachedInstanceProto = proto;
					}
					InterpretedFieldInitializerRuntimeContext blockContext =
							new InterpretedFieldInitializerRuntimeContext(classScope, instance, cachedInstanceHomeObjectCarrier);
					blockContext.run( () -> {
						initializeInstance(blockContext, clazz, instance);
					});
				}
			});

			// Class decorators evaluate in the ENCLOSING context (this method's
			// own `context`, not the class body's own scope used inside the
			// Initializer callback above) and apply once the class is fully
			// defined - a decorator returning a callable replaces the binding
			// used for both the `class X {}` statement's own name binding and
			// this expression's value.
			Object classValue = _clazz;
			if(getDecorators().length>0) {
				Object className = getClassName()!=null ? getClassName() : RuntimeUtil.UNDEFINED;
				classValue = ASTClassMember.applyDecorators(context, getDecorators(), _clazz, "class", className, false, false);
			}

			if(statement) {
				// The class's own name binding is block-scoped (VAR_TYPE.LET,
				// registered on the nearest IContextBlockContainer - see
				// init() above), NOT hoisted to the nearest function/root
				// scope like VAR/FUNCTION - so it must be set on `context`
				// itself (the actual block runtime context the TDZ
				// placeholder was created in), not getVarDeclContext() (which
				// skips past intervening block contexts to the var-scoped
				// one, exactly like ASTFunctionDecl's `hoistedToRoot` branch
				// does for a genuinely hoisted declaration - see its own
				// comment). Using getVarDeclContext() here made the binding
				// unfindable whenever the class sits inside a nested block,
				// e.g. `{ class Base {} class Sub extends Base {} }`.
				// An ANONYMOUS statement-mode class - only possible via
				// `export default class {}` (a bare `class {}` statement is
				// itself a SyntaxError; only export-default's ClassDeclaration
				// form allows an omitted name) - has no such binding to set at
				// all (init() above already skipped registering one, guarded
				// by the same isNotEmpty check). Unlike the named case, this
				// is the ONLY way the class's own value is ever observable
				// again (no binding anywhere holds it), so - unlike the
				// "NormalCompletion(empty)" comment below, which is correct
				// for the NAMED case - `result` must actually carry it, same
				// as the non-statement (expression) branch: ASTExport.
				// evaluate() reads a NAMED default export's value back BY
				// NAME afterward (a live-ish re-read), but an anonymous one
				// has no name to re-read, so its ONLY value ever flows
				// through here (confirmed via eval-export-dflt-cls-name-
				// meth.js/eval-export-dflt-cls-anon-semi.js: `imported.
				// default` must be the actual class, not undefined).
				if(StringUtil.isNotEmpty(getClassName())) {
					context.setVariable(getClassName(),classValue);
				} else {
					result.setValue(classValue);
				}
				// ClassDeclaration : class BindingIdentifier ClassTail returns
				// NormalCompletion(empty) - unlike ASTFunctionDecl (whose
				// statement branch calls result.setUndefined() unconditionally
				// and gets away with it only because a FunctionDeclaration is
				// ALSO a HoistableNode, physically reordered to run before
				// every other statement, so its result-clobbering is always
				// immediately overwritten by whatever runs after it),
				// ASTClassDecl is deliberately NOT Hoistable - a class
				// declaration evaluates in its normal source position, so
				// "empty" here must mean truly leaving `result` untouched,
				// preserving whatever a PRIOR statement already left there
				// (confirmed via test262 language/statements/class/
				// cptn-decl.js: `eval('class C {}')` must be undefined - true
				// here because ASTProgram.evaluate() seeds `result` with
				// setUndefined() before running any statement - while
				// `eval('1; class C {}')` must still read back `1`).
			} else {
				result.setValue(classValue);
			}
			return Signal.NONE;
		} catch(Throwable ex) {
			throw fillInStackTrace(ex);
		}		
	}
	
	public void initializeInstance(JSInterpretedRuntimeContext context,  BuiltinClassConstructor clazz, Object instance) {
		// Spec's InitializeInstanceElements installs ALL "own"-placement
		// elements (i.e. instance private methods/accessors - public
		// instance methods live on the prototype instead, installed once at
		// initClass time) FIRST, in source order, THEN runs every field
		// initializer (public or private), also in source order - NOT a
		// single interleaved pass over source order. Per the spec's own
		// editor's note: "Value properties are added before initializers so
		// that private methods are visible from all initializers" - e.g. a
		// field `a = this.#m()` must see `#m` even if `#m` is declared
		// LATER in the class body (test262
		// prod-private-method-initialize-order.js).
		ASTNode[] nodes = getElements();
		for(int i=0; i<nodes.length; i++) {
			if(nodes[i] instanceof ASTClassMethod || nodes[i] instanceof ASTClassGetter || nodes[i] instanceof ASTClassSetter) {
				((ASTClassMember)nodes[i]).initInstance(context, clazz, instance);
			}
		}
		for(int i=0; i<nodes.length; i++) {
			if(nodes[i] instanceof ASTClassField m) {
				m.initInstance(context, clazz, instance);
			}
		}
	}


	@Override
	public void transpileJavaStatement(JSTranspilerGeneratorContext _jsContext, TranspilerJavaBuilder b) {
		VariableDef vf = findVariable(getClassName());
		b.println("{0} = {1};", vf.getJavaVariableValue(), transpileJavaExpression(_jsContext));
	}

    @Override
	public String transpileJavaExpression(JSTranspilerGeneratorContext _jsContext) {
    	ASTNode[] nodes = getElements();

    	// Only classes that actually declare a private member pay for the
    	// per-evaluation private-name scope below - an ordinary class compiles
    	// to exactly the same code as before this feature existed.
    	boolean hasPrivateMembers = false;
    	for(int i=0; i<nodes.length; i++) {
    		if(nodes[i] instanceof ASTClassMember m && m.isPrivate()) {
    			hasPrivateMembers = true;
    			break;
    		}
    	}

    	// Unique per class EVALUATION SITE (not per runtime evaluation - this is a
    	// compile-time generated Java field name), never just "classScope": a
    	// class nested inside an OUTER private-bearing class's own body (e.g. an
    	// instance field initializer `x = class Inner { #y; ... }`) gets its OWN
    	// Initializer with its OWN field of this name - an unqualified reference
    	// to a fixed, non-unique name like "classScope" would be silently
    	// shadowed by the INNER class's own field of the same name (Java's
    	// ordinary nested-class shadowing rules), making the inner class's field
    	// self-referentially read its own not-yet-initialized (null) value
    	// instead of reaching the outer class's real one.
    	String classScopeFieldName = hasPrivateMembers ? _jsContext.generateUniqueId("classScope_") : null;

    	// TRANSPILER ONLY - see `selfBindingVarDef`'s own doc and KnownGaps.md
    	// "a class's own name isn't bound in its own separate, immutable inner
    	// scope". Inserted BELOW `_jsContext` (i.e. checked FIRST by the
    	// getOwnVariable() parent-chain walk every class-element body's own
    	// identifier resolution performs) so any reference to this class's own
    	// name from WITHIN the class body (methods/getters/setters/field
    	// initializers/static blocks) resolves to this separate inner binding
    	// instead of whatever OUTER same-named binding happens to already
    	// exist - never used for the ClassHeritage expression itself, which
    	// keeps resolving through the un-wrapped `_jsContext` below (see its
    	// own comment: heritage must keep seeing exactly what it saw before
    	// this fix, except for the already-separately-fixed bare-self-
    	// reference special case).
    	JSTranspilerGeneratorContext selfNameContext = selfBindingVarDef!=null
    			? new TranspilerClassSelfNameContext(_jsContext, getClassName(), selfBindingVarDef)
    			: _jsContext;

    	// The class Initializer is emitted as its own separate Java class
    	// (`new BuiltinClassConstructor.Initializer() {...}`), so - exactly like
    	// each per-function class in ASTVarContainer (see its
    	// TranspilerGeneratorConstantPoolContext use) - it must own its OWN
    	// constant pool: a large string/array literal referenced inside a class
    	// element's body belongs to THIS class, not to whatever enclosing
    	// function/module pool happens to be next up the parent chain. Without
    	// this, getConstantPool() delegated to that enclosing pool, so line
    	// ~745's createConstantPool(jsContext,...) dumped every UNRELATED
    	// enclosing-scope constant into the class body (and duplicated it,
    	// since the enclosing scope emits its own pool too). The ClassHeritage
    	// expression below deliberately keeps using `_jsContext` (NOT this
    	// pooled context), so its constants correctly register in - and are
    	// emitted by - the enclosing scope, matching where heritage evaluates.
    	JSTranspilerGeneratorContext classPoolContext = new TranspilerGeneratorConstantPoolContext(selfNameContext);

    	// A class body does NOT introduce a new Java method scope (it's still
    	// inside whatever enclosing initClass/initInstance/callVoid the class
    	// declaration itself appears in) - so when this class has no private
    	// members of its own, its context name must be INHERITED from whatever
    	// encloses it (via the null-means-inherit 2-arg constructor), not reset
    	// to the plain single-arg form (which would incorrectly reference a
    	// nonexistent "_ctx" when nested inside an OUTER private-bearing class's
    	// own body - see TranspilerGeneratorFunctionContext's doc). The
    	// interposed constant-pool context above delegates getContextJavaName()
    	// straight through, so that inheritance is unaffected.
    	TranspilerGeneratorFunctionContext jsContext =
    			new TranspilerGeneratorFunctionContext(classPoolContext, classScopeFieldName);
  		TranspilerJavaBuilder b = jsContext.createJavaBuilder();

		// NO_SUPERCLASS (not a literal "null") when there's no ClassHeritage at
		// all - see RuntimeUtil.createClass()'s doc: a real `null` here must be
		// reserved for a ClassHeritage expression that evaluates to JS null
		// (`extends null`), which needs different [[Prototype]] handling than
		// "no heritage clause" does.
		//
		// The ClassHeritage expression is transpiled with `_jsContext` (the
		// ORIGINAL, un-wrapped parameter - whatever truly lexically encloses
		// this class declaration), NOT `jsContext` (wrapped, above, pinned to
		// this class's own `classScope_N` when it has private members) -
		// mirrors evaluate()'s own `heritageContext` (built from `context`,
		// never from anything private-scope-related). Per spec the heritage
		// clause evaluates BEFORE this class's own scope (private-name or
		// otherwise) exists at all; using `jsContext` here previously emitted
		// a reference to `classScope_N` from within the heritage expression's
		// own (textually earlier, sibling, not enclosing) generated
		// Initializer - a Java "cannot find symbol" compile error whenever
		// the heritage expression contained its own class body needing a
		// context at all (test262 privatefieldset-evaluation-order-1.js:
		// `class C extends class {} { #field; ... } `).
		String superClass;
		if(getSuperClass() instanceof ASTIdentifier heritageIdent && StringUtil.isNotEmpty(getClassName()) && getClassName().equals(heritageIdent.getId())) {
			// `class x extends x {}` - see RuntimeUtil.throwClassSelfHeritageReferenceError()'s
			// own doc for why this always throws, regardless of any outer
			// same-named binding the generic identifier-resolution codegen
			// below would otherwise (wrongly) fall through to.
			superClass = "throwClassSelfHeritageReferenceError(" + JSTranspiler.literal(getClassName()) + ")";
		} else {
			superClass = getSuperClass()!=null ? JSTranspiler.asValue(_jsContext, getSuperClass()) : "NO_SUPERCLASS";
		}

		b.println("createClass({0},{1},new BuiltinClassConstructor.Initializer() {", JSTranspiler.literal(getClassName()), superClass);

		b.incIndent();
		if(hasPrivateMembers) {
			// Set once (initClass) and reused by every later initInstance call
			// for THIS SAME evaluation - see TranspiledClassPrivateScopeRuntimeContext.
			b.println("private JSTranspiledRuntimeContext {0};", classScopeFieldName);
			for(int i=0; i<nodes.length; i++) {
				if(needsPrivateInstanceFunctionField(nodes[i])) {
					// A private (non-static) method/accessor's function is created
					// once, here, and reused per-instance in initInstance -
					// private access never walks the prototype chain, so every
					// instance needs its OWN [[PrivateElements]] entry pointing
					// at the SAME shared function value.
					b.println("private BuiltinFunction priv_{0};", i);
				}
			}
		}
		// Per spec (ClassFieldDefinitionEvaluation), EVERY field's NAME -
		// static or instance alike - is computed once, at class-DEFINITION
		// time (initClass), in source order; only a field's VALUE
		// initializer is deferred (instance fields: per-`new`, in
		// initInstance). A computed instance-field name needs somewhere to
		// live between those two separately-generated methods - an
		// instance field of this SAME Initializer object, set once in
		// initClass() and read back (not recomputed) in initInstance() -
		// see ASTClassField.transpileInitClassStatement/
		// transpileInitInstanceStatement, which key off this same `i`
		// index (matching the `priv_{0}` field-naming pattern above).
		for(int i=0; i<nodes.length; i++) {
			if(nodes[i] instanceof ASTClassField f && !f.isStatic() && !f.isPrivate() && f.getNameNode()!=null) {
				b.println("private Object instFieldKey_{0};", i);
			}
		}
		// Same caching, for a STATIC field's computed name - its VALUE is
		// deferred to a later pass (see ASTClassField.
		// transpileInitStaticValueStatement's own doc for the full
		// rationale), so the key computed here (in the first, source-order
		// pass) needs somewhere to live until that later pass reads it back.
		for(int i=0; i<nodes.length; i++) {
			if(nodes[i] instanceof ASTClassField f && f.isStatic() && !f.isPrivate() && f.getNameNode()!=null) {
				b.println("private Object statFieldKey_{0};", i);
			}
		}
		b.println("@Override");
		b.println("public void initClass(BuiltinClassConstructor {0}) {",JSTranspiler.THIS_VAR);
		b.incIndent();
		if(hasPrivateMembers) {
			// The parent argument here must be the ENCLOSING scope's own resolved
			// context name (_jsContext.getContextJavaName()), not a hardcoded
			// "_ctx" - when this class is itself nested inside an OUTER
			// private-bearing class's body (e.g. an instance field initializer
			// `x = class Inner { #y; ... }`), the enclosing scope's real context
			// variable is "classScope" (the OUTER class's), not "_ctx" (which
			// wouldn't even exist as a variable there) - same root cause as the
			// no-private-members case, see TranspilerGeneratorFunctionContext's doc.
			b.println("{0} = new TranspiledClassPrivateScopeRuntimeContext({1}, {2});", classScopeFieldName, _jsContext.getContextJavaName(), JSTranspiler.THIS_VAR);
		}
		if(hasPrivateMembers) {
			// Pre-mint every private name declared anywhere in this class
			// body UPFRONT, mirroring the interpreter's own identical
			// pre-pass (ASTClassDecl.evaluate()'s Initializer.initClass(),
			// see its comment) and spec's NewPrivateName/
			// PrivateBoundIdentifiers timing (ClassDefinitionEvaluation):
			// private names are lexically visible throughout the WHOLE
			// class body regardless of declaration order. Private (static
			// or instance) METHODS/getters/setters already pre-mint
			// correctly as a side effect of running inline, in source
			// order, within THIS SAME initClass loop below (their own
			// transpileInitClassStatement calls getOrCreatePrivateName
			// eagerly) - but a private INSTANCE field's name was only
			// ever minted lazily, inside initInstance (ASTClassField.
			// transpileInitInstanceStatement), the first time `new`
			// actually ran for THIS class. So a #name reference reached
			// before any instance of the declaring class existed (test262
			// language/expressions/in/private-field-presence-field-shadowed.js:
			// a nested class's own static method checks `#field in value`
			// before any instance of that nested class was ever
			// constructed) incorrectly found nothing in this (still-empty)
			// class scope and fell through to an ENCLOSING class's own
			// same-named private field instead. Reuses
			// transpileClassElementLoop's existing chunking (not just an
			// inline for-loop) so a class with thousands of private
			// fields (language/identifiers/*unicode*-class.js) doesn't
			// reintroduce the 64KB-bytecode-limit compile failure that
			// splitter was added to fix - getOrCreatePrivateName is
			// idempotent (computeIfAbsent), so re-minting a static
			// field's/method's own name here too is harmless.
			transpileClassElementLoop(jsContext, b, nodes.length, (ctx,i) -> {
				if(nodes[i] instanceof ASTClassMember m && m.isPrivate()) {
					b.println("{0}.getOrCreatePrivateName({1});", JSTranspiler.THIS_VAR, JSTranspiler.literal("#"+m.getName()));
				}
			});
		}
		if(selfBindingVarDef!=null) {
			// The class's own name is bound, immutably, in its own separate
			// inner slot (see `selfBindingVarDef`'s own doc) - transitions it
			// from TDZ to the real class value HERE, before any class element
			// below runs, mirroring the interpreter's identical
			// `heritageContext.setVariable(getClassName(), clazz)` call in
			// evaluate()'s Initializer.initClass(). A direct slot write (not
			// through the const-throws-on-write user-assignment path) - this
			// is the engine's own one-time binding initialization, exactly
			// like every other slot's TDZ pre-fill/clear.
			b.println("{0} = {1};", selfBindingVarDef.getJavaVariableValue(), JSTranspiler.THIS_VAR);
		}
		// The class object's own "length" is hardcoded to 1 by every
		// BuiltinClassConstructor (see BaseConstructor's constructor) - must
		// be overwritten with the real constructor arity here, before the
		// per-element loop below, so a class element literally named
		// "length" (a static field/method) can still win afterward, exactly
		// mirroring the interpreter's own initClassConstructorLength() call
		// in ASTClassDecl.evaluate()'s Initializer.initClass().
		b.println("{0}.initClassConstructorLength({1});", JSTranspiler.THIS_VAR, ctor.getFunctionDecl().getParamLength());
		// First pass: every element's own key/name is computed here, in
		// source order (methods/getters/setters are also fully installed
		// here - they have no deferred "value" step, unlike fields) - but a
		// STATIC field's VALUE and a static BLOCK's body are NOT run here
		// (see ASTClassField.transpileInitStaticValueStatement's own
		// comment for the full rationale and the previously-reverted
		// attempt this generalizes correctly).
		transpileClassElementLoop(jsContext, b, nodes.length, (ctx,i) -> {
			if(nodes[i] instanceof ASTClassMember m) {
				b.debugLocation(m);
				m.transpileInitClassStatement(ctx, b, JSTranspiler.THIS_VAR, i);
			}
		});
		// Second, deferred pass: a static field's VALUE initializer and a
		// static block's BODY both run here, strictly AFTER every element
		// above has already had its own key/name computed and every
		// method/getter/setter fully installed - see ASTClassMember.
		// transpileInitStaticValueStatement's own doc for the full spec
		// citation. Static fields and static blocks share ONE ordered list
		// per spec (ClassDefinitionEvaluation step 34) - iterating `nodes`
		// in source order here keeps them correctly interleaved, exactly
		// mirroring the interpreter's own identical second pass
		// (ASTClassDecl.evaluate()'s Initializer.initClass()).
		transpileClassElementLoop(jsContext, b, nodes.length, (ctx,i) -> {
			if(nodes[i] instanceof ASTClassField f && f.isStatic()) {
				b.debugLocation(f);
				f.transpileInitStaticValueStatement(ctx, b, JSTranspiler.THIS_VAR, i);
			} else if(nodes[i] instanceof ASTClassStaticBlock sb) {
				b.debugLocation(sb);
				sb.transpileInitStaticValueStatement(ctx, b, JSTranspiler.THIS_VAR, i);
			}
		});
		b.decIndent();
		b.println("}");
		b.decIndent();

		b.incIndent();
		b.println("@Override");
		b.println("public void initInstance(BuiltinClassConstructor clazz, Object {0}) {",JSTranspiler.THIS_VAR);
		b.incIndent();
		// Same 2-pass ordering as the interpreter's initializeInstance()
		// above: all "own"-placement methods/accessors first, THEN all
		// field initializers, both in source order - not a single
		// interleaved source-order pass. See that method's comment for the
		// spec citation (InitializeInstanceElements steps 4-5) and the
		// test262 file this fixes.
		transpileClassElementLoop(jsContext, b, nodes.length, (ctx,i) -> {
			if(nodes[i] instanceof ASTClassMethod || nodes[i] instanceof ASTClassGetter || nodes[i] instanceof ASTClassSetter) {
				ASTClassMember m = (ASTClassMember)nodes[i];
				b.debugLocation(m);
				m.transpileInitInstanceStatement(ctx, b, "clazz", JSTranspiler.THIS_VAR, i);
			}
		});
		transpileClassElementLoop(jsContext, b, nodes.length, (ctx,i) -> {
			if(nodes[i] instanceof ASTClassField m) {
				b.debugLocation(m);
				m.transpileInitInstanceStatement(ctx, b, "clazz", JSTranspiler.THIS_VAR, i);
			}
		});
		b.decIndent();
		b.println("}");

		JSTranspiler.createConstantPool(jsContext,b);

		b.decIndent();
		b.println("})");

		return b.toString();
    }

    // Splits a large per-class-element loop (initClass/initInstance) into
    // multiple separately-compiled Runnable.run() bodies once the element
    // count grows large enough to risk exceeding the JVM's 64KB per-method
    // bytecode limit - a class with thousands of elements (test262
    // language/identifiers/*unicode*.js: thousands of `#privateField;`
    // declarations, one statement each) previously emitted one flat
    // sequence of statements straight into a single method body ("This is
    // initialization, no need to split?" - it did need to). Same
    // "(new Runnable(){...}).run()" idiom ASTBlock.transpileBlockStatements
    // already uses for ordinary statement blocks, each chunk getting its
    // own constant pool (TranspilerGeneratorBlockSplitContext) since it's
    // now a distinct generated scope; the enclosing method's own THIS_VAR/
    // "clazz" parameters and any classScope/priv_N fields remain reachable
    // via ordinary Java closure/outer-class-instance access, unaffected by
    // this wrapping. Below the threshold, emits inline exactly as before
    // (no anonymous class overhead for the common, small-class case).
    private void transpileClassElementLoop(JSTranspilerGeneratorContext jsContext, TranspilerJavaBuilder b, int length, java.util.function.BiConsumer<JSTranspilerGeneratorContext,Integer> emitter) {
    	int chunkSize = TranspilerCodeSplitter.DEFAULT_BLOCK_SPLIT_MAX;
    	if(length<=chunkSize) {
    		for(int i=0; i<length; i++) {
    			emitter.accept(jsContext, i);
    		}
    		return;
    	}
    	for(int pos=0; pos<length; pos+=chunkSize) {
    		int end = Math.min(pos+chunkSize, length);
    		b.println("(new Runnable() { // Begin class element split");
    		b.incIndent();
    		b.println("@Override");
    		b.println("public void run() {");
    		b.incIndent();
    		TranspilerGeneratorBlockSplitContext splitCtx = new TranspilerGeneratorBlockSplitContext(jsContext);
    		for(int i=pos; i<end; i++) {
    			emitter.accept(splitCtx, i);
    		}
    		b.decIndent();
    		b.println("}");
    		b.decIndent();
    		JSTranspiler.createConstantPool(splitCtx,b);
    		b.println("}).run(); // End class element split");
    	}
    }

    // A private (non-static) method/getter/setter's function value must be
    // created once (initClass) and reused per-instance (initInstance, via
    // addInstancePrivateMethod/addInstancePrivateAccessor) - static private
    // members and fields don't need this (static: set once, directly on the
    // class object; fields: each instance evaluates its own initializer).
    private static boolean needsPrivateInstanceFunctionField(ASTNode n) {
    	if(n instanceof ASTClassMethod m) {
    		return !m.isStatic() && !m.isConstructor() && m.isPrivate();
    	}
    	if(n instanceof ASTClassGetter g) {
    		return !g.isStatic() && g.isPrivate();
    	}
    	if(n instanceof ASTClassSetter s) {
    		return !s.isStatic() && s.isPrivate();
    	}
    	return false;
    }
    
    
    @Override
	public String decompileExpression() {
		JavaBuilder b = new JavaBuilder();
   		b.append("class ");
		b.append(getClassName());
		if(getSuperClass()!=null) {
			b.append(" extends ");
			b.append(getSuperClass().decompileExpression());
		}
   		b.append(" {\n");

   		b.incIndent();
		ASTNode[] nodes = getElements();
		for(int i=0; i<nodes.length; i++) {
			if(nodes[i] instanceof ASTClassMember m) {
				if(!m.isAutoGenerated()) {
					m.decompile(b);
				}				
			}
		}
		b.decIndent();
   		b.append("}\n");

		return b.toString();
	}
}