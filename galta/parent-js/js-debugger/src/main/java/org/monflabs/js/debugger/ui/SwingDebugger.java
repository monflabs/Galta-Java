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
package org.monflabs.js.debugger.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.swing.JFrame;

import org.monflabs.galtajs.cdp.inprocess.InProcessCdpServer;
import org.monflabs.galtajs.debug.api.DebugOptions;
import org.monflabs.galtajs.debug.api.impl.DebuggerImpl;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSGlobalContext;

/**
 * A standalone Swing window that visually steps through a script - the
 * programmatic (non-playground) equivalent of GaltaJSPlaygroundFrame's own
 * "Debugger" button: same in-process, socket-free CDP transport
 * ({@link InProcessCdpServer}), same {@link DebuggerPanel}, same "the script
 * starts paused at its first statement the moment the panel actually
 * connects" sequencing. Mirrors the retired
 * {@code org.monflabs.galtajs.debugger.JSDebugger}'s API shape (construct,
 * {@code setVisible(true)}, {@code debugScript(unit, contextFactory)}) so
 * every former call site of that class swaps in here with no behavioral
 * change.
 */
public class SwingDebugger extends JFrame {
	private static final long serialVersionUID = 1L;

	private final DebuggerPanel panel;

	public SwingDebugger() {
		super("GaltaJS Visual Debugger");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1200, 850);
		panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false);
		getContentPane().add(panel, BorderLayout.CENTER);
	}

	public void debugScript(final JSInterpretedUnit script, final Supplier<? extends JSGlobalContext> contextFactory) {
		final DebuggerImpl debugger = new DebuggerImpl(script, contextFactory);
		// no socket, no port: same in-process transport debugNow() uses
		final InProcessCdpServer.Handle server = InProcessCdpServer.open(debugger, DebugOptions.parse("", false));
		// pause at the very first statement, like a real debugger launch
		debugger.pauseOnStart();

		final AtomicBoolean started = new AtomicBoolean();
		// Started once the panel is connected, so it sees the script from its
		// first event. (Debugger.enable would also replay a pause that
		// happened before the client attached - see DebuggerDomain - but
		// starting afterwards keeps the panel's state simple.)
		panel.onConnectionChange((state, detail) -> {
			if(state == DebuggerPanel.ConnectionState.CONNECTED && started.compareAndSet(false, true)) {
				debugger.start();
			}
		});
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(final WindowEvent e) {
				panel.close();
				server.close();
				debugger.close();
			}
		});

		panel.attach(server.clientChannel());
	}
}
