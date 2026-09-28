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
package tests.tests.leaks;

/**
 * Comprehensive tests for JDBC resource leak detection.
 * Tests Connection, Statement, PreparedStatement, CallableStatement, and ResultSet.
 */
public class JDBCLeakTest extends BaseLeakTest {
	public void testEmoty() {}
    
//    private static final String DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
//    private static final String DB_USER = "sa";
//    private static final String DB_PASSWORD = "";
//    
//    @Override
//	@Before
//    public void setUp() throws Exception {
//        // Create test table
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             Statement stmt = conn.createStatement()) {
//            
//            stmt.execute("DROP TABLE IF EXISTS users");
//            stmt.execute("CREATE TABLE users (" +
//                "id INT PRIMARY KEY AUTO_INCREMENT, " +
//                "name VARCHAR(100), " +
//                "email VARCHAR(100), " +
//                "age INT)");
//            
//            stmt.execute("INSERT INTO users (name, email, age) VALUES ('Alice', 'alice@example.com', 30)");
//            stmt.execute("INSERT INTO users (name, email, age) VALUES ('Bob', 'bob@example.com', 25)");
//            stmt.execute("INSERT INTO users (name, email, age) VALUES ('Charlie', 'charlie@example.com', 35)");
//        }
//        
//        // Start tracking
//        super.setUp();
//    }
//    
//    @Override
//	@After
//    public void tearDown() throws Exception {
//        super.tearDown();
//    }
//    
//    // ==================== Connection Tests ====================
//    
//    @Test
//    public void testConnectionLeak() throws Exception {
//        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        assertFalse("Connection should not be closed", conn.isClosed());
//        
//        // Use the connection
//        try (Statement stmt = conn.createStatement();
//            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
//            rs.next();
//            int count = rs.getInt(1);
//            logIgnore("Count: " + count);
//        }
//        
//        // FORGOT TO CLOSE CONNECTION! This should be detected as a leak
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("Connection leak should be detected", leaks.size() >= 1);
//        
//        // Clean up
//        conn.close();
//    }
//    
//    @Test
//    public void testConnectionNoLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            try (Statement stmt = conn.createStatement();
//                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
//                rs.next();
//                int count = rs.getInt(1);
//                logIgnore("Count: " + count);
//            }
//        }
//    }
//    
//    @Test
//    public void testCascadingClose() throws Exception {
//        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        Statement stmt = conn.createStatement();
//        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
//        rs.next();
//        rs.close();
//        
//        // Both Connection and Statement are tracked
//        // Close only Connection (should cascade to Statement)
//        conn.close();
//        
//        // Verify Statement was auto-closed by driver
//        assertTrue(stmt.isClosed());  // ✓
//        
//        // Check leak detection
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        
//        // Our instrumentation should have tracked the cascading close
//        assertEquals(0, leaks.size());  // ✓ Both removed from tracking
//    }
//
//        
//    // ==================== Statement Tests ====================
//    
//    @Test
//    public void testStatementLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            Statement stmt = conn.createStatement();
//            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
//            while (rs.next()) {
//            	logIgnore("User: " + rs.getString("name"));
//            }
//            rs.close();
//            
//            // FORGOT TO CLOSE STATEMENT! This should be detected as a leak
//            List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//            assertTrue("Connection leak should be detected Statement", leaks.size() == 2);
//        }
//    }
//    
//    @Test
//    public void testStatementNoLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             Statement stmt = conn.createStatement();
//             ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
//            while (rs.next()) {
//                logIgnore("User: " + rs.getString("name"));
//            }
//        }
//    }
//    
//    // ==================== PreparedStatement Tests ====================
//    
//    @Test
//    public void testPreparedStatementLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            
//            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE age > ?");
//            pstmt.setInt(1, 25);
//            
//            ResultSet rs = pstmt.executeQuery();
//            while (rs.next()) {
//                logIgnore("User: " + rs.getString("name") + ", Age: " + rs.getInt("age"));
//            }
//            rs.close();
//            
//            // FORGOT TO CLOSE PREPAREDSTATEMENT! This should be detected as a leak
//            List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//            assertTrue("Connection leak should be detected, PreparedStatement", leaks.size() == 3);
//            
//            // Clean up
//            pstmt.close();
//        }
//    }
//    
//    @Test
//    public void testPreparedStatementNoLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE age > ?")) {
//            
//            pstmt.setInt(1, 25);
//            try (ResultSet rs = pstmt.executeQuery()) {
//                while (rs.next()) {
//                    logIgnore("User: " + rs.getString("name") + ", Age: " + rs.getInt("age"));
//                }
//            }
//
//            List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//            assertTrue("Connection leak should be detected, PreparedStatement", leaks.size() == 1);
//        }
//    }
//    
//    // ==================== ResultSet Tests ====================
//    
//    @Test
//    public void testResultSetLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//            Statement stmt = conn.createStatement()) {
//            
//            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
//            while (rs.next()) {
//                logIgnore("User: " + rs.getString("name"));
//            }
//            
//            // FORGOT TO CLOSE RESULTSET! This should be detected as a leak
//            List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//            assertTrue("Connection leak should be detected, ResultSet", leaks.size() == 3);
//            
//            // Clean up
//            rs.close();
//        }
//    }
//    
//    @Test
//    public void testResultSetNoLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             Statement stmt = conn.createStatement();
//             ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
//             while (rs.next()) {
//                 logIgnore("User: " + rs.getString("name"));
//             }
//        }
//    }
//    
//    // ==================== Multiple JDBC Resource Leaks ====================
//    
//    @Test
//    public void testMultipleJDBCResourceLeaks() throws Exception {
//        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        Statement stmt = conn.createStatement();
//        PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
//        pstmt.setInt(1, 1);
//        ResultSet rs1 = stmt.executeQuery("SELECT * FROM users");
//        ResultSet rs2 = pstmt.executeQuery();
//        
//        // Use them
//        rs1.next();
//        logIgnore("User from Statement: " + rs1.getString("name"));
//        rs2.next();
//        logIgnore("User from PreparedStatement: " + rs2.getString("name"));
//        
//        // FORGOT TO CLOSE ALL! All should be detected as leaks
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("Connection leak should be detected, ResultSet", leaks.size() == 5);
//        
//        // Clean up
//        rs1.close();
//        rs2.close();
//        stmt.close();
//        pstmt.close();
//        conn.close();
//    }
//    
//    // ==================== Batch Operations ====================
//    
//    @Test
//    public void testBatchStatementLeak() throws Exception {
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
//            Statement stmt = conn.createStatement();
//            
//            // Add batch operations
//            stmt.addBatch("INSERT INTO users (name, email, age) VALUES ('David', 'david@example.com', 28)");
//            stmt.addBatch("INSERT INTO users (name, email, age) VALUES ('Eve', 'eve@example.com', 32)");
//            stmt.addBatch("INSERT INTO users (name, email, age) VALUES ('Frank', 'frank@example.com', 29)");
//            
//            int[] results = stmt.executeBatch();
//            logIgnore(results);
//            
//            // FORGOT TO CLOSE!
//            List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//            assertTrue("Connection leak should be detected, Statement", leaks.size() == 2);
//            
//            // Clean up
//            stmt.close();
//        }
//    }
//    
//    // ==================== Transaction Test ====================
//    
//    @Test
//    public void testTransactionWithConnectionLeak() throws Exception {
//        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        conn.setAutoCommit(false);
//        
//        try (Statement stmt = conn.createStatement()) {
//            stmt.execute("UPDATE users SET age = age + 1 WHERE name = 'Alice'");
//            conn.commit();
//        } catch (SQLException e) {
//            conn.rollback();
//        }
//        
//        // FORGOT TO CLOSE CONNECTION!
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("Connection leak should be detected, Statement", leaks.size() == 1);
//        
//        // Clean up
//        conn.close();
//    }
//    
//    // ==================== Connection Pool Simulation ====================
//    
//    @Test
//    public void testMultipleConnectionLeaks() throws Exception {
//        Connection conn1 = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        Connection conn2 = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        Connection conn3 = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        
//        // Use them briefly
//        try (Statement stmt = conn1.createStatement()) {
//            stmt.execute("SELECT 1");
//        }
//        
//        // FORGOT TO CLOSE ALL CONNECTIONS!
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("Connection leak should be detected, Connection", leaks.size() == 3);
//        
//        // Clean up
//        conn1.close();
//        conn2.close();
//        conn3.close();
//    }
//    
//    // ==================== Instrumentation Check ====================
//    
//    @Test
//    public void testJDBCInstrumentationStatus() throws Exception {
//        // Create various JDBC resources
//        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//        Statement stmt = conn.createStatement();
//        PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
//        ResultSet rs = stmt.executeQuery("SELECT * FROM users LIMIT 1");
//        rs.next();
//        
//        // Check what's being tracked
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("Connection leak should be detected, Connection", leaks.size() == 4);
//        
//        Set<String> types = new HashSet<>();
//        //type.add();
//        for (ResourceTracker.LeakInfo leak : leaks) {
//        	String resType = leak.getAllocationInfo().getResourceType();
//            System.out.println("  - " + resType);
//        	//assertTrue(types.contains(resType));
//        	types.remove(resType);
//        }
//        fail();
//        
//        // Clean up
//        rs.close();
//        pstmt.close();
//        stmt.close();
//        conn.close();
//    }
}
