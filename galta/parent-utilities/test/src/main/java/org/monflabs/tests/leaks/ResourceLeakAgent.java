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
package org.monflabs.tests.leaks;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.monflabs.tests.leaks.BootstrapHelper.debug;
import static org.monflabs.tests.leaks.BootstrapHelper.error;
import static org.monflabs.tests.leaks.BootstrapHelper.info;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.instrument.Instrumentation;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.ZipFile;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

/**
 * Byte Buddy agent that instruments resource classes to track allocations and closures.
 */
public class ResourceLeakAgent {
    private static volatile boolean installed = false;
    
    public static boolean INSTRUMENT_FILE = true;
    public static boolean INSTRUMENT_PATH = true;
    public static boolean INSTRUMENT_ZIP= true;
    
    public static boolean INSTRUMENT_SOCKET = false; // not reliable
    public static boolean INSTRUMENT_JDBC = false; // not reliable
    
    public static boolean isInstalled() {
    	return installed;
    }
    
    /**
     * Install the agent and instrument resource classes.
     * This uses ByteBuddyAgent.install() to attach at runtime and enables retransformation.
     */
    public static synchronized void install() {
        if (installed) {
            debug("[Agent] Already installed, skipping");
            return;
        }
        
        debug("[Agent] Starting installation...");
        
        // Install ByteBuddy agent with retransformation support
        ByteBuddyAgent.install();
        debug("[Agent] ✓ ByteBuddy agent installed");
        
        try {
            // Add BootstrapHelper to bootstrap classloader
        	debug("[Agent] Adding BootstrapHelper to bootstrap classloader...");
            addBootstrapHelperToBootstrapClassLoader();
            
            // NOTE: We don't call BootstrapHelper.initialize() here!
            // It will initialize LAZILY when first called from the bootstrap classloader.
            // This avoids the double-loading issue where BootstrapHelper gets loaded
            // in both the application CL (from this call) and bootstrap CL (from FileInputStream).
            debug("[Agent] ✓ BootstrapHelper will initialize on first use");
            
            // Use explicit RETRANSFORMATION strategy to handle already-loaded classes
            debug("[Agent] Creating retransformation strategy...");
            ClassReloadingStrategy strategy = 
                ClassReloadingStrategy.fromInstalledAgent(
                    ClassReloadingStrategy.Strategy.RETRANSFORMATION);
            
            // Now we CAN instrument bootstrap classes!
            debug("[Agent] Instrumenting resource classes...");

            /*
            * NOTE: 
            * - BufferedStreams are just WRAPPERS and don't hold OS resources themselves.
            *   We should NOT instrument them to avoid double-counting.
            *   The underlying FileInputStream/FileOutputStream will be tracked instead.
            * - FileReader is a WRAPPER around FileInputStream - we track the FileInputStream instead.
            *   This avoids double-tracking and follows the "resource holders only" principle.
            * - FileWriter is a WRAPPER around FileOutputStream - we track the FileOutputStream instead.
            *   This avoids double-tracking and follows the "resource holders only" principle.
            */
            if(INSTRUMENT_FILE) {
            	instrumentFileStreams(strategy);
            	instrumentRandomAccessFile(strategy);
            }
            if(INSTRUMENT_PATH) {
            	instrumentFileChannel(strategy);
            }
            if(INSTRUMENT_ZIP) {
            	instrumentZipFile(strategy);
            }
            
            if(INSTRUMENT_SOCKET) {
            	instrumentSocket(strategy);
            }
            
            if(INSTRUMENT_JDBC) {
            	instrumentJdbc(strategy);
            }
            
            installed = true;
            info("[Agent] ✓✓✓ INSTALLATION COMPLETE ✓✓✓");
            
        } catch (Exception e) {
            error("[Agent] !!!! INSTALLATION FAILED !!!! ");
            e.printStackTrace();
            throw new RuntimeException("Failed to install resource leak detection agent", e);
        }
    }
    
