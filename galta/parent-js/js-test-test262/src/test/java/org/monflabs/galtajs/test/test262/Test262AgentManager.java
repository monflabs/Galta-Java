package org.monflabs.galtajs.test.test262;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Shared state for one test262 file's `$262.agent` - the MAIN thread (the
 * one running the test file itself) and every thread spawned via
 * `$262.agent.start(...)` hold a reference to the SAME instance, so
 * broadcast/report communication crosses the real Java thread boundary.
 *
 * Scoped per-test-file (a fresh instance per {@code Test262TestLibrary}, in
 * turn per {@code JSEnvironment} - see {@code BaseTestSuiteTest.execFile()},
 * which builds a brand-new environment for every file) - never shared
 * ACROSS different test262 files, so state from one file's agents can never
 * leak into another's.
 */
public class Test262AgentManager {

	// Only ONE broadcast is ever meaningfully in flight for the test262
	// usage pattern this supports (a single `$262.agent.safeBroadcast(sab)`
	// call per test file) - a sentinel distinguishes "no broadcast yet" from
	// a genuine `null`/`undefined` broadcast value.
	private static final Object NO_BROADCAST = new Object();
	private final Object broadcastLock = new Object();
	private volatile Object lastBroadcast = NO_BROADCAST;

	private final ConcurrentLinkedQueue<String> reports = new ConcurrentLinkedQueue<>();

	public void broadcast(Object sab) {
		synchronized(broadcastLock) {
			lastBroadcast = sab;
			broadcastLock.notifyAll();
		}
	}

	// Blocks the CALLING (real Java) thread until a broadcast has happened -
	// immediately if one already has by the time this is called.
	public Object receiveBroadcast() {
		synchronized(broadcastLock) {
			while(lastBroadcast==NO_BROADCAST) {
				try {
					broadcastLock.wait();
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
			return lastBroadcast;
		}
	}

	public void report(String s) {
		reports.add(s);
	}

	// Non-blocking - returns null if nothing has been reported yet. Per
	// INTERPRETING.md this is the RAW host primitive; test262's own
	// atomicsHelper.js layers a `sleep(1)`-polling loop on top of it.
	public String getReport() {
		return reports.poll();
	}
}
