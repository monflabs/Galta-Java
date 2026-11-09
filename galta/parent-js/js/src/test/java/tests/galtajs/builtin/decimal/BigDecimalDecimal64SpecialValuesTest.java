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
package tests.galtajs.builtin.decimal;

import java.math.MathContext;

import org.monflabs.galtajs.JSEnvironment;

import tests.galtajs.GaltaJSTestCase;
import util.GlobalTestEnvironment;

/**
 * BigDecimal decimals computed with DECIMAL64: overflow and underflow follow that format's exponent range.
 */
public class BigDecimalDecimal64SpecialValuesTest extends GaltaJSTestCase {

	public void testScript() throws Exception {
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		return GlobalTestEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.useBigDecimal(true)
				.mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64");
	}
}
