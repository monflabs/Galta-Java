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
package tests;

import junit.framework.TestSuite;
import tests.debugger.DebuggerTest;
import org.monflabs.js.debugger.ui.DebuggerPanelTest;
import org.monflabs.js.debugger.ui.InProcessDebuggerPanelTest;
import org.monflabs.js.debugger.ui.panel.SourcePanelTest;
import org.monflabs.js.debugger.ui.panel.SourceViewTest;
import org.monflabs.js.debugger.ui.test.CdpConnectionTest;
import org.monflabs.js.debugger.ui.test.DebugSessionTest;
import org.monflabs.js.debugger.ui.test.InProcessCdpConnectionTest;
import org.monflabs.js.debugger.ui.test.InProcessDebugSessionTest;

public class AllDebuggerTests extends TestSuite {

	public static TestSuite suite() throws Exception {
		TestSuite suite = new TestSuite();

		suite.addTestSuite(DebuggerTest.class);
		suite.addTestSuite(DebuggerPanelTest.class);
		suite.addTestSuite(InProcessDebuggerPanelTest.class);
		suite.addTestSuite(SourcePanelTest.class);
		suite.addTestSuite(SourceViewTest.class);
		suite.addTestSuite(CdpConnectionTest.class);
		suite.addTestSuite(DebugSessionTest.class);
		suite.addTestSuite(InProcessCdpConnectionTest.class);
		suite.addTestSuite(InProcessDebugSessionTest.class);

		return suite;
	}

}
