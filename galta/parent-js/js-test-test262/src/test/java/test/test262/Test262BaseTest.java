package test.test262;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.test.test262.GlobalTest262Environment;

import org.monflabs.util.Console;

import tests.suite.BaseTestSuiteTest;


public abstract class Test262BaseTest extends BaseTestSuiteTest {

	// Current file metadata - set during preprocessFile(), used in handleException()
	// Tests run sequentially, so a single field is safe
	private Test262Metadata currentMetadata;
	// Set by handleException() when the current file threw at all - lets
	// afterExecute() catch a negative test that threw NOTHING (a pass before
	// negative tests were checked strictly)
	private boolean currentThrew;

	// -----------------------------------------------------------------------
	// Content that must be excluded from tests when introspecting the source
	// files for different reasons.
	// -----------------------------------------------------------------------
	public static final String[] EXCLUDED_FROM_TESTS = new String[] {
		// Fixture files are not standalone tests
		"_FIXTURE",
		// Harness files themselves
		"test262/harness/",
		
		// ECMA-402 Internationalization (requires Intl)
		"intl402/",
		// Staging tests may rely on unfinished proposals
		"staging/"
	};

	// -----------------------------------------------------------------------
	// Features GaltaJS does not support -- skip any test that requires them
	// -----------------------------------------------------------------------
	private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
		"tail-call-optimization",
		// Multiple realms are partially supported ($262.createRealm() returns
		// a real second JSEnvironment with its own root context and
		// evalScript; GetFunctionRealm / proto-from-ctor-realm resolution is
		// implemented; all but 26 of the 203 tagged files pass in interpreted mode)
		// but the remainder needs a "current realm" switch on every builtin
		// entry, which is not planned. Skipped wholesale so the sweeps stay
		// at zero; see docs/GaltaJS/KnownGaps.md's "Multiple realms" entry.
		"cross-realm",
		// ES2024 Explicit Resource Management is implemented except
		// `using`/`await using` as a for-of/for-await-of loop HEAD
		// declaration (needs `for-await-of` grammar support - see
		// KnownGaps.md) - filtered individually below by path, not by this
		// wholesale flag, so the rest stays test262-visible.
		// ShadowRealm isn't implemented at all - a new global constructor plus
		// callable-boundary wrapping on top of the multi-realm support above.
		"ShadowRealm",
		// Temporal (the whole Temporal.* namespace) isn't implemented at
		// all - zero classes exist for it. A ~4500-test subdirectory;
		// skipped wholesale rather than an exhaustive per-file FILTER list.
		"Temporal"
	));

	// -----------------------------------------------------------------------
	// File path filters – skip files whose relative path contains any token
	// -----------------------------------------------------------------------
	// Negative tests the strict harness (negativeMismatch()) fails because the
	// engine does not report the early error at parse time, grouped by cause;
	// each group is described in docs/GaltaJS/KnownGaps.md, "Early errors".
	public static final String[] FILTER = new String[] {
		// KnownGaps.md "Early errors": RegExp pattern early errors the regexp engine does not report
		"built-ins/RegExp/property-escapes/character-class-range-end.js",
		"built-ins/RegExp/property-escapes/character-class-range-no-dash-start.js",
		"built-ins/RegExp/property-escapes/character-class-range-start.js",
		"built-ins/RegExp/property-escapes/grammar-extension-In-prefix-Block-implicit-negated.js",
		"built-ins/RegExp/property-escapes/grammar-extension-In-prefix-Block-implicit.js",
		"built-ins/RegExp/property-escapes/grammar-extension-In-prefix-Script-implicit-negated.js",
		"built-ins/RegExp/property-escapes/grammar-extension-In-prefix-Script-implicit.js",
		"built-ins/RegExp/property-escapes/loose-matching-01-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-01.js",
		"built-ins/RegExp/property-escapes/loose-matching-02-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-02.js",
		"built-ins/RegExp/property-escapes/loose-matching-03-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-03.js",
		"built-ins/RegExp/property-escapes/loose-matching-04-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-04.js",
		"built-ins/RegExp/property-escapes/loose-matching-05-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-05.js",
		"built-ins/RegExp/property-escapes/loose-matching-06-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-06.js",
		"built-ins/RegExp/property-escapes/loose-matching-07-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-07.js",
		"built-ins/RegExp/property-escapes/loose-matching-08-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-08.js",
		"built-ins/RegExp/property-escapes/loose-matching-09-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-09.js",
		"built-ins/RegExp/property-escapes/loose-matching-10-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-10.js",
		"built-ins/RegExp/property-escapes/loose-matching-12-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-12.js",
		"built-ins/RegExp/property-escapes/loose-matching-13-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-13.js",
		"built-ins/RegExp/property-escapes/loose-matching-14-negated.js",
		"built-ins/RegExp/property-escapes/loose-matching-14.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Grapheme_Link-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Grapheme_Link.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Hyphen-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Hyphen.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Alphabetic-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Alphabetic.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Default_Ignorable_Code_Point-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Default_Ignorable_Code_Point.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Grapheme_Extend-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Grapheme_Extend.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_ID_Continue-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_ID_Continue.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_ID_Start-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_ID_Start.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Lowercase-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Lowercase.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Math-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Math.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Uppercase-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Other_Uppercase.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Prepended_Concatenation_Mark-negated.js",
		"built-ins/RegExp/property-escapes/unsupported-binary-property-Prepended_Concatenation_Mark.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-01.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-02.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-04.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-05.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-06.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-07.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-08.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-09.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-10.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-11.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-12.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-13.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-14.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-15.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-16.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-17.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-18.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-19.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-20.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-21.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-22.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-23.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-24.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-25.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-26.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-27.js",
		"built-ins/RegExp/prototype/unicodeSets/breaking-change-from-u-to-v-28.js",
		"language/literals/regexp/invalid-optional-lookbehind.js",
		"language/literals/regexp/invalid-optional-negative-lookbehind.js",
		"language/literals/regexp/invalid-range-lookbehind.js",
		"language/literals/regexp/invalid-range-negative-lookbehind.js",
		"language/literals/regexp/named-groups/invalid-incomplete-groupname-6.js",
		"language/literals/regexp/named-groups/invalid-incomplete-groupname.js",
		"language/literals/regexp/u-invalid-optional-lookbehind.js",
		"language/literals/regexp/u-invalid-optional-negative-lookbehind.js",
		"language/literals/regexp/u-invalid-range-lookbehind.js",
		"language/literals/regexp/u-invalid-range-negative-lookbehind.js",
		// KnownGaps.md "Early errors": ClassHeritage early errors
		"language/expressions/class/elements/syntax/early-errors/class-heritage-array-literal-arrow-heritage.js",
		"language/expressions/class/elements/syntax/early-errors/class-heritage-array-literal-async-arrow-heritage.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-array-literal.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-chained-usage.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-function-expression.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-obj-literal.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-recursive.js",
		"language/expressions/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage.js",
		"language/statements/class/elements/syntax/early-errors/class-heritage-array-literal-arrow-heritage.js",
		"language/statements/class/elements/syntax/early-errors/class-heritage-array-literal-async-arrow-heritage.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-array-literal.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-chained-usage.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-function-expression.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-obj-literal.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage-recursive.js",
		"language/statements/class/elements/syntax/early-errors/grammar-private-environment-on-class-heritage.js",
		// KnownGaps.md "Early errors": Module import/export names are not resolved before evaluation
		"language/import/import-attributes/json-named-bindings.js",
		"language/module-code/ambiguous-export-bindings/error-export-from-named-as.js",
		"language/module-code/ambiguous-export-bindings/error-export-from-named.js",
		"language/module-code/ambiguous-export-bindings/error-import-named-as.js",
		"language/module-code/ambiguous-export-bindings/error-import-named.js",
		"language/module-code/early-export-global.js",
		"language/module-code/early-export-unresolvable.js",
		"language/module-code/instn-iee-err-circular-as.js",
		"language/module-code/instn-iee-err-dflt-thru-star-as.js",
		"language/module-code/instn-iee-err-dflt-thru-star.js",
		"language/module-code/instn-iee-err-not-found-as.js",
		"language/module-code/instn-iee-err-not-found.js",
		"language/module-code/instn-named-err-dflt-thru-star-as.js",
		"language/module-code/instn-named-err-dflt-thru-star-dflt.js",
		"language/module-code/instn-named-err-not-found-as.js",
		"language/module-code/instn-named-err-not-found-dflt.js",
		"language/module-code/instn-named-err-not-found.js",
		// KnownGaps.md "Early errors": Module-only grammar early errors
		"language/module-code/comment-multi-line-html-close.js",
		"language/module-code/comment-single-line-html-close.js",
		"language/module-code/comment-single-line-html-open.js",
		"language/module-code/early-dup-export-as-star-as.js",
		"language/module-code/early-dup-export-dflt-id.js",
		"language/module-code/early-dup-export-id-as.js",
		"language/module-code/early-dup-export-id.js",
		"language/module-code/early-dup-export-star-as-dflt.js",
		"language/module-code/early-dup-lables.js",
		"language/module-code/early-export-ill-formed-string.js",
		"language/module-code/export-expname-from-as-unpaired-surrogate.js",
		"language/module-code/export-expname-from-unpaired-surrogate.js",
		"language/module-code/export-expname-import-unpaired-surrogate.js",
		"language/module-code/export-expname-string-binding.js",
		"language/module-code/export-expname-unpaired-surrogate.js",
		"language/module-code/import-attributes/allow-nlt-before-with.js",
		"language/module-code/parse-err-export-dflt-expr.js",
		"language/module-code/parse-err-invoke-anon-fun-decl.js",
		"language/module-code/parse-err-invoke-anon-gen-decl.js",
		"language/module-code/parse-err-yield.js",
		// KnownGaps.md "Early errors": import/export declarations tolerated in scripts (GaltaJS extension)
		"language/global-code/export.js",
		"language/global-code/import.js",
		// KnownGaps.md "Early errors": await/yield as identifiers in some contexts
		"language/expressions/arrow-function/param-dflt-yield-expr.js",
		"language/expressions/async-arrow-function/await-as-param-ident-nested-arrow-parameter-position.js",
		"language/expressions/async-arrow-function/await-as-param-nested-arrow-body-position.js",
		"language/expressions/async-arrow-function/await-as-param-nested-arrow-parameter-position.js",
		"language/expressions/async-arrow-function/await-as-param-rest-nested-arrow-parameter-position.js",
		"language/expressions/async-arrow-function/early-errors-arrow-await-in-formals-default.js",
		"language/expressions/async-arrow-function/early-errors-arrow-await-in-formals.js",
		"language/expressions/async-generator/early-errors-expression-await-as-function-binding-identifier.js",
		"language/expressions/async-generator/early-errors-expression-yield-as-function-binding-identifier.js",
		"language/expressions/await/await-BindingIdentifier-nested.js",
		"language/expressions/generators/yield-as-generator-expression-binding-identifier.js",
		"language/expressions/yield/in-iteration-stmt.js",
		"language/expressions/yield/star-in-iteration-stmt.js",
		"language/statements/await-using/syntax/await-using-invalid-switchstatement-caseclause.js",
		"language/statements/await-using/syntax/await-using-invalid-switchstatement-defaultclause.js",
		"language/statements/await-using/syntax/with-initializer-case-expression-statement-list.js",
		"language/statements/await-using/syntax/with-initializer-default-statement-list.js",
		"language/statements/class/definition/methods-gen-yield-as-function-expression-binding-identifier.js",
		"language/statements/class/static-init-invalid-await.js",
		"language/statements/class/static-init-invalid-yield.js",
		"language/statements/labeled/static-init-invalid-await.js",
		"language/statements/labeled/value-await-module-escaped.js",
		"language/statements/labeled/value-await-module.js",
		"language/statements/labeled/value-yield-strict-escaped.js",
		"language/statements/labeled/value-yield-strict.js",
		"language/statements/let/syntax/let-newline-yield-in-generator-function.js",
		// KnownGaps.md "Early errors": Class static block early errors
		"language/statements/class/static-init-invalid-label-dup.js",
		"language/statements/class/static-init-invalid-return.js",
		// KnownGaps.md "Early errors": Other remaining early errors
		"annexB/language/comments/single-line-html-close.js",
		"language/asi/S7.9_A11_T4.js",
		"language/asi/S7.9_A4.js",
		"language/expressions/arrow-function/syntax/early-errors/asi-restriction-invalid-parenless-parameters-expression-body.js",
		"language/expressions/arrow-function/syntax/early-errors/asi-restriction-invalid-parenless-parameters.js",
		"language/expressions/arrow-function/syntax/early-errors/asi-restriction-invalid.js",
		"language/expressions/conditional/in-branch-2.js",
		"language/expressions/conditional/in-condition.js",
		"language/expressions/in/private-field-in.js",
		"language/expressions/in/private-field-invalid-rhs.js",
		"language/expressions/object/__proto__-duplicate.js",
		"language/expressions/object/cover-initialized-name.js",
		"language/expressions/object/getter-param-dflt.js",
		"language/expressions/optional-chaining/early-errors-tail-position-null-optchain-template-string-esi.js",
		"language/expressions/optional-chaining/early-errors-tail-position-null-optchain-template-string.js",
		"language/expressions/optional-chaining/early-errors-tail-position-optchain-template-string-esi.js",
		"language/expressions/optional-chaining/early-errors-tail-position-optchain-template-string.js",
		"language/identifiers/vertical-tilde-continue.js",
		"language/identifiers/vertical-tilde-start.js",
		"language/statements/for-of/head-decl-no-expr.js",
		"language/statements/for-of/head-expr-no-expr.js",
		"language/statements/for-of/head-lhs-async-invalid.js",
		"language/statements/for-of/head-var-no-expr.js",
		"language/statements/for/S12.6.3_A4_T1.js",
		"language/statements/for/S12.6.3_A4_T2.js",
		"language/statements/let/syntax/let-let-declaration-split-across-two-lines.js",
		"language/statements/let/syntax/let-let-declaration-with-initializer-split-across-two-lines.js",
		"language/statements/try/early-catch-function.js",
	};

	// -----------------------------------------------------------------------
	// TRANSPILER-mode-only file path filters – FILTER above is shared between
	// interpreted and transpiled runs; entries here are for gaps specific to
	// transpiled mode only. A file listed here MUST still pass in
	// INTERPRETED mode - if it doesn't, it belongs in FILTER instead, not
	// here.
	// -----------------------------------------------------------------------
	public static final String[] TRANSPILER_ONLY_FILTER = new String[] {
	};

	public Test262BaseTest() {
	}

	public Test262BaseTest(boolean debugger) {
		super(debugger);
	}

	// Stale-filter audit tool: run with
	// `-DargLine="-Dtest262.noFilter=true"` (mvn test -pl parent-js/js-test-test262
	// -Dtest=Test262OneTest, TEST_FILE set to any directory) to see the CURRENT,
	// real pass/fail state ignoring FILTER entirely - many entries get fixed as
	// side effects of unrelated work over time and are never individually
	// re-verified/removed. Diff the resulting failure count against the
	// directory's FILTER entry count to find stale ones. NOT for use in a real
	// CI/regression run (a full unfiltered sweep can be very slow, and a few
	// filtered files are excluded specifically because they're pathological -
	// e.g. multi-hundred-level recursion - not just wrong-answer).
	@Override
	protected String[] getFILTER() {
		if(System.getProperty("test262.noFilter")!=null) {
			return new String[0];
		}
		// Combine not implemented vs filtered because of failures
		String[] filtered = new String[FILTER.length + EXCLUDED_FROM_TESTS.length];
		System.arraycopy(FILTER, 0, filtered, 0, FILTER.length);
		System.arraycopy(EXCLUDED_FROM_TESTS, 0, filtered, FILTER.length, EXCLUDED_FROM_TESTS.length);
		
		// Transpiler exclusion
		if(isJavaTranspiler() && TRANSPILER_ONLY_FILTER.length>0) {
			String[] combined = new String[filtered.length + TRANSPILER_ONLY_FILTER.length];
			System.arraycopy(filtered, 0, combined, 0, filtered.length);
			System.arraycopy(TRANSPILER_ONLY_FILTER, 0, combined, filtered.length, TRANSPILER_ONLY_FILTER.length);
			return combined;
		}
		return filtered;
	}

	// No pre-known error counts – start clean
	@Override
	protected Object[] getFILTER_ERRORS() {
		return new Object[0];
	}

	// -----------------------------------------------------------------------
	// Environment
	// -----------------------------------------------------------------------
	@Override
	protected JSEnvironment.Builder createEnvironment() {
		JSEnvironment.Builder env = GlobalTest262Environment.newBuilder();
		// Needed for `import(...)`/static `import` to resolve a RELATIVE
		// specifier (e.g. `./module-code_FIXTURE.js`) against the test
		// file's own directory - without this, ModuleUtil.resolvePath()'s
		// result never matches anything (importModule() falls through to
		// "Cannot find module") because there is no resolver registered
		// at all to look it up against. Rooted at the test262 test/
		// directory so a resolved name like "language/expressions/.../
		// module-code_FIXTURE.js" (see execFile()'s own fix passing the
		// FULL relative path, not just the bare filename, as the script's
		// descriptor name) maps straight to the real file on disk.
		env.addModuleResolver(new org.monflabs.galtajs.modules.JSFileModuleResolver(getTest262TestDirectory()));
		// test262's source-phase-imports host contract: the specifier
		// '<module source>' resolves to a module that HAS a source-phase
		// representation (a native, non-source-text module here - any JS
		// file would per spec have none), while '<do not resolve>' must fail
		// resolution - which it does naturally, nothing resolves it.
		env.addModuleResolver(new org.monflabs.galtajs.modules.NativeModuleResolver() {
			@Override
			protected org.monflabs.galtajs.JSModuleDescriptor findModule(String name) {
				if(!"<module source>".equals(name)) {
					return null;
				}
				return new org.monflabs.galtajs.modules.NativeModuleDescriptor(name) {
					@Override
					public org.monflabs.galtajs.JSModule loadModule(org.monflabs.galtajs.rt.JSGlobalContext globalContext) {
						return new org.monflabs.galtajs.modules.JSNativeModule(globalContext.getEnvironment(), this);
					}
				};
			}
			@Override
			public java.util.stream.Stream<org.monflabs.galtajs.JSModuleDescriptor> getModules() {
				return java.util.stream.Stream.empty();
			}
		});
		return env;
	}

	// -----------------------------------------------------------------------
	// Harness loading
	// Each test needs assert.js + sta.js loaded before it runs.
	// readShell() is called once per test file and its result is prepended to
	// (or executed before) the actual test source.
	// -----------------------------------------------------------------------
	@Override
	protected String readShell(Path folder, String shell) throws Exception {
		// Already populated from a previous recursive call – return as-is
		if (shell != null && !shell.isEmpty()) {
			return shell;
		}

		Path harnessDir = getTest262HarnessDirectory().toPath();
		StringBuilder sb = new StringBuilder();

		// Order matters: sta.js defines Test262Error, assert.js uses it
		for (String harnessFile : new String[]{"sta.js", "assert.js"}) {
			Path p = harnessDir.resolve(harnessFile);
			if (Files.exists(p)) {
				sb.append("// harness: ").append(harnessFile).append("\n");
				sb.append(Files.readString(p)).append("\n\n");
			}
		}

		return sb.toString();
	}

	// -----------------------------------------------------------------------
	// Per-file preprocessing
	// Parses the YAML front-matter, loads additional harness includes, and
	// stores metadata so handleException() can honour negative tests.
	// -----------------------------------------------------------------------
	@Override
	protected String preprocessFile(Path file, String text) {
		Test262Metadata meta = Test262Metadata.parse(text);
		// Deliberately NOT set unconditionally to `meta` here - afterExecute()
		// below independently checks currentMetadata.hasFlag("async") to
		// catch "$DONE never called", and a SKIPPED file never runs its real
		// body at all, so its $DONE genuinely never fires. `currentMetadata`
		// only gets set to a real (non-null) value once a file is confirmed
		// to actually run, at the bottom of this method; every skip path
		// below implicitly leaves it null, so afterExecute() never wrongly
		// flags a skipped file as a failure.
		currentMetadata = null;
		currentThrew = false;

		if (meta == null) {
			return text;
		}

		// Skip files with unsupported features by returning a no-op program
		if (meta.hasUnsupportedFeature(UNSUPPORTED_FEATURES)) {
			return "// SKIPPED: unsupported features " + meta.features;
		}

		// flags:[async] tests run for real - the harness side (native
		// $DONE() in Test262TestLibrary, getFAILED()/getPASSED() matching
		// its print markers, afterExecute() below catching "$DONE never
		// called at all") is wired up for it.

		// Test262TestLibrary supports real Atomics.wait()/notify()
		// suspend/wake on the main (test-executing) thread, so
		// CanBlockIsTrue-flagged tests apply; CanBlockIsFalse-flagged tests
		// (which assert the opposite - that wait() unconditionally throws)
		// don't.
		if (meta.hasFlag("CanBlockIsFalse")) {
			return "// SKIPPED: CanBlockIsFalse flag (GaltaJS's test262 runner supports Atomics.wait() suspend, unlike this flag's assumption)";
		}

		// Past every skip check - this file will actually run for real,
		// so only NOW does currentMetadata get set to the real metadata
		// (see this method's own opening comment for why).
		currentMetadata = meta;
		// Must reset per file (this ThreadLocal latch otherwise stays
		// true forever after the FIRST async test in a run calls $DONE(),
		// silently hiding "never called $DONE" failures in every async
		// test that runs after it) - afterExecute() below reads it once
		// this file finishes.
		if (meta.hasFlag("async")) {
			org.monflabs.galtajs.test.test262.Test262TestLibrary.resetAsyncDoneCalled();
		}

		// Load additional harness files declared in the 'includes' metadata
		String prefix = "";
		if (!meta.includes.isEmpty()) {
			Path harnessDir = getTest262HarnessDirectory().toPath();
			StringBuilder sb = new StringBuilder();
			for (String include : meta.includes) {
				Path includePath = harnessDir.resolve(include);
				if (Files.exists(includePath)) {
					try {
						sb.append("// include: ").append(include).append("\n");
						sb.append(Files.readString(includePath)).append("\n\n");
					} catch (Exception e) {
						// Non-fatal – the test will likely fail on its own
					}
				}
			}
			prefix = sb.toString();
		}

		// "use strict" must be the very first statement of the WHOLE assembled
		// program, before even the includes prefix - a directive prologue is
		// only recognized as the program's own leading run of string-literal
		// statements (ASTRootStatementList), and the includes files (real
		// function declarations, not string literals) would otherwise push it
		// out of that leading position, silently discarding the test's
		// intended strictness (same class of bug already fixed for
		// combineShellAndScript's transpiled-mode combined text, below).
		String combined = prefix + text;
		// Diagnostic: -Dtest262.stripDoNotEvaluate=true removes the
		// $DONOTEVALUATE() guard from parse/resolution negative tests, so a
		// NEGATIVE MISMATCH reports what the engine really does with the
		// invalid code (an error during EXECUTE instead of PARSE, or nothing)
		// instead of just "$DONOTEVALUATE reached".
		if (STRIP_DO_NOT_EVALUATE && meta.negative != null && !"runtime".equals(meta.negative.phase)) {
			combined = combined.replace("$DONOTEVALUATE();", "");
		}
		if (meta.hasFlag("onlyStrict")) {
			combined = "\"use strict\";\n" + combined;
		}

		return combined;
	}

	// A test262 file with `flags: [module]` must be compiled as a genuine
	// ES module (SCRIPT_MODULE) - not just permissively PARSED with
	// import/export syntax tolerated (which is all the default
	// SCRIPT_ADDTOCACHE-only flags give it). Without this, ASTProgram.
	// isModule() is false for the file's own ROOT execution, which means
	// (among other spec-semantic checks that key off it) a self-import
	// (`import {x} from './this-same-file.js'`, a common test262 idiom)
	// never registers the root file itself in the module resolver's cache,
	// so it always resolves through an independent duplicate re-parse
	// instead of truly self-referencing - see
	// InterpretedGlobalRuntimeContext.registerRootModule()'s own doc
	// comment. currentMetadata is already parsed (by preprocessFile(),
	// called from loadScript()) by the time execFile() reaches the
	// createScript() call this feeds.
	@Override
	protected int getScriptFlags(Path scriptFile) {
		int flags = JSEnvironment.SCRIPT_ADDTOCACHE;
		if (currentMetadata != null && currentMetadata.hasFlag("module")) {
			flags |= JSEnvironment.SCRIPT_MODULE;
		}
		return flags;
	}

	// Transpiled-mode execution compiles the shell (sta.js/assert.js) and
	// this file's own (already-preprocessed) text as ONE combined Java
	// class - unlike interpreted mode, which runs the shell as a genuinely
	// separate script unit (BaseTestSuiteTest.loadShell()) before the test's
	// own script, so an onlyStrict test's "use strict" (prepended above)
	// naturally stays the leading statement of its own, separately-parsed
	// program. In the combined-text case, the shell's own content (function
	// declarations, not string literals) ends up BEFORE that "use strict",
	// pushing it out of the Directive Prologue's required leading position
	// (ASTRootStatementList only recognizes a "use strict" that's part of
	// the program's own leading run of string-literal statements) - so it's
	// silently never recognized, and the whole combined script runs sloppy
	// regardless of the test's own strictness. Re-anchor it at the very
	// front of the combined text instead; the copy preprocessFile() already
	// embedded further in is now a harmless, redundant Directive Prologue
	// entry. sta.js/assert.js don't rely on any sloppy-mode-specific
	// behavior, so running them under strict mode too (as interpreted mode's
	// separate shell script never does) is not expected to change their
	// own behavior.
	@Override
	protected String combineShellAndScript(String shell, String script) {
		// flags:[raw] - the file must run exactly as written, with no harness
		// before it (test262 INTERPRETING.md). These files use no harness
		// function, and several depend on their own first line: a directive
		// prologue ("use strict" must be the program's first statement) or a
		// SingleLineHTMLCloseComment only valid at the start of the source.
		// Interpreted mode never prepends (it runs the shell as a separate
		// script), so only this combined transpiled-mode text needs it.
		if (currentMetadata != null && currentMetadata.hasFlag("raw")) {
			return script;
		}
		// A hashbang (#!...) is only valid as the ABSOLUTE first characters
		// of a Script - same "must be the leading thing in the COMBINED
		// text, not just this file's own text" problem as "use strict"
		// above, but for a comment-like directive instead of a directive
		// prologue statement. Re-anchor it at the very front (before even
		// the shell) rather than leaving it at script's original position,
		// where the prepended shell content pushes it into the MIDDLE of
		// the combined text - not the start of anything, just a bare `#`
		// token there, a genuine parse error ("Encountered #"). Confirmed
		// via test262 language/comments/hashbang/*.js, which all pass
		// cleanly in interpreted mode (BaseTestSuiteTest.loadShell() runs
		// the shell as a genuinely separate script unit, so the test file's
		// own hashbang naturally stays at the front of ITS OWN, separately-
		// parsed program).
		String hashbang = null;
		if (script.startsWith("#!")) {
			int nl = script.indexOf('\n');
			if (nl < 0) {
				hashbang = script;
				script = "";
			} else {
				hashbang = script.substring(0, nl + 1);
				script = script.substring(nl + 1);
			}
		}
		String combined = super.combineShellAndScript(shell, script);
		if (currentMetadata != null && currentMetadata.hasFlag("onlyStrict")) {
			combined = "\"use strict\";\n" + combined;
		}
		if (hashbang != null) {
			combined = hashbang + combined;
		}
		return combined;
	}

	// -----------------------------------------------------------------------
	// Exception handling
	// A negative test passes only when it throws the error type its metadata
	// names (negative.type), at the right time (negative.phase):
	//   parse      - while the script is created (env.createScript(), with its
	//                early errors), before anything runs;
	//   resolution - while modules are linked: after parsing, but before the
	//                test body is evaluated;
	//   runtime    - while running.
	// Test files for the parse and resolution phases start with
	// $DONOTEVALUATE(), which throws a string: reaching it means the script
	// started running, so the expected early error never happened. Accepting
	// any exception (as this harness once did) let such files pass - hiding,
	// for instance, a parser that accepted an invalid identifier escape.
	// -----------------------------------------------------------------------
	static final boolean STRIP_DO_NOT_EVALUATE = Boolean.getBoolean("test262.stripDoNotEvaluate");
	static final String DO_NOT_EVALUATE = "Test262: This statement should not be evaluated.";

	@Override
	protected void handleException(String relativePathStr, AtomicInteger errorCount, Throwable t) {
		Test262Metadata meta = currentMetadata;
		currentThrew = true;

		if (meta != null && meta.negative != null) {
			String mismatch = negativeMismatch(meta.negative, execPhase, t);
			if (mismatch == null) {
				return; // the expected error, at the expected time
			}
			errorCount.getAndIncrement();
			Console.log("       NEGATIVE MISMATCH {0}: {1}", relativePathStr, mismatch);
			return;
		}

		errorCount.getAndIncrement();
		t.printStackTrace();
	}

	/**
	 * Why a thrown exception does not satisfy a negative test's expectation,
	 * or null when it does.
	 */
	static String negativeMismatch(Test262Metadata.NegativeInfo negative, ExecPhase phase, Throwable t) {
		String actual = errorTypeName(t);
		String expectedType = negative.type;
		String expectedPhase = negative.phase;
		String got = "got " + actual + " during " + phase + " (" + firstLine(t) + ")";
		if (DO_NOT_EVALUATE.equals(actual)) {
			return "expected " + expectedType + " at " + expectedPhase + ", but the script was evaluated ($DONOTEVALUATE reached)";
		}
		if ("parse".equals(expectedPhase) && phase != ExecPhase.PARSE) {
			return "expected " + expectedType + " at parse, " + got;
		}
		if ("resolution".equals(expectedPhase) && phase != ExecPhase.PARSE && phase != ExecPhase.EXECUTE) {
			return "expected " + expectedType + " at resolution, " + got;
		}
		if (expectedType != null && !expectedType.equals(actual)) {
			return "expected " + expectedType + " at " + expectedPhase + ", " + got;
		}
		return null;
	}

	/**
	 * The JavaScript error type of a thrown exception - the constructor name
	 * of a thrown error object ("SyntaxError", "Test262Error"...), SyntaxError
	 * for a parse failure raised as a Java exception, the value itself for a
	 * thrown string, or the Java class name for anything else (a transpiler
	 * or javac failure, for example).
	 */
	static String errorTypeName(Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
			if (c instanceof org.monflabs.galtajs.rt.JSRuntimeException rt) {
				Object js = rt.getJavascriptException();
				if (js instanceof org.monflabs.galtajs.jsonfactory.JSObject jo) {
					try {
						Object ctor = jo.getProperty("constructor");
						if (ctor instanceof org.monflabs.galtajs.jsonfactory.JSObject co) {
							Object n = co.getProperty("name");
							if (n instanceof String s && !s.isEmpty()) {
								return s;
							}
						}
						Object n = jo.getProperty("name");
						if (n instanceof String s && !s.isEmpty()) {
							return s;
						}
					} catch (Throwable ignored) {
						// fall through to a generic name
					}
					return "Object";
				}
				if (js instanceof CharSequence cs) {
					return cs.toString();
				}
				if (js != null) {
					return String.valueOf(js);
				}
			}
			if (c instanceof org.monflabs.galtajs.JSParseException) {
				return "SyntaxError";
			}
		}
		return t.getClass().getSimpleName();
	}

	private static String firstLine(Throwable t) {
		String m = String.valueOf(t.getMessage()).trim();
		int nl = m.indexOf('\n');
		m = nl >= 0 ? m.substring(0, nl) : m;
		return m.length() > 160 ? m.substring(0, 160) + "..." : m;
	}

	// -----------------------------------------------------------------------
	// flags:[async] - $DONE(error) tracking (see Test262TestLibrary for the
	// native $DONE implementation itself). getFAILED()/getPASSED() reuse
	// BaseTestSuiteTest's existing per-file output-stream-driven pass/fail
	// detection - test262's OWN (non-async) tests never print anything
	// starting with either of these, so repurposing them here is safe and
	// doesn't change behavior for any other test file.
	// -----------------------------------------------------------------------
	@Override
	public String getFAILED() {
		return org.monflabs.galtajs.test.test262.Test262TestLibrary.ASYNC_TEST_FAILURE_PREFIX;
	}
	@Override
	public String getPASSED() {
		return org.monflabs.galtajs.test.test262.Test262TestLibrary.ASYNC_TEST_COMPLETE;
	}

	@Override
	protected void afterExecute(String relativePathStr, java.util.concurrent.atomic.AtomicInteger errorCount) {
		Test262Metadata meta = currentMetadata;
		// A negative test must throw: completing normally is a failure
		if (meta != null && meta.negative != null && !currentThrew) {
			errorCount.getAndIncrement();
			Console.log("       NEGATIVE MISMATCH {0}: expected {1} at {2}, but nothing was thrown", relativePathStr, meta.negative.type, meta.negative.phase);
		}
		if (meta != null && meta.hasFlag("async") && meta.negative == null
				&& !org.monflabs.galtajs.test.test262.Test262TestLibrary.wasAsyncDoneCalled()) {
			errorCount.getAndIncrement();
		}
	}

	// -----------------------------------------------------------------------
	// Directory helpers
	// -----------------------------------------------------------------------
	public File getTest262Directory() {
		return support.getProjectDirectory("test262");
	}

	public File getTest262TestDirectory() {
		return support.getProjectDirectory("test262/test");
	}

	public File getTest262HarnessDirectory() {
		return support.getProjectDirectory("test262/harness");
	}

	// -----------------------------------------------------------------------
	// Minimal YAML front-matter parser for test262 metadata
	// Handles: negative, includes, flags, features
	// -----------------------------------------------------------------------
	static class Test262Metadata {

		String description = "";
		List<String> includes = new ArrayList<>();
		List<String> flags = new ArrayList<>();
		List<String> features = new ArrayList<>();
		NegativeInfo negative;

		static class NegativeInfo {
			String phase;
			String type;
		}

		/**
		 * Extracts and parses the {@code /*---…---*\/} block from a test262 source file.
		 * Returns {@code null} when no front-matter is present.
		 */
		static Test262Metadata parse(String content) {
			int start = content.indexOf("/*---");
			int end = content.indexOf("---*/");
			if (start < 0 || end < 0 || end <= start) {
				return null;
			}

			String yaml = content.substring(start + 5, end).trim();
			Test262Metadata meta = new Test262Metadata();

			// A handful of test262 files (built-ins/Function/prototype/toString/
			// line-terminator-normalisation-CR.js and its CR_LF sibling) use bare
			// CR or CR+LF as their ONLY line terminator, including inside the
			// YAML front-matter itself. Splitting on "\n" alone then sees the
			// whole front-matter as a single non-matching "line" - silently
			// dropping its includes/flags/features (e.g. nativeFunctionMatcher.js
			// never gets included, so the test body's own assertion helper is
			// undefined). Split on any of the three LineTerminatorSequence forms.
			String[] lines = yaml.split("\r\n|\r|\n");
			String currentKey = null;
			boolean inNegative = false;
			boolean inMultilineDescription = false;

			for (String line : lines) {
				if (line.isEmpty()) {
					inMultilineDescription = false;
					continue;
				}

				int indent = leadingSpaces(line);
				String trimmed = line.trim();

				if (indent == 0) {
					inMultilineDescription = false;
					inNegative = false;
					currentKey = null;

					if (trimmed.startsWith("negative:")) {
						meta.negative = new NegativeInfo();
						inNegative = true;
						currentKey = "negative";
					} else if (trimmed.startsWith("includes:")) {
						currentKey = "includes";
						String rest = trimmed.substring("includes:".length()).trim();
						if (!rest.isEmpty()) {
							parseInlineList(rest, meta.includes);
						}
					} else if (trimmed.startsWith("flags:")) {
						currentKey = "flags";
						String rest = trimmed.substring("flags:".length()).trim();
						if (!rest.isEmpty()) {
							parseInlineList(rest, meta.flags);
						}
					} else if (trimmed.startsWith("features:")) {
						currentKey = "features";
						String rest = trimmed.substring("features:".length()).trim();
						if (!rest.isEmpty()) {
							parseInlineList(rest, meta.features);
						}
					} else if (trimmed.startsWith("description:")) {
						String rest = trimmed.substring("description:".length()).trim();
						if (rest.startsWith(">") || rest.startsWith("|")) {
							inMultilineDescription = true;
						} else {
							meta.description = rest;
						}
					}
				} else {
					// Indented content
					if (inNegative && meta.negative != null) {
						if (trimmed.startsWith("phase:")) {
							meta.negative.phase = trimmed.substring("phase:".length()).trim();
						} else if (trimmed.startsWith("type:")) {
							meta.negative.type = trimmed.substring("type:".length()).trim();
						}
					} else if (trimmed.startsWith("- ")) {
						String item = trimmed.substring(2).trim();
						if ("includes".equals(currentKey)) {
							meta.includes.add(item);
						} else if ("flags".equals(currentKey)) {
							meta.flags.add(item);
						} else if ("features".equals(currentKey)) {
							meta.features.add(item);
						}
					}
				}
			}

			return meta;
		}

		/** Parses an inline YAML list of the form {@code [a, b, c]} or a bare value. */
		private static void parseInlineList(String s, List<String> out) {
			s = s.trim();
			if (s.startsWith("[") && s.endsWith("]")) {
				s = s.substring(1, s.length() - 1);
				for (String part : s.split(",")) {
					String item = part.trim();
					if (!item.isEmpty()) {
						out.add(item);
					}
				}
			} else if (!s.isEmpty()) {
				// Single inline value (no brackets)
				out.add(s);
			}
		}

		private static int leadingSpaces(String line) {
			int count = 0;
			while (count < line.length() && line.charAt(count) == ' ') {
				count++;
			}
			return count;
		}

		boolean hasFlag(String flag) {
			return flags.contains(flag);
		}

		boolean hasUnsupportedFeature(Set<String> unsupported) {
			for (String f : features) {
				if (unsupported.contains(f)) {
					return true;
				}
			}
			return false;
		}
	}
}
