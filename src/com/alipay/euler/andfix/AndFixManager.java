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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.util.Log;

import com.alipay.euler.andfix.annotation.MethodReplace;
import com.alipay.euler.andfix.security.SecurityChecker;

import dalvik.system.DexFile;

/**
 * AndFix Manager
 * 
 * @author sanping.li@alipay.com
 * 
 */
public class AndFixManager {
	private static final String TAG = "AndFixManager";

	private static final String DIR = "apatch_opt";

	/**
	 * context
	 */
	private final Context mContext;

	/**
	 * classes will be fixed
	 */
	private static Map<String, Class<?>> mFixedClass = new ConcurrentHashMap<String, Class<?>>();

	/**
	 * whether support AndFix
	 */
	private boolean mSupport = false;

	/**
	 * security check
	 */
	private SecurityChecker mSecurityChecker;

	/**
	 * optimize directory
	 */
	private File mOptDir;

	public AndFixManager(Context context) {
		mContext = context;
		// 判断Android机型是否适支持AndFix
		mSupport = Compat.isSupport();
		if (mSupport) {
			// 初始化签名判断类
			mSecurityChecker = new SecurityChecker(mContext);
			// 初始化 存放 odex文件的目录
			mOptDir = new File(mContext.getFilesDir(), DIR);
			if (!mOptDir.exists() && !mOptDir.mkdirs()) {// make directory fail
				mSupport = false;
				Log.e(TAG, "opt dir create error.");
			} else if (!mOptDir.isDirectory()) {// not directory
				//如果不是文件目录就删除
				mOptDir.delete();
				mSupport = false;
			}
		}
	}

	/**
	 * delete optimize file of patch file
	 * 
	 * @param file
	 *            patch file
	 */
	public synchronized void removeOptFile(File file) {
		File optfile = new File(mOptDir, file.getName());
		if (optfile.exists() && !optfile.delete()) {
			Log.e(TAG, optfile.getName() + " delete error.");
		}
	}

	/**
	 * fix
	 * 
	 * @param patchPath
	 *            patch path
	 */
	public synchronized void fix(String patchPath) {
		fix(new File(patchPath), mContext.getClassLoader(), null);
	}

	/**
	 * fix
	 * 
	 * @param file
	 *            patch file
	 * @param classLoader
	 *            classloader of class that will be fixed
	 * @param classes
	 *            classes will be fixed
	 */
	public synchronized void fix(File file, ClassLoader classLoader,
			List<String> classes) {
		if (!mSupport) {
			return;
		}

		// 1、检查 patch的签名
		if (!mSecurityChecker.verifyApk(file)) {// security check fail
			return;
		}

		try {
			File optfile = new File(mOptDir, file.getName());
			boolean saveFingerprint = true;
			if (optfile.exists()) {
				// need to verify fingerprint when the optimize file exist,
				// prevent someone attack on jailbreak device with
				// Vulnerability-Parasyte.
				// btw:exaggerated android Vulnerability-Parasyte
				// http://secauo.com/Exaggerated-Android-Vulnerability-Parasyte.html
				if (mSecurityChecker.verifyOpt(optfile)) {
					saveFingerprint = false;
				} else if (!optfile.delete()) {
					return;
				}
			}

			// 2、加载patch文件中的dex，并生成 DexFile文件
			final DexFile dexFile = DexFile.loadDex(file.getAbsolutePath(),
					optfile.getAbsolutePath(), Context.MODE_PRIVATE);

			if (saveFingerprint) {
				mSecurityChecker.saveOptSig(optfile);
			}

			// 新建ClassLoader，并重写findClass方法
			ClassLoader patchClassLoader = new ClassLoader(classLoader) {
				@Override
				protected Class<?> findClass(String className)
						throws ClassNotFoundException {
					// 自定义findClass方法，使用DexFile的loadClass进行加载类
					// 调用 DexFile来加载class
					Class<?> clazz = dexFile.loadClass(className, this);
					if (clazz == null
							&& className.startsWith("com.alipay.euler.andfix")) {
						return Class.forName(className);// annotation’s class
														// not found
					}
					if (clazz == null) {
						throw new ClassNotFoundException(className);
					}
					return clazz;
				}
			};
			Enumeration<String> entrys = dexFile.entries();
			Class<?> clazz = null;
			while (entrys.hasMoreElements()) {
				String entry = entrys.nextElement();
				if (classes != null && !classes.contains(entry)) {
					continue;// skip, not need fix
				}
				clazz = dexFile.loadClass(entry, patchClassLoader);
				if (clazz != null) {
					// 进行 fix
					fixClass(clazz, classLoader);
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "pacth", e);
		}
	}

	/**
	 * fix class
	 * 
	 * @param clazz
	 *            class
	 */
	private void fixClass(Class<?> clazz, ClassLoader classLoader) {
		// 获取 class类中所有声明的方法
		Method[] methods = clazz.getDeclaredMethods();
		MethodReplace methodReplace;
		String clz;
		String meth;
		// 遍历这些方法
		for (Method method : methods) {
			// 获取 MethodReplace注解
			methodReplace = method.getAnnotation(MethodReplace.class);
			if (methodReplace == null)
				continue;
			// 获取MethodReplace注解中类名
			clz = methodReplace.clazz();
			// 方法名
			meth = methodReplace.method();
			if (!isEmpty(clz) && !isEmpty(meth)) {
				// 类名和方法名 不为 null，进行实际的方法修复工作
				replaceMethod(classLoader, clz, meth, method);
			}
		}
	}

	/**
	 * replace method
	 * 
	 * @param classLoader classloader
	 * @param clz class
	 * @param meth name of target method 
	 * @param method source method
	 */
	private void replaceMethod(ClassLoader classLoader, String clz,
			String meth, Method method) {
		try {
			String key = clz + "@" + classLoader.toString();
			Class<?> clazz = mFixedClass.get(key);
			if (clazz == null) {// class not load
				Class<?> clzz = classLoader.loadClass(clz);
				// initialize target class
				clazz = AndFix.initTargetClass(clzz);
			}
			if (clazz != null) {// initialize class OK
				mFixedClass.put(key, clazz);
				// 通过方法名以及参数信息 获取到 原有的方法对象
				Method src = clazz.getDeclaredMethod(meth,
						method.getParameterTypes());
				// 将原有方法（有bug）和新加入的方法（解决bug后）传入AndFix，进行方法的替换
				AndFix.addReplaceMethod(src, method);
			}
		} catch (Exception e) {
			Log.e(TAG, "replaceMethod", e);
		}
	}

	private static boolean isEmpty(String string) {
		return string == null || string.length() <= 0;
	}

}
