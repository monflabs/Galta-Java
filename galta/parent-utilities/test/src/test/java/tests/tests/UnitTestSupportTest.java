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

import java.io.File;
import java.sql.SQLException;

import org.monflabs.json.JsonObject;
import org.monflabs.tests.UnitTestSupport;
import org.monflabs.tests.__BaseTestCase;

/**
 * Tests for the golden-file template checks of {@link UnitTestSupport}.
 */
public class UnitTestSupportTest extends __BaseTestCase {

	/** Stores its templates in a scratch folder rather than the project's tests folder. */
	private static class ScratchSupport extends UnitTestSupport {
		private final File dir;
		private Class<?> templateClass;
		ScratchSupport(Class<?> c, File dir) {
			super(c);
			this.dir = dir;
			setVerbose(false);
		}
		@Override
		public File getTestResultsDirectory() {
			return dir;
		}
		@Override
		public Class<?> getTemplateClass() {
			return templateClass!=null ? templateClass : super.getTemplateClass();
		}
		@Override
		public void print(String fmt, Object... parameters) {
			// Keep the test output quiet
		}
	}

	private ScratchSupport newSupport() {
		return new ScratchSupport(getClass(), support.getTargetTempDirectory("template-tests/"+getName(), true));
	}

	public void testMissingTemplateFails() {
		ScratchSupport s = newSupport();
		assertFalse(s.checkTextResult("value", "missing.txt"));
		assertFalse(s.resourceExists(getClass(), "missing.txt"));
		AssertionError e = assertThrows(() -> s.assertTextResult("value", "missing.txt"));
		assertTrue(e.getMessage(), e.getMessage().contains("Template missing.txt does not exist"));
		assertTrue(e.getMessage(), e.getMessage().contains(UnitTestSupport.SAVE_TEMPLATES_PROPERTY+"=missing"));
	}

	public void testSaveMissingTemplate() {
		ScratchSupport s = newSupport();
		s.setSaveMissingTemplates(true);
		assertTrue(s.checkTextResult("value", "saved.txt"));
		assertEquals("value", s.loadText(getClass(), "saved.txt"));

		// Existing templates are compared, not rewritten
		assertFalse(s.checkTextResult("other", "saved.txt"));
		assertEquals("value", s.loadText(getClass(), "saved.txt"));
	}

	public void testMismatchFails() {
		ScratchSupport s = newSupport();
		s.saveText(getClass(), "t.txt", "expected");
		assertTrue(s.checkTextResult("expected", "t.txt"));
		AssertionError e = assertThrows(() -> s.assertTextResult("actual", "t.txt"));
		assertTrue(e.getMessage(), e.getMessage().contains("Result does not match template t.txt"));
		s.saveText(getClass(), "t.json", "{\"a\":1}");
		AssertionError j = assertThrows(() -> s.assertJsonTemplate(JsonObject.of("a", 2), "t.json"));
		assertTrue(j.getMessage(), j.getMessage().startsWith("Error in JSON document: Result does not match template t.json"));
	}

	public void testSystemProperty() {
		String old = System.getProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY);
		try {
			System.setProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY, "missing");
			ScratchSupport s = newSupport();
			assertTrue(s.saveMissingTemplates());
			assertFalse(s.forceTemplateSave());
			assertTrue(s.checkTextResult("value", "p.txt"));
			assertFalse(s.checkTextResult("changed", "p.txt"));

