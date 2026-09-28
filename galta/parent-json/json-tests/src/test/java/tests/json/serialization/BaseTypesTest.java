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
package tests.json.serialization;

import static org.junit.Assert.assertArrayEquals;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.monflabs.json.JsonObject;
import org.monflabs.json.serialization.SimpleRegistry;
import org.monflabs.json.serialization.classes.SimpleClassAdapter;
import org.monflabs.util.Console;

import tests.ProjectTestCase;

public class BaseTypesTest extends ProjectTestCase {
	
	public static class MyClass {
		
		public boolean pBoolean;
		public byte pByte;
		public short pShort;
		public int pInt;
		public long pLong;
		public float pFloat;
		public double pDouble;
		public BigInteger pBigInteger;
		public BigDecimal pBigDecimal;
		public String pString;
		
		public boolean[] pBooleanArray;
		public byte[] pByteArray;
		public short[] pShortArray;
		public int[] pIntArray;
		public long[] pLongArray;
		public float[] pFloatArray;
		public double[] pDoubleArray;
		public BigInteger[] pBigIntegerArray;
		public BigDecimal[] pBigDecimalArray;
		public String[] pStringArray;
		
		public Boolean oBoolean;
		public Byte oByte;
		public Short oShort;
		public Integer oInt;
		public Long oLong;
		public Float oFloat;
		public Double oDouble;
		public BigInteger oBigInteger;
		public BigDecimal oBigDecimal;
		public String oString;
		
		public Byte[] oByteArray;
		public Short[] oShortArray;
		public Integer[] oIntegerArray;
		public Long[] oLongArray;
		public Float[] oFloatArray;
		public Double[] oDoubleArray;
		
		// What is the serialization ctor that avoids all initialization??
		public MyClass() {
		}
		public MyClass(boolean init) {
			if(init) {
				this.pBoolean = true;
				this.pByte = 71;
				this.pShort = 72;
				this.pInt = 79;
				this.pLong = 82L;
				this.pFloat = 83.2f;
				this.pDouble = 84.3;
				this.pBigInteger = new BigInteger("1234567890123456789012345");
				this.pBigDecimal = new BigDecimal("1234567890123456789012345.6789");
				this.pString = "abcABC";
	
				this.pBooleanArray = new boolean[] {true,false,true};
				this.pByteArray = new byte[] {1,2,3};
				this.pShortArray = new short[] {4,5,6};
				this.pIntArray = new int[] {7,8,9};
				this.pLongArray = new long[] {10,11,12};
				this.pFloatArray = new float[] {13.1f,14.2f,15.3f};
				this.pDoubleArray = new double[] {16.1,16.2,16.3};
				this.pBigIntegerArray = new BigInteger[] {new BigInteger("1234"), new BigInteger("456")};
				this.pBigDecimalArray = new BigDecimal[] {new BigDecimal("789"), new BigDecimal("456.789")};
				this.pStringArray = new String[] {"abc","def",""};
	
				this.oBoolean = true;
				this.oByte = 51;
				this.oShort = 52;
				this.oInt = 59;
				this.oLong = 62L;
				this.oFloat = 63.2f;
				this.oDouble = 64.3;
				this.oBigInteger = new BigInteger("991234567890123456789012345");
				this.oBigDecimal = new BigDecimal("991234567890123456789012345.6789");
				this.oString = "defDEF";
	
				this.oByteArray = new Byte[] {1,2};
				this.oShortArray = new Short[] {3,4};
				this.oIntegerArray = new Integer[] {5,6};
				this.oLongArray = new Long[] {7L,8L};
				this.oFloatArray = new Float[] {9.1f,9.2f};
				this.oDoubleArray = new Double[] {10.34,11.5};
			}
		}
	}
	
	public void testReflection() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(SimpleClassAdapter.newBuilder(MyClass.class)
						.reflection()
						.build())
				.build();
		
		MyClass a = new MyClass(true);
		checkObject(a);
		
		JsonObject json = reg.serialize(a);
		Console.log(json.toString());
		
		MyClass a2 = reg.deserialize( MyClass.class, json);
		checkObject(a2);
	}
	private void checkObject(MyClass o) {
		assertEquals(true, o.pBoolean);
		assertEquals(71, o.pByte);
		assertEquals(72, o.pShort);
		assertEquals(79, o.pInt);
		assertEquals(82L, o.pLong);
		assertEquals(83.2f, o.pFloat);
		assertEquals(84.3, o.pDouble);
		assertEquals(new BigInteger("1234567890123456789012345"), o.pBigInteger);
		assertEquals(new BigDecimal("1234567890123456789012345.6789"), o.pBigDecimal);
		assertEquals("abcABC", o.pString);

		assertArrayEquals(new boolean[] {true,false,true}, o.pBooleanArray);
		assertArrayEquals(new byte[] {1,2,3}, o.pByteArray);
		assertArrayEquals(new short[] {4,5,6}, o.pShortArray);
		assertArrayEquals(new int[] {7,8,9}, o.pIntArray);
		assertArrayEquals(new long[] {10,11,12}, o.pLongArray);
		assertArrayEquals(new float[] {13.1f,14.2f,15.3f}, o.pFloatArray, 0);
		assertArrayEquals(new double[] {16.1,16.2,16.3}, o.pDoubleArray, 0);
		assertArrayEquals(new BigInteger[] {new BigInteger("1234"), new BigInteger("456")}, o.pBigIntegerArray);
		assertArrayEquals(new BigDecimal[] {new BigDecimal("789"), new BigDecimal("456.789")}, o.pBigDecimalArray);
		assertArrayEquals(new String[] {"abc","def",""}, o.pStringArray);
		
		assertEquals(Boolean.valueOf(true), o.oBoolean);
		assertEquals(Byte.valueOf((byte)51), o.oByte);
		assertEquals(Short.valueOf((short)52), o.oShort);
		assertEquals(Integer.valueOf(59), o.oInt);
		assertEquals(Long.valueOf(62L), o.oLong);
		assertEquals(Float.valueOf(63.2f), o.oFloat);
		assertEquals(Double.valueOf(64.3), o.oDouble);
		assertEquals(new BigInteger("991234567890123456789012345"), o.oBigInteger);
		assertEquals(new BigDecimal("991234567890123456789012345.6789"), o.oBigDecimal);
		assertEquals("defDEF", o.oString);

		assertArrayEquals(new Byte[] {1,2}, o.oByteArray);
		assertArrayEquals(new Short[] {3,4}, o.oShortArray);
		assertArrayEquals(new Integer[] {5,6}, o.oIntegerArray);
		assertArrayEquals(new Long[] {7L,8L}, o.oLongArray);
		assertArrayEquals(new Float[] {9.1f,9.2f}, o.oFloatArray);
		assertArrayEquals(new Double[] {10.34,11.5}, o.oDoubleArray);
	}

	public void testCustomFieldAdapter() throws Exception {
		SimpleRegistry reg = SimpleRegistry.newBuilder()
				.add(SimpleClassAdapter.newBuilder(MyClass.class)
						.add("aInt", (o) -> o.pInt, (o,v) -> o.pInt = v.intValue())
						.build())
				.build();
		
		MyClass a = new MyClass(true);
		
		JsonObject json = reg.serialize(a);
		Console.log(json.toString());
		
		MyClass a2 = reg.deserialize( MyClass.class, json);
		Console.log(a2.toString());
	}
}
