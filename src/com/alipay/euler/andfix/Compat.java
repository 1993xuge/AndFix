/*
 * 
 * Copyright (c) 2015, alipay.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.euler.andfix;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;

/**
 * Compatibility
 * 
 * @author sanping.li@alipay.com
 * 
 */
public class Compat {
	public static boolean isChecked = false;
	public static boolean isSupport = false;

	/**
	 * whether support on the device
	 * 
	 * @return true if the device support AndFix
	 */
	public static synchronized boolean isSupport() {
		if (isChecked)
			return isSupport;

		isChecked = true;
		// not support alibaba's YunOs
		// isYunOS():判断系统是否是YunOs系统，YunOs系统是阿里巴巴的系统
		// AndFix.setup():在Native层进行热修复设置
		// isSupportSDKVersion():根据sdk版本判断是否支持
		if (!isYunOS() && AndFix.setup() && isSupportSDKVersion()) {
			// 不是YunOs、注册native方法成功、当前SDK版本支持
			isSupport = true;
		}

		// 如果在黑名单中，那么就表明不支持
		if (inBlackList()) {
			isSupport = false;
		}

		return isSupport;
	}

	@SuppressLint("DefaultLocale")
	private static boolean isYunOS() {
		String version = null;
		String vmName = null;
		try {
			Method m = Class.forName("android.os.SystemProperties").getMethod(
					"get", String.class);
			version = (String) m.invoke(null, "ro.yunos.version");
			vmName = (String) m.invoke(null, "java.vm.name");
		} catch (Exception e) {
			// nothing todo
		}
		if ((vmName != null && vmName.toLowerCase().contains("lemur"))
				|| (version != null && version.trim().length() > 0)) {
			return true;
		} else {
			return false;
		}
	}

	// from android 2.3 to android 7.0
	private static boolean isSupportSDKVersion() {
		if (android.os.Build.VERSION.SDK_INT >= 8
				&& android.os.Build.VERSION.SDK_INT <= 24) {
			return true;
		}
		return false;
	}

	private static boolean inBlackList() {
		return false;
	}
}