			System.setProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY, "all");
			assertTrue(s.forceTemplateSave());
			assertTrue(s.checkTextResult("changed", "p.txt"));
			assertEquals("changed", s.loadText(getClass(), "p.txt"));
		} finally {
			if(old==null) {
				System.clearProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY);
			} else {
				System.setProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY, old);
			}
		}
	}

	public void testForceSaveIsPerInstance() {
		ScratchSupport s1 = newSupport();
		s1.saveText(getClass(), "f.txt", "old");
		s1.setForceTemplateSave(true);
		assertTrue(s1.checkTextResult("new", "f.txt"));
		assertEquals("new", s1.loadText(getClass(), "f.txt"));

		// Another instance is not affected
		ScratchSupport s2 = new ScratchSupport(getClass(), s1.getTestResultsDirectory());
		assertFalse(s2.forceTemplateSave());
		assertFalse(s2.checkTextResult("newer", "f.txt"));
	}

	public void testTemplateClassUsedForSaveAndLoad() {
		ScratchSupport s = newSupport();
		s.templateClass = String.class;
		s.setSaveMissingTemplates(true);
		assertTrue(s.checkTextResult("value", "c.txt"));
		assertTrue(s.resourceExists(String.class, "c.txt"));
		assertFalse(s.resourceExists(getClass(), "c.txt"));
		assertTrue(s.checkTextResult("value", "c.txt"));
	}

	public void testDisabledChecks() {
		ScratchSupport s = newSupport();
		assertTrue(s.checkTextResult("anything", "*disabled.txt"));
		assertTrue(s.checkTextResult("anything", null));
		assertFalse(s.resourceExists(getClass(), "*disabled.txt"));
	}

	public void testNormalizedComparisons() {
		ScratchSupport s = newSupport();
		s.saveText(getClass(), "crlf.txt", "a\r\nb\r\n");
		assertTrue(s.checkTextResult("a\nb\n", "crlf.txt"));

		s.saveText(getClass(), "trim.txt", "a  \nb\n");
		assertFalse(s.checkTextResult("a\nb\n", "trim.txt"));
		assertTrue(s.checkResultTemplate(new UnitTestSupport.TrimmedStringResult("a\nb \n"), "trim.txt"));

		s.saveText(getClass(), "j.json", "{\n  \"b\": [1, 2],\n  \"a\": \"x\"\n}");
		assertTrue(s.checkJsonTemplate(JsonObject.of("a", "x", "b", org.monflabs.json.JsonArray.of(1, 2)), "j.json"));
		assertFalse(s.checkJsonTemplate(JsonObject.of("a", "x", "b", org.monflabs.json.JsonArray.of(2, 1)), "j.json"));
	}

	public void testFailureMessageHasDiff() {
		ScratchSupport s = newSupport();
		s.saveText(getClass(), "d.txt", "line1\nline2\nexpected\nline4\n");
		AssertionError e = assertThrows(() -> s.assertTextResult("line1\nline2\nactual\nline4\n", "d.txt"));
		assertTrue(e.getMessage(), e.getMessage().contains("first difference at line 3"));
		assertTrue(e.getMessage(), e.getMessage().contains("\n  line2\n- expected\n+ actual\n"));
		// The file is named, so it can be inspected or updated
		assertTrue(e.getMessage(), e.getMessage().contains(s.resourceFile(getClass(), "d.txt", false).getPath()));

		AssertionError j = assertThrows(() -> s.assertJsonEquals(JsonObject.of("a", 1), JsonObject.of("a", 2)));
		assertTrue(j.getMessage(), j.getMessage().contains("- ") && j.getMessage().contains("+ "));
		AssertionError t = assertThrows(() -> s.assertNormalizedTextEquals("x\ny", "x\nz"));
		assertTrue(t.getMessage(), t.getMessage().contains("- y\n+ z\n"));
	}

	public void testCompactDiff() {
		assertEquals("first difference at line 1, column 3 (expected 1 lines, actual 1 lines):\n- abc\n+ abd\n",
				UnitTestSupport.compactDiff("abc", "abd"));
		// Extra lines only
		assertEquals("first difference at line 2 (expected 1 lines, actual 2 lines):\n  a\n+ b\n",
				UnitTestSupport.compactDiff("a", "a\nb"));
		// Long lines are clipped around the difference
		String base = "x".repeat(500);
		String d = UnitTestSupport.compactDiff(base+"1", base+"2");
		assertTrue(d, d.length() < 500);
		assertTrue(d, d.contains("x1") && d.contains("x2"));
	}

	public void testSaveTemplatesRefusedOnCI() {
		String old = System.getProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY);
		try {
			System.setProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY, "all");
			ScratchSupport ci = new ScratchSupport(getClass(), support.getTargetTempDirectory("template-tests/"+getName(), true)) {
				@Override
				protected boolean isContinuousIntegration() {
					return true;
				}
			};
			IllegalStateException e = org.junit.Assert.assertThrows(IllegalStateException.class, () -> ci.checkTextResult("value", "ci.txt"));
			assertTrue(e.getMessage(), e.getMessage().contains("CI"));
			assertFalse(ci.resourceExists(getClass(), "ci.txt"));
		} finally {
			if(old==null) {
				System.clearProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY);
			} else {
				System.setProperty(UnitTestSupport.SAVE_TEMPLATES_PROPERTY, old);
			}
		}
	}

	public void testBomAndTrailingNewlines() {
		ScratchSupport s = newSupport();
		s.saveText(getClass(), "bom.txt", "﻿value\n\n");
		assertTrue(s.checkTextResult("value", "bom.txt"));
		assertTrue(s.checkTextResult("value\n", "bom.txt"));
		assertFalse(s.checkTextResult("value\n\nmore", "bom.txt"));
		s.saveText(getClass(), "bom.json", "﻿{\"a\":1}\n");
		assertTrue(s.checkJsonTemplate(JsonObject.of("a", 1), "bom.json"));
	}

	public void testResourceFileForClass() {
		ScratchSupport s = newSupport();
		File dir = s.getTestResultsDirectory();
		assertEquals(new File(dir, "tests/tests/UnitTestSupportTest.x"), s.resourceFileForClass(getClass(), ".x", false));
		// A class without a package (here a primitive type: its name has no dot)
		assertEquals(new File(dir, "int.x"), s.resourceFileForClass(int.class, ".x", false));
	}

	public void testNormalizeLineBreaks() {
		assertEquals("a\nb", UnitTestSupport.normalizeLineBreaks("a\r\nb"));
		assertEquals("a\nb", UnitTestSupport.normalizeLineBreaks("a\rb"));
		// LF followed by a lone CR is two line breaks
		assertEquals("a\n\nb", UnitTestSupport.normalizeLineBreaks("a\n\rb"));
		assertEquals("a\n\nb", UnitTestSupport.normalizeLineBreaks("a\r\n\rb"));
		assertEquals("", UnitTestSupport.normalizeLineBreaks(""));
	}

	public void testGetCause() {
		RuntimeException root = new RuntimeException("root");
		assertSame(root, UnitTestSupport.getCause(new RuntimeException("wrapper", root)));
		assertNull(UnitTestSupport.getCause(root));

		SQLException sql = new SQLException("first");
		SQLException next = new SQLException("next");
		sql.setNextException(next);
		assertSame(next, UnitTestSupport.getCause(sql));
	}

	private static AssertionError assertThrows(Runnable r) {
		try {
			r.run();
		} catch(AssertionError e) {
			return e;
		}
		throw new AssertionError("Expected an assertion failure");
	}
}
