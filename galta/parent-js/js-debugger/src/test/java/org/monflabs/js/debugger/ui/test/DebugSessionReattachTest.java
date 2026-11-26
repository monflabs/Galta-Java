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
package org.monflabs.js.debugger.ui.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.monflabs.galtajs.cdp.CdpClientChannel;
import org.monflabs.js.debugger.ui.model.DebugSession;

import junit.framework.TestCase;

/**
 * The callbacks of a connection that run after the session attached to
 * another one: they are queued on the executor, and must not act on the
 * connection in use.
 */
public class DebugSessionReattachTest extends TestCase {

	private final Queue<Runnable> tasks = new ArrayDeque<>();
	private final DebugSession session = new DebugSession(tasks::add);

	/** A channel whose server answers every request with an empty result, or never. */
	private static final class Channel implements CdpClientChannel {
		private final boolean answers;
		ChannelListener listener;
		final List<String> sent = new ArrayList<>();

		Channel(boolean answers) {
			this.answers = answers;
		}

		@Override
		public void listen(ChannelListener l) {
			listener = l;
		}

		@Override
		public CompletionStage<?> sendText(String text) {
			sent.add(text);
			if (answers) {
				String id = text.replaceFirst(".*\"id\":\\s*(\\d+).*", "$1");
				listener.onText("{\"id\":" + id + ",\"result\":{}}", true);
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void requestClose() {
			// nothing to close
		}
	}

	private void pump() {
		for (Runnable task; (task = tasks.poll()) != null;) {
			task.run();
		}
	}

	// Debugger.enable of the first connection fails when it closes, after the second attached
	public void testFailureOfAPreviousCall() {
		session.attach(new Channel(false));
		session.detach();
		Channel second = new Channel(true);
		session.attach(second);
		pump();
		assertEquals(DebugSession.State.RUNNING, session.state());
		int before = second.sent.size();
		session.toggleBreakpoint("file:///a.js", 1);
		assertTrue("the new connection is in use", second.sent.size() > before);
	}

	public void testFailureOfAPreviousCallAfterADetach() {
		session.attach(new Channel(false));
		session.detach();
		pump();
		assertEquals(DebugSession.State.DETACHED, session.state());
	}

	public void testCloseOfThePreviousConnection() {
		session.attach(new Channel(true));
		pump();
		assertEquals(DebugSession.State.RUNNING, session.state());
		session.detach();
		Channel second = new Channel(true);
		session.attach(second);
		pump();
		assertEquals(DebugSession.State.RUNNING, session.state());
		int before = second.sent.size();
		session.toggleBreakpoint("file:///a.js", 1);
		assertTrue("the new connection is in use", second.sent.size() > before);
	}

	public void testCloseOfTheCurrentConnection() {
		Channel channel = new Channel(true);
		session.attach(channel);
		pump();
		channel.listener.onClosed("gone");
		pump();
		assertEquals(DebugSession.State.DETACHED, session.state());
	}
}
