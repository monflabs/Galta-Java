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

import org.monflabs.util.StringUtil;
import org.monflabs.util.Version;

import tests.ProjectTestCase;

public final class VersionTest extends ProjectTestCase {

    public void testVersionNumbers() throws Exception {
    	checkVersion(0,0,0,null,"0.0.0");
    	checkVersion(1,0,0,null,"1.0.0");
    	checkVersion(11,0,0,null,"11.0.0");
    	checkVersion(1,2,0,null,"1.2.0");
    	checkVersion(11,22,0,null,"11.22.0");
    	checkVersion(1,2,3,null,"1.2.3");
    	checkVersion(11,22,33,null,"11.22.33");
    	
    	checkVersion(1,2,3,"","1.2.3");
    	checkVersion(1,2,3,"q","1.2.3-q");
    	checkVersion(1,2,3,"qq","1.2.3-qq");
    }
    
    private void checkVersion(int major, int minor, int subversion, String qualifier, String asString) {
    	Version v = Version.parse(asString);
    	assertEquals(major,v.getMajor());
    	assertEquals(minor,v.getMinor());
    	assertEquals(subversion,v.getSubversion());
    	if(!StringUtil.equals(qualifier,v.getQualifier())) { // Trick to display the error when not string equal
    		assertEquals(qualifier,v.getQualifier());
    	}
    	assertEquals(asString, v.toString());
    }

    public void testEquals() throws Exception {
    	assertTrue(isEquals("1","1"));
    	assertTrue(isEquals("1.1","1.1"));
    	assertTrue(isEquals("0.0.0","0.0.0"));
    	assertTrue(isEquals("0.0.1","0.0.1"));
    	assertTrue(isEquals("0.1.1","0.1.1"));
    	assertTrue(isEquals("1.1.1","1.1.1"));
    	
    	assertFalse(isEquals("0","1"));
    	assertFalse(isEquals("1.0","1.1"));
    	assertFalse(isEquals("1.1.0","1.1.1"));
    }
    private boolean isEquals(String v1,String v2) {
    	return Version.parse(v1).equals(Version.parse(v2));
    }

    public void testLessThan() throws Exception {
    	assertTrue(!isLessThan("0","0"));
    	assertTrue(isLessThan("0","1"));
    	assertTrue(!isLessThan("1","1"));
    	assertTrue(isLessThan("1","2"));

    	assertTrue(!isLessThan("0.0","0.0"));
    	assertTrue(isLessThan("0.0","1.0"));
    	assertTrue(!isLessThan("1.0","1.0"));
    	assertTrue(isLessThan("1.0","2.0"));

    	assertTrue(!isLessThan("1.0","1.0"));
    	assertTrue(isLessThan("1.0","1.1"));
    	assertTrue(!isLessThan("1.1","1.1"));
    	assertTrue(isLessThan("1.1","1.2"));
    	
    	assertTrue(!isLessThan("1.0.0","1.0.0"));
    	assertTrue(isLessThan("1.0.0","1.1.0"));
    	assertTrue(!isLessThan("1.1.0","1.1.0"));
    	assertTrue(isLessThan("1.1.0","1.2.0"));

    	assertTrue(isLessThan("1.1.0","2.1.0"));
    	assertTrue(isLessThan("1.1.0","2.1.1"));
    	assertTrue(isLessThan("1.1.1","2.1.1"));
    	assertTrue(isLessThan("1.1.1","2.1.2"));

    	assertTrue(!isLessThan("2.1.0","1.1.0"));
    	assertTrue(!isLessThan("2.1.0","1.1.1"));
    	assertTrue(!isLessThan("2.1.1","1.1.1"));
    	assertTrue(!isLessThan("2.1.1","1.1.2"));
    }
    private boolean isLessThan(String v1,String v2) {
    	return Version.parse(v1).lessThan(Version.parse(v2));
    }
    
    public void testLessOrEqualThan() throws Exception {
    	assertTrue(isLessOrEqualThan("0","0"));
    	assertTrue(isLessOrEqualThan("0","1"));
    	assertTrue(isLessOrEqualThan("1","1"));
    	assertTrue(isLessOrEqualThan("1","2"));

    	assertTrue(isLessOrEqualThan("0.0","0.0"));
    	assertTrue(isLessOrEqualThan("0.0","1.0"));
    	assertTrue(isLessOrEqualThan("1.0","1.0"));
    	assertTrue(isLessOrEqualThan("1.0","2.0"));

    	assertTrue(isLessOrEqualThan("1.0","1.0"));
    	assertTrue(isLessOrEqualThan("1.0","1.1"));
    	assertTrue(isLessOrEqualThan("1.1","1.1"));
    	assertTrue(isLessOrEqualThan("1.1","1.2"));
    	
    	assertTrue(isLessOrEqualThan("1.0.0","1.0.0"));
    	assertTrue(isLessOrEqualThan("1.0.0","1.1.0"));
    	assertTrue(isLessOrEqualThan("1.1.0","1.1.0"));
    	assertTrue(isLessOrEqualThan("1.1.0","1.2.0"));

    	assertTrue(isLessOrEqualThan("1.1.0","2.1.0"));
    	assertTrue(isLessOrEqualThan("1.1.0","2.1.1"));
    	assertTrue(isLessOrEqualThan("1.1.1","2.1.1"));
    	assertTrue(isLessOrEqualThan("1.1.1","2.1.2"));

    	assertTrue(!isLessOrEqualThan("2.1.0","1.1.0"));
    	assertTrue(!isLessOrEqualThan("2.1.0","1.1.1"));
    	assertTrue(!isLessOrEqualThan("2.1.1","1.1.1"));
    	assertTrue(!isLessOrEqualThan("2.1.1","1.1.2"));
    }
    private boolean isLessOrEqualThan(String v1,String v2) {
    	Version p1 = Version.parse(v1);
    	Version p2 = Version.parse(v2);
    	return p1.lessOrEqualThan(p2);
    }
    
