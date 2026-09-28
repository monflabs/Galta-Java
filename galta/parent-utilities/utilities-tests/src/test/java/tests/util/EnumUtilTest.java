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

import static org.junit.Assert.assertArrayEquals;

import org.monflabs.util.EnumUtil;

import tests.ProjectTestCase;

public class EnumUtilTest extends ProjectTestCase {
	
	public static enum EN {
		V1, V2, V3
	}
	
	public void testNames() {
		assertArrayEquals( new String[]{"V1","V2","V3"}, EnumUtil.names(EN.class) );
	}
}
