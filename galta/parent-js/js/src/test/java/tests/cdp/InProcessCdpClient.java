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
package tests.cdp;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.monflabs.galtajs.cdp.CdpClientChannel;
import org.monflabs.galtajs.cdp.json.Json;

/**
 * A CDP client over a {@link CdpClientChannel} - the in-process counterpart
 * to {@link CdpClient}, same request/response/event semantics, no socket
 * anywhere. Used to prove {@code CdpSession}'s dispatch logic is 100%
 * transport-agnostic: {@link InProcessCdpProtocolTest} mirrors {@link
 * CdpProtocolTest}'s own test bodies against this instead.
 */
final class InProcessCdpClient implements AutoCloseable {
	static final long TIMEOUT = 20;

	private final CdpClientChannel channel;
	private final BlockingQueue<Map<String, Object>> responses = new LinkedBlockingQueue<>();
	private final BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
	private long nextId = 1;

	@SuppressWarnings("unchecked")
	InProcessCdpClient(final CdpClientChannel channel) {
		this.channel = channel;
		channel.listen(new CdpClientChannel.ChannelListener() {
			@Override
			public void onText(final CharSequence data, final boolean last) {
				final Map<String, Object> message = (Map<String, Object>) Json.parse(data.toString());
				if (message.containsKey("id")) {
					responses.add(message);
				} else {
					events.add(message);
				}
			}

			@Override
			public void onClosed(final String reason) {
				// nothing to do - tests observe the close through the debugger/thread instead
			}
		});
	}

	/** Sends a request and returns its result, or throws with the error's message. */
	Map<String, Object> call(final String method, final Object... params) throws Exception {
		final long id = nextId++;
		channel.sendText(Json.write(Json.object("id", id, "method", method, "params", Json.object(params)))).toCompletableFuture().get(TIMEOUT, TimeUnit.SECONDS);
		while (true) {
			final Map<String, Object> response = responses.poll(TIMEOUT, TimeUnit.SECONDS);
			if (response == null) {
				throw new AssertionError("no response to " + method + " within " + TIMEOUT + "s");
			}
			if (((Number) response.get("id")).longValue() != id) {
				continue;
			}
			if (response.containsKey("error")) {
				@SuppressWarnings("unchecked")
				final Map<String, Object> error = (Map<String, Object>) response.get("error");
				throw new CdpClient.CdpFailure(((Number) error.get("code")).intValue(), String.valueOf(error.get("message")));
			}
			@SuppressWarnings("unchecked")
			final Map<String, Object> result = (Map<String, Object>) response.get("result");
			return result;
		}
	}

	/** The next event of a given method, skipping others. */
	Map<String, Object> event(final String method) throws Exception {
		while (true) {
			final Map<String, Object> event = events.poll(TIMEOUT, TimeUnit.SECONDS);
			if (event == null) {
				throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
			}
			if (method.equals(event.get("method"))) {
				@SuppressWarnings("unchecked")
				final Map<String, Object> params = (Map<String, Object>) event.get("params");
				return params;
			}
		}
	}

	void sendRaw(final String text) throws Exception {
		channel.sendText(text).toCompletableFuture().get(TIMEOUT, TimeUnit.SECONDS);
	}

	Map<String, Object> rawResponse() throws Exception {
		return responses.poll(TIMEOUT, TimeUnit.SECONDS);
	}

	@Override
	public void close() {
		channel.requestClose();
	}
}