    public void testGreaterThan() throws Exception {
    	assertTrue(!isGreaterThan("0","0"));
    	assertTrue(!isGreaterThan("0","1"));
    	assertTrue(!isGreaterThan("1","1"));
    	assertTrue(!isGreaterThan("1","2"));

    	assertTrue(!isGreaterThan("0.0","0.0"));
    	assertTrue(!isGreaterThan("0.0","1.0"));
    	assertTrue(!isGreaterThan("1.0","1.0"));
    	assertTrue(!isGreaterThan("1.0","2.0"));

    	assertTrue(!isGreaterThan("1.0","1.0"));
    	assertTrue(!isGreaterThan("1.0","1.1"));
    	assertTrue(!isGreaterThan("1.1","1.1"));
    	assertTrue(!isGreaterThan("1.1","1.2"));
    	
    	assertTrue(!isGreaterThan("1.0.0","1.0.0"));
    	assertTrue(!isGreaterThan("1.0.0","1.1.0"));
    	assertTrue(!isGreaterThan("1.1.0","1.1.0"));
    	assertTrue(!isGreaterThan("1.1.0","1.2.0"));

    	assertTrue(!isGreaterThan("1.1.0","2.1.0"));
    	assertTrue(!isGreaterThan("1.1.0","2.1.1"));
    	assertTrue(!isGreaterThan("1.1.1","2.1.1"));
    	assertTrue(!isGreaterThan("1.1.1","2.1.2"));

    	assertTrue(isGreaterThan("2.1.0","1.1.0"));
    	assertTrue(isGreaterThan("2.1.0","1.1.1"));
    	assertTrue(isGreaterThan("2.1.1","1.1.1"));
    	assertTrue(isGreaterThan("2.1.1","1.1.2"));
    }
    private boolean isGreaterThan(String v1,String v2) {
    	return Version.parse(v1).greaterThan(Version.parse(v2));
    }
    
    public void testGreaterOrEqualThan() throws Exception {
    	assertTrue(isGreaterOrEqualThan("0","0"));
    	assertTrue(!isGreaterOrEqualThan("0","1"));
    	assertTrue(isGreaterOrEqualThan("1","1"));
    	assertTrue(!isGreaterOrEqualThan("1","2"));

    	assertTrue(isGreaterOrEqualThan("0.0","0.0"));
    	assertTrue(!isGreaterOrEqualThan("0.0","1.0"));
    	assertTrue(isGreaterOrEqualThan("1.0","1.0"));
    	assertTrue(!isGreaterOrEqualThan("1.0","2.0"));

    	assertTrue(isGreaterOrEqualThan("1.0","1.0"));
    	assertTrue(!isGreaterOrEqualThan("1.0","1.1"));
    	assertTrue(isGreaterOrEqualThan("1.1","1.1"));
    	assertTrue(!isGreaterOrEqualThan("1.1","1.2"));
    	
    	assertTrue(isGreaterOrEqualThan("1.0.0","1.0.0"));
    	assertTrue(!isGreaterOrEqualThan("1.0.0","1.1.0"));
    	assertTrue(isGreaterOrEqualThan("1.1.0","1.1.0"));
    	assertTrue(!isGreaterOrEqualThan("1.1.0","1.2.0"));

    	assertTrue(!isGreaterOrEqualThan("1.1.0","2.1.0"));
    	assertTrue(!isGreaterOrEqualThan("1.1.0","2.1.1"));
    	assertTrue(!isGreaterOrEqualThan("1.1.1","2.1.1"));
    	assertTrue(!isGreaterOrEqualThan("1.1.1","2.1.2"));

    	assertTrue(isGreaterOrEqualThan("2.1.0","1.1.0"));
    	assertTrue(isGreaterOrEqualThan("2.1.0","1.1.1"));
    	assertTrue(isGreaterOrEqualThan("2.1.1","1.1.1"));
    	assertTrue(isGreaterOrEqualThan("2.1.1","1.1.2"));
    }
    private boolean isGreaterOrEqualThan(String v1,String v2) {
    	return Version.parse(v1).greaterOrEqualThan(Version.parse(v2));
    }

