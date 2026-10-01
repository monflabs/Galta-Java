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
package org.monflabs.util.generators;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class GeneratorScheduler {
	
	private static ExecutorService executor;

	/**
	 * Generator bodies run on virtual threads, which neither cost a platform thread nor
	 * keep the JVM alive. A generator abandoned without being closed still releases its
	 * body once it is garbage collected, or when this executor is shut down with
	 * {@code shutdownNow()} (see GeneratorImpl).
	 */
	public static ExecutorService createExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	/**
	 * An unbounded executor of daemon platform threads, for generator bodies that yield
	 * while holding a monitor ({@code synchronized}): on JDK 21 to 23 such a virtual
	 * thread pins its carrier thread for as long as it is parked, and the application
	 * deadlocks once every carrier is pinned (fixed in JDK 24 by JEP 491). Each parked
	 * body then holds a platform thread, so close the generators.
	 * Pass it to {@link GeneratorImpl#create(ExecutorService, java.util.function.Function)}.
	 */
	public static ExecutorService createPlatformExecutor() {
		return Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "generator");
			t.setDaemon(true);
			return t;
		});
	}

	public static synchronized ExecutorService getExecutorService() {
		if(executor==null) {
			executor = createExecutor();
		}
		return executor;
	}
}
