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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.java.JavaJsonFactoryChecked;

import tests.ProjectTestCase;

public class JavaFactoryCheckedTest extends ProjectTestCase {

	public void testChecks() throws Exception {
		JavaJsonFactoryChecked f = new JavaJsonFactoryChecked();
		
		JsonObject o = f.createObject();
		o.put("p", (Object)null);
		o.put("p", true);
		o.put("p", (byte)1);
		o.put("p", (short)1);
		o.put("p", (int)1);
		o.put("p", (long)1);
		o.put("p", (float)1);
		o.put("p", (double)1);
		o.put("p", Boolean.TRUE);
		o.put("p", Byte.valueOf((byte)1));
		o.put("p", Short.valueOf((short)1));
		o.put("p", Integer.valueOf(1));
		o.put("p", Long.valueOf((long)1));
		o.put("p", Float.valueOf((float)1));
		o.put("p", Double.valueOf((double)1));
		o.put("p", BigInteger.ONE);
		o.put("p", BigDecimal.ONE);
		o.put("p", "abc");
		o.put("p", f.createObject());
		o.put("p", f.createArray());
		o.put("p", JsonUtil.parseLocalDate("2020-09-24"));
		o.put("p", JsonUtil.parseLocalTime("20:45:25"));
		o.put("p", JsonUtil.parseLocalDateTime("2020-09-24T21:45:25"));
		o.put("p", JsonUtil.parseOffsetTime("20:45:25+05:00"));
		o.put("p", JsonUtil.parseOffsetDateTime("2020-09-24T21:45:25+04:00"));
		o.put("p", JsonUtil.parseZonedDateTime("2022-08-14T14:06:43-04:00[US/Eastern]"));

		o.put("p", (Boolean)null);
		o.put("p", (Byte)null);
		o.put("p", (Short)null);
		o.put("p", (Integer)null);
		o.put("p", (Long)null);
		o.put("p", (Float)null);
		o.put("p", (Double)null);
		o.put("p", (BigInteger)null);
		o.put("p", (BigDecimal)null);
		o.put("p", (String)null);
		o.put("p", (JsonObject)null);
		o.put("p", (JsonArray)null);
		o.put("p", (LocalDate)null);
		o.put("p", (LocalTime)null);
		o.put("p", (LocalDateTime)null);
		o.put("p", (LocalDate)null);
		o.put("p", (OffsetTime)null);
		o.put("p", (OffsetDateTime)null);
		o.put("p", (ZonedDateTime)null);

		JsonArray a = f.createArray();
		a.add((Object)null);
		a.add(true);
		a.add((byte)1);
		a.add((short)1);
		a.add((int)1);
		a.add((long)1);
		a.add((float)1);
		a.add((double)1);
		a.add(Boolean.TRUE);
		a.add(Byte.valueOf((byte)1));
		a.add(Short.valueOf((short)1));
		a.add(Integer.valueOf(1));
		a.add(Long.valueOf((long)1));
		a.add(Float.valueOf((float)1));
		a.add(Double.valueOf((double)1));
		a.add(BigInteger.ONE);
		a.add(BigDecimal.ONE);
		a.add("abc");
		a.add(f.createObject());
		a.add(f.createArray());
		a.add(JsonUtil.parseLocalDate("2020-09-24"));
		a.add(JsonUtil.parseLocalTime("20:45:25"));
		a.add(JsonUtil.parseLocalDateTime("2020-09-24T21:45:25"));
		a.add(JsonUtil.parseOffsetTime("20:45:25+05:00"));
		a.add(JsonUtil.parseOffsetDateTime("2020-09-24T21:45:25+04:00"));
		a.add(JsonUtil.parseZonedDateTime("2022-08-14T14:06:43-04:00[US/Eastern]"));

		a.add((Boolean)null);
		a.add((Byte)null);
		a.add((Short)null);
		a.add((Integer)null);
		a.add((Long)null);
		a.add((Float)null);
		a.add((Double)null);
		a.add((BigInteger)null);
		a.add((BigDecimal)null);
		a.add((String)null);
		a.add((JsonObject)null);
		a.add((JsonArray)null);
		a.add((LocalDate)null);
		a.add((LocalTime)null);
		a.add((LocalDateTime)null);
		a.add((OffsetTime)null);
		a.add((OffsetDateTime)null);
		a.add((ZonedDateTime)null);

		assertThrows( Exception.class, () -> o.put("p", Character.valueOf('a')));
		assertThrows( Exception.class, () -> o.put("p", (Object)LocalDate.now()));
		assertThrows( Exception.class, () -> o.put("p", new HashMap<String,Object>()));
		assertThrows( Exception.class, () -> o.put("p", new ArrayList<Object>()));

		assertThrows( Exception.class, () -> a.add(Character.valueOf('a')));
		assertThrows( Exception.class, () -> a.add((Object)LocalDate.now()));
		assertThrows( Exception.class, () -> a.add(new HashMap<String,Object>()));
		assertThrows( Exception.class, () -> a.add(new ArrayList<Object>()));
	}
}
