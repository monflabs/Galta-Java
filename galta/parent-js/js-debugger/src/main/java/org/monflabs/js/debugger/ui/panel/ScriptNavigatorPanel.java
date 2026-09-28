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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.monflabs.js.debugger.ui.model.ScriptInfo;

/**
 * The list of scripts, one row per url (the newest script id for that url
 * wins), like Chrome's file navigator. Selecting one opens it in the source
 * panel.
 */
final class ScriptNavigatorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, ScriptInfo> byUrl = new LinkedHashMap<>();
    private final DefaultListModel<ScriptInfo> model = new DefaultListModel<>();
    private final transient JList<ScriptInfo> list = new JList<>(model);
    private transient boolean selecting;

    ScriptNavigatorPanel(final Consumer<ScriptInfo> onOpen) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (!selecting && !e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                onOpen.accept(list.getSelectedValue());
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Selects the row for a script, if present, without re-firing {@code onOpen}. */
    void select(final ScriptInfo script) {
        if (script == null || script.url() == null) {
            return;
        }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).url().equals(script.url())) {
                if (i != list.getSelectedIndex()) {
                    selecting = true;
                    try {
                        list.setSelectedIndex(i);
                        list.ensureIndexIsVisible(i);
                    } finally {
                        selecting = false;
                    }
                }
                return;
            }
        }
    }

    /** Adds or refreshes a script row, keyed by url. */
    void add(final ScriptInfo script) {
        if (script.url() == null) {
            return;
        }
        final boolean known = byUrl.containsKey(script.url());
        byUrl.put(script.url(), script);
        if (!known) {
            model.addElement(script);
        } else {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).url().equals(script.url())) {
                    model.set(i, script);
                    break;
                }
            }
        }
    }

    /** Clears the list (a fresh attach). */
    void clear() {
        byUrl.clear();
        model.clear();
    }

    private static final class Renderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof ScriptInfo script) {
                setText(script.shortName());
                setToolTipText(script.url());
            }
            return this;
        }
    }
}
