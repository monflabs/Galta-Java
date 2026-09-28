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
package tests.tests;

import org.monflabs.tests.JavaAccessor;
import org.monflabs.tests.__BaseTestCase;

public class AccessorTest extends __BaseTestCase {

	
	//
	// Fields and method accessors
	//

	static class O1 {}
	static class O2 {}
	static class O3 {}
	static class O4 {}
	static class O5 {}
	static class O6 {}
	static class O7 {}
	static class O8 {}
	
	static class CA {
		@SuppressWarnings("unused")
		private boolean a_boolean1 = true;
		@SuppressWarnings("unused")
		private char a_char1 = '1';
		@SuppressWarnings("unused")
		private byte a_byte1 = (byte)11;
		@SuppressWarnings("unused")
		private short a_short1 = (short)56;
		@SuppressWarnings("unused")
		private int a_int1 = 78;
		@SuppressWarnings("unused")
		private long a_long1 = 145L;
		@SuppressWarnings("unused")
		private float a_float1 = 34.79f;
		@SuppressWarnings("unused")
		private double a_double1 = 79.34;
		@SuppressWarnings("unused")
		private Object a_object1 = new O1();

		public boolean a_boolean2 = true;
		public char a_char2 = '2';
		public byte a_byte2 = (byte)12;
		public short a_short2 = (short)562;
		public int a_int2 = 782;
		public long a_long2 = 1452L;
		public float a_float2 = 34.792f;
		public double a_double2 = 79.342;
		public Object a_object2 = new O2();

		@SuppressWarnings("unused")
		private boolean a_boolean3() { return true; }
		@SuppressWarnings("unused")
		private char a_char3() { return '3'; }
		@SuppressWarnings("unused")
		private byte a_byte3() { return (byte)13; }
		@SuppressWarnings("unused")
		private short a_short3() { return (short)563; }
		@SuppressWarnings("unused")
		private int a_int3() { return 783; }
		@SuppressWarnings("unused")
		private long a_long3() { return 1453L; }
		@SuppressWarnings("unused")
		private float a_float3() { return 34.793f; }
		@SuppressWarnings("unused")
		private double a_double3() { return 79.343; }
		@SuppressWarnings("unused")
		private Object a_object3() { return new O3(); }

		public boolean a_boolean4() { return true; }
		public char a_char4() { return '4'; }
		public byte a_byte4() { return (byte)14; }
		public short a_short4() { return (short)564; }
		public int a_int4() { return 784; }
		public long a_long4() { return 1454L; }
		public float a_float4() { return 34.794f; }
		public double a_double4() { return 79.344; }
		public Object a_object4() { return new O4(); }
	}
	static class CB extends CA {	
	}
	static class CC extends CA {
		@SuppressWarnings("unused")
		private boolean a_boolean1 = false;
		@SuppressWarnings("unused")
		private char a_char1 = '3';
		@SuppressWarnings("unused")
		private byte a_byte1 = (byte)13;
		@SuppressWarnings("unused")
		private short a_short1 = (short)563;
		@SuppressWarnings("unused")
		private int a_int1 = 783;
		@SuppressWarnings("unused")
		private long a_long1 = 1453L;
		@SuppressWarnings("unused")
		private float a_float1 = 34.793f;
		@SuppressWarnings("unused")
		private double a_double1 = 79.343;
		@SuppressWarnings("unused")
		private Object a_object1 = new O3();

		public boolean a_boolean2 = false;
		public char a_char2 = '4';
		public byte a_byte2 = (byte)14;
		public short a_short2 = (short)564;
		public int a_int2 = 784;
		public long a_long2 = 1454L;
		public float a_float2 = 34.794f;
		public double a_double2 = 79.344;
		public Object a_object2 = new O4();

