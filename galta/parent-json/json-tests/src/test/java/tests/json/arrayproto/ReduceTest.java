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
package tests.json.arrayproto;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.util.Reducers;

import tests.ProjectTestCase;

public class ReduceTest extends ProjectTestCase {

	private static final String JSON = 
"""
[10, 11, 12, 11, 13]
""";	
	

	JsonArray json;
	
	@Override
	@Before
    public void setUp() throws Exception {
		super.setUp();
		
		json = JsonArray.parse(JSON);
	}

	
	public void testReduceJsonValue() throws Exception {
		Integer r1 = json.reduce( (a,v) -> a.intValue()+JsonUtil.checkInt(v), 0 );
		assertEquals(57,r1.intValue());
	}

	public void testReduceGeneric() throws Exception {
		Integer r1 = json.reduce( (a,v) -> a.intValue()+JsonUtil.checkInt(v), 0 );
		assertEquals(57,r1.intValue());
	}
	
	public void testDefaultReducers() {
		Integer sumi = json.reduce(Reducers.sumInt());
		assertEquals(57,sumi.intValue());
		
		Long suml = json.reduce(Reducers.sumLong());
		assertEquals(57,suml.longValue());
		
		Double sumd = json.reduce(Reducers.sumDouble());
		assertEquals(57.0,sumd.doubleValue());

		BigInteger sumbi = json.reduce(Reducers.sumBigInteger());
		assertEquals(new BigInteger("57"),sumbi);

		BigDecimal sumbd = json.reduce(Reducers.sumBigDecimal());
		assertEquals(new BigDecimal("57"),sumbd);
	}
}
