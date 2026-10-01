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
package org.monflabs.tests.leaks;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit 4 Rule that detects resource leaks in tests.
 *
 * Usage:
 * <pre>
 * public class MyTest {
 *     &#64;Rule
 *     public ResourceLeakRule leakDetector = new ResourceLeakRule();
 *
 *     &#64;Test
 *     public void testSomething() {
 *         // Your test code
 *     }
 * }
 * </pre>
 */
public class ResourceLeakRule implements TestRule {
    private static volatile boolean agentInstalled = false;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Install agent once per JVM
                installAgentIfNeeded();

                // Start tracking before test: only the resources allocated by the test thread,
                // so the resources opened concurrently by other threads are not reported
                ResourceTracker.getInstance().startTracking(true);

                Throwable testException = null;
                try {
                    // Run the test
                    base.evaluate();
                } catch (Throwable t) {
                    testException = t;
                } finally {
                    // Check for leaks after test
                    try {
                        checkForLeaks();
                    } catch (AssertionError leakError) {
                        // If test already failed, add leak info as suppressed exception
                        if (testException != null) {
                            testException.addSuppressed(leakError);
                            throw testException;
                        } else {
                            // Test passed but leaked resources
                            throw leakError;
                        }
                    } finally {
                        // Always stop tracking
                        ResourceTracker.getInstance().stopTracking();
                    }

                    // Re-throw original test exception if any
                    if (testException != null) {
                        throw testException;
                    }
                }
            }
        };
    }

    private synchronized void installAgentIfNeeded() {
        if (!agentInstalled) {
            ResourceLeakAgent.install();
            agentInstalled = true;
        }
    }

    private void checkForLeaks() {
        // Give GC a chance to clean up - should we??
        //System.gc();
        //System.runFinalization();

        // Small delay to let GC do its work
        //try {
        //    Thread.sleep(100);
        //} catch (InterruptedException e) {
        //    Thread.currentThread().interrupt();
        //}

        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
        // Same filter as __BaseTestCase
        leaks.removeIf(l -> !ResourceTracker.shouldReport(l));
        if (!leaks.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("\n==================================================\n");
            message.append("    RESOURCE LEAK DETECTION FAILED!\n");
            message.append("==================================================\n");
            message.append("Detected ").append(leaks.size()).append(" leaked resource(s):\n\n");

            for (int i = 0; i < leaks.size(); i++) {
                ResourceTracker.LeakInfo leak = leaks.get(i);
                message.append("Leak #").append(i + 1).append(":\n");
                message.append(leak.formatReport());
                message.append("\n");
            }

            message.append("==================================================\n");
            fail(message.toString());
        }
    }
}
