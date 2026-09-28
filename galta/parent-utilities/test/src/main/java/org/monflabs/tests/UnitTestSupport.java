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
package org.monflabs.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.MessageFormat;

import org.junit.Assert;
import org.monflabs.json.JsonFactory;
import org.monflabs.json.JsonObject;
import org.monflabs.json.config.JsonFileConfig;
import org.monflabs.util.FileUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.UserPath;
import org.monflabs.util.config.Config;

/**
 * Base class to support JUnit tests.
 */
public class UnitTestSupport {
	
	public static final String USER_DIR = ".monflabs";
	public static final String CONFIG_FILE = "test-config.json";
	
	@SuppressWarnings("serial")
	public static class TestException extends RuntimeException {
		public TestException(Throwable t) {
			super(t);
		}
	}
	
	/**
	 * System property controlling how the golden-file templates are saved:
	 * <ul>
	 * <li>not set: a missing template fails the check, an existing one is compared
	 * <li><code>missing</code>: a missing template is saved from the current result
	 * <li><code>all</code>: every template checked is rewritten from the current result
	 * </ul>
	 * Example: <code>mvn test -Dmonflabs.tests.saveTemplates=missing</code>
	 */
	public static final String SAVE_TEMPLATES_PROPERTY = "monflabs.tests.saveTemplates";

