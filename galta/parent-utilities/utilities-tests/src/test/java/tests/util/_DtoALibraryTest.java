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
package tests.util;

import org.monflabs.util.impl.ryu.RyuDouble;

import tests.ProjectTestCase;

public class _DtoALibraryTest extends ProjectTestCase {

//	public void testApacheHarmony() {
//		//assertEquals( "0", NumberConverter.convert(0.0) );
//		//assertEquals( "1", NumberConverter.convert(1.0) );
//		assertEquals( "1.1", NumberConverter.convert(1.1) );
//		assertEquals( "1.123456", NumberConverter.convert(1.123456) );
//		assertEquals( "120000000000000000000", NumberConverter.convert(1.2E20) );
//		assertEquals( "1.2e25", NumberConverter.convert(1.2E25) );
//	}

	public void testRyu() {
		assertEquals( "0.0", RyuDouble.doubleToString(0.0) );
		assertEquals( "1.0", RyuDouble.doubleToString(1.0) );
		assertEquals( "1.1", RyuDouble.doubleToString(1.1) );
		assertEquals( "1.123456", RyuDouble.doubleToString(1.123456) );
		assertEquals( "1.2e20", RyuDouble.doubleToString(1.2E20) );
		assertEquals( "1.2e25", RyuDouble.doubleToString(1.2E25) );
	}
	
}
