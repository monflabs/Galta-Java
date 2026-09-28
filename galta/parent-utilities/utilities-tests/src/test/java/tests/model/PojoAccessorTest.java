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
package tests.model;


import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.monflabs.util.model.ModelAccessor;
import org.monflabs.util.model.ModelException;
import org.monflabs.util.model.PojoAccessor;

import tests.ProjectTestCase;

public class PojoAccessorTest extends ProjectTestCase {
	
	public static class C1 {
		public byte valueByte = 10;
		public short valueShort = 20;
		public int valueInt = 30;
		public long valueLong = 40;
		public float valueFloat = 50.23f;
		public double valueDouble = 69.17;
		public BigInteger valueBigInteger = BigInteger.TWO;
		public BigDecimal valueBigDecimal = BigDecimal.TEN;
		
		public String valueString = "xyz";
		
		public int propInt = 45;
		public int getPropInt() {
			return 55;
		}
		public int getPropInt2() {
			return 65;
		}

		
		public int _int3 = 45;
		public int getPropInt3() {
			return _int3;
		}
		public void setPropInt3(int value) {
			this._int3 = value;
		}
		public int getPropInt4() {
			return _int3;
		}
		
		public Map<String,String> map = Map.of("p1", "val1");
		
		
		public int m1() {
			return 22;
		}
		public int m1(int a) {
			return 10+a;
		}
		public int m1(double a) {
			return 10+((int)a)*2;
		}
	}
	
	public static class C3 {
		// A field and a method with the same name: the field must not hide the method for call()
		public int foo = 1;
		public int foo() {
			return 2;
		}
		public long twice(long v) {
			return v*2;
		}
	}

	public static class C2 extends C1 {
		public int valueInt = 66;
		
		@Override
		public int getPropInt() {
			return 25;
		}
	}

	public void testReadMember() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		
		C1 c1 = new C1();
		
		assertEquals( (byte)10, a.getMember(c1, "valueByte"));
		assertEquals( (short)20, a.getMember(c1, "valueShort"));
		assertEquals( (int)30, a.getMember(c1, "valueInt"));
		assertEquals( (long)40, a.getMember(c1, "valueLong"));
		assertEquals( 50.23f, a.getMember(c1, "valueFloat"));
		assertEquals( 69.17, a.getMember(c1, "valueDouble"));
		assertEquals( BigInteger.TWO, a.getMember(c1, "valueBigInteger"));
		assertEquals( BigDecimal.TEN, a.getMember(c1, "valueBigDecimal"));
		assertEquals( "xyz", a.getMember(c1, "valueString"));
		assertEquals( ModelAccessor.UNHANDLED, a.getMember(c1, "valueFake"));
		
		assertEquals( 55, a.getMember(c1, "propInt"));
		assertEquals( 65, a.getMember(c1, "propInt2"));

		assertEquals( "val1", a.getMember(c1.map, "p1"));
		assertEquals( ModelAccessor.UNHANDLED, a.getMember(c1.map, "p2"));
		
		C2 c2 = new C2();
		
		assertEquals( (byte)10, a.getMember(c2, "valueByte"));
		assertEquals( 66, a.getMember(c2, "valueInt"));
		assertEquals( 25, a.getMember(c2, "propInt"));
	}	

	public void testWriteMember() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		
		C1 c1 = new C1();

		a.putMember(c1, "valueByte", 96.36);
		a.putMember(c1, "valueShort", 96.36);
		a.putMember(c1, "valueInt", 96.36);
		a.putMember(c1, "valueLong", 96.36);
		a.putMember(c1, "valueFloat", 96.36);
		a.putMember(c1, "valueDouble", 96.36);
		a.putMember(c1, "valueBigInteger", 96.36);
		a.putMember(c1, "valueBigDecimal", 96.36);
		a.putMember(c1, "valueString", "96.36");

		assertEquals( (byte)96, a.getMember(c1, "valueByte"));
		assertEquals( (short)96, a.getMember(c1, "valueShort"));
		assertEquals( (int)96, a.getMember(c1, "valueInt"));
		assertEquals( (long)96, a.getMember(c1, "valueLong"));
		assertEquals( 96.36f, a.getMember(c1, "valueFloat"));
		assertEquals( 96.36, a.getMember(c1, "valueDouble"));
		assertEquals( new BigInteger("96"), a.getMember(c1, "valueBigInteger"));
		assertEquals( new BigDecimal("96.36"), a.getMember(c1, "valueBigDecimal"));
		assertEquals( "96.36", a.getMember(c1, "valueString"));
		assertEquals( ModelAccessor.UNHANDLED, a.getMember(c1, "valueFake"));
		
		assertEquals( 45, a.getMember(c1, "propInt3"));
		a.putMember(c1, "propInt3", 88);
		assertEquals( 88, a.getMember(c1, "propInt3"));
		assertThrows( ModelException.class, () -> a.putMember(c1, "propInt4", 88) );
	}	

	public void testCallMember() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		
		C1 c1 = new C1();
		assertEquals( 22, a.call(c1, "m1", new Object[] {}));
		assertEquals( 14, a.call(c1, "m1", new Object[] {4}));
		assertEquals( 18, a.call(c1, "m1", new Object[] {4.0}));
	}

	public void testCallMemberHiddenByField() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		C3 c3 = new C3();
		assertEquals( 2, a.call(c3, "foo", new Object[] {}));
		// Accepted with a conversion (Integer -> long): the argument must be converted before invoke()
		assertEquals( 8L, a.call(c3, "twice", new Object[] {4}));
	}

	public void testWriteArrayOutOfRange() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		int[] arr = new int[] {1,2,3};
		assertTrue( a.putMember(arr, 1, 9) );
		assertEquals( 9, arr[1] );
		assertFalse( a.putMember(arr, 5, 9) );
	}

	public void testConstructUnknownClass() {
		PojoAccessor a = PojoAccessor.getDataAccessor();
		// Used to throw a NullPointerException (and a second one while building the message)
		assertSame(ModelAccessor.UNHANDLED, a.constructObject("does.not.Exist", null));
		assertSame(ModelAccessor.UNHANDLED, a.constructArray("does.not.Exist", 2));
	}

	public static class C4 {
		public final int a;
		public final long b;
		public C4(int a, long b) {
			this.a = a;
			this.b = b;
		}
	}

	public void testConstructWithConversions() {
		// The constructor was selected with conversions but invoked with the raw arguments
		// (IllegalArgumentException, so UNHANDLED)
		PojoAccessor a = new PojoAccessor();
		C4 c = (C4)a.constructObject(C4.class.getName(), new Object[] {1.9, 2});
		assertEquals(1, c.a);
		assertEquals(2L, c.b);
	}

	public static class C5 {
		public int getBroken() {
			throw new IllegalStateException("getter failed");
		}
	}

	public void testGetterExceptionIsTheCause() {
		// The getter's exception used to be dropped (the ModelException had no cause)
		ModelException e = assertThrows(ModelException.class, () -> new PojoAccessor().getMember(new C5(), "broken"));
		assertTrue(e.getCause() instanceof java.lang.reflect.InvocationTargetException);
		assertEquals("getter failed", e.getCause().getCause().getMessage());
	}
}
