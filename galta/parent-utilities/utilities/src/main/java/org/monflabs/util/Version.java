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
package org.monflabs.util;

import java.io.Serializable;
import java.util.regex.Pattern;

public class Version implements Serializable, Comparable<Version> {

	public static final Version EMPTY = new Version(0, 0, 0);
	public static final Pattern VERSION_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+){0,2}(-.*)?");
	
	private final int major;
	private final int minor;
	private final int subversion;
	private final String qualifier;
	
	private static final char POINT = '.';
	private static final char DASH = '-';
	private static final long serialVersionUID = 1L;
	
	/**
	 * Parses a string containing a dot separated version of Connections with the format Major.Minor.Subversion and returns a Version object
	 * @param s
	 */
	public static Version parse(String s) {
		if (StringUtil.isEmpty(s)) {
			return EMPTY;
		}
		String sMajor = "";
		String sMinor = "";
		String sSubversion = "";
		String qualifier = null;
		// The qualifier can follow any number of components ("1-rc", "1.0-beta", "1.0.0-SNAPSHOT")
		int dash = s.indexOf(DASH);
		if (dash >= 0) {
			qualifier = s.substring(dash + 1);
			s = s.substring(0, dash);
		}
		int d1 = s.indexOf(POINT);
		if (d1 >= 0) {
			sMajor = s.substring(0, d1);
			int d2 = s.indexOf(POINT, d1 + 1);
			if (d2 >= 0) {
				sMinor = s.substring(d1 + 1, d2);
				sSubversion = s.substring(d2 + 1);
			} else {
				sMinor = s.substring(d1 + 1);
			}
		} else {
			sMajor = s;
		}
		try {
			int major = sMajor.isEmpty() ? 0 : Integer.parseInt(sMajor);
			int minor = sMinor.isEmpty() ? 0 : Integer.parseInt(sMinor);
			int subversion = sSubversion.isEmpty() ? 0 : Integer
					.parseInt(sSubversion);
			return new Version(major, minor, subversion,qualifier);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(StringFormat.format(
					"Invalid version format {0}", s), ex);
		}
	}

	public Version(int major, int minor, int subversion) {
		this(major, minor, subversion, null);
	}
	public Version(int major, int minor, int subversion, String qualifier) {
		this.major = major;
		this.minor = minor;
		this.subversion = subversion;
		this.qualifier = qualifier;
	}

	public Version(int major, int minor) {
		this(major, minor, 0);
	}

	public Version(int major) {
		this(major, 0, 0);
	}
	
	/**
	 * Returns true for the empty version 0.0.0 without a qualifier (0.0.0-rc is not empty).
	 */
	public boolean isEmpty() {
		return major==0 && minor==0 && subversion==0 && normalizedQualifier()==null;
	}

	@Override
	public String toString() {
		if(StringUtil.isNotEmpty(qualifier)) {
			return StringFormat.format("{0}.{1}.{2}-{3}", getMajor(), getMinor(), getSubversion(), getQualifier());
		}
		return StringFormat.format("{0}.{1}.{2}", getMajor(), getMinor(), getSubversion());
	}

	/**
	 * Returns major version
	 */
	public int getMajor() {
		return major;
	}

	/**
	 * Returns minor version
	 */
	public int getMinor() {
		return minor;
	}

	/**
	 * Returns subversion
	 */
	public int getSubversion() {
		return subversion;
	}

	/**
	 * Returns the qualifier
	 */
	public String getQualifier() {
		return qualifier;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Version) {
			Version v = (Version) o;
			return (this == v) || (v.major == major && v.minor == minor
					&& v.subversion == subversion && java.util.Objects.equals(normalizedQualifier(), v.normalizedQualifier()));
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int prime = 31;
		int hash = 1;
		hash = hash * prime + getMajor();
		hash = hash * prime + getMinor();
		hash = hash * prime + getSubversion();
		hash = hash * prime + java.util.Objects.hashCode(normalizedQualifier());
		return hash;
	}

	private String normalizedQualifier() {
		return StringUtil.isEmpty(qualifier) ? null : qualifier;
	}

	/**
	 * Orders by major, minor, subversion, then qualifier. A version without a
	 * qualifier (a release) sorts after the same numbers with a qualifier
	 * (a pre-release), e.g. 1.0.0-SNAPSHOT &lt; 1.0.0.
	 */
	@Override
	public int compareTo(Version v) {
		int c = Integer.compare(major, v.major);
		if (c != 0) return c;
		c = Integer.compare(minor, v.minor);
		if (c != 0) return c;
		c = Integer.compare(subversion, v.subversion);
		if (c != 0) return c;
		String q1 = normalizedQualifier();
		String q2 = v.normalizedQualifier();
		if (q1 == null) {
			return q2 == null ? 0 : 1;
		}
		if (q2 == null) {
			return -1;
		}
		return q1.compareTo(q2);
	}

	/**
	 * Returns true if the current version is at least the version in parameter
	 * @param version
	 */
	public boolean isAtLeast(Version version) {
		// Compares the three numbers only, by design (documented): a snapshot of a version
		// is "at least" that version. Use greaterOrEqualThan() for the full ordering.
		return isAtLeast(version.getMajor(), version.getMinor(), version.getSubversion());
	}

	/**
	 * Returns true if the current version is at least the version in parameter
	 * @param _major
	 */
	public boolean isAtLeast(int _major) {
		return isAtLeast(_major, 0, 0);
	}

	/**
	 * Returns true if the current version is at least the version in parameters
	 * @param _major
	 * @param _minor
	 */
	public boolean isAtLeast(int _major, int _minor) {
		return isAtLeast(_major, _minor, 0);
	}

	/**
	 * Returns true if the current version is at least the version in parameters
	 * @param _major
	 * @param _minor
	 * @param _subversion
	 */
	public boolean isAtLeast(int _major, int _minor, int _subversion) {
		if (this.major > _major) {
			return true;
		}
		if (this.major == _major) {
			if (this.minor > _minor) {
				return true;
			}
			if (this.minor == _minor) {
				if (this.subversion >= _subversion) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns true if the current version is greater than the parameter
	 * @param v
	 */
	public boolean greaterThan(Version v) {
		return compareTo(v) > 0;
	}
	public boolean lessOrEqualThan(Version v) {
		return !greaterThan(v);
	}

	/**
	 * Returns true if the current version is less than the parameter
	 * @param v
	 */
	public boolean lessThan(Version v) {
		return compareTo(v) < 0;
	}
	public boolean greaterOrEqualThan(Version v) {
		return !lessThan(v);
	}
}