		@SuppressWarnings("unused")
		private boolean a_boolean3() { return false; }
		@SuppressWarnings("unused")
		private char a_char3() { return '5'; }
		@SuppressWarnings("unused")
		private byte a_byte3() { return (byte)15; }
		@SuppressWarnings("unused")
		private short a_short3() { return (short)565; }
		@SuppressWarnings("unused")
		private int a_int3() { return 785; }
		@SuppressWarnings("unused")
		private long a_long3() { return 1455L; }
		@SuppressWarnings("unused")
		private float a_float3() { return 34.795f; }
		@SuppressWarnings("unused")
		private double a_double3() { return 79.345; }
		@SuppressWarnings("unused")
		private Object a_object3() { return new O5(); }

		@Override
		public boolean a_boolean4() { return false; }
		@Override
		public char a_char4() { return '6'; }
		@Override
		public byte a_byte4() { return (byte)16; }
		@Override
		public short a_short4() { return (short)566; }
		@Override
		public int a_int4() { return 786; }
		@Override
		public long a_long4() { return 1456L; }
		@Override
		public float a_float4() { return 34.796f; }
		@Override
		public double a_double4() { return 79.346; }
		@Override
		public Object a_object4() { return new O6(); }
	}
	
	public void testCAFields() throws Exception {
		CA ca = new CA();
		JavaAccessor ja = support.getObjectAccessor(ca);
		
		// private fields
		assertEquals(true, ja.getBoolean("a_boolean1"));
		assertEquals('1', ja.getChar("a_char1"));
		assertEquals((byte)11, ja.getByte("a_byte1"));
		assertEquals((short)56, ja.getShort("a_short1"));
		assertEquals(78, ja.getInt("a_int1"));
		assertEquals(145L, ja.getLong("a_long1"));
		assertEquals(34.79f, ja.getFloat("a_float1"));
		assertEquals(79.34, ja.getDouble("a_double1"));
		assertEquals(O1.class, ja.get("a_object1").getClass());
		
		// public fields
		assertEquals(true, ja.getBoolean("a_boolean2"));
		assertEquals('2', ja.getChar("a_char2"));
		assertEquals((byte)12, ja.getByte("a_byte2"));
		assertEquals((short)562, ja.getShort("a_short2"));
		assertEquals(782, ja.getInt("a_int2"));
		assertEquals(1452L, ja.getLong("a_long2"));
		assertEquals(34.792f, ja.getFloat("a_float2"));
		assertEquals(79.342, ja.getDouble("a_double2"));
		assertEquals(O2.class, ja.get("a_object2").getClass());
	}
	
	public void testCAGetters() throws Exception {
		CA ca = new CA();
		JavaAccessor ja = support.getObjectAccessor(ca);
		
		// private fields
		assertEquals(true, ja.callBoolean("a_boolean3"));
		assertEquals('3', ja.callChar("a_char3"));
		assertEquals((byte)13, ja.callByte("a_byte3"));
		assertEquals((short)563, ja.callShort("a_short3"));
		assertEquals(783, ja.callInt("a_int3"));
		assertEquals(1453L, ja.callLong("a_long3"));
		assertEquals(34.793f, ja.callFloat("a_float3"));
		assertEquals(79.343, ja.callDouble("a_double3"));
		assertEquals(O3.class, ja.call("a_object3").getClass());
		
		// public fields
		assertEquals(true, ja.callBoolean("a_boolean4"));
		assertEquals('4', ja.callChar("a_char4"));
		assertEquals((byte)14, ja.callByte("a_byte4"));
		assertEquals((short)564, ja.callShort("a_short4"));
		assertEquals(784, ja.callInt("a_int4"));
		assertEquals(1454L, ja.callLong("a_long4"));
		assertEquals(34.794f, ja.callFloat("a_float4"));
		assertEquals(79.344, ja.callDouble("a_double4"));
		assertEquals(O4.class, ja.call("a_object4").getClass());
	}
	
