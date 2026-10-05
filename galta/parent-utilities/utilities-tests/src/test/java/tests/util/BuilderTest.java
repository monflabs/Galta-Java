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

import static org.junit.Assert.assertThrows;

import org.monflabs.util.DebugMode;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.ObjectBuilderException;
import org.monflabs.util.builder.Required;

import tests.ProjectTestCase;

public class BuilderTest extends ProjectTestCase {

	static class PersonBuilder extends ObjectBuilder<String> {
		@Required
		String name;
		int age = -1;
		PersonBuilder name(String name) {
			this.name = name;
			return this;
		}
		PersonBuilder age(int age) {
			this.age = age;
			return this;
		}
		@Override
		protected void validate() {
			if(age<0) {
				throw exception("Invalid age {0}", age);
			}
		}
		@Override
		protected String _build() {
			return name+":"+age;
		}
	}

	public void testValidateAlwaysRuns() throws Exception {
		assertEquals("bob:3", new PersonBuilder().name("bob").age(3).build());
		ObjectBuilderException e = assertThrows(ObjectBuilderException.class, () -> new PersonBuilder().name("bob").build());
		assertTrue(e.getMessage(), e.getMessage().contains("Invalid age -1"));
	}

	static class EmployeeBuilder extends PersonBuilder {
		@Required
		String company;
		@SuppressWarnings("unused")
		String optional;
		EmployeeBuilder company(String company) {
			this.company = company;
			return this;
		}
		@Override
		protected String _build() {
			return super._build()+"@"+company;
		}
	}

	public void testRequiredAlwaysChecked() throws Exception {
		// @Required used to be checked only with a debugger attached
		PersonBuilder b = new PersonBuilder().age(3);
		ObjectBuilderException e = assertThrows(ObjectBuilderException.class, () -> b.build());
		assertEquals("Field name is required", e.getMessage());
		// Inherited fields are checked too
		e = assertThrows(ObjectBuilderException.class, () -> new EmployeeBuilder().company("acme").age(3).build());
		assertEquals("Field name is required", e.getMessage());
		e = assertThrows(ObjectBuilderException.class, () -> ((EmployeeBuilder)new EmployeeBuilder().name("ann").age(3)).build());
		assertEquals("Field company is required", e.getMessage());
		assertEquals("ann:3@acme", ((EmployeeBuilder)new EmployeeBuilder().company("acme").name("ann").age(3)).build());
	}
}