    public void testAtLeast() throws Exception {
    	assertTrue(isAtLeast("0","0"));
    	assertTrue(!isAtLeast("0","1"));
    	assertTrue(isAtLeast("1","1"));
    	assertTrue(!isAtLeast("1","2"));

    	assertTrue(isAtLeast("0.0","0.0"));
    	assertTrue(!isAtLeast("0.0","1.0"));
    	assertTrue(isAtLeast("1.0","1.0"));
    	assertTrue(!isAtLeast("1.0","2.0"));

    	assertTrue(isAtLeast("1.0","1.0"));
    	assertTrue(!isAtLeast("1.0","1.1"));
    	assertTrue(isAtLeast("1.1","1.1"));
    	assertTrue(!isAtLeast("1.1","1.2"));
    	
    	assertTrue(isAtLeast("1.0.0","1.0.0"));
    	assertTrue(!isAtLeast("1.0.0","1.1.0"));
    	assertTrue(isAtLeast("1.1.0","1.1.0"));
    	assertTrue(!isAtLeast("1.1.0","1.2.0"));

    	assertTrue(!isAtLeast("1.1.0","2.1.0"));
    	assertTrue(!isAtLeast("1.1.0","2.1.1"));
    	assertTrue(!isAtLeast("1.1.1","2.1.1"));
    	assertTrue(!isAtLeast("1.1.1","2.1.2"));

    	assertTrue(isAtLeast("2.1.0","1.1.0"));
    	assertTrue(isAtLeast("2.1.0","1.1.1"));
    	assertTrue(isAtLeast("2.1.1","1.1.1"));
    	assertTrue(isAtLeast("2.1.1","1.1.2"));
    }
    private boolean isAtLeast(String v1,String v2) {
    	return Version.parse(v1).isAtLeast(Version.parse(v2));
    }

    public void testQualifier() throws Exception {
    	// A qualifier may follow any number of components
    	Version v = Version.parse("1.0-beta");
    	assertEquals(1, v.getMajor());
    	assertEquals(0, v.getMinor());
    	assertEquals("beta", v.getQualifier());
    	assertEquals("rc", Version.parse("1-rc").getQualifier());
    	assertEquals("SNAPSHOT", Version.parse("1.2.3-SNAPSHOT").getQualifier());

    	// The qualifier takes part in equality and ordering: a pre-release sorts before the release
    	assertFalse(Version.parse("1.0.0-SNAPSHOT").equals(Version.parse("1.0.0")));
    	assertTrue(Version.parse("1.0.0").equals(Version.parse("1.0.0")));
    	assertTrue(Version.parse("1.0.0-SNAPSHOT").equals(Version.parse("1.0.0-SNAPSHOT")));
    	assertEquals(Version.parse("1.0.0-SNAPSHOT").hashCode(), Version.parse("1.0.0-SNAPSHOT").hashCode());
    	assertTrue(Version.parse("1.0.0-SNAPSHOT").lessThan(Version.parse("1.0.0")));
    	assertTrue(Version.parse("1.0.0").greaterThan(Version.parse("1.0.0-SNAPSHOT")));
    	assertTrue(Version.parse("1.0.0-SNAPSHOT").compareTo(Version.parse("1.0.0"))<0);
    	assertTrue(Version.parse("1.0.0-alpha").compareTo(Version.parse("1.0.0-beta"))<0);
    	assertTrue(Version.parse("1.0.1-SNAPSHOT").greaterThan(Version.parse("1.0.0")));
    	assertEquals(0, Version.parse("1.0.0").compareTo(Version.parse("1.0.0")));

    	assertTrue(Version.VERSION_PATTERN.matcher("1.2.3-SNAPSHOT").matches());
    	assertTrue(Version.VERSION_PATTERN.matcher("1.2").matches());
    	assertFalse(Version.VERSION_PATTERN.matcher("1.2.3.4").matches());
    	assertFalse(Version.VERSION_PATTERN.matcher("a.b").matches());
    }

    public void testQualifierSemantics() throws Exception {
    	Version snapshot = Version.parse("1.0.0-SNAPSHOT");
    	Version release = Version.parse("1.0.0");
    	// isAtLeast compares the numbers only (documented): a snapshot is "at least" its version
    	assertTrue(snapshot.isAtLeast(release));
    	assertTrue(snapshot.isAtLeast(1,0,0));
    	assertFalse(snapshot.isAtLeast(1,0,1));
    	// ... while the ordering takes the qualifier into account
    	assertTrue(snapshot.compareTo(release)<0);
    	assertFalse(snapshot.greaterOrEqualThan(release));
    	// 0.0.0-rc is a real version, not the empty one
    	assertTrue(Version.parse("0.0.0").isEmpty());
    	assertTrue(Version.parse("").isEmpty());
    	assertFalse(Version.parse("0.0.0-rc").isEmpty());
    	assertTrue(Version.parse("0.0.0-").isEmpty());
    }
}
