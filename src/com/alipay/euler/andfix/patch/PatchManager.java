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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.alipay.euler.andfix.AndFixManager;
import com.alipay.euler.andfix.util.FileUtil;

/**
 * patch manager
 * 
 * @author sanping.li@alipay.com
 * 
 */
public class PatchManager {
	private static final String TAG = "PatchManager";
	// patch extension
	private static final String SUFFIX = ".apatch";
	private static final String DIR = "apatch";
	private static final String SP_NAME = "_andfix_";
	private static final String SP_VERSION = "version";

	/**
	 * context
	 */
	private final Context mContext;
	/**
	 * AndFix manager
	 */
	private final AndFixManager mAndFixManager;
	/**
	 * patch directory
	 */
	private final File mPatchDir;
	/**
	 * patchs
	 */
	private final SortedSet<Patch> mPatchs;
	/**
	 * classloaders
	 */
	private final Map<String, ClassLoader> mLoaders;

	/**
	 * @param context
	 *            context
	 */
	public PatchManager(Context context) {
		mContext = context;
		// 初始化 AndFixManager
		mAndFixManager = new AndFixManager(mContext);
		// 初始化存放patch补丁文件的文件夹（/data/data/应用包名/files/apatch目录中）
		mPatchDir = new File(mContext.getFilesDir(), DIR);

		// 初始化存在Patch类的集合
		mPatchs = new ConcurrentSkipListSet<Patch>();
		// 初始化存放类对应的类加载器集合
		mLoaders = new ConcurrentHashMap<String, ClassLoader>();
	}

	/**
	 * initialize
	 * 
	 * @param appVersion
	 *            App version
	 */
	public void init(String appVersion) {
		// 保护，判断存放Patch文件的目录是否存在，这个目录是在初始化的时候创建的
		if (!mPatchDir.exists() && !mPatchDir.mkdirs()) {// make directory fail
			Log.e(TAG, "patch dir create error.");
			return;
		} else if (!mPatchDir.isDirectory()) {// not directory
			mPatchDir.delete();
			return;
		}

		// 存储关于patch文件的信息，在SharedPreferences中存储app版本的信息
		SharedPreferences sp = mContext.getSharedPreferences(SP_NAME,
				Context.MODE_PRIVATE);
		String ver = sp.getString(SP_VERSION, null);
		// 根据你传入的版本号和之前的对比，做不同的处理
		// ver == null ： 表明这是第一次初始化
		// !ver.equalsIgnoreCase(appVersion) : 版本升级
		if (ver == null || !ver.equalsIgnoreCase(appVersion)) {
			// 版本升级后，清理之前的Patch文件
			cleanPatch();
			// 更新版本号
			sp.edit().putString(SP_VERSION, appVersion).commit();
		} else {
			// 初始化patch列表，把本地的patch文件加载到内存
			initPatchs();
		}
	}

	// 遍历PatchDir目录下的所有patch文件，将patch文件挨个加入到内存中
	private void initPatchs() {
		File[] files = mPatchDir.listFiles();
		for (File file : files) {
			// 将File文件转换成Patch对象，并存储在内存中
			addPatch(file);
		}
	}

	/**
	 * add patch file
	 * 根据 传入的File文件，实例化 Patch对象，并将其加入到mPatchs中
	 * 
	 * @param file
	 * @return patch
	 */
	private Patch addPatch(File file) {
		Patch patch = null;
		if (file.getName().endsWith(SUFFIX)) {
			try {
				// 实例化Patch对象
				patch = new Patch(file);
				// 把patch实例存储到内存的集合中,在PatchManager实例化集合
				mPatchs.add(patch);
			} catch (IOException e) {
				Log.e(TAG, "addPatch", e);
			}
		}
		return patch;
	}

	private void cleanPatch() {
		File[] files = mPatchDir.listFiles();
		for (File file : files) {
			// 删除所有的本地缓存patch文件
			mAndFixManager.removeOptFile(file);
			if (!FileUtil.deleteFile(file)) {
				Log.e(TAG, file.getName() + " delete error.");
			}
		}
	}

	/**
	 * add patch at runtime
	 * 
	 * @param path
	 *            patch path
	 * @throws IOException
	 */
	public void addPatch(String path) throws IOException {
		// 根据路径创建文件
		File src = new File(path);
		// 在mPatchDir目录下创建相同名称的文件
		File dest = new File(mPatchDir, src.getName());
		if(!src.exists()){
			throw new FileNotFoundException(path);
		}
		if (dest.exists()) {
			Log.d(TAG, "patch [" + path + "] has be loaded.");
			return;
		}
		// 将src文件中的内容 拷贝到 dest文件中
		FileUtil.copyFile(src, dest);// copy to patch's directory
		// 将File文件转换成Patch对象，并将其加入到内存中
		Patch patch = addPatch(dest);
		if (patch != null) {
			// load Patch对象，进行修复 bug
			loadPatch(patch);
		}
	}

	/**
	 * remove all patchs
	 */
	public void removeAllPatch() {
		cleanPatch();
		SharedPreferences sp = mContext.getSharedPreferences(SP_NAME,
				Context.MODE_PRIVATE);
		sp.edit().clear().commit();
	}

	/**
	 * load patch,call when plugin be loaded. used for plugin architecture.</br>
	 * 
	 * need name and classloader of the plugin
	 * 
	 * @param patchName
	 *            patch name
	 * @param classLoader
	 *            classloader
	 */
	public void loadPatch(String patchName, ClassLoader classLoader) {
		mLoaders.put(patchName, classLoader);
		Set<String> patchNames;
		List<String> classes;
		for (Patch patch : mPatchs) {
			patchNames = patch.getPatchNames();
			if (patchNames.contains(patchName)) {
				classes = patch.getClasses(patchName);
				mAndFixManager.fix(patch.getFile(), classLoader, classes);
			}
		}
	}

	/**
	 * load patch,call when application start
	 * 
	 */
	public void loadPatch() {
		mLoaders.put("*", mContext.getClassLoader());// wildcard
		Set<String> patchNames;
		List<String> classes;
		for (Patch patch : mPatchs) {
			patchNames = patch.getPatchNames();
			for (String patchName : patchNames) {
				classes = patch.getClasses(patchName);
				mAndFixManager.fix(patch.getFile(), mContext.getClassLoader(),
						classes);
			}
		}
	}

	/**
	 * load specific patch
	 * 
	 * @param patch
	 *            patch
	 */
	private void loadPatch(Patch patch) {
		// 获取所有patch 文件的名字
		Set<String> patchNames = patch.getPatchNames();
		ClassLoader cl;
		List<String> classes;
		// 遍历 所有的patch名
		for (String patchName : patchNames) {
			// 获取 加载该patch文件 class的 ClassLoader
			if (mLoaders.containsKey("*")) {
				cl = mContext.getClassLoader();
			} else {
				cl = mLoaders.get(patchName);
			}
			if (cl != null) {
				// 获取该patch文件中涉及到的Class的集合
				classes = patch.getClasses(patchName);
				// 修复bug方法
				mAndFixManager.fix(patch.getFile(), cl, classes);
			}
		}
	}

}
