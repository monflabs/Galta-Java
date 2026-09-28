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

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.monflabs.js.debugger.ui.model.Breakpoint;
import org.monflabs.js.debugger.ui.model.ScriptInfo;

/**
 * The open source tabs, one {@link SourceView} per url, created on demand -
 * the centre of the Sources layout. It shows the execution line during a pause
 * and keeps each view's breakpoint dots in sync with the model.
 */
final class SourcePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Fetches a script's source text, calling the callback on the ui thread. */
    interface SourceSupplier {
        /** @param scriptId the script @param whenReady receives the source */
        void fetch(String scriptId, java.util.function.Consumer<String> whenReady);
    }

    private final Font font;
    private final boolean dark;
    private final transient SourceView.BreakpointToggle toggle;
    private final transient SourceSupplier sources;
    private final transient Map<String, SourceView> views = new HashMap<>();
    private final transient Map<String, ScriptInfo> scriptsByUrl = new HashMap<>();
    private final JLabel placeholder = new JLabel("No script open", SwingConstants.CENTER);
    private String currentUrl;

    SourcePanel(final Font font, final boolean dark, final SourceView.BreakpointToggle toggle, final SourceSupplier sources) {
        super(new BorderLayout());
        this.font = font;
        this.dark = dark;
        this.toggle = toggle;
        this.sources = sources;
        add(placeholder, BorderLayout.CENTER);
    }

    /** Opens (or reveals) a script. */
    void open(final ScriptInfo script) {
        scriptsByUrl.put(script.url(), script);
        show(script.url());
    }

    private void show(final String url) {
        withView(url, view -> { });
    }

    // Ensures url's SourceView exists (creating it via the asynchronous
    // `sources.fetch()` on first use), then runs `action` on it -
    // synchronously if it's already loaded, or once the fetch's callback
    // fires otherwise. Needed because showExecutionLine() used to look the
    // view up right after calling show() with no regard for that fetch
    // still being in flight: a script's FIRST pause can arrive before its
    // Debugger.scriptParsed-triggered fetch resolves (confirmed via a real
    // race - a fast/warm JVM run processes Debugger.paused before the
    // fetch's callback runs, silently dropping the execution-line highlight
    // that a slower/cold run happened to have time for; a later pause/step
    // always worked because by then the fetch was long finished).
    private void withView(final String url, final java.util.function.Consumer<SourceView> action) {
        if (url == null) {
            return;
        }
        final SourceView existing = views.get(url);
        if (existing != null) {
            if (!url.equals(currentUrl)) {
                swapTo(url, existing);
            }
            action.accept(existing);
            return;
        }
        final ScriptInfo script = scriptsByUrl.get(url);
        if (script == null) {
            return;
        }
        sources.fetch(script.scriptId(), source -> {
            final SourceView view = new SourceView(url, source, font, dark, toggle);
            views.put(url, view);
            swapTo(url, view);
            action.accept(view);
        });
    }

    private void swapTo(final String url, final SourceView view) {
        removeAll();
        add(view, BorderLayout.CENTER);
        currentUrl = url;
        revalidate();
        repaint();
    }

    /** Shows the execution arrow on a url's line, opening the script if needed. */
    void showExecutionLine(final String url, final int line) {
        withView(url, view -> view.setExecutionLine(line));
    }

    /** Clears the execution arrow from every open view. */
    void clearExecutionLine() {
        for (final SourceView view : views.values()) {
            view.clearExecutionLine();
        }
    }

    /** Reconciles every open view's breakpoint dots to the model. */
    void syncBreakpoints(final List<Breakpoint> breakpoints) {
        for (final SourceView view : views.values()) {
            final List<Breakpoint> forUrl = breakpoints.stream().filter(b -> b.url().equals(view.url())).toList();
            view.syncBreakpoints(forUrl);
        }
    }

    /** Forgets all open views (a fresh attach). */
    void clear() {
        views.clear();
        scriptsByUrl.clear();
        currentUrl = null;
        removeAll();
        add(placeholder, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
