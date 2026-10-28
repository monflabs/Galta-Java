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
package org.monflabs.util.number;

import java.math.RoundingMode;

/**
 * The rounding modes of {@link Decimal128}, as defined by the ECMAScript Decimal proposal.
 * <p>
 * Each one maps to an IEEE 754-2019 rounding mode and to a {@link RoundingMode}. The default mode is {@link #HALF_EVEN}.
 */
public enum DecimalRoundingMode {

	/** Toward positive infinity ("ceil", IEEE roundTowardPositive). */
	CEIL("ceil", RoundingMode.CEILING),
	/** Toward negative infinity ("floor", IEEE roundTowardNegative). */
	FLOOR("floor", RoundingMode.FLOOR),
	/** Toward zero ("trunc", IEEE roundTowardZero). */
	TRUNC("trunc", RoundingMode.DOWN),
	/** Ties away from zero ("halfExpand", IEEE roundTiesToAway). */
	HALF_EXPAND("halfExpand", RoundingMode.HALF_UP),
	/** Ties toward an even digit ("halfEven", IEEE roundTiesToEven). */
	HALF_EVEN("halfEven", RoundingMode.HALF_EVEN);

	/** The default rounding mode. */
	public static final DecimalRoundingMode DEFAULT = HALF_EVEN;

	private final String name;
	private final RoundingMode roundingMode;

	DecimalRoundingMode(String name, RoundingMode roundingMode) {
		this.name = name;
		this.roundingMode = roundingMode;
	}

	/**
	 * The name of the mode in the proposal: "ceil", "floor", "trunc", "halfExpand" or "halfEven".
	 */
	public String getName() {
		return name;
	}

	/**
	 * The equivalent {@link RoundingMode}.
	 */
	public RoundingMode toRoundingMode() {
		return roundingMode;
	}

	/**
	 * Finds a mode from its name in the proposal ("ceil", "floor", "trunc", "halfExpand", "halfEven").
	 * @throws IllegalArgumentException (the RangeError of the proposal) when the name is not a rounding mode
	 */
	public static DecimalRoundingMode fromName(String name) {
		for (DecimalRoundingMode m : values()) {
			if (m.name.equals(name)) {
				return m;
			}
		}
		throw new IllegalArgumentException("Invalid rounding mode: " + name);
	}

	/**
	 * Finds the mode equivalent to a {@link RoundingMode}.
	 * @throws IllegalArgumentException when there is no equivalent ({@code UP}, {@code HALF_DOWN}, {@code UNNECESSARY})
	 */
	public static DecimalRoundingMode from(RoundingMode mode) {
		switch (mode) {
		case CEILING:
			return CEIL;
		case FLOOR:
			return FLOOR;
		case DOWN:
			return TRUNC;
		case HALF_UP:
			return HALF_EXPAND;
		case HALF_EVEN:
			return HALF_EVEN;
		default:
			throw new IllegalArgumentException("Unsupported rounding mode: " + mode);
		}
	}
}
