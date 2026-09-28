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

import java.awt.Component;
import java.awt.Font;
import java.util.function.Consumer;

import org.monflabs.js.debugger.ui.model.ScriptInfo;

import junit.framework.TestCase;

/**
 * {@link SourcePanel} against a deliberately DEFERRED {@link SourcePanel.SourceSupplier}
 * (the callback is captured, not invoked immediately) - reproduces a real bug:
 * a script's first Debugger.paused can arrive before its Debugger.scriptParsed
 * -triggered source fetch resolves. showExecutionLine() used to look the
 * SourceView up right after kicking off that fetch, found nothing yet, and
 * silently dropped the highlight - invisible on a slow/cold run (the fetch had
 * time to finish first) but reproducible every time on a fast/warm one. A
 * later pause/step always worked because by then the fetch was long done,
 * which is exactly what made this so easy to miss by eye.
 */
public class SourcePanelTest extends TestCase {

	private static final String URL = "file:///work/x.js";
	private static final ScriptInfo SCRIPT = new ScriptInfo("script1", URL, 3, false);

	private SourcePanel panelWithDeferredFetch(final Consumer<String>[] capturedCallback) {
		return new SourcePanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false,
				(url, line) -> { },
				(scriptId, whenReady) -> capturedCallback[0] = whenReady);
	}

	private SourceView shownView(final SourcePanel panel) {
		final Component shown = panel.getComponent(0);
		return shown instanceof SourceView view ? view : null;
	}

	@SuppressWarnings("unchecked")
	public void testExecutionLineAppliesOnceTheDeferredFetchResolves() {
		final Consumer<String>[] callback = new Consumer[1];
		final SourcePanel panel = panelWithDeferredFetch(callback);

		// scriptAdded()'s own openScript() call - kicks off the fetch, does not
		// resolve it
		panel.open(SCRIPT);
		assertNotNull("fetch should have been requested", callback[0]);
		assertNull("no view yet - the fetch has not resolved", shownView(panel));

		// paused()'s own showExecutionLine() call - arrives before the fetch
		// resolves; must not throw, and must not be silently lost
		panel.showExecutionLine(URL, 1);
		assertNull("still no view - this is the race window", shownView(panel));

		// the fetch finally resolves
		callback[0].accept("var a = 1;\nvar b = 2;\nvar c = 3;\n");

		final SourceView view = shownView(panel);
		assertNotNull("the view should exist once the fetch resolves", view);
		assertTrue("the pending execution line must be applied, not dropped", view.hasExecutionMarker());
	}

	@SuppressWarnings("unchecked")
	public void testExecutionLineAppliesImmediatelyWhenAlreadyLoaded() {
		final Consumer<String>[] callback = new Consumer[1];
		final SourcePanel panel = panelWithDeferredFetch(callback);

		panel.open(SCRIPT);
		callback[0].accept("var a = 1;\nvar b = 2;\nvar c = 3;\n");
		assertFalse(shownView(panel).hasExecutionMarker());

		panel.showExecutionLine(URL, 0);
		assertTrue(shownView(panel).hasExecutionMarker());
	}
}
