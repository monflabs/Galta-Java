package org.monflabs.galtajs.test.test262;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.standard.StandardLibrary;
import org.monflabs.galtajs.rt.builtins.standard.regexp.joni.RegExpEngineJoni;


/**
 * A {@link JSEnvironment} configured for the TC39 test262 suite.
 *
 * <ul>
 *   <li>GaltaJS extensions are disabled so tests run in pure ECMAScript mode.</li>
 *   <li>Strict mode is enabled via {@link StandardLibrary}.</li>
 *   <li>{@link Test262TestLibrary} exposes the {@code print()} function and the
 *       {@code $262} host object required by the test262 harness.</li>
 * </ul>
 */
public class GlobalTest262Environment {
	
	public static JSEnvironment.Builder newBuilder() {
		return newBuilder(new Test262AgentManager());
	}

	// Used for a `$262.agent.start(...)`-spawned agent's own environment,
	// which must share the SAME Test262AgentManager as the spawning
	// environment (main test-file thread, or another agent) for
	// broadcast/report to actually cross the real Java thread boundary -
	// see Test262AgentManager's own doc comment.
	public static JSEnvironment.Builder newBuilder(Test262AgentManager sharedAgentManager) {
		return JSEnvironment.newBuilder()
				// Float16Array/DataView.prototype.get|setFloat16 is a shipped
				// ES2025 feature, not a proposal - test262 expects it present.
				.supportFloat16Array(true)
				// Use Joni for the best compatibility
				.regexpEngineFactory(RegExpEngineJoni.factory())
				.registerLibrary(new StandardLibrary())
				.registerLibrary(new Test262TestLibrary(sharedAgentManager));
	}

	public static JSEnvironment create() {
		return newBuilder().build();
	}

	private GlobalTest262Environment() {}
}
