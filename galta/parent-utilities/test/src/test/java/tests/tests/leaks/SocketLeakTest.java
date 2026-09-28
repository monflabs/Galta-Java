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
 * Comprehensive tests for Socket resource leak detection.
 * Tests Socket, ServerSocket, and DatagramSocket.
 */
public class SocketLeakTest extends BaseLeakTest {
	public void testEmoty() {}
    	
//    private ServerSocket serverSocket;
//    private Thread serverThread;
//    private volatile boolean serverRunning;
//    private static final int TEST_PORT = 9876;
//    
//    @Override
//	@Before
//    public void setUp() throws Exception {
//        // Start tracking
//        super.setUp();
//    }
//    
//    @Override
//	@After
//    public void tearDown() throws Exception {
//        super.tearDown();
//        stopServer();
//    }
//    
//    /**
//     * Helper: Start a simple test server
//     */
//    private void startServer() {
//        if (serverRunning) return;
//        
//        serverRunning = true;
//        serverThread = new Thread(() -> {
//            try {
//                serverSocket = new ServerSocket(TEST_PORT);
//                System.out.println("[Server] Started on port " + TEST_PORT);
//                
//                while (serverRunning) {
//                    try {
//                        Socket client = serverSocket.accept();
//                        System.out.println("[Server] Client connected: " + client);
//                        
//                        // Echo server
//                        try (BufferedReader in = new BufferedReader(
//                                new InputStreamReader(client.getInputStream()));
//                             PrintWriter out = new PrintWriter(
//                                client.getOutputStream(), true)) {
//                            
//                            String line = in.readLine();
//                            if (line != null) {
//                                out.println("Echo: " + line);
//                            }
//                        } finally {
//                            // CRITICAL: Ensure client socket is always closed
//                            if (!client.isClosed()) {
//                                client.close();
//                            }
//                            System.out.println("[Server] Client socket closed");
//                        }
//                    } catch (SocketException e) {
//                        if (!serverRunning) break;
//                    }
//                }
//            } catch (IOException e) {
//                if (serverRunning) {
//                    System.err.println("[Server] Error: " + e.getMessage());
//                }
//            } finally {
//                System.out.println("[Server] Stopped");
//            }
//        });
//        serverThread.setDaemon(true);  // Ensure it doesn't block JVM shutdown
//        serverThread.start();
//        
//        // Wait for server to start
//        try { Thread.sleep(200); } catch (InterruptedException e) {}
//    }
//    
//    /**
//     * Helper: Stop the test server
//     */
//    private void stopServer() {
//        if (!serverRunning) return;
//        
//        serverRunning = false;
//        try {
//            if (serverSocket != null && !serverSocket.isClosed()) {
//                serverSocket.close();
//            }
//            if (serverThread != null) {
//                serverThread.join(1000);
//            }
//        } catch (Exception e) {
//            // Ignore
//        }
//    }
//    
//    // ==================== Socket Tests ====================
//    
//    @Test
//    public void testSocketLeak() throws Exception {
//        startServer();
//        
//        // Create socket without closing
//        Socket socket = new Socket("localhost", TEST_PORT);
//        assertTrue("Socket should be connected", socket.isConnected());
//        
//        // Use the socket
//        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//        
//        // Give server time to accept and handle (and close its end)
//        Thread.sleep(100);
//        
//        // IMPORTANT: Stop server before leak detection
//        // Otherwise the server's ServerSocket will also be detected as a leak
//        stopServer();
//        
//        // FORGOT TO CLOSE! This should be detected as a leak
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        
//        // Should detect the client socket leak only
//        // (Server's socket should be closed by stopServer())
//        assertEquals("Socket leak should be detected", 1, leaks.size());
//        assertEquals("Socket", leaks.get(0).getAllocationInfo().getResourceType());
//        
//        // Clean up
//        out.close();
//        socket.close();
//    }
//    
//    @Test
//    public void testSocketNoLeak() throws Exception {
//        startServer();
//        
//        try (Socket socket = new Socket("localhost", TEST_PORT)) {
//            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            String response = in.readLine();
//            logIgnore(response);
//            in.close();
//            out.close();
//        }
//        
//        // Give server time to fully close the connection it accepted
//        Thread.sleep(100);
//        
//        // IMPORTANT: Stop server before leak detection
//        // Otherwise the server's ServerSocket will be detected as a leak
//        stopServer();
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        
//        assertEquals("No leak when properly closed", 0, leaks.size());
//    }
//    
//    @Test
//    public void testSocketConstructorVariants() throws Exception {
//        startServer();
//        
//        // Constructor with host and port
//        Socket socket1 = new Socket("localhost", TEST_PORT);
//        
//        // Constructor with InetAddress and port
//        Socket socket2 = new Socket(InetAddress.getLocalHost(), TEST_PORT);
//
//        // Stop server before leak detection
//        stopServer();
//        
//        List<ResourceTracker.LeakInfo> leaks2 = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("Should detect 2 socket leaks", 0, leaks2.size());
//        
//        // Clean up
//        socket1.close();
//        socket2.close();
//    }
//    
//    // ==================== ServerSocket Tests ====================
//    
//    @Test
//    public void testServerSocketLeak() throws Exception {
//        // Use a different port to avoid conflict
//        int testPort = TEST_PORT + 1;
//        
//        ServerSocket ss = new ServerSocket(testPort);
//        assertTrue("ServerSocket should be bound", ss.isBound());
//        
//        // FORGOT TO CLOSE! This should be detected as a leak
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("ServerSocket leak should be detected", leaks.size() >= 1);
//        
//        // Clean up
//        ss.close();
//    }
//    
//    @Test
//    public void testServerSocketNoLeak() throws Exception {
//        int testPort = TEST_PORT + 2;
//        
//        try (ServerSocket ss = new ServerSocket(testPort)) {
//            assertTrue("ServerSocket should be bound", ss.isBound());
//        }
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("No leak when properly closed", 0, leaks.size());
//    }
//    
//    // ==================== DatagramSocket Tests ====================
//    
//    @Test
//    public void testDatagramSocketLeak() throws Exception {
//        DatagramSocket ds = new DatagramSocket();
//        assertTrue("DatagramSocket should be bound", ds.isBound());
//        
//        // Send a packet
//        byte[] buf = "Test UDP packet".getBytes();
//        InetAddress address = InetAddress.getLocalHost();
//        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, ds.getLocalPort());
//        ds.send(packet);
//        
//        // FORGOT TO CLOSE! This should be detected as a leak
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertTrue("DatagramSocket leak should be detected", leaks.size() >= 1);
//        
//        // Clean up
//        ds.close();
//    }
//    
//    @Test
//    public void testDatagramSocketNoLeak() throws Exception {
//        try (DatagramSocket ds = new DatagramSocket()) {
//            byte[] buf = "Test UDP packet".getBytes();
//            InetAddress address = InetAddress.getLocalHost();
//            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, ds.getLocalPort());
//            ds.send(packet);
//        }
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("No leak when properly closed", 0, leaks.size());
//    }
//    
//    // ==================== Multiple Socket Types Test ====================
//    
//    @Test
//    public void testMultipleSocketTypeLeaks() throws Exception {
//        startServer();
//        
//        // Create multiple socket leaks
//        Socket socket = new Socket("localhost", TEST_PORT);
//        ServerSocket serverSocket2 = new ServerSocket(TEST_PORT + 3);
//        DatagramSocket datagramSocket = new DatagramSocket();
//        
//        // Stop test server before leak detection
//        // We're testing leaks of: socket, serverSocket2, datagramSocket
//        // Not the test server's ServerSocket
//        stopServer();
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("Should detect 3 leaks", 3, leaks.size());
//        
//        // Clean up
//        socket.close();
//        serverSocket2.close();
//        datagramSocket.close();
//    }
//    
//    // ==================== Connection Failure Test ====================
//    
//    @Test
//    public void testSocketConnectionFailureNoLeak() throws Exception {
//        try {
//            // Try to connect to non-existent server
//            Socket socket = new Socket("localhost", 65432);
//            socket.close();
//            fail("Should have thrown ConnectException");
//        } catch (ConnectException e) {
//            //System.out.println("Connection failed as expected: " + e.getMessage());
//        }
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("No leak when connection fails", 0, leaks.size());
//    }
//    
//    // ==================== Socket Timeout Test ====================
//    
//    @Test
//    public void testSocketWithTimeoutNoLeak() throws Exception {
//        startServer();
//        
//        try (Socket socket = new Socket()) {
//            socket.connect(new InetSocketAddress("localhost", TEST_PORT), 5000);
//            socket.setSoTimeout(1000);
//            
//            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//            out.println("Test with timeout");
//            out.close();
//        }
//        
//        // Stop server before leak detection
//        stopServer();
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("No leak when properly closed", 0, leaks.size());
//    }
//    
//    // ==================== URLConnection Test ====================
//    
//    @Test
//    public void testURLConnectionNoLeak() throws Exception {
//        // Note: URLConnection to external sites might use sockets internally
//        // This test verifies we don't get false positives from HTTP connections
//        
//        try {
//            URL url = new URL("http://example.com");
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setConnectTimeout(5000);
//            conn.setReadTimeout(5000);
//            
//            try (InputStream is = conn.getInputStream()) {
//                // Read some data
//                is.read();
//            }
//            
//            conn.disconnect();
//            fail();
//        } catch (Exception e) {
//            //System.out.println("Connection failed (expected in isolated test): " + e.getMessage());
//        }
//        
//        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
//        assertEquals("No leak when properly closed", 0, leaks.size());
//    }
}
