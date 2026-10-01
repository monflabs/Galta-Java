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
package tests.tests;

import org.monflabs.tests.SuiteGuard;
import org.monflabs.tests.__BaseTestCase;

import doc_examples.testing.LeakRuleExample;
import junit.framework.Test;
import junit.framework.TestSuite;
import tests.AllTests;
import tests.tests.leaks.BaseLeakTest;

public class SuiteGuardTest extends __BaseTestCase {

	/** A suite missing most of the module's test classes. */
	public static class PartialSuite {
		public static Test suite() {
			TestSuite s = new TestSuite();
			s.addTestSuite(AccessorTest.class);
			return s;
		}
	}

	/** A suite without any test. */
	public static class EmptySuite {
		public static Test suite() {
			TestSuite s = new TestSuite();
			s.addTest(SuiteGuard.newTest(EmptySuite.class));
			return s;
		}
	}

	public void testTestClassDetection() {
		assertTrue(SuiteGuard.isTestClass(SuiteGuardTest.class));
		// JUnit 4 class
		assertTrue(SuiteGuard.isTestClass(LeakRuleExample.class));
		// Abstract, annotated, not a test
		assertFalse(SuiteGuard.isTestClass(BaseLeakTest.class));
		assertFalse(SuiteGuard.isTestClass(LeakRuleExample.Leaking.class));
		assertFalse(SuiteGuard.isTestClass(PartialSuite.class));
		assertFalse(SuiteGuard.isTestClass(String.class));
	}

	public void testMissingClassesAreReported() {
		AssertionError e = assertThrows(AssertionError.class, () -> SuiteGuard.assertComplete(PartialSuite.class));
		assertTrue(e.getMessage(), e.getMessage().contains("tests.tests.SuiteGuardTest"));
		assertTrue(e.getMessage(), e.getMessage().contains("tests.tests.UnitTestSupportTest"));
		assertFalse(e.getMessage(), e.getMessage().contains("tests.tests.AccessorTest,"));
		assertFalse(e.getMessage(), e.getMessage().contains("BaseLeakTest"));
	}

	public void testEmptySuiteFails() {
		AssertionError e = assertThrows(AssertionError.class, () -> SuiteGuard.assertComplete(EmptySuite.class));
		assertTrue(e.getMessage(), e.getMessage().contains("run no test"));
	}

	public void testModuleSuiteIsComplete() throws Exception {
		SuiteGuard.assertComplete(AllTests.class);
	}

	private static <T extends Throwable> T assertThrows(Class<T> type, org.junit.function.ThrowingRunnable r) {
		return org.junit.Assert.assertThrows(type, r);
	}
}
