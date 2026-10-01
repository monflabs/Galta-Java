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
package org.monflabs.galtajs.rt.builtins.standard.function;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseInternalObject;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.primitives.BaseStandardConstructor;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateElementsHolder;
import org.monflabs.galtajs.rt.builtins.privatename.PrivateName;

/**
 * Use to create a JavaScript class.
 */
public class BuiltinClassConstructor extends BaseStandardConstructor {
	
	public static interface Initializer {
		public default void initClass(BuiltinClassConstructor clazz) {}
		public default void initInstance(BuiltinClassConstructor clazz, Object instance) {}
	}

	public static final String CLASSNAME = "Class";
	
	private Initializer initializer;
	private Constructor superClass;
	// Whether a ClassHeritage ("extends ...") clause is textually PRESENT,
	// independent of what it evaluates to - distinct from `superClass!=null`
	// below (the ACTUAL superclass Constructor object, which is Java `null`
	// both for "no extends clause at all" AND for "extends null"). Per spec
	// (ClassDefinitionEvaluation), F's [[ConstructorKind]] is "derived"
	// whenever ClassHeritage is present, even when it evaluates to null -
	// this drives constructObject()'s branch below (derived constructors
	// start with an uninitialized `this`, only bound once/if super() runs;
	// base constructors get `this` eagerly). Using `superClass!=null` alone
	// incorrectly treated `class Foo extends null {}` as a non-derived (base)
	// class - `this` was eagerly initialized instead of staying TDZ'd until
	// a super() call, so a constructor body referencing `this` before
	// super() (or never calling super() at all) never threw the required
	// ReferenceError (test262 language/statements/class/subclass/
	// class-definition-null-proto-this.js).
	private boolean derived;
	private BuiltinFunction functionConstructor;
	// One fresh PrivateName token minted per private declaration NAME, scoped
	// to THIS evaluation of the class (spec: PrivateEnvironment/NewPrivateName)
	// - keyed by the literal "#name" string purely as a lookup convenience
	// within this single class-evaluation instance, never used as the actual
	// storage key on an object (that's always the PrivateName value itself).
	private Map<String,PrivateName> privateNames;
	// Non-static field computed names must be evaluated once at class-
	// definition time (ClassFieldDefinitionEvaluation's "Let fieldName be
	// the result of evaluating ClassElementName" runs during
	// ClassDefinitionEvaluation, alongside methods/accessors/static fields),
	// then REUSED for every later `new` - only the field's VALUE initializer
	// is genuinely per-instance. Scoped per-BuiltinClassConstructor (i.e. per
	// evaluation of the class declaration/expression, exactly like
	// privateNames above) since the SAME AST node (ASTClassField) is reused
	// across every evaluation of an enclosing function's class declaration -
	// caching on the AST node itself would leak a stale name across separate
	// executions. See ASTClassField.initClass/initInstance.
	private Map<Object,Object> computedFieldNames;

	public BuiltinClassConstructor(JSEnvironment env, String className, BaseInternalObject prototypeOfConstructed, Constructor superClass, boolean derived, Initializer initializer) {
		super(env,className,prototypeOfConstructed,1);
		this.initializer = initializer;
		if(superClass!=null) {
			setPrototype(superClass);
		}
		prototypeOfConstructed.setOwnProperty(Constructor.CONSTRUCTOR, this, PropertyDescriptor.DESC_PROP_CONSTRUCTOR);
		this.superClass = superClass;
		this.derived = derived;

		if(initializer!=null) {
			initializer.initClass(this);
		}
	}

	// A class constructor's own "caller"/"arguments" should always be a
	// %ThrowTypeError% poison pill (spec) - tried adding
	// getOwnPropertyDescriptor/getOwnProperty/setOwnProperty overrides here
	// unconditionally poisoning both, but that incorrectly also poisoned a
	// user-defined static method literally named "arguments"/"caller"
	// (legal - see language/statements/class/definition/
	// methods-named-eval-arguments.js, which regressed). The correct fix is
	// upstream: %Function.prototype%'s own "caller"/"arguments" own
	// properties (BuiltinFunctionPrototype, only installed when
	// env.isDeprecatedApis() && !env.isStrictMode()) are currently a
	// silent no-op stub (get returns null, set returns false) rather than
	// a genuine throwing accessor - fixing that would let an ordinary
	// class inherit correct poisoning through the prototype chain, same as
	// BuiltinFunction.poisonsCallerArguments() already does for a plain
	// function, WITHOUT needing any BuiltinClassConstructor-specific
	// override (a class never gets its own "caller"/"arguments" property
	// unless a user explicitly defines one, so the inherited behavior
	// would apply cleanly). Attempted this fix too; reverted after it
	// surfaced test-order-dependent flakiness across a test262 directory
	// sweep (passes every file in isolation, but 2-3 files intermittently
	// fail only when run together in one JVM fork - the same class of
	// shared-state-across-files issue documented for built-ins/Iterator
	// elsewhere in Test262BaseTest.java) that wasn't worth chasing down
	// for this narrow a gap. See
	// language/statements/class/restricted-properties.js's FILTER entry.