	public void testCBFields() throws Exception {
		CB cb = new CB();
		JavaAccessor jb = support.getObjectAccessor(cb);
		
		// private fields
		assertEquals(true, jb.getBoolean("a_boolean1"));
		assertEquals('1', jb.getChar("a_char1"));
		assertEquals((byte)11, jb.getByte("a_byte1"));
		assertEquals((short)56, jb.getShort("a_short1"));
		assertEquals(78, jb.getInt("a_int1"));
		assertEquals(145L, jb.getLong("a_long1"));
		assertEquals(34.79f, jb.getFloat("a_float1"));
		assertEquals(79.34, jb.getDouble("a_double1"));
		assertEquals(O1.class, jb.get("a_object1").getClass());
		
		// public fields
		assertEquals(true, jb.getBoolean("a_boolean2"));
		assertEquals('2', jb.getChar("a_char2"));
		assertEquals((byte)12, jb.getByte("a_byte2"));
		assertEquals((short)562, jb.getShort("a_short2"));
		assertEquals(782, jb.getInt("a_int2"));
		assertEquals(1452L, jb.getLong("a_long2"));
		assertEquals(34.792f, jb.getFloat("a_float2"));
		assertEquals(79.342, jb.getDouble("a_double2"));
		assertEquals(O2.class, jb.get("a_object2").getClass());
	}
	
	public void testCBGetters() throws Exception {
		CB cb = new CB();
		JavaAccessor jb = support.getObjectAccessor(cb);
		
		// private fields
		assertEquals(true, jb.callBoolean("a_boolean3"));
		assertEquals('3', jb.callChar("a_char3"));
		assertEquals((byte)13, jb.callByte("a_byte3"));
		assertEquals((short)563, jb.callShort("a_short3"));
		assertEquals(783, jb.callInt("a_int3"));
		assertEquals(1453L, jb.callLong("a_long3"));
		assertEquals(34.793f, jb.callFloat("a_float3"));
		assertEquals(79.343, jb.callDouble("a_double3"));
		assertEquals(O3.class, jb.call("a_object3").getClass());
		
		// public fields
		assertEquals(true, jb.callBoolean("a_boolean4"));
		assertEquals('4', jb.callChar("a_char4"));
		assertEquals((byte)14, jb.callByte("a_byte4"));
		assertEquals((short)564, jb.callShort("a_short4"));
		assertEquals(784, jb.callInt("a_int4"));
		assertEquals(1454L, jb.callLong("a_long4"));
		assertEquals(34.794f, jb.callFloat("a_float4"));
		assertEquals(79.344, jb.callDouble("a_double4"));
		assertEquals(O4.class, jb.call("a_object4").getClass());
	}
	
	public void testCCFields() throws Exception {
		CC cc = new CC();
		JavaAccessor jb = support.getObjectAccessor(cc);
		
		// private fields
		assertEquals(false, jb.getBoolean("a_boolean1"));
		assertEquals('3', jb.getChar("a_char1"));
		assertEquals((byte)13, jb.getByte("a_byte1"));
		assertEquals((short)563, jb.getShort("a_short1"));
		assertEquals(783, jb.getInt("a_int1"));
		assertEquals(1453L, jb.getLong("a_long1"));
		assertEquals(34.793f, jb.getFloat("a_float1"));
		assertEquals(79.343, jb.getDouble("a_double1"));
		assertEquals(O3.class, jb.get("a_object1").getClass());
		
		// public fields
		assertEquals(false, jb.getBoolean("a_boolean2"));
		assertEquals('4', jb.getChar("a_char2"));
		assertEquals((byte)14, jb.getByte("a_byte2"));
		assertEquals((short)564, jb.getShort("a_short2"));
		assertEquals(784, jb.getInt("a_int2"));
		assertEquals(1454L, jb.getLong("a_long2"));
		assertEquals(34.794f, jb.getFloat("a_float2"));
		assertEquals(79.344, jb.getDouble("a_double2"));
		assertEquals(O4.class, jb.get("a_object2").getClass());
	}
	
