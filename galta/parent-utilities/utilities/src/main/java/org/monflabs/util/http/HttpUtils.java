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
package org.monflabs.util.http;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Map;

import org.monflabs.util.StringUtil;

/**
 * Some java HTTP utilities.
 * 
 */
public class HttpUtils {
	
	private HttpUtils() {
	}

	public static String getErrorMessage(HttpResponse<?> response) {
		int status = response.statusCode();
		Object body = response.body();
		String error = body!=null ? body.toString() : null;
		
		if(StringUtil.isNotEmpty(error)) {
			return MessageFormat.format("Status: {0}\nError: {1}",status,error);
		}
		return MessageFormat.format("Status: {0}",status);
	}
	
	/**
	 * Encodes a map as application/x-www-form-urlencoded data. A null value produces a bare
	 * key ("a"), an empty value an empty one ("a=").
	 * @throws IllegalArgumentException for a null key
	 */
    public static String encodeFormData(Map<String, String> data) {
        StringBuilder b = new StringBuilder();
        if(data!=null) {
	        for (Map.Entry<String, String> entry : data.entrySet()) {
	        	if(entry.getKey()==null) {
	        		throw new IllegalArgumentException("A form data or query string key cannot be null");
	        	}
	            if (b.length() > 0) {
	                b.append("&");
	            }
	            b.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
	            String value = entry.getValue();
	            if(value!=null) {
	                b.append("=");
	                b.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
	            }
	        }
        }
        return b.toString();
    }
    
    public static String encodeQueryString(Map<String, String> qs) {
    	return encodeFormData(qs);
    }
    
    /**
     * Appends parameters to the query string of a URI. They go before the fragment, if any.
     */
    public static String appendQueryString(String uri, Map<String, String> qs) {
    	String s = encodeQueryString(qs);
    	if(StringUtil.isNotEmpty(s)) {
    		String fragment = "";
    		int hash = uri.indexOf('#');
    		if(hash>=0) {
    			fragment = uri.substring(hash);
    			uri = uri.substring(0,hash);
    		}
    		int q = uri.indexOf('?');
    		if(q<0) {
    			uri += '?';
    		} else if(q<uri.length()-1 && uri.charAt(uri.length()-1)!='&') {
    			uri += '&';
    		}
    		uri += s + fragment;
    	}
    	return uri;
    }
}
