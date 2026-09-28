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

package org.monflabs.js.debugger.ui.panel;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.js.debugger.ui.model.Breakpoint;

import junit.framework.TestCase;

/**
 * The source view headless: the gutter toggle reaches the callback once, the
 * model reconciles dots, and the execution pointer leaves nothing behind.
 */
public class SourceViewTest extends TestCase {

	private static SourceView view(final List<String> toggles) {
		return new SourceView("file:///work/x.js", "var a = 1;\nvar b = 2;\nvar c = 3;\n",
				new Font(Font.MONOSPACED, Font.PLAIN, 12), false,
				(url, line) -> toggles.add(url + ":" + line));
	}

	public void testAGutterToggleReachesTheCallbackOnce() throws Exception {
		final List<String> toggles = new ArrayList<>();
		final SourceView view = view(toggles);
		// a user click is what RSTA turns into toggleBookmark; drive it directly
		view.gutter().toggleBookmark(1);
		assertEquals(List.of("file:///work/x.js:1"), toggles);
	}

	public void testSyncingBreakpointsDoesNotLoopBackAsToggles() throws Exception {
		final List<String> toggles = new ArrayList<>();
		final SourceView view = view(toggles);
		view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 2, null, true, "1", List.of())));
		// the dot was placed by the model, not the user: no callback
		assertTrue("unexpected toggles: " + toggles, toggles.isEmpty());
		assertEquals(1, view.gutter().getBookmarks().length);
	}

	public void testAResolvedLineMovesTheDot() throws Exception {
		final List<String> toggles = new ArrayList<>();
		final SourceView view = view(toggles);
		// requested line 0 but the server resolved it to line 1
		view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 0, null, true, "1", List.of(1))));
		assertEquals(1, view.gutter().getBookmarks().length);
		assertEquals(1, view.textArea().getLineOfOffset(view.gutter().getBookmarks()[0].getMarkedOffset()));
	}

	public void testABreakpointOnThePausedLineBecomesOneCombinedGlyph() {
		final List<String> toggles = new ArrayList<>();
		final SourceView view = view(toggles);
		view.setExecutionLine(1);
		// a breakpoint arrives on the very line the engine is paused on: the plain
		// dot is suppressed and a single arrow-over-dot glyph stands for both, so
		// there is exactly one icon on the line rather than two fighting for it
		view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 1, null, true, "1", List.of())));
		assertEquals(0, view.gutter().getBookmarks().length);
		assertTrue("the combined execution glyph is missing", view.hasExecutionMarker());
	}

	public void testABreakpointOnAnotherLineKeepsItsDotWhilePaused() throws Exception {
		final List<String> toggles = new ArrayList<>();
		final SourceView view = view(toggles);
		view.setExecutionLine(0);
		view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 2, null, true, "1", List.of())));
		// the arrow on line 0 and a plain dot on line 2 coexist
		assertTrue(view.hasExecutionMarker());
		assertEquals(1, view.gutter().getBookmarks().length);
		assertEquals(2, view.textArea().getLineOfOffset(view.gutter().getBookmarks()[0].getMarkedOffset()));
	}

	public void testTheExecutionPointerSetsAndClears() {
		final SourceView view = view(new ArrayList<>());
		view.setExecutionLine(1);
		view.clearExecutionLine();
		// the arrow lives as a tracking icon, not a bookmark; a cleared pointer leaves the bookmarks alone
		assertEquals(0, view.gutter().getBookmarks().length);
		// and setting it again does not accumulate
		view.setExecutionLine(2);
		view.setExecutionLine(0);
		view.clearExecutionLine();
		assertEquals(0, view.gutter().getBookmarks().length);
	}
}