	public void testCCGetters() throws Exception {
		CC cc = new CC();
		JavaAccessor jb = support.getObjectAccessor(cc);
		
		// private fields
		assertEquals(false, jb.callBoolean("a_boolean3"));
		assertEquals('5', jb.callChar("a_char3"));
		assertEquals((byte)15, jb.callByte("a_byte3"));
		assertEquals((short)565, jb.callShort("a_short3"));
		assertEquals(785, jb.callInt("a_int3"));
		assertEquals(1455L, jb.callLong("a_long3"));
		assertEquals(34.795f, jb.callFloat("a_float3"));
		assertEquals(79.345, jb.callDouble("a_double3"));
		assertEquals(O5.class, jb.call("a_object3").getClass());
		
		// public fields
		assertEquals(false, jb.callBoolean("a_boolean4"));
		assertEquals('6', jb.callChar("a_char4"));
		assertEquals((byte)16, jb.callByte("a_byte4"));
		assertEquals((short)566, jb.callShort("a_short4"));
		assertEquals(786, jb.callInt("a_int4"));
		assertEquals(1456L, jb.callLong("a_long4"));
		assertEquals(34.796f, jb.callFloat("a_float4"));
		assertEquals(79.346, jb.callDouble("a_double4"));
		assertEquals(O6.class, jb.call("a_object4").getClass());
	}

	
	//
	// Constructors
	//
	static class DA {
		private String v;
		private DA(String v) {
			this.v = v;
		}
		@Override
		public String toString() {
			return v;
		}
	}
	static class DB {
		private String v;
		protected DB(String v) {
			this.v = v;
		}
		@Override
		public String toString() {
			return v;
		}
	}
	static class DC extends DB {
		public DC(String v) {
			super(v);
		}
	}
	
	public void testContructors() {
		JavaAccessor da = support.getClassAccessor(DA.class);
		Object dao = da.newObject("o1");
		assertEquals("o1", dao.toString());
	}

	
	//
	// Methods
	//
	static class MA {
		@SuppressWarnings("unused")
		private String m1() { return "m1"; }
		@SuppressWarnings("unused")
		private String m1(int i1) { return "m1(i1)"; }
		@SuppressWarnings("unused")
		private String m1(int i1, int i2) { return "m1(i1,i2)"; }
		@SuppressWarnings("unused")
		private String m1(long l1) { return "m1(l1)"; }
		@SuppressWarnings("unused")
		private String m1(long l1,long l2) { return "m1(l1,l2)"; }
		@SuppressWarnings("unused")
		private String m1(int i1, long l2) { return "m1(i1,l2)"; }

		public String m2() { return "m2"; }
		public String m2(int i1) { return "m2(i1)"; }
		public String m2(int i1, int i2) { return "m2(i1,i2)"; }
		public String m2(long l1) { return "m2(l1)"; }
		public String m2(long l1,long l2) { return "m2(l1,l2)"; }
		public String m2(int i1, long l2) { return "m2(i1,l2)"; }
	}
	static class MB extends MA{
	}
	static class MC extends MA {
		@SuppressWarnings("unused")
		private String m1() { return "cm1"; }
		@SuppressWarnings("unused")
		private String m1(int i1) { return "cm1(i1)"; }
		@SuppressWarnings("unused")
		private String m1(int i1, int i2) { return "cm1(i1,i2)"; }
		@SuppressWarnings("unused")
		private String m1(long l1) { return "cm1(l1)"; }
		@SuppressWarnings("unused")
		private String m1(long l1,long l2) { return "cm1(l1,l2)"; }
		@SuppressWarnings("unused")
		private String m1(int i1, long l2) { return "cm1(i1,l2)"; }

		@Override
		public String m2() { return "cm2"; }
		@Override
		public String m2(int i1) { return "cm2(i1)"; }
		@Override
		public String m2(int i1, int i2) { return "cm2(i1,i2)"; }
		@Override
		public String m2(long l1) { return "cm2(l1)"; }
		@Override
		public String m2(long l1,long l2) { return "cm2(l1,l2)"; }
		@Override
		public String m2(int i1, long l2) { return "cm2(i1,l2)"; }
	}