    /**
     * Add BootstrapHelper to the bootstrap classloader.
     * We use a simpler approach: create an in-memory JAR with just BootstrapHelper.
     */
    private static void addBootstrapHelperToBootstrapClassLoader() {
        try {
            // Get the class file for BootstrapHelper
            String className = BootstrapHelper.class.getName();
            String classAsPath = className.replace('.', '/') + ".class";
            java.io.InputStream classStream = BootstrapHelper.class.getClassLoader().getResourceAsStream(classAsPath);
            
            if (classStream == null) {
                throw new RuntimeException("Could not find BootstrapHelper class file");
            }
            
            // Read the class file
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = classStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            classStream.close();
            byte[] classBytes = buffer.toByteArray();
            
            // Create a temporary JAR file
            java.io.File tempJar = java.io.File.createTempFile("bootstrap-helper", ".jar");
            tempJar.deleteOnExit();
            
            // Write the JAR
            try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                    new java.io.FileOutputStream(tempJar))) {
                
                // Add BootstrapHelper class
                java.util.jar.JarEntry entry = new java.util.jar.JarEntry(classAsPath);
                jos.putNextEntry(entry);
                jos.write(classBytes);
                jos.closeEntry();
            }
            
            // Add to bootstrap classloader
            ByteBuddyAgent.getInstrumentation()
                .appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(tempJar));
            
            info("✓ BootstrapHelper added to bootstrap classloader");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to add BootstrapHelper to bootstrap classloader", e);
        }
    }
    
    /**
     * Instrument file stream classes (including bootstrap classes).
     * Now works because BootstrapHelper is on the bootstrap classpath!
     * Uses specialized advice to capture file paths.
     */
    private static void instrumentFileStreams(ClassReloadingStrategy strategy) throws Exception {
        // FileInputStream - THIS IS A BOOTSTRAP CLASS
        new ByteBuddy()
            .redefine(FileInputStream.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))))
            .make()
            .load(FileInputStream.class.getClassLoader(), strategy);  // Fixed: was FileOutputStream
        debug("[Agent] ✓ FileInputStream instrumented (constructor + close)");
        
        // FileOutputStream - THIS IS A BOOTSTRAP CLASS
        new ByteBuddy()
            .redefine(FileOutputStream.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))))
            .make()
            .load(FileOutputStream.class.getClassLoader(), strategy);
        debug("[Agent] ✓ FileOutputStream instrumented (constructor + close)");
    }
    
    /**
     * Instrument FileChannel for Files API support.
     * Files.newInputStream() and Files.newOutputStream() use FileChannel internally.
     * 
     * CRITICAL: We must instrument AbstractInterruptibleChannel.close(), NOT FileChannelImpl.close()!
     * FileChannelImpl inherits close() from AbstractInterruptibleChannel, so that's where
     * the actual close() method is defined.
     */
    private static void instrumentFileChannel(ClassReloadingStrategy strategy) throws Exception {
        // Instrument the concrete implementation for constructor tracking
        Class<?> fileChannelImplClass = Class.forName("sun.nio.ch.FileChannelImpl");
        new ByteBuddy()
            .redefine(fileChannelImplClass)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .make()
            .load(fileChannelImplClass.getClassLoader(), strategy);
        debug("[Agent] ✓ FileChannelImpl constructor instrumented");
        
        // Instrument AbstractInterruptibleChannel for close() tracking
        // This is where close() is actually defined!
        Class<?> abstractInterruptibleChannelClass = 
            Class.forName("java.nio.channels.spi.AbstractInterruptibleChannel");
        new ByteBuddy()
            .redefine(abstractInterruptibleChannelClass)
            .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))))
            .make()
            .load(abstractInterruptibleChannelClass.getClassLoader(), strategy);
        debug("[Agent] ✓ AbstractInterruptibleChannel.close() instrumented");
    }
    
    private static void instrumentRandomAccessFile(ClassReloadingStrategy strategy) throws Exception {
        // RandomAccessFile
        new ByteBuddy()
            .redefine(RandomAccessFile.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close")))
            .make()
            .load(RandomAccessFile.class.getClassLoader(), strategy);
        debug("[Agent] ✓ RandomAccessFile instrumented");
    }
    
    private static void instrumentZipFile(ClassReloadingStrategy strategy) throws Exception {
        // ZipFile
        new ByteBuddy()
            .redefine(ZipFile.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close")))
            .make()
            .load(ZipFile.class.getClassLoader(), strategy);
        debug("[Agent] ✓ ZipFile instrumented");
    }
    
    private static void instrumentSocket(ClassReloadingStrategy strategy) throws Exception {
        // Socket
        new ByteBuddy()
            .redefine(Socket.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close")))
            .make()
            .load(Socket.class.getClassLoader(), strategy);
        debug("[Agent] ✓ Socket instrumented");
        
        // ServerSocket
        new ByteBuddy()
            .redefine(ServerSocket.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close")))
            .make()
            .load(ServerSocket.class.getClassLoader(), strategy);
        debug("[Agent] ✓ ServerSocket instrumented");

        // DatagramSocket
        new ByteBuddy()
            .redefine(DatagramSocket.class)
            .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
            .visit(Advice.to(CloseAdvice.class).on(named("close")))
            .make()
            .load(DatagramSocket.class.getClassLoader(), strategy);
        debug("[Agent] ✓ DatagramSocket instrumented");
    }
    
    private static void instrumentJdbc(ClassReloadingStrategy strategy) throws Exception {
        Instrumentation instrumentation = ByteBuddyAgent.getInstrumentation();
        
        // Instrument ALL classes that implement java.sql.Connection
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(isSubTypeOf(Connection.class).and(not(isInterface())))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                debug("[Agent] ✓ Instrumenting Connection implementation: " + typeDescription.getName());
                return builder
                    .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
                    .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))));
            })
            .installOn(instrumentation);
        debug("[Agent] ✓ Connection instrumentation installed");
        
        // Instrument ALL classes that implement java.sql.Statement
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(isSubTypeOf(Statement.class).and(not(isInterface())))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                debug("[Agent] ✓ Instrumenting Statement implementation: " + typeDescription.getName());
                return builder
                    .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
                    .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))));
            })
            .installOn(instrumentation);
        debug("[Agent] ✓ Statement instrumentation installed");
        
        // Instrument ALL classes that implement java.sql.PreparedStatement
        // (PreparedStatement extends Statement, but we need to catch the specific implementations)
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(isSubTypeOf(java.sql.PreparedStatement.class).and(not(isInterface())))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                debug("[Agent] ✓ Instrumenting PreparedStatement implementation: " + typeDescription.getName());
                return builder
                    .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
                    .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))));
            })
            .installOn(instrumentation);
        debug("[Agent] ✓ PreparedStatement instrumentation installed");
        
        // Instrument ALL classes that implement java.sql.ResultSet
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(isSubTypeOf(ResultSet.class).and(not(isInterface())))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                debug("[Agent] ✓ Instrumenting ResultSet implementation: " + typeDescription.getName());
                return builder
                    .visit(Advice.to(ConstructorAdvice.class).on(isConstructor()))
                    .visit(Advice.to(CloseAdvice.class).on(named("close").and(takesArguments(0))));
            })
            .installOn(instrumentation);
        debug("[Agent] ✓ ResultSet instrumentation installed");
    }
    
    /**
     * Advice for constructors - records allocation.
     * Calls BootstrapHelper which is on the bootstrap classpath.
     */
    public static class ConstructorAdvice {
        @Advice.OnMethodExit
        public static void exit(@Advice.This Object thiz, 
                                @Advice.AllArguments Object[] args) {
            try {
                // Just pass the arguments - extraction happens in BootstrapHelper
                BootstrapHelper.recordAllocationWithArgs(thiz, thiz.getClass().getSimpleName(), args);
            } catch (Throwable t) {
                error("[FileStreamConstructorAdvice]: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }
    
    /**
     * Advice for close() methods - records closure.
     * Calls BootstrapHelper which is on the bootstrap classpath.
     */
    public static class CloseAdvice {
        @Advice.OnMethodEnter
        public static void enter(@Advice.This Object thiz) {
            try {
                debug("[CloseAdvice] close() called on: " + thiz.getClass().getSimpleName() + " @ " + Integer.toHexString(System.identityHashCode(thiz)));
                BootstrapHelper.recordClosure(thiz);
            } catch (Throwable t) {
                error("[CloseAdvice]: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }
}
