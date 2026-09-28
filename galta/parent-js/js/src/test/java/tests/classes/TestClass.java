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
package tests.classes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.monflabs.util.Console;



/**
 * Test class
 * @author Philippe Riand
 */
public class TestClass {

	public char charVar = 'A';
	public byte byteVar = 3;
	public short shortVar = 156;
	public int intVar = -56;
	public long longVar = 4789;
	public float floatVar = 10.56F;
	public double doubleVar = 56.89;
	public BigDecimal bigDecimalVar = BigDecimal.TEN;
	public boolean booleanVar = true;
	public String stringVar = "AAA";

	public int[] intArrayVar = new int[10];

	public TestClass() {
	}

	public TestClass(int a1, int a2, int a3) {
		this.intVar = a1 + a2 + a3;
	}
	
	@Override
	public String toString() {
		return "Test "+stringVar;
	}

	public void dump() {
		Console.log("charVar={0}", charVar);
		Console.log("byteVar={0}", byteVar);
		Console.log("shortVar={0}", shortVar);
		Console.log("intVar={0}", intVar);
		Console.log("longVar={0}", longVar);
		Console.log("floatVar={0}", floatVar);
		Console.log("doubleVar={0}", doubleVar);
		Console.log("bigDecimalVar={0}", bigDecimalVar);
		Console.log("booleanVar={0}", booleanVar);
		Console.log("stringVar={0}", stringVar);
	}

	public int getIntProp() {
		return intVar;
	}

	public void setIntProp(int intVar) {
		this.intVar = intVar;
	}

	public char getCharVar() {
		return charVar;
	}

	public void setCharVar(char charVar) {
		this.charVar = charVar;
	}

	public byte getByteVar() {
		return byteVar;
	}

	public void setByteVar(byte byteVar) {
		this.byteVar = byteVar;
	}

	public short getShortVar() {
		return shortVar;
	}

	public void setShortVar(short shortVar) {
		this.shortVar = shortVar;
	}

	public int getIntVar() {
		return intVar;
	}

	public void setIntVar(int intVar) {
		this.intVar = intVar;
	}

	public long getLongVar() {
		return longVar;
	}

	public void setLongVar(long longVar) {
		this.longVar = longVar;
	}

	public float getFloatVar() {
		return floatVar;
	}

	public void setFloatVar(float floatVar) {
		this.floatVar = floatVar;
	}

	public double getDoubleVar() {
		return doubleVar;
	}

	public void setDoubleVar(double doubleVar) {
		this.doubleVar = doubleVar;
	}

	public BigDecimal getBigDecimalVar() {
		return bigDecimalVar;
	}

	public void setBigDecimalVar(BigDecimal bigDecimalVar) {
		this.bigDecimalVar = bigDecimalVar;
	}

	public boolean isBooleanVar() {
		return booleanVar;
	}

	public void setBooleanVar(boolean booleanVar) {
		this.booleanVar = booleanVar;
	}

	public String getStringVar() {
		return stringVar;
	}

	public void setStringVar(String stringVar) {
		this.stringVar = stringVar;
	}

	public void f() {
		Console.log("f() called");
	}

	public void f(int i) {
		Console.log("f(i={0}) called",i);
	}

	public void f(String s) {
		Console.log("f(s={0}) called",s);
	}


	// ForAll Iteration
	public Object getForAllIterator() {
		ArrayList<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(2);
		l.add(3);
		return l.iterator();
	}
	public Object getForAllEnumeration() {
		Vector<Object> l = new Vector<Object>();
		l.add(1);
		l.add(2);
		l.add(3);
		l.add(4);
		return l.elements();
	}
	public Object getForAllList() {
		ArrayList<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(2);
		l.add(3);
		l.add(4);
		l.add(5);
		return l;
	}
	public Object getForAllMap() {
		Map<String,Object> l = new HashMap<String,Object>();
		l.put("d","D");
		l.put("e","EE");
		return l;
	}
	public Object getForAllArray() {
		return new Object[]{1,2,3,4,5,6};
	}
}
