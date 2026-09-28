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
package org.monflabs.galtajs.rt.builtins.errors;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.NativeObject;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * @author Philippe Riand
 */
public abstract class BaseError extends NativeObject {

    public BaseError(JSEnvironment env) {
		super(env);
    }
    
    @Override
	public String getClassName() {
		return Error.ConstructorImpl.CLASSNAME;
	}

    @Override
	public String toString() {
		Object _name = RuntimeUtil.getProperty(getEnvironment(),this,"name","Error");
		String name = RuntimeUtil.toString(getEnvironment(),_name);
		Object _msg = RuntimeUtil.getProperty(getEnvironment(),this,"message","");
		String msg = RuntimeUtil.toString(getEnvironment(),_msg);
		if(StringUtil.isEmpty(name)) {
			return msg;
		}
		if(StringUtil.isEmpty(msg)) {
			return name;
		}
		return StringFormat.format("{0}: {1}",name,msg);
    }
}