	// Mints (on first request) or returns the SAME PrivateName token for
	// "#name" within THIS evaluation of the class - shared by a field, every
	// method/getter/setter declaration using that name, and every #name
	// reference lexically inside the class body (see
	// InterpretedClassPrivateScopeContext).
	public PrivateName getOrCreatePrivateName(String name) {
		if(privateNames==null) {
			privateNames = new LinkedHashMap<>();
		}
		return privateNames.computeIfAbsent(name, PrivateName::new);
	}

	// See computedFieldNames' field comment. "key" identifies the specific
	// field declaration (its own ASTClassField instance is a fine key - one
	// entry per textual field, regardless of how many times it's read back
	// across possibly-many `new` calls).
	public void setComputedFieldName(Object key, Object name) {
		if(computedFieldNames==null) {
			computedFieldNames = new java.util.IdentityHashMap<>();
		}
		computedFieldNames.put(key, name);
	}
	public Object getComputedFieldName(Object key) {
		return computedFieldNames!=null ? computedFieldNames.get(key) : null;
	}
	// Lookup-only (no minting) - used when resolving a #name REFERENCE
	// against an enclosing class scope; null means this class doesn't
	// declare that name (the caller should keep walking outward).
	public PrivateName getOwnPrivateName(String name) {
		return privateNames!=null ? privateNames.get(name) : null;
	}

	// All "#name" keys minted so far for THIS class evaluation - used only by
	// the direct-eval AllPrivateNamesValid early-error check (see
	// JSRuntimeContext.collectEnclosingPrivateNames(),
	// InterpretedClassPrivateScopeContext) to build the caller-supplied set
	// of currently-valid private names threaded into a freshly-parsed eval's
	// PrivateNameValidator check. Every member declaration mints its
	// PrivateName eagerly during ClassDefinitionEvaluation (getOrCreate
	// PrivateName(), called by ASTClassMember/ASTClassDecl while the class
	// body evaluates) - by the time any of this class's OWN methods/field
	// initializers/static blocks can actually run (and so could reach an
	// eval() call), the class has already fully evaluated, so this always
	// reflects the class's complete declared set, never a partial one.
	public java.util.Set<String> getOwnPrivateNames() {
		return privateNames!=null ? privateNames.keySet() : java.util.Collections.emptySet();
	}

	public void addClassStaticField(Object name, Object value) {
		// DefineField's installation step is CreateDataPropertyOrThrow, not a
		// plain non-throwing define - e.g. a static field initializer that
		// calls Object.preventExtensions(ClassName) on the class being
		// defined (making it non-extensible) makes THIS define fail; the
		// non-throwing 2-arg setOwnProperty() used to silently drop the
		// field instead of throwing TypeError, so `C.x` ended up undefined
		// with no error at all.
		if(name instanceof PrivateName pn) {
			checkCanDefinePrivate(this, pn);
			definePrivateElement(pn, value, PropertyDescriptor.DESC_HIDDEN_PROP);
		} else if(name instanceof Symbol sy) {
			setOwnProperty(sy, value, null, DESC_CHECK.STRICT);
		} else {
			setOwnProperty(RuntimeUtil.toString(getEnvironment(), name), value, null, DESC_CHECK.STRICT);
		}
	}
	public void addClassField(Object instance, Object name, Object value) {
		// ClassFieldDefinitionEvaluation/InitializeInstanceElements: an
		// instance field is installed via CreateDataPropertyOrThrow
		// ([[DefineOwnProperty]]), never [[Set]] - it must always become an
		// OWN property of the instance, ignoring (not consulting the
		// writability of) any same-named inherited accessor/data property on
		// the prototype chain, exactly like the static-field case below
		// already correctly does via setOwnProperty. A private field instead
		// goes straight to [[PrivateElements]] (PrivateFieldAdd) - never
		// through JSAccessor at all.
		// Same CreateDataPropertyOrThrow contract as addClassStaticField above
		// (ClassFieldDefinitionEvaluation's own DefineField step) - must throw,
		// not silently drop, on a [[DefineOwnProperty]] failure (e.g. an
		// already-non-extensible instance).
		if(name instanceof PrivateName pn) {
			PrivateElementsHolder holder = (PrivateElementsHolder)instance;
			checkCanDefinePrivate(holder, pn);
			holder.definePrivateElement(pn, value, PropertyDescriptor.DESC_HIDDEN_PROP);
		} else if(name instanceof Symbol sy) {
			getEnvironment().getAccessor(instance).setOwnProperty(instance, sy, value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.STRICT, instance);
		} else {
			getEnvironment().getAccessor(instance).setOwnProperty(instance, RuntimeUtil.toString(getEnvironment(),name), value, PropertyDescriptor.DESC_DEFAULT, DESC_CHECK.STRICT, instance);
		}
	}

