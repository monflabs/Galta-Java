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

import org.monflabs.galtajs.JSEnvironment;

import tests.galtajs.GaltaJSTestCase;
import util.GlobalTestEnvironment;

/**
 * Decimal special values with BigDecimal decimals (the Number NaN, infinities and -0): the shared expectations, then the BigDecimal specifics.
 */
public class BigDecimalSpecialValuesTest extends GaltaJSTestCase {

	public void testScript() throws Exception {
		executeFile("/tests/galtajs/builtin/decimal/DecimalSpecialValues");
		execute();
	}

	@Override
	protected JSEnvironment.Builder createEnvironment() {
		return GlobalTestEnvironment.newBuilder()
				.enableGaltaJSExtensions()
				.useBigDecimal(true);
	}
}
