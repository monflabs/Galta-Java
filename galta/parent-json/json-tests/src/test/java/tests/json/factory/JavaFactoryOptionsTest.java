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
package tests.json.factory;

import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonFactory.DECIMAL;
import org.monflabs.json.JsonFactory.INTEGER;
import org.monflabs.json.java.JavaJsonFactory;

import tests.ProjectTestCase;

public class JavaFactoryOptionsTest extends ProjectTestCase {
	
	private static class ExtendedJavaFactory extends JavaJsonFactory {
		INTEGER defaultInteger = INTEGER.INT;
		DECIMAL defaultDecimal = DECIMAL.DOUBLE;
		
		@Override
		public INTEGER defaultInteger() { return defaultInteger; }
		@Override
		public DECIMAL defaultDecimal() { return defaultDecimal; }
	}

	public void testParse() throws Exception {
		// Only when the main factory is Java, so we don;t repeat the tests...
		if(JsonFactory.get()==JavaJsonFactory.instance) {
			runJavaFactory();
		}
	}
	
	private void runJavaFactory() {
		ExtendedJavaFactory f = new ExtendedJavaFactory();
		
		assertEquals( 12, f.parseInteger("12") );
		assertEquals( 12, f.parseInteger("+12") );
		assertEquals( -12, f.parseInteger("-12") );
		assertEquals( 18, f.parseInteger("12",16) );
		assertThrows( JsonException.class, () -> f.parseInteger(null) );
		assertThrows( JsonException.class, () -> f.parseInteger("",JsonFactory.PARSEINT_IGNOREEXTRACHAR) );
		
		assertEquals( Integer.MAX_VALUE, f.parseInteger( Long.toString( Integer.MAX_VALUE) ));
		assertEquals( ((long)Integer.MAX_VALUE)+1L, f.parseInteger( Long.toString( ((long)Integer.MAX_VALUE)+1L ) ));

		assertEquals( 1234, f.parseInteger( new BigInteger("1234").toString() ));
		assertEquals( Long.MAX_VALUE, f.parseInteger( new BigInteger(Long.toString(Long.MAX_VALUE)).toString() ));
		assertEquals( new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.ONE), f.parseInteger( new BigInteger(Long.toString(Long.MAX_VALUE)).add(BigInteger.ONE).toString() ));
		
		assertEquals( 12.4, f.parseDecimal("12.4") );
		assertEquals( 12.4, f.parseDecimal("+12.4") );
		assertEquals( -12.4, f.parseDecimal("-12.4") );
		assertThrows( JsonException.class, () -> f.parseDecimal(null) );
		assertThrows( JsonException.class, () -> f.parseDecimal("") );
		
		assertEquals( 12, f.parseNumber("12",0) );
		assertEquals( 12, f.parseNumber("+12",0) );
		assertEquals( -12, f.parseNumber("-12",0) );
		assertEquals( 12.4, f.parseNumber("12.4",0) );
		assertThrows( JsonException.class, () -> f.parseNumber(null,0) );
		assertThrows( JsonException.class, () -> f.parseNumber("",0) );

		f.defaultInteger = INTEGER.LONG; 
		assertEquals( 12L, f.parseInteger("12") );
		assertEquals( 18L, f.parseInteger("12",16) );

		f.defaultInteger = INTEGER.BIGINT; 
		assertEquals( new BigInteger("12"), f.parseInteger("12") );
		assertEquals( new BigInteger("18"), f.parseInteger("12",16) );

		f.defaultDecimal = DECIMAL.BIGDEC; 
		assertEquals( new BigDecimal("12.0"), f.parseDecimal("12.0") );
	}
}