	// `accessor x = v;` (the decorators proposal's auto-accessor class
	// element): a getter/setter pair on the prototype (or on the class
	// itself when static) reading/writing a class-private backing slot -
	// the value never becomes an own data property of the instance. The slot
	// is minted through the same per-class-evaluation PrivateName registry as
	// a `#x` field, under a key no source text can spell, so two evaluations
	// of the same class expression get distinct slots (a getter from one
	// class throws on an instance of the other, as for any private member).
	// Shared by both execution modes (see ASTClassField.initClass()/
	// initInstance()/initStaticFieldValue() and their transpiled twins).
	// One backing slot per distinct property KEY, per class evaluation.
	//
	// Keyed by the key OBJECT, never by its string form. Two Symbols with the
	// same description - or no description at all - are different property
	// keys but stringify identically ("Symbol()"), so a string key silently
	// merged their slots and one auto-accessor read the other's value
	// (test262 staging/decorators/public-auto-accessor.js,
	// TestDerivedPublicAutoAccessor). A PrivateName has no ToString at all and
	// threw outright. Strings still compare by value, so `accessor y` and
	// `accessor [n]` with n === "y" correctly share one slot, as do a literal
	// and a computed form of the same name.
	//
	// The PrivateName is minted directly rather than through
	// getOrCreatePrivateName(): this map already provides the per-class-
	// evaluation identity, and "[accessor] ..." is not spellable in source, so
	// it has no business in the class's script-visible private-name registry.
	private java.util.Map<Object,PrivateName> accessorStorages;
	private PrivateName accessorStorage(Object name) {
		if(accessorStorages==null) {
			accessorStorages = new java.util.HashMap<>();
		}
		PrivateName storage = accessorStorages.get(name);
		if(storage==null) {
			storage = new PrivateName("[accessor] " + name);
			accessorStorages.put(name, storage);
		}
		return storage;
	}
	// The getter/setter pair for one auto-accessor, created once per class
	// evaluation and shared by every instance - the same contract a private
	// method's single shared function object has. Keyed by the backing slot's
	// PrivateName, which accessorStorage() has already canonicalized, so two
	// declarations that resolve to the same key (`accessor y; accessor [n]`
	// with n === "y") share one pair.
	private java.util.Map<PrivateName,BuiltinFunction[]> accessorPairs;
	private BuiltinFunction[] accessorPair(Object name) {
		final PrivateName storage = accessorStorage(name);
		if(accessorPairs==null) {
			accessorPairs = new java.util.HashMap<>();
		}
		BuiltinFunction[] pair = accessorPairs.get(storage);
		if(pair!=null) {
			return pair;
		}
		final JSEnvironment env = getEnvironment();
		// A PrivateName has no ToString - its own description IS the "#x" form
		// the getter/setter should be named after (spec: "get #x"/"set #x").
		String fnName = name instanceof Symbol sy ? "[" + sy.getDescription() + "]"
				: name instanceof PrivateName pn ? pn.getDescription()
				: RuntimeUtil.toString(env, name);
		BuiltinFunction getter = new BuiltinFunctionNative(env, "get " + fnName, 0) {
			@Override
			public Object call(Object _this, Object[] parameters, Constructor newTarget) {
				return accessorHolder(_this, storage).getPrivateElementValue(storage);
			}
		};
		BuiltinFunction setter = new BuiltinFunctionNative(env, "set " + fnName, 1) {
			@Override
			public Object call(Object _this, Object[] parameters, Constructor newTarget) {
				accessorHolder(_this, storage).setPrivateElementValue(storage, parameters.length>0 ? parameters[0] : RuntimeUtil.UNDEFINED);
				return RuntimeUtil.UNDEFINED;
			}
		};
		pair = new BuiltinFunction[] { getter, setter };
		accessorPairs.put(storage, pair);
		return pair;
	}
	public void addClassAccessor(Object name, boolean isStatic) {
		BuiltinFunction[] pair = accessorPair(name);
		if(isStatic) {
			// Routes to defineStaticPrivateAccessor for a PrivateName (the two
			// calls merge into one accessor element) and to litGetter/litSetter
			// on the constructor for a public name.
			addClassStaticGetter(name, pair[0]);
			addClassStaticSetter(name, pair[1]);
		} else {
			// For a public name these define the pair on the prototype. For a
			// PRIVATE one they only set [[HomeObject]] and return - a private
			// instance accessor is registered per instance instead, exactly
			// like a private getter/setter, via addInstanceAutoAccessor()
			// below (called from ASTClassField.initInstance).
			addClassGetter(name, pair[0]);
			addClassSetter(name, pair[1]);
		}
	}
	// The per-instance half of a PRIVATE instance auto-accessor: installs the
	// shared getter/setter pair as this instance's private accessor element.
	// The backing slot itself is filled separately by setAccessorFieldValue().
	public void addInstanceAutoAccessor(Object instance, Object name) {
		BuiltinFunction[] pair = accessorPair(name);
		addInstancePrivateAccessor(instance, (PrivateName)name, pair[0], pair[1]);
	}
	private static PrivateElementsHolder accessorHolder(Object _this, PrivateName storage) {
		if(_this instanceof PrivateElementsHolder h && h.hasPrivateElement(storage)) {
			return h;
		}
		throw RuntimeUtil.typeError("Cannot access an auto-accessor of an object whose class did not declare it");
	}
	// The per-holder half of addClassAccessor: installs the backing slot's
	// initial value on the instance (or the class, when static), exactly
	// once - same CreateDataPropertyOrThrow/PrivateFieldAdd contract as
	// addClassField/addClassStaticField.
	public void setAccessorFieldValue(Object holder, Object name, Object value) {
		PrivateName storage = accessorStorage(name);
		PrivateElementsHolder h = (PrivateElementsHolder)holder;
		// Deliberately NOT checkCanDefinePrivate(): unlike a `#x` field, whose
		// second definition on the same object is a TypeError, a PUBLIC class
		// element may legally be declared twice and the later declaration
		// simply wins (`class C { accessor x = 0; accessor x = 1; }` - also
		// two computed names that evaluate to the same key, e.g.
		// `accessor y = 2; accessor [name] = 3` with name === "y"). Both
		// declarations resolve to the same backing slot, so the second must
		// overwrite the first rather than throw (test262 staging/decorators/
		// public-auto-accessor.js, TestRedeclaredPublicAutoaccessor). A
		// PRIVATE duplicate never reaches here at all - ClassBody's early
		// error rejects it at parse time (ASTClassDecl.checkDuplicatePrivateNames).
		// The extensibility half of the check still applies.
		if(!getEnvironment().getAccessor(h).isExtensible(h)) {
			throw RuntimeUtil.typeError("Cannot add private member {0} to a non-extensible object", storage);
		}
		h.definePrivateElement(storage, value, PropertyDescriptor.DESC_HIDDEN_PROP);
	}

