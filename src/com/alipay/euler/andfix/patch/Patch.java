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

package com.alipay.euler.andfix.patch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * patch model
 * 
 * @author sanping.li@alipay.com
 * 
 */
public class Patch implements Comparable<Patch> {
	private static final String ENTRY_NAME = "META-INF/PATCH.MF";
	private static final String CLASSES = "-Classes";
	private static final String PATCH_CLASSES = "Patch-Classes";
	private static final String CREATED_TIME = "Created-Time";
	private static final String PATCH_NAME = "Patch-Name";

	/**
	 * patch file
	 */
	private final File mFile;
	/**
	 * name
	 */
	private String mName;
	/**
	 * create time
	 */
	private Date mTime;
	/**
	 * classes of patch
	 */
	private Map<String, List<String>> mClassesMap;

	public Patch(File file) throws IOException {
		mFile = file;
		init();
	}

	@SuppressWarnings("deprecation")
	private void init() throws IOException {
		JarFile jarFile = null;
		InputStream inputStream = null;
		try {
			// 使用JarFile读取Patch文件
			jarFile = new JarFile(mFile);
			// 获取META-INF/PATCH.MF文件
			JarEntry entry = jarFile.getJarEntry(ENTRY_NAME);
			inputStream = jarFile.getInputStream(entry);
			Manifest manifest = new Manifest(inputStream);
			Attributes main = manifest.getMainAttributes();
			// 获取PATCH.MF属性Patch-Name
			mName = main.getValue(PATCH_NAME);
			// 获取PATCH.MF属性Created-Time
			mTime = new Date(main.getValue(CREATED_TIME));

			mClassesMap = new HashMap<String, List<String>>();
			Attributes.Name attrName;
			String name;
			List<String> strings;
			// 这个是遍历META-INF/PATCH.MF文件中内容
			for (Iterator<?> it = main.keySet().iterator(); it.hasNext();) {
				// 属性名
				attrName = (Attributes.Name) it.next();
				name = attrName.toString();
				//判断name的后缀是否是-Classes，
				// 并把name对应的值加入到集合中，对应的值就是class类名的列表
				if (name.endsWith(CLASSES)) {
					// 属性名是以-Classes结尾，那么它的value值是以逗号分隔的热修复类名
					strings = Arrays.asList(main.getValue(attrName).split(","));
					if (name.equalsIgnoreCase(PATCH_CLASSES)) {
						// key：patchName，value：该patch文件中涉及到的类名
						mClassesMap.put(mName, strings);
					} else {
						mClassesMap.put(
								name.trim().substring(0, name.length() - 8),// remove
																			// "-Classes"
								strings);
					}
				}
			}
		} finally {
			if (jarFile != null) {
				jarFile.close();
			}
			if (inputStream != null) {
				inputStream.close();
			}
		}

	}

	public String getName() {
		return mName;
	}

	public File getFile() {
		return mFile;
	}

	public Set<String> getPatchNames() {
		return mClassesMap.keySet();
	}

	public List<String> getClasses(String patchName) {
		return mClassesMap.get(patchName);
	}

	public Date getTime() {
		return mTime;
	}

	@Override
	public int compareTo(Patch another) {
		return mTime.compareTo(another.getTime());
	}

}