	public void testMA() {
		JavaAccessor ma = support.getObjectAccessor(new MA());
		
		assertEquals("m1", ma.<String>call("m1"));
		assertEquals("m1(i1)", ma.<String>call("m1",1));
		assertEquals("m1(i1,i2)", ma.<String>call("m1",1,2));
		assertEquals("m1(l1)", ma.<String>call("m1",1L));
		assertEquals("m1(l1,l2)", ma.<String>call("m1",1L,2L));
		assertEquals("m1(i1,l2)", ma.<String>call("m1",1,2L));
		try {
			assertEquals("m1(l1,l2)", ma.<String>call("m1",1L,2));
			fail();
		} catch(Exception e) {}
		try {
			assertEquals("XXX", ma.<String>call("n"));
			fail();
		} catch(Exception e) {}
		
		assertEquals("m2", ma.<String>call("m2"));
		assertEquals("m2(i1)", ma.<String>call("m2",1));
		assertEquals("m2(i1,i2)", ma.<String>call("m2",1,2));
		assertEquals("m2(l1)", ma.<String>call("m2",1L));
		assertEquals("m2(l1,l2)", ma.<String>call("m2",1L,2L));
		assertEquals("m2(i1,l2)", ma.<String>call("m2",1,2L));		
		try {
			assertEquals("m2(l1,l2)", ma.<String>call("m2",1L,2));
			fail();
		} catch(Exception e) {}
	}

	public void testMB() {
		JavaAccessor mb = support.getObjectAccessor(new MB());
		
		assertEquals("m1", mb.<String>call("m1"));
		assertEquals("m1(i1)", mb.<String>call("m1",1));
		assertEquals("m1(i1,i2)", mb.<String>call("m1",1,2));
		assertEquals("m1(l1)", mb.<String>call("m1",1L));
		assertEquals("m1(l1,l2)", mb.<String>call("m1",1L,2L));
		assertEquals("m1(i1,l2)", mb.<String>call("m1",1,2L));
		try {
			assertEquals("m1(l1,l2)", mb.<String>call("m1",1L,2));
			fail();
		} catch(Exception e) {}
		
		assertEquals("m2", mb.<String>call("m2"));
		assertEquals("m2(i1)", mb.<String>call("m2",1));
		assertEquals("m2(i1,i2)", mb.<String>call("m2",1,2));
		assertEquals("m2(l1)", mb.<String>call("m2",1L));
		assertEquals("m2(l1,l2)", mb.<String>call("m2",1L,2L));
		assertEquals("m2(i1,l2)", mb.<String>call("m2",1,2L));		
		try {
			assertEquals("m2(l1,l2)", mb.<String>call("m2",1L,2));
			fail();
		} catch(Exception e) {}
	}

	public void testMC() {
		JavaAccessor mc = support.getObjectAccessor(new MC());
		
		assertEquals("cm1", mc.<String>call("m1"));
		assertEquals("cm1(i1)", mc.<String>call("m1",1));
		assertEquals("cm1(i1,i2)", mc.<String>call("m1",1,2));
		assertEquals("cm1(l1)", mc.<String>call("m1",1L));
		assertEquals("cm1(l1,l2)", mc.<String>call("m1",1L,2L));
		assertEquals("cm1(i1,l2)", mc.<String>call("m1",1,2L));
		try {
			assertEquals("cm1(l1,l2)", mc.<String>call("m1",1L,2));
			fail();
		} catch(Exception e) {}
		
		assertEquals("cm2", mc.<String>call("m2"));
		assertEquals("cm2(i1)", mc.<String>call("m2",1));
		assertEquals("cm2(i1,i2)", mc.<String>call("m2",1,2));
		assertEquals("cm2(l1)", mc.<String>call("m2",1L));
		assertEquals("cm2(l1,l2)", mc.<String>call("m2",1L,2L));
		assertEquals("cm2(i1,l2)", mc.<String>call("m2",1,2L));		
		try {
			assertEquals("cm2(l1,l2)", mc.<String>call("m2",1L,2));
			fail();
		} catch(Exception e) {}
	}

}