	public void addClassStaticMethod(Object name, BuiltinFunction method) {
		// [[HomeObject]] for a static method is the class constructor
		// itself (unlike an instance method, whose [[HomeObject]] is the
		// prototype - see addClassMethod below) - `super.x`/`super[x]`
		// inside a static method resolves via the constructor's own
		// [[Prototype]] (the superclass constructor), never the instance
		// prototype chain. Was missing entirely (every static method's
		// `super` reference incorrectly threw "super() called outside of a
		// member function", regardless of key shape - confirmed via
		// language/statements/class/super/in-static-methods.js and
		// definition/numeric-property-names.js's static members).
		method.setHomeObject(this);
		if(name instanceof PrivateName pn) {
			checkCanDefinePrivate(this, pn);
			definePrivateElement(pn, method, PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
		} else if(name instanceof Symbol sy) {
			method.setOwnProperty("name", PropertyDescriptor.propertyKeyToFunctionName(sy), null, DESC_CHECK.NONE);
			setOwnProperty(sy, method, PropertyDescriptor.DESC_METHOD, DESC_CHECK.CHECK, this);
		} else {
			String sname = RuntimeUtil.toString(getEnvironment(),name);
			method.setOwnProperty("name", sname, null, DESC_CHECK.NONE);
			setOwnProperty(sname, method, PropertyDescriptor.DESC_METHOD, DESC_CHECK.CHECK, this);
		}
	}
	public void addClassConstructor(String name, BuiltinFunction constructor) {
		Object proto = getProperty(Constructor.PROTOTYPE);
		constructor.setHomeObject(proto);
		constructor.setClassConstructor(this);
		setFunctionConstructor(constructor);
	}

	// ClassDefinitionEvaluation (steps 14-15 / DefineMethod): the class's own
	// "length" must reflect the ACTUAL constructor's parameter count - not
	// the placeholder `1` BaseConstructor's own super() call unconditionally
	// seeds every class with (see this class's own constructor, which passes
	// a hardcoded ctorLength of 1 with no way to know the real arity yet at
	// that point). Called once, BEFORE any class element (including a static
	// element literally named "length") is processed - per spec the
	// constructor's own DefineMethod (steps 14-15) always runs before the
	// "for each ClassElement" static-elements loop (step 25), so a static
	// `length` override must win, never get clobbered back afterward. NOT
	// done from addClassConstructor() itself: withConstructor() appends an
	// AUTO-GENERATED default constructor at the END of the elements list, so
	// for a class with no explicit constructor, addClassConstructor() would
	// otherwise run LAST - after, not before, any static "length" override
	// (confirmed via test262 language/statements/class/definition/
	// fn-length-static-precedence.js). ctorParamLength is computed directly
	// from the AST (ASTClassMethod's own ASTFunctionMethod), not from the
	// eventual compiled BuiltinFunction, since it must be known before that
	// function is even created.
	public void initClassConstructorLength(int ctorParamLength) {
		setOwnProperty("length", ctorParamLength, PropertyDescriptor.DESC_PROP_READONLY_CONFIGURABLE, DESC_CHECK.NONE);
	}
	public void addClassMethod(Object name, BuiltinFunction method) {
		Object proto = getProperty(Constructor.PROTOTYPE);
		method.setHomeObject(proto);
		if(name instanceof PrivateName) {
			// PrivateMethodOrAccessorAdd copies a reference to this SAME,
			// shared method function onto every instance's own
			// [[PrivateElements]] (per instance, once per `new`) - not onto
			// the prototype at all, since private access never walks the
			// prototype chain. See addInstancePrivateMethod, called from
			// ASTClassMethod.initInstance for each new instance.
			return;
		}
		// PropertyDefinitionEvaluation for a class method installs it via
		// CreateMethodProperty ([[DefineOwnProperty]]), never [[Set]] - it
		// must become an own data property on the prototype unconditionally,
		// ignoring any same-named inherited accessor on the superclass's
		// prototype chain (confirmed via
		// language/statements/class/definition/side-effects-in-property-
		// define.js: an inherited setter must NOT fire when a subclass
		// defines its own method of the same name).
		if(name instanceof Symbol sy) {
			method.setOwnProperty("name", PropertyDescriptor.propertyKeyToFunctionName(sy), null, DESC_CHECK.NONE);
			getEnvironment().getAccessor(proto).setOwnProperty(proto, sy, method, PropertyDescriptor.DESC_METHOD, DESC_CHECK.CHECK, proto);
		} else {
			String sname = RuntimeUtil.toString(getEnvironment(),name);
			method.setOwnProperty("name", sname, null, DESC_CHECK.NONE);
			getEnvironment().getAccessor(proto).setOwnProperty(proto, sname, method, PropertyDescriptor.DESC_METHOD, DESC_CHECK.CHECK, proto);
		}
	}
	// Per-instance registration for a private (non-static) method - the
	// function itself is created once (during initClass) and shared by every
	// instance; only the [[PrivateElements]] entry is per-instance.
	public void addInstancePrivateMethod(Object instance, PrivateName name, BuiltinFunction method) {
		PrivateElementsHolder holder = (PrivateElementsHolder)instance;
		checkCanDefinePrivate(holder, name);
		holder.definePrivateElement(name, method, PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
	}
	// PrivateFieldAdd/PrivateMethodOrAccessorAdd:
	// - "If O.[[Extensible]] is false, throw a TypeError" (the
	//   "nonextensible-applies-to-private" TC39 change - ADD-time only, a
	//   later PrivateFieldSet on an already-added field never re-checks
	//   extensibility, test262's private-class-field-on-nonextensible-*.js).
	// - "If entry is not empty, throw a TypeError": construction must fail
	//   if the SAME PrivateName is already present on this object - e.g. a
	//   constructor trick that returns an object from a PREVIOUS
	//   construction (test262's privatefieldadd-typeerror.js/private-method-
	//   double-initialisation.js). NOT used for accessors, which legitimately
	//   touch the same PrivateName twice (getter then setter) within a
	//   single construction - see defineOrMergePrivateAccessor.
	private void checkCanDefinePrivate(PrivateElementsHolder holder, PrivateName name) {
		if(!getEnvironment().getAccessor(holder).isExtensible(holder)) {
			throw RuntimeUtil.typeError("Cannot add private member {0} to a non-extensible object", name);
		}
		if(holder.hasPrivateElement(name)) {
			throw RuntimeUtil.typeError("Cannot initialize private member {0} more than once on the same object", name);
		}
	}

	public void addClassStaticGetter(Object name, BuiltinFunction method) {
		if(name instanceof PrivateName pn) {
			// Unlike litGetter/litSetter (used below for the public case),
			// which set [[HomeObject]] themselves, defineStaticPrivateAccessor's
			// PrivateElement path never touches it - must be set explicitly
			// here, same as addClassStaticMethod does for private methods,
			// or `super.x` inside a private static getter/setter always
			// throws "super() called outside of a member function".
			method.setHomeObject(this);
			defineStaticPrivateAccessor(pn, method, null);
			return;
		}
		// A class accessor is never enumerable (spec: MethodDefinitionEvaluation
		// -> PropertyDescriptor{[[Enumerable]]: false}), unlike an object
		// literal's getter/setter (enumerable: true) - litGetter/litSetter's
		// 2-arg overload defaults to the object-literal shape, so class
		// accessors must use the 3-arg overload with DESC_METHOD explicitly.
		litGetter(name, method, PropertyDescriptor.DESC_METHOD);
	}
	public void addClassGetter(Object name, BuiltinFunction method) {
		if(name instanceof PrivateName) {
			// See addClassMethod above: registered per-instance instead, via
			// addInstancePrivateAccessor (ASTClassGetter.initInstance) - but
			// the method object itself is created once and shared, so its
			// [[HomeObject]] (the prototype, same as addClassMethod's private
			// branch) is set here, once, rather than repeatedly per-instance.
			method.setHomeObject(getProperty(Constructor.PROTOTYPE));
			return;
		}
		JSObjectImpl proto = (JSObjectImpl)getProperty(Constructor.PROTOTYPE);
		proto.litGetter(name, method, PropertyDescriptor.DESC_METHOD);
	}
	public void addClassStaticSetter(Object name, BuiltinFunction method) {
		if(name instanceof PrivateName pn) {
			method.setHomeObject(this);
			defineStaticPrivateAccessor(pn, null, method);
			return;
		}
		litSetter(name, method, PropertyDescriptor.DESC_METHOD);
	}
	public void addClassSetter(Object name, BuiltinFunction method) {
		if(name instanceof PrivateName) {
			// See addClassGetter above: registered per-instance instead, via
			// addInstancePrivateAccessor (ASTClassSetter.initInstance).
			method.setHomeObject(getProperty(Constructor.PROTOTYPE));
			return;
		}
		JSObjectImpl proto = (JSObjectImpl)getProperty(Constructor.PROTOTYPE);
		proto.litSetter(name, method, PropertyDescriptor.DESC_METHOD);
	}

	// Merges a getter and/or setter declared under the same private name
	// (get #x()/set #x() share one PrivateElement, spec: PrivateElement
	// Record { [[Kind]]: accessor }) - whichever half isn't being defined by
	// THIS call is preserved from any existing entry, exactly mirroring how
	// JSObjectImpl.litGetter/litSetter merge for String/Symbol keys.
	//
	// Unlike checkCanDefinePrivate (fields/methods: any pre-existing entry
	// is a duplicate), an accessor legitimately gets touched TWICE within a
	// single construction (once for get #x, once for set #x) - so only
	// throw if the SPECIFIC HALF being defined right now was already filled
	// in (a genuine PrivateMethodOrAccessorAdd re-add, e.g. a second
	// construction reusing an object that already has this accessor).
	private void defineOrMergePrivateAccessor(PrivateElementsHolder holder, PrivateName name, BuiltinFunction getter, BuiltinFunction setter) {
		if(!getEnvironment().getAccessor(holder).isExtensible(holder)) {
			throw RuntimeUtil.typeError("Cannot add private member {0} to a non-extensible object", name);
		}
		PropertyDescriptor existing = holder.getPrivateElementDescriptor(name);
		if(existing!=null && ((getter!=null && existing.getGetter()!=null) || (setter!=null && existing.getSetter()!=null))) {
			throw RuntimeUtil.typeError("Cannot initialize private member {0} more than once on the same object", name);
		}
		BuiltinFunction g = getter!=null ? getter : (existing!=null ? (BuiltinFunction)existing.getGetter() : null);
		BuiltinFunction s = setter!=null ? setter : (existing!=null ? (BuiltinFunction)existing.getSetter() : null);
		holder.definePrivateElement(name, RuntimeUtil.NOT_AVAILABLE, PropertyDescriptor.of(false, false, false, g, s));
	}
	private void defineStaticPrivateAccessor(PrivateName name, BuiltinFunction getter, BuiltinFunction setter) {
		defineOrMergePrivateAccessor(this, name, getter, setter);
	}
	// Per-instance registration for a private (non-static) getter/setter -
	// see addInstancePrivateMethod above for why this is per-instance.
	public void addInstancePrivateAccessor(Object instance, PrivateName name, BuiltinFunction getter, BuiltinFunction setter) {
		defineOrMergePrivateAccessor((PrivateElementsHolder)instance, name, getter, setter);
	}
	
	@Override
	public Class<?> getNativeClass() {
		return BuiltinClassConstructor.class; 
	}
	
	@Override
	public Constructor getSuperClass() {
		return superClass;
	}

	// See the `derived` field's own doc - true whenever a ClassHeritage
	// clause is textually present, even for `extends null` (where
	// getSuperClass() above stays null since there's no actual superclass
	// Constructor object to call). Callers that need "is this constructor
	// running under [[ConstructorKind]] derived semantics" (this-TDZ,
	// derived-constructor return-value coercion, eval's callerIsDerivedCtor)
	// must use this, not `getSuperClass()!=null`.
	public boolean isDerived() {
		return derived;
	}

	public Initializer getInitializer() {
		return initializer;
	}

	public BuiltinFunction getFunctionConstructor() {
		return functionConstructor;
	}
	public void setFunctionConstructor(BuiltinFunction functionConstructor) {
		this.functionConstructor = functionConstructor;
	}

	// A class constructor has no [[Call]] at all (spec) - always a
	// TypeError, whether invoked directly (`MyClass()`) or through
	// Function.prototype.bind()/call()/apply() (BuiltinFunctionBind simply
	// delegates to the wrapped target's own call() - see
	// language/statements/class/subclass/binding.js).
	@Override
	public Object call(Object _this, @NonNull Object[] parameters) {
		// In the class's own realm (its [[Call]] is a function of that realm)
		Object realm = JSEnvironment.enterRealm(getEnvironment());
		try {
			throw RuntimeUtil.typeError("Class constructor {0} cannot be invoked without 'new'", getProperty("name",""));
		} finally {
			JSEnvironment.exitRealm(realm);
		}
	}
	
	// Whether the WHOLE superclass chain starting here is made entirely of
	// ordinary BuiltinClassConstructors (pure user-defined `class`
	// declarations/expressions, all the way down to the ultimate base with
	// no superclass at all) - as opposed to bottoming out at a genuine
	// native built-in constructor (Array/Map/TypedArray/Error/etc.). See
	// constructObject()'s prototype-re-stamp doc for why this distinction
	// matters.
	private boolean isPureJSChain() {
		Constructor c = superClass;
		while(c instanceof BuiltinClassConstructor bcc) {
			c = bcc.getSuperClass();
		}
		// An ordinary JavaScript function as the base threads newTarget itself
		return c==null || c instanceof BuiltinFunctionInterpreter || c instanceof BuiltinFunctionTranspiler;
	}

	@Override
	public Object constructObject(Object[] parameters, Constructor topConstructor) {
		try {
			return doConstructObject(parameters, topConstructor);
		} catch(org.monflabs.galtajs.rt.ConstructResultError e) {
			// Thrown by the constructor's body, reported in the caller's realm
			throw e.toJavaScriptError();
		}
	}

	private Object doConstructObject(Object[] parameters, Constructor topConstructor) {
		BaseInternalObject prototype = (BaseInternalObject)getProperty(PROTOTYPE,RuntimeUtil.NOT_AVAILABLE);
		// Branches on `derived` (ClassHeritage textually present), NOT
		// `superClass!=null` - see the `derived` field's doc: `extends null`
		// must still go through this "start with an uninitialized `this`"
		// path even though there's no real superclass Constructor to call.
		if(derived) {
			Object _this = functionConstructor.call(RuntimeUtil.UNDEFINED, parameters, topConstructor);
			if(!RuntimeUtil.isObject(getEnvironment(), _this)) {
				// Fallback: the constructor body didn't produce a valid `this`
				// (e.g. it never called super()). This fresh replacement never
				// went through initInstance, so it still needs it now - unlike
				// the normal case below, where `_this` is exactly the object
				// RuntimeUtil's super()-handling already ran initInstance on
				// (immediately after super() returned, per spec's actual
				// required timing) - calling it AGAIN here was a genuine
				// double-initialization bug, harmless under the old shared-
				// "#name"-string-key scheme (silent overwrite) but a real
				// PrivateFieldAdd/PrivateMethodOrAccessorAdd duplicate under
				// PrivateName's per-declaration identity (test262's
				// privatefieldget-success-2.js and friends).
				_this = prototype!=RuntimeUtil.NOT_AVAILABLE ?
						JSObject.createWithPrototype(getEnvironment(),prototype) : JSObject.create(getEnvironment());
				if(initializer!=null) {
					initializer.initInstance(this,_this);
				}
			}
			// GetPrototypeFromConstructor(topConstructor, ...): a derived
			// class must respect an explicit newTarget (Reflect.construct,
			// or a further-derived subclass reaching this constructor via
			// super()), not unconditionally re-stamp its OWN prototype -
			// that degenerates to the same value when topConstructor==this
			// (the common `new Bar()` case) but was wrong otherwise.
			//
			// Only when !isPureJSChain() - this re-stamp is itself a
			// workaround for GaltaJS's built-in super constructors
			// (Array/Map/etc.) not threading newTarget correctly when THEY
			// create the initial `this` (confirmed via subclass-builtins/*
			// and builtin-objects/* regressing when this was tried removed
			// unconditionally). A chain made ENTIRELY of ordinary
			// BuiltinClassConstructors (no superclass at all, or every
			// superclass up to the ultimate base is itself one) never hits
			// that bug - each level's own super()-produced `this` was
			// ALREADY built with the correct (topConstructor-derived)
			// prototype (by induction: the ultimate base's OWN branch below
			// already threads topConstructor through GetPrototypeFromConstructor,
			// and every derived level in between only ever forwards that
			// same object), so re-stamping it again here is at best a
			// no-op and at worst - when the constructor instead returns a
			// genuinely DIFFERENT explicit object (`return {};`) - actively
			// wrong: per spec (10.2.2 [[Construct]] step 13.a) an explicitly
			// returned object is used completely as-is, with NO prototype
			// adjustment whatsoever (test262 language/statements/class/
			// subclass/derived-class-return-override-with-object.js).
			if(!isPureJSChain() && prototype!=RuntimeUtil.NOT_AVAILABLE) {
				Object targetProto = RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), topConstructor, prototype);
				getEnvironment().getAccessor(_this).setPrototype(_this, targetProto);
			}
			return _this;
		} else {
			// Same GetPrototypeFromConstructor consultation, applied at
			// object-creation time (the base-class case creates `this`
			// before running the constructor body, per spec).
			Object _this = prototype!=RuntimeUtil.NOT_AVAILABLE ?
					JSObject.createWithPrototype(getEnvironment(),RuntimeUtil.getPrototypeFromConstructor(getEnvironment(), topConstructor, prototype)) : JSObject.create(getEnvironment());
			if(initializer!=null) {
				initializer.initInstance(this,_this);
			}
			Object r = BuiltinFunction.resolveTailCalls(functionConstructor.call(_this, parameters, topConstructor));
			if(RuntimeUtil.isObject(getEnvironment(), r)) {
				return  r;
			}
			return _this;
		}
	}
}
