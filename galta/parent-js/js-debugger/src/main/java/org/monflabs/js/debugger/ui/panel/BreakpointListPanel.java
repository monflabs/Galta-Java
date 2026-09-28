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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import org.monflabs.js.debugger.ui.model.Breakpoint;

/**
 * The breakpoint list: each with its enabled state, double-click to reveal,
 * and a context menu to remove, remove all, or edit a condition.
 */
final class BreakpointListPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** What the panel asks the session to do. */
    interface Actions {
        /** @param url the url @param line the line reveal a breakpoint in the source */
        void reveal(String url, int line);
        /** @param url the url @param line the line remove a breakpoint */
        void remove(String url, int line);
        /** remove every breakpoint */
        void removeAll();
        /** @param url the url @param line the line @param on enable or disable a breakpoint */
        void setEnabled(String url, int line, boolean on);
        /** @param url the url @param line the line @param condition the condition, or null set a condition */
        void setCondition(String url, int line, String condition);
    }

    private final DefaultListModel<Breakpoint> model = new DefaultListModel<>();
    private final transient JList<Breakpoint> list = new JList<>(model);
    private final transient Actions actions;

    BreakpointListPanel(final Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                final int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                final Breakpoint bp = model.get(index);
                if (e.getClickCount() == 2) {
                    actions.reveal(bp.url(), bp.line());
                }
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                maybePopup(e);
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private void maybePopup(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        final int index = list.locationToIndex(e.getPoint());
        final JPopupMenu menu = new JPopupMenu();
        if (index >= 0) {
            list.setSelectedIndex(index);
            final Breakpoint bp = model.get(index);
            final JMenuItem toggle = new JMenuItem(bp.enabled() ? "Disable" : "Enable");
            toggle.addActionListener(a -> actions.setEnabled(bp.url(), bp.line(), !bp.enabled()));
            menu.add(toggle);
            final JMenuItem condition = new JMenuItem("Edit condition…");
            condition.addActionListener(a -> {
                final String current = bp.condition() == null ? "" : bp.condition();
                final String entered = JOptionPane.showInputDialog(this, "Pause only when this expression is truthy:", current);
                if (entered != null) {
                    actions.setCondition(bp.url(), bp.line(), entered.isEmpty() ? null : entered);
                }
            });
            menu.add(condition);
            final JMenuItem remove = new JMenuItem("Remove");
            remove.addActionListener(a -> actions.remove(bp.url(), bp.line()));
            menu.add(remove);
            menu.addSeparator();
        }
        final JMenuItem removeAll = new JMenuItem("Remove all");
        removeAll.addActionListener(a -> actions.removeAll());
        removeAll.setEnabled(!model.isEmpty());
        menu.add(removeAll);
        menu.show(list, e.getX(), e.getY());
    }

    /** Shows the current breakpoints. */
    void setBreakpoints(final List<Breakpoint> breakpoints) {
        model.clear();
        for (final Breakpoint bp : breakpoints) {
            model.addElement(bp);
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Breakpoint bp) {
                setText((bp.enabled() ? "● " : "○ ") + bp.label());
                setEnabled(bp.enabled());
            }
            return this;
        }
    }
}
