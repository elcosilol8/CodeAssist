package com.tyron.builder.model;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.tyron.builder.BuildModule;
import com.tyron.builder.compiler.manifest.xml.AndroidManifestParser;
import com.tyron.builder.compiler.manifest.xml.ManifestData;
import com.tyron.builder.compiler.resource.AAPT2Compiler;
import com.tyron.builder.compiler.LibraryChecker;
import com.tyron.builder.parser.FileManager;
import com.tyron.common.util.Decompress;
import com.tyron.common.util.StringSearch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openjdk.javax.lang.model.SourceVersion;

/**
 * Class for storing project data, directories and files
 */
public class Project {
    
    public final File mRoot;
    
    public Map<String, File> javaFiles = new HashMap<>();
    private final Map<String, File> kotlinFiles = new HashMap<>();
    public List<String> jarFiles = new ArrayList<>();
    private final Set<File> libraries = new HashSet<>();
    private final Map<String, File> RJavaFiles = new HashMap<>();

    //TODO: Adjust these values according to build.gradle or manifest\
    private final File mAssetsDir;
    private final File mNativeLibsDir;
    private final FileManager mFileManager;
    private ManifestData mManifestData;

    /**
     * Creates a project object from specified root
     */
    public Project(File root) {
        mRoot = root;

        mAssetsDir = new File(root, "app/src/main/assets");
        mNativeLibsDir = new File(root, "app/src/main/jniLibs");

        mFileManager = new FileManager();
    }

    @VisibleForTesting
    public Project(FileManager manager) {
        try {
            mAssetsDir = File.createTempFile("assets", "");
            mNativeLibsDir = new File("jniLibs");
            mRoot = mNativeLibsDir.getParentFile();
            mFileManager = manager;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void open() throws IOException {
        mFileManager.openProject(this);
        mManifestData = AndroidManifestParser.parse(getManifestFile().toPath());
    }

    public FileManager getFileManager() {
        return mFileManager;
    }


    public String getPackageName() {
       return mManifestData.getPackage();
    }

    
    public Map<String, File> getJavaFiles() {
        if (javaFiles.isEmpty()) {
            findJavaFiles(getJavaDirectory());
        }
        return javaFiles;
    }

    public Map<String, File> getKotlinFiles() {
        if (kotlinFiles.isEmpty()) {
            findKotlinFiles(getJavaDirectory());
        }
        return kotlinFiles;
    }

    private void searchRJavaFiles(File root) {
        File[] files = root.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    searchRJavaFiles(file);
                } else {
                    String packageName = StringSearch.packageName(file);
                    if (!packageName.isEmpty()) {
                        packageName = packageName + "." + file.getName().substring(0, file.getName().lastIndexOf("."));
                        RJavaFiles.put(packageName, file);
                    }
                }
            }
        }
    }

    public Map<String, File> getRJavaFiles() {
        if (RJavaFiles.isEmpty()) {
            searchRJavaFiles(new File(getBuildDirectory(), "gen"));
        }
        return RJavaFiles;
    }

    public void clearRJavaFiles() {
        RJavaFiles.clear();
    }

    public Set<File> getLibraries() {
        if (libraries.isEmpty()) {
            LibraryChecker checker = new LibraryChecker(this);
            checker.check();

            searchLibraries();
        }
        return libraries;
    }

    /**
     * Clears all the cached files stored in this project, the next time ths project
     * is opened, it will get loaded again
     */
    public void clear() {
        RJavaFiles.clear();
        libraries.clear();
        javaFiles.clear();
        libraries.clear();
    }
    /**
     * Used to check if this project contains the required directories
     * such as app/src/main/java, resources and others
     */
    public boolean isValidProject() {
        File check = new File(mRoot, "app/src/main/java");

        if (!check.exists()) {
            return false;
        }

        if (!getResourceDirectory().exists()) {
            return false;
        }

        return true;
    }
    
    /**
     * Creates a new project configured at mRoot, returns true if the project
     * has been created, false if not.
     */
    public boolean create() {
        
        // this project already exists
        if (isValidProject()) {
            return false;
        }
        
        File java = getJavaDirectory();
        
        if (!java.mkdirs()) {
            return false;
        }
        
		if (!getResourceDirectory().mkdirs()) {
			return false;
		}
		
        Decompress.unzipFromAssets(BuildModule.getContext(), "test_project.zip",
                java.getAbsolutePath());
		
        if (!getLibraryDirectory().mkdirs()) {
            return false;
        }

        return getBuildDirectory().mkdirs();
    }

    private void findKotlinFiles(File file) {
        File[] files = file.listFiles();

        if (files != null) {
            for (File child : files) {
                if (child.isDirectory()) {
                    findKotlinFiles(child);
                } else {
                    if (child.getName().endsWith(".kt")) {
                        String packageName = StringSearch.packageName(child);
                        if (packageName.isEmpty()) {
                            Log.d("Error package empty", child.getAbsolutePath());
                        } else {
                            javaFiles.put(packageName + "." + child.getName().replace(".kt", ""), child);
                        }
                    }
                }
            }
        }
    }

    public void searchLibraries() {
        libraries.clear();

        File libPath = new File(getBuildDirectory(), "libs");
        File[] files = libPath.listFiles();
        if (files != null) {
            for (File lib : files) {
                if (lib.isDirectory()) {
                    File check = new File(lib, "classes.jar");
                    if (check.exists()) {
                        libraries.add(check);
                    }
                }
            }
        }
    }

    private void findJavaFiles(File file) {
        File[] files = file.listFiles();

        if (files != null) {
            for (File child : files) {
                if (child.isDirectory()) {
                    findJavaFiles(child);
                } else {
                    if (child.getName().endsWith(".java")) {
                        String packageName = StringSearch.packageName(child);
                        Log.d("PROJECT FIND JAVA", "Found " + child.getAbsolutePath());
                        if (packageName.isEmpty()) {
                            Log.d("Error package empty", child.getAbsolutePath());
                        } else {
                            if (SourceVersion.isName(packageName + "." + child.getName().replace(".java", ""))) {
                                javaFiles.put(packageName + "." + child.getName().replace(".java", ""), child);
                            }
                        }
                    }
                }
            }
        }
    }

    public int getMinSdk() {
        return mManifestData.getMinSdkVersion();
    }

    public int getTargetSdk() {
        return mManifestData.getTargetSdkVersion();
    }
	
	public File getResourceDirectory() {
		return new File(mRoot, "app/src/main/res");
	}
    
    public File getJavaDirectory() {
        return new File(mRoot, "app/src/main/java");
    }
    
    public File getLibraryDirectory() {
        return new File(mRoot, "app/libs");
    }
    
    public File getBuildDirectory() {
        return new File(mRoot, "app/build");
    }

    public File getManifestFile() {
        return new File(mRoot, "app/src/main/AndroidManifest.xml");
    }

    public File getAssetsDirectory() {
        return mAssetsDir;
    }

    public File getNativeLibsDirectory() {
        return mNativeLibsDir;
    }

    @Override
    public int hashCode() {
        return mRoot.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Project) {
            Project that = (Project) obj;
            if (this.mRoot.equals(that.mRoot)) {
                return true;
            }
        }
        return false;
    }
}
