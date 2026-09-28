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
import java.awt.Component;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.monflabs.js.debugger.ui.model.CallFrame;

/** The paused call stack, innermost first; selecting a frame drives the rest. */
final class CallStackPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<CallFrame> model = new DefaultListModel<>();
    private final transient JList<CallFrame> list = new JList<>(model);
    private boolean updating;

    CallStackPanel(final IntConsumer onSelect) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating && list.getSelectedIndex() >= 0) {
                onSelect.accept(list.getSelectedIndex());
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Shows a new stack, selecting the top frame. */
    void setFrames(final List<CallFrame> frames) {
        updating = true;
        model.clear();
        for (final CallFrame frame : frames) {
            model.addElement(frame);
        }
        if (!frames.isEmpty()) {
            list.setSelectedIndex(0);
        }
        updating = false;
    }

    /** Reflects the selected frame without firing the callback. */
    void select(final int index) {
        updating = true;
        list.setSelectedIndex(index);
        updating = false;
    }

    /** Empties the stack (resume). */
    void clear() {
        updating = true;
        model.clear();
        updating = false;
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof CallFrame frame) {
                setText(frame.label());
            }
            return this;
        }
    }
}