	public static final boolean DUMP = false;

	
	public String readString(InputStream is, Charset encoding) {
		try {
			// Decode the whole content at once: decoding 8K chunks separately turned a
			// multi-byte character straddling a chunk boundary into U+FFFD
			return new String(is.readAllBytes(), encoding);
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	public String readString(InputStream is) {
		return readString(is,StandardCharsets.UTF_8);
	}
	public String readString(Reader r) {
		try {
			StringBuilder sb = new StringBuilder(8192);
			char[] b = new char[8192];
			int c;
			while( (c=r.read(b)) >=0 ) {
				sb.append(b,0,c);
			}
			return sb.toString();	
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	public String readString(File f) {
		try {
			FileInputStream is = new FileInputStream(f);
			try {
				return readString(is);
			} finally {
				is.close();
			}
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	
	private void writeString(OutputStream os, String s, Charset encoding) {
		try {
			os.write(s.getBytes(encoding));
		} catch(Exception e) {
			throw new TestException(e);
		}
	}


	// Per instance settings, overriding the system property when set
	private Boolean forceTemplateSave;
	private Boolean saveMissingTemplates;
	private String templateFailure;

	private static String saveTemplatesMode() {
		String mode = System.getProperty(SAVE_TEMPLATES_PROPERTY);
		return mode!=null ? mode.trim().toLowerCase(java.util.Locale.ROOT) : "";
	}
	
	/**
	 * Tell if every template checked is rewritten from the current result.
	 * @return true to rewrite the templates
	 */
	public boolean forceTemplateSave() {
		if(forceTemplateSave!=null) {
			return forceTemplateSave;
		}
		String mode = saveTemplatesMode();
		return mode.equals("all") || mode.equals("true");
	}
	/**
	 * Rewrite every template checked by this instance from the current result.
	 * This only affects this {@link UnitTestSupport} instance.
	 * @param forceSave true to rewrite the templates
	 */
	public void setForceTemplateSave(boolean forceSave) {
		this.forceTemplateSave = forceSave;
	}
	/**
	 * Tell if a missing template is saved from the current result, instead of failing the check.
	 * @return true to save the missing templates
	 */
	public boolean saveMissingTemplates() {
		if(saveMissingTemplates!=null) {
			return saveMissingTemplates;
		}
		return forceTemplateSave() || saveTemplatesMode().equals("missing");
	}
	/**
	 * Save the missing templates from the current result, for this instance only.
	 * @param saveMissing true to save the missing templates
	 */
	public void setSaveMissingTemplates(boolean saveMissing) {
		this.saveMissingTemplates = saveMissing;
	}

	private Config configObject;
	public synchronized Config getConfigObject() {
		if(configObject==null) {
			configObject = JsonFileConfig.newBuilder()
					.readOnly(true)
					.folder(getUserMonflabsDirectory().toPath())
					.fileName(CONFIG_FILE)
					.build();
		}
		return configObject;
	}
	
	
	
	private Class<?> unitTestClass;
	
	private File projectRoot = null;
	public synchronized File getProjectRoot() {
		if(projectRoot==null) {
			String userDir = System.getProperties().getProperty("user.dir");
			projectRoot = new File(userDir);
		}
		return projectRoot;
	}
	public synchronized File getProjectDirectory(String path) {
		return new File(getProjectRoot(),path);
	}

	public synchronized File getProjectRoot(String projectName) {
		File parent = getProjectRoot().getParentFile();
		File prj = new File(parent,projectName);
		if(!prj.exists()) {
			throw new IllegalStateException(MessageFormat.format("Project {0} does not exist", projectName)); 
		}
		return prj;
	}

	private File _MAINJAVA_BASEDIR;
	public File getMainJavaDirectory() {
		if(_MAINJAVA_BASEDIR==null) {
			_MAINJAVA_BASEDIR = new File(getProjectRoot(),"src/main/java");
		}
		return _MAINJAVA_BASEDIR;
	}

	private File _MAINRESOURCES_BASEDIR;
	public File getMainResourcesDirectory() {
		if(_MAINRESOURCES_BASEDIR==null) {
			_MAINRESOURCES_BASEDIR = new File(getProjectRoot(),"src/main/resources");
		}
		return _MAINRESOURCES_BASEDIR;
	}

	private File _TESTJAVA_BASEDIR;
	public File getTestJavaDirectory() {
		if(_TESTJAVA_BASEDIR==null) {
			_TESTJAVA_BASEDIR = new File(getProjectRoot(),"src/test/java");
		}
		return _TESTJAVA_BASEDIR;
	}

	private File _TESTRESOURCES_BASEDIR;
	public File getTestResourcesDirectory() {
		if(_TESTRESOURCES_BASEDIR==null) {
			_TESTRESOURCES_BASEDIR = new File(getProjectRoot(),"src/test/resources");
		}
		return _TESTRESOURCES_BASEDIR;
	}
	public File getTestResourcesDirectory(String path) {
		return new File(getTestResourcesDirectory(),path);
	}
	
	private File _TESTRESULTS_BASEDIR;
	public File getTestResultsDirectory() {
		if(_TESTRESULTS_BASEDIR==null) {
			_TESTRESULTS_BASEDIR = new File(getProjectRoot(),"tests");
		}
		return _TESTRESULTS_BASEDIR;
	}

	private File _TARGET_BASEDIR;
	public File getTargetDirectory() {
		if(_TARGET_BASEDIR==null) {
			_TARGET_BASEDIR = new File(getProjectRoot(),"target");
		}
		return _TARGET_BASEDIR;
	}

	private File _TARGETCLASSES_BASEDIR;
	public File getTargetClassesDirectory() {
		if(_TARGETCLASSES_BASEDIR==null) {
			_TARGETCLASSES_BASEDIR = new File(getProjectRoot(),"target/classes");
		}
		return _TARGETCLASSES_BASEDIR;
	}

	private File _TARGETTESTCLASSES_BASEDIR;
	public File getTargetTextClassesDirectory() {
		if(_TARGETTESTCLASSES_BASEDIR==null) {
			_TARGETTESTCLASSES_BASEDIR = new File(getProjectRoot(),"target/test-classes");
		}
		return _TARGETTESTCLASSES_BASEDIR;
	}

	private File _TARGETTEMP_BASEDIR;
	public File getTargetTempDirectory() {
		if(_TARGETTEMP_BASEDIR==null) {
			_TARGETTEMP_BASEDIR = new File(getProjectRoot(),"target/temp");
			_TARGETTEMP_BASEDIR.mkdirs();
		}
		return _TARGETTEMP_BASEDIR;
	}
	public File getTargetTempDirectory(String subPath) {
		return getTargetTempDirectory(subPath, false);
	}
	public File getTargetTempDirectory(String subPath, boolean empty) {
		File f = new File(getTargetTempDirectory(),subPath);
		f.mkdirs();
		if(empty) {
			FileUtil.emptyDirectory(f);
		}
		return f;
	}

	private File _MONFLABS_BASEDIR;
	public File getUserMonflabsDirectory() {
		if(_MONFLABS_BASEDIR==null) {
			_MONFLABS_BASEDIR = UserPath.getMonflabsFolder().toFile();
		}
		return _MONFLABS_BASEDIR;
	}
	
	public File clearFolder(File file) {
		if(file.exists()) {
			FileUtil.emptyDirectory(file);
		} else {
			file.mkdirs();
		}
		return file;
	}


	public String loadClassResourceText(String name) {
		return loadClassResourceText(name,false);
	}
	public String loadClassResourceText(Class<?> clazz, String name) {
		return loadClassResourceText(clazz,name,false);
	}
	public String loadClassResourceText(String name, boolean nullIfNotExist) {
		return loadClassResourceText(unitTestClass,name,nullIfNotExist);
	}
	public String loadClassResourceText(Class<?> clazz, String name, boolean nullIfNotExist) {
		InputStream is = clazz.getResourceAsStream(name);
		if(is==null) {
			if(nullIfNotExist) {
				return null;
			}
			throw new IllegalStateException(MessageFormat.format("Class resource {0} does not exist", name)); 
		}
		Reader r = new InputStreamReader(is,StandardCharsets.UTF_8);
		try {
			return readString(r);
		} finally {
			try {
				r.close();
			} catch(Exception e) {
				throw new TestException(e);
			}
		}
	}
	
	public File resourceFile(Class<?> c, String name, boolean mkdirs) {
		if(c==null) c = unitTestClass;
		File baseDir = getTestResultsDirectory();
		String s = c.getName().replace('.','/');
		File classDir = new File(baseDir,s);
		if(mkdirs) {
			classDir.mkdirs();
		}
		return new File(classDir,name);
	}
	public File resourceFileForClass(Class<?> c, String name, boolean mkdirs) {
		if(c==null) c = unitTestClass;
		File baseDir = getTestResultsDirectory();
		String dir = c.getName().substring(0,c.getName().lastIndexOf('.'));
		String s = dir.replace('.','/');
		File classDir = new File(baseDir,s);
		if(mkdirs) {
			classDir.mkdirs();
		}
		return new File(classDir,c.getSimpleName()+(name!=null?name:""));
	}
	
	public boolean resourceExists(Class<?> c, String name) {
		File f = resourceFile(c, name, false);
		return f.exists();
	}

	public String loadText(Class<?> c, String name) {
		try {
			File f = resourceFile(c, name, false);
			if(f.exists()) {
				InputStream is = new FileInputStream(f);
				try {
					return readString(is,StandardCharsets.UTF_8);
				} finally {
					is.close();
				}
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException(StringFormat.format("Cannot find resource {0}",name));
	}
	public String loadText(File f) {
		try {
			if(f.exists()) {
				@SuppressWarnings("resource")
				InputStream is = new FileInputStream(f);
				try {
					return readString(is,StandardCharsets.UTF_8);
				} finally {
					is.close();
				}
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException(StringFormat.format("Cannot find resource {0}",f));
	}
	public String loadText(String resourceName) {
		try {
			InputStream is = UnitTestSupport.class.getClassLoader().getResourceAsStream(resourceName);
			if(is!=null) {
				try {
					return readString(is,StandardCharsets.UTF_8);
				} finally {
					is.close();
				}
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException(StringFormat.format("Cannot find resource {0}",resourceName));
	}

	public void saveText(Class<?> c, String name, String text) {
		try {
			File f = resourceFile(c, name, true);
			OutputStream os = new FileOutputStream(f);
			try {
				writeString(os,text,StandardCharsets.UTF_8);
			} finally {
				os.close();
			}
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	public void saveFile(File f, String text) {
		try {
			OutputStream os = new FileOutputStream(f);
			try {
				writeString(os,text,StandardCharsets.UTF_8);
			} finally {
				os.close();
			}
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	
	public void out(String s) {
		System.out.println(s);
	}
	public void err(String s) {
		System.err.println(s);
	}
	public void print(String msg) {
		out(msg);
	}
	public void print(String fmt, Object...parameters) {
		String s = MessageFormat.format(fmt, parameters);
		out(s);
	}
	public void error(String fmt, Object...parameters) {
		String s = MessageFormat.format(fmt, parameters);
		err(s);
	}

	public void log(String fmt, Object...parameters) {
		String s = MessageFormat.format(fmt, parameters);
		out(s);
	}
	public void log(Throwable t) {
		out("Exception:");
		t.printStackTrace();
	}
	public void logCompact(Throwable t) {
		for(Throwable ti=t; ti!=null; ti=getCause(ti)) {
			out("    "+ti.getLocalizedMessage());
		}
	}
	public void logMessage(Throwable t) {
		out("    "+t.getLocalizedMessage());
	}
    public static Throwable getCause(Throwable t) {
    	Throwable cause = null;
    	if(t.getClass().getName().equals("javax.servlet.ServletException")) {
    		try {
	    		Method m = t.getClass().getMethod("getRootCause");
	    		if(m!=null) {
	    			cause = (Throwable)m.invoke(t);
	    		}
    		} catch(Throwable t2) {}
    	}
        if(cause==null) {
	        if(t instanceof SQLException se) {
	            cause = se.getNextException();
	        }
	        if(cause==null) {
	            cause = t.getCause();
	        }
        }
        if(cause==t) {
        	cause=null;
        }
        return cause;
    }

	
	// Just the time for the new System.currentTimeMillis() to increase
	public void sleep() {
		try {
			// Make sure that the time has elapsed enough
			Thread.sleep(20); 
		} catch(Exception e) {
			throw new TestException(e);
		}
	}
	public void sleep(int ms) {
		try {
			// Make sure that the time has elapsed enough
			Thread.sleep(ms); 
		} catch(Exception e) {
			throw new TestException(e);
		}
	}


	
	//
	// Test with templates
	//

	
	public static abstract class Result {
		public abstract String asString();

		protected String normalizeTemplate(String text) {
			return normalizeLineBreaks(text);
		}
	}
	
	public static class StringResult extends Result {
		private String s;
		public StringResult(String value) {
			this.s = value!=null ? value : "";
		}
		@Override
		public String asString() {
			return s;
		}
	}
	public static class TrimmedStringResult extends StringResult {
		public TrimmedStringResult(String value) {
			super(value);
		}
		@Override
		protected String normalizeTemplate(String text) {
			text = super.normalizeTemplate(text);
			while(text.indexOf(" \n")>=0) {
				text = text.replace(" \n", "\n");
			}	
			return text;
		}
	}

	public Class<?> getTemplateClass() {
		return unitTestClass; // test class by default
	}

	public void dumpResult(Result result) {
		String s = result.asString();
		print("{0}", s);
	}

	public boolean checkTextResult(String result, String templateName) {
		return checkResultTemplate(new StringResult(result), templateName);
	}
	
	/**
	 * Compare a result with a template file.
	 * <p>
	 * A missing template fails the check, unless {@link #saveMissingTemplates()} is on. A template
	 * name that is null, or that starts with <code>*</code>, disables the check (it always passes).
	 * @param result the result
	 * @param templateName the template file name
	 * @return true if the result matches the template
	 */
	public boolean checkResultTemplate(Result result, String templateName) {
		templateFailure = null;
		String s = result.asString();
		
		if(templateName!=null && !templateName.startsWith("*")) {
			Class<?> templateClass = getTemplateClass();
			// We don't save the normalized template on purpose, as it is easier to debug this way
			if(forceTemplateSave()) {
				saveText(templateClass, templateName, s);
			} else if(!resourceExists(templateClass, templateName)) {
				if(!saveMissingTemplates()) {
					templateFailure = StringFormat.format("Template {0} does not exist ({1}). Run with -D{2}=missing to create it from the current result",
							templateName, resourceFile(templateClass, templateName, false).getPath(), SAVE_TEMPLATES_PROPERTY);
					print("{0}", templateFailure);
					return false;
				}
				saveText(templateClass, templateName, s);
			}
			String readText = loadText(templateClass,templateName);
			String templateText = result.normalizeTemplate(readText);
			String resultText = result.normalizeTemplate(s);
			if(!_checkTemplate(resultText, templateText, templateName)) {
				templateFailure = StringFormat.format("Result does not match template {0}", templateName);
				return false;
			}
		}
		
		return true;
	}
	
	private String failureMessage(String defaultMessage) {
		return templateFailure!=null ? defaultMessage+": "+templateFailure : defaultMessage;
	}
	
	public void assertTextResult(String result, String templateName) {
		if(!checkTextResult(result, templateName)) {
			Assert.fail(failureMessage("Error in checking text value"));
		}
	}
	
	public void assertResult(Result result, String templateName) {
		if(!checkResultTemplate(result, templateName)) {
			Assert.fail(failureMessage("Error in checking value"));
		}
	}
	
	
	//
	// Text comparison
	//
	protected boolean _checkTemplate(String resultText, String templateText, String templateName) {
		if(!templateText.equals(resultText)) {
			int l1 = templateText.length();
			int l2 = resultText.length();
			int l = Math.min(l1, l2);
			for(int i=0; i<l; i++) {
				char c1 = templateText.charAt(i);
				char c2 = resultText.charAt(i);
				if(c1!=c2) {
					l = i; break;
				}
			}

			print("!!!!!!! Failed when comparing text with !!!!!!!!!!!!!!!!!!!!!!!!!!");
			if(templateName!=null) {
				File f = resourceFile(getTemplateClass(),templateName, false);
				print("    Template name: {0}",templateName);
				print("    File: {0}",f.getPath());
			}
			print("");
			if(true) {
				print("**** Result Document:");
				print("{0}\n",resultText.substring(0,Math.min(2000,resultText.length())));
				print("**** Expected Result:");
				print("{0}\n",templateText.substring(0,Math.min(2000,templateText.length())));
			}
			if(true) {
				try {
					print("**** Formatted Result Document:");
					print("{0}\n",JsonFactory.get().stringifyDebug(JsonObject.parse(resultText)));
					print("**** Formatted Expected Result:");
					print("{0}\n",JsonFactory.get().stringifyDebug(JsonObject.parse(templateText)));
				} catch(Exception e) {} // Ignore non JSON data
			}			
			print("**** Error found in position: {0}",l);
			print("Result:");
			print("{0}\n",extractError(resultText,l));
			print("");
			print("Expected:");
			print("{0}\n",extractError(templateText,l));
			print("");
			print("");
			print("!!!!!!! End !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			print("");
			print("");
			print("");
			return false;
		}
		
		return true;
	}
	public static String normalizeLineBreaks(String s) {
		// Normalize the lines breaks to make it compatible between the different platforms (Mac, Windows, ....)
		// "\n\r" is two line breaks (LF, then a lone CR), not one
		s = s.replace("\r\n", "\n");
		s = s.replace("\r", "\n");
		return s;
	}
	protected String extractError(String text, int pos) {
		int start = Math.max(0,pos-60);
		int end = Math.min(text.length(),pos+80);
		String s1 = text.substring(start, pos);
		String s2 = text.substring(pos, end);
		String res = s1+"@@@@"+s2;
		res = res.replace("\n", "\\n");
		res = res.replace("\r", "\\r");
		res = res.replace("\t", "\\t");
		//res = res.replace(" ", "\u00A0"); // Insecable space
		return res;
	}

	
	//
	// Access to object members
	//

	public JavaAccessor getObjectAccessor(Object o) {
		return new JavaAccessor(o.getClass(),o);
	}

	public JavaAccessor getClassAccessor(Class<?> clazz) {
		return new JavaAccessor(clazz,null);
	}

	
	//
	// JSON results
	//
	public class JsonResult extends Result {
		private Object json;
		public JsonResult(Object json) {
			this.json = json;
		}
		@Override
		protected String normalizeTemplate(String text) {
			// Normalize the JSON content (order, indentation...)
			Object json = getJsonFactory().parse(text);
			return getJsonFactory().stringifyDebug(json);
		}
//		@Override
//		protected String normalizeTemplate(String text) {
//			if(json instanceof String) {
//				json = getJsonFactory().parse((String)json);
//			}
//			return getJsonFactory().stringifyDebug(json);
//		}

		@Override
		public String asString() {
			if(json instanceof String s) {
				json = getJsonFactory().parse(s);
			}
			return getJsonFactory().stringify(json,false);
		}
	}

	private boolean verbose;
	
	public UnitTestSupport(Class<?> unitTestClass) {
		this.unitTestClass = unitTestClass;
	}
	
	public boolean isVerbose() {
		return verbose;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	

	public JsonFactory getJsonFactory() {
		return JsonFactory.get();
	}
	
	public Object loadJson(Class<?> c, String resourceName) {
		return getJsonFactory().parse(loadText(c,resourceName));
	}
	
	public Object loadJson(String resourceName) {
		return getJsonFactory().parse(loadText(resourceName));
	}
	
	public Object loadJson(File f) {
		return getJsonFactory().parse(loadText(f));
	}
	
	public boolean checkJsonTemplate(Object result, String templateName) {
		return checkResultTemplate(new UnitTestSupport.JsonResult(result), templateName);
	}	

	public void assertJsonTemplate(Object expected, String templateName)  {
		if(!checkJsonTemplate(expected, templateName)) {
			Assert.fail(failureMessage("Error in JSON document"));
		}
	}
	public void assertJsonTemplate(Object expected, String templateName, String msg) {
		if(!checkJsonTemplate(expected, templateName)) {
			Assert.fail(failureMessage(StringFormat.format("Error in JSON document, {0}",msg)));
		}
	}
	

	public void assertJsonEquals(Object expected, Object value) {
		if(!checkJsonEquals(expected, value)) {
			Assert.fail("Error in JSON document");
		}
	}
	private boolean checkJsonEquals(Object expected, Object value) {
		String s1 = stringifyDebug(expected);
		String s2 = stringifyDebug(value);
		return _checkTemplate(s2, s1, null);
	}
	public boolean isJsonEquals(Object expected, Object value) {
		String s1 = stringifyDebug(expected);
		String s2 = stringifyDebug(value);
		return s1.equals(s2);
	}
	

	public void assertNormalizedTextEquals(String expected, String value) {
		if(!checkNormalizedTextEquals(expected, value)) {
			Assert.fail("Error in TEXT result");
		}
	}
	private boolean checkNormalizedTextEquals(String expected, String value) {
		String s1 = normalizeLineBreaks(expected);
		String s2 = normalizeLineBreaks(value);
		return _checkTemplate(s2, s1, null);
	}
	public boolean isNormalizedTextEquals(String expected, String value) {
		String s1 = normalizeLineBreaks(expected);
		String s2 = normalizeLineBreaks(value);
		return s1.equals(s2);
	}

	
	// JSON support
	private String stringifyDebug(Object value) {
		if(value instanceof String s) {
			value = getJsonFactory().parse(s);
		}
		return getJsonFactory().stringifyDebug(value);
	}
}
