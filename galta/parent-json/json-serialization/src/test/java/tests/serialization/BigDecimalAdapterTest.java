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
package tests.serialization;

import java.math.BigDecimal;

import org.monflabs.json.serialization.SimpleRegistry;

import tests.ProjectTestCase;

/**
 * BigDecimal adapter must recognise BigDecimal JSON values (A8).
 */
public class BigDecimalAdapterTest extends ProjectTestCase {

	public void testBigDecimalPassThrough() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder().build();
		BigDecimal d = new BigDecimal("123456789012345678901234567890.123456789");
		Object v = reg.deserialize(BigDecimal.class, d);
		assertSame(d, v);
		assertEquals(BigDecimal.valueOf(1.5), reg.deserialize(BigDecimal.class, Double.valueOf(1.5)));
		assertEquals(new BigDecimal(7), reg.deserialize(BigDecimal.class, Integer.valueOf(7)));
	}
}
