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
package tests.javascript.builtin.performance;

import tests.javascript.JavaScriptStrictTestCase;

/**
 * Micro benchmark tests.
 * 
 * This is for info only, actual results in code will be very different.
 * 
 * @author Philippe Riand
 */
public class VariousPerfTest extends JavaScriptStrictTestCase {

	public void testDummy() throws Exception {
	}

//	public void testJavaChecking() throws Exception {
//		Boolean b = true;
//
//		final int LOOP = 1_000_000_000;
//		final int MUL = 10;
//		
//		for(int l=0; l<8; l++) {
//			long s1 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				for(int m=0; m<MUL; m++) {
//					f(b instanceof Boolean);
//				}
//			}
//			long e1 = System.currentTimeMillis();
//			GlobalObject.log("Instance="+(e1-s1));
//
//			long s2 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				for(int m=0; m<MUL; m++) {
//					f(b.getClass()==Boolean.class);
//				}
//			}
//			long e2 = System.currentTimeMillis();
//			GlobalObject.log("getClass()="+(e2-s2));
//		}
//
//	}
//	private static void f(boolean b) {
//		 b = !b;
//	}
//
//	/*
//	 * Test the performance of instanceof vs a map.
//	 */
//	public void testTypeChecking() throws Exception {
//		final int LOOP = 100_000_000;
//		
//		for(int l=0; l<3; l++) {
//			Number n1 = Integer.valueOf(1);
//			Number n2 = Double.valueOf(2.0);
//			Number n3 = Float.valueOf(2.0f);
//			
//			long s1 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				numberType1(n1);
//				numberType1(n2);
//				numberType1(n3);
//			}
//			long e1 = System.currentTimeMillis();
//			GlobalObject.log("1="+(e1-s1));
//	
//			long s2 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				numberType2(n1);
//				numberType2(n2);
//				numberType2(n3);
//			}
//			long e2 = System.currentTimeMillis();
//			GlobalObject.log("2="+(e2-s2));
//		}
//	}
//	
//	public static JsonNumberType numberType1(Number o) {
//		if(o instanceof Integer) {
//			return NUMBER_INTEGER;
//		}
//		if(o instanceof Double d) {
//			if(d.isNaN()) {
//				return NUMBER_NAN;
//			}
//			return NUMBER_DOUBLE;
//		}
//		if(o instanceof Long) {
//			return NUMBER_LONG;
//		}
//		if(o instanceof BigDecimal) {
//			return NUMBER_BIGDECIMAL;
//		}
//		if(o instanceof BigInteger) {
//			return NUMBER_BIGINTEGER;
//		}
//		if(o instanceof Short) {
//			return NUMBER_SHORT;
//		}
//		if(o instanceof Byte) {
//			return NUMBER_BYTE;
//		}
//		if(o instanceof Float f) {
//			if(f.isNaN()) {
//				return NUMBER_NAN;
//			}
//			return NUMBER_FLOAT;
//		}
//		throw new JsonException(null,"Unsupported JSON number type {0}", o.getClass());
//	}
//	
//	private static Map<Class<?>,JsonNumberType> allTypes = Map.of(
//				Integer.class, NUMBER_INTEGER,
//				Double.class, NUMBER_DOUBLE,
//				Long.class, NUMBER_LONG,
//				BigDecimal.class, NUMBER_BIGDECIMAL,
//				BigInteger.class, NUMBER_BIGINTEGER,
//				Short.class, NUMBER_SHORT,
//				Byte.class, NUMBER_BYTE,
//				Float.class, NUMBER_FLOAT
//			);
//	public static JsonNumberType numberType2(Number o) {
//		JsonNumberType t = allTypes.get(o.getClass());
//		if(t!=null) {
//			return t;
//		}
//		throw new JsonException(null,"Unsupported JSON number type {0}", o.getClass());
//	}
//
//	
//	/*
//	 * Test the performance of enum switch.
//	 */
//	
//	static enum ENUM {
//		E1, E2, E3, E4, E5
//	}
//	static final int E1 = 1;
//	static final int E2 = 2;
//	static final int E3 = 3;
//	static final int E4 = 4;
//	static final int E5 = 5;
//	
//	public void testEnumSwitch() throws Exception {
//		final int LOOP = 1_000_000_000;
//
//		for(int l=0; l<3; l++) {
//			@SuppressWarnings("unused")
//			int v = 0;
//	
//			long s2 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				switch(i%5) {
//					case E1-> v+=1;	
//					case E2-> v+=2;	
//					case E3-> v+=3;	
//					case E4-> v+=4;	
//					case E5-> v+=5;	
//				}
//			}
//			long e2 = System.currentTimeMillis();
//			GlobalObject.log("Int switch="+(e2-s2));
//	
//			ENUM es = ENUM.E3;
//			long s1 = System.currentTimeMillis();
//			for(int i=0; i<LOOP; i++) {
//				switch(es) {
//					case E1-> v+=1;	
//					case E2-> v+=2;	
//					case E3-> v+=3;	
//					case E4-> v+=4;	
//					case E5-> v+=5;	
//				}
//			}
//			long e1 = System.currentTimeMillis();
//			GlobalObject.log("Enum switch="+(e1-s1));
//		}
//	}

}
