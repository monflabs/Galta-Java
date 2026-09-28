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
package tests.javascript.javatranspiler;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;

import tests.javascript.JavaScriptStrictTestCase;
import util.GlobalTestEnvironment;

/**
 * Verifies that the three transpiler performance optimisations are both
 * behaviourally correct and structurally applied (i.e. the expected Java
 * patterns actually appear in the generated source).
 *
 * Optimisation 1 – compound-assignment direct field access
 *   x += y  →  p.value = add(_env, p.value, rhs)
 *   instead of  assignAdd(_env, p, rhs)
 *
 * Optimisation 2 – inc/dec direct-field variants
 *   ++x / x++  →  preIncVar(_env, p) / postIncVar(_env, p)
 *   instead of  preInc(_env, p) / postInc(_env, p)
 *
 * Optimisation 3 – global identifier hoisting
 *   Identifiers present in StandardObjects are resolved once at the top
 *   of _runValue and reused, rather than looked up on every use site.
 */
public class TranspilerOptimizationsTest extends JavaScriptStrictTestCase {

    @Override
    protected JSEnvironment.Builder createEnvironment() {
        return GlobalTestEnvironment.newBuilder()
                .supportReturnOutsideFunction(true);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String javaFor(String js) throws Exception {
        JSEnvironment env = getEnvironment();
        return transpileToJava(env, JAVA_CLASSNAME, null, env.createScript(js, "T"));
    }

    private Object run(String js) throws Exception {
        JSEnvironment env = getEnvironment();
        JSInterpretedUnit unit = env.createScript(js, "T");
        return transpilerExecute(env, JAVA_CLASSNAME, null, unit).value();
    }


    // -----------------------------------------------------------------------
    // Optimisation 1: compound-assignment direct field access
    // -----------------------------------------------------------------------

    public void testCompoundAddCorrectness() throws Exception {
        // sum += i inside a loop must produce the correct result
        Object res = run("var sum = 0; for(var i=0;i<10;i++){sum += i;} return sum;");
        assertEquals(45, res);
    }

    public void testCompoundAddGeneratesDirect() throws Exception {
        // getJavaVariableValue() produces  p_X.value/*name*/  so the pattern is "= add("
        String java = javaFor("var sum = 0; sum += 1; return sum;");
        assertTrue("Expected direct-field compound-add (= add(): " + java,
                java.contains("= add("));
        // Must NOT fall back to the VarAccessor path for a plain local variable
        assertFalse("Unexpected assignAdd for local var: " + java,
                java.contains("assignAdd("));
    }

    public void testCompoundSubGeneratesDirect() throws Exception {
        String java = javaFor("var x = 10; x -= 3; return x;");
        assertTrue(java.contains("= sub("));
        assertFalse(java.contains("assignSub("));
    }

    public void testCompoundMulGeneratesDirect() throws Exception {
        String java = javaFor("var x = 4; x *= 3; return x;");
        assertTrue(java.contains("= mul("));
        assertFalse(java.contains("assignMul("));
    }

    public void testCompoundDivGeneratesDirect() throws Exception {
        String java = javaFor("var x = 12; x /= 4; return x;");
        assertTrue(java.contains("= div("));
        assertFalse(java.contains("assignDiv("));
    }

    public void testCompoundBitAndGeneratesDirect() throws Exception {
        String java = javaFor("var x = 15; x &= 6; return x;");
        assertTrue(java.contains("= bitAnd("));
        assertFalse(java.contains("assignBitAnd("));
    }

    public void testCompoundAssignOnObjectMemberNotAffected() throws Exception {
        // The optimisation must NOT be applied to member (property) assignments –
        // those still go through the property-set path.
        String java = javaFor("var o = {x:1}; o.x += 2; return o.x;");
        assertTrue("Member compound-add should still use assignAdd: " + java,
                java.contains("assignAdd("));
    }

    public void testCompoundAssignWithScope() throws Exception {
        // Inside a `with` block the optimisation must be suppressed.
        String java = javaFor("var o = {x:1}; with(o){ x += 2; } return o.x;");
        // The 'x' inside with() must fall back to the VarAccessor path
        assertFalse("Direct field write must not be used inside with(): " + java,
                java.contains(".value = add("));
    }

    public void testCompoundAddLoopCorrectness() throws Exception {
        // Regression: loop where both loop counter and accumulator use +=
        Object res = run(
            "var n=0; var s=0; for(var i=1;i<=100;i++){n+=1; s+=i;} return s;");
        assertEquals(5050, res);
    }


    // -----------------------------------------------------------------------
    // Optimisation 2: inc/dec direct-field variants
    // -----------------------------------------------------------------------

    public void testPreIncCorrectness() throws Exception {
        Object res = run("var x = 5; var y = ++x; return y;");
        assertEquals(6, res);
    }

    public void testPostIncCorrectness() throws Exception {
        Object res = run("var x = 5; var y = x++; return y;");
        assertEquals(5, res);
    }

    public void testPreDecCorrectness() throws Exception {
        Object res = run("var x = 5; var y = --x; return y;");
        assertEquals(4, res);
    }

    public void testPostDecCorrectness() throws Exception {
        Object res = run("var x = 5; var y = x--; return y;");
        assertEquals(5, res);
    }

    public void testForLoopPreIncCorrectness() throws Exception {
        Object res = run("var s=0; for(var i=0;i<10;++i){s+=i;} return s;");
        assertEquals(45, res);
    }

    public void testForLoopPostIncCorrectness() throws Exception {
        Object res = run("var s=0; for(var i=0;i<10;i++){s+=i;} return s;");
        assertEquals(45, res);
    }

    public void testPreIncGeneratesDirectVar() throws Exception {
        String java = javaFor("function f(){ var x = 0; var y = ++x; return x+y; } return f();");
        assertTrue("Expected preIncVar: " + java, java.contains("preIncVar("));
        assertFalse("Unexpected preInc: " + java, java.contains("preInc("));
    }

    public void testPostIncGeneratesDirectVar() throws Exception {
        String java = javaFor("function f(){ var x = 0; var y = x++; return x+y; } return f();");
        assertTrue("Expected postIncVar: " + java, java.contains("postIncVar("));
        assertFalse("Unexpected postInc: " + java, java.contains("postInc("));
    }

    public void testPreDecGeneratesDirectVar() throws Exception {
        String java = javaFor("function f(){ var x = 5; var y = --x; return x+y; } return f();");
        assertTrue("Expected preDecVar: " + java, java.contains("preDecVar("));
        assertFalse("Unexpected preDec: " + java, java.contains("preDec("));
    }

    public void testPostDecGeneratesDirectVar() throws Exception {
        String java = javaFor("function f(){ var x = 5; var y = x--; return x+y; } return f();");
        assertTrue("Expected postDecVar: " + java, java.contains("postDecVar("));
        assertFalse("Unexpected postDec: " + java, java.contains("postDec("));
    }


    // -----------------------------------------------------------------------
    // Optimisation 3: global identifier hoisting
    //
    // Global identifiers are no longer cached in a local: a global binding
    // (even a standard one like Math) can be reassigned or deleted at any
    // time, so each use is a lookup. These tests check the results stay right.
    // -----------------------------------------------------------------------

    public void testIdentifierHoistingCorrectness() throws Exception {
        // Math is in StandardObjects; the hoisted reference must produce correct results
        Object res = run("return Math.sqrt(4) + Math.abs(-3);");
        assertEquals(5.0, res);
    }



    public void testWrittenIdentifierNotHoisted() throws Exception {
        // An identifier that is written must NOT be hoisted (stale cache would give wrong value)
        String java = javaFor(
            "var PI = Math.PI; Math = null; return typeof Math;");
        // The write 'Math = null' marks Math as written; it must fall back to per-use lookup
        assertFalse("Written identifier must not be hoisted: " + java,
                java.contains("final Object _id_") && !java.contains("getIdentifier(_ctx,\"Math\""));
    }

    public void testNonStandardObjectNotHoisted() throws Exception {
        // 'obj2' is not registered in the test environment's StandardObjects;
        // it must not be hoisted — each use must be an inline getIdentifier call
        // so that a ReferenceError at the use site is correctly catchable.
        String java = javaFor("var x = typeof obj2; return x;");
        // obj2 must appear inline as a getIdentifier call (not false/throwError=false hoisted)
        assertTrue("obj2 must be looked up inline: " + java,
                java.contains("\"obj2\""));
        // Must NOT appear as a hoisted final Object declaration
        assertFalse("obj2 must not appear as a hoisted final declaration: " + java,
                java.contains("final Object _id_"));
    }



    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static long countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) >= 0) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
