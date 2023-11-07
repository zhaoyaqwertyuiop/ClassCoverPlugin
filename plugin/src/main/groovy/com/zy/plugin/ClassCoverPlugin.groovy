package com.zy.plugin

import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.gson.Gson
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

// 本地文件替换jar包的文件 出自 讯飞-高超
class ClassCoverPlugin extends Transform implements Plugin<Project> {

    static class ConfigBean {
        public boolean isTest = true
        public List<String> list = new ArrayList<>()
    }

    private static ConfigBean bean
    private String rootPath

    @Override
    void apply(Project project) {
        println "plugin start: ${this.class.name}"

        project.extensions.create("simpleBuildConfig", ConfigBean.class)
        bean = project.extensions.simpleBuildConfig
        def android = project.extensions.getByType(AppExtension.class)
        android.registerTransform(this)
        rootPath = project.getProjectDir().getAbsolutePath() + "${File.separator}src${File.separator}main${File.separator}java${File.separator}"
    }

    private void saveFile(File dir) {
        if (!dir.exists())
            return
        if (dir.isDirectory()) {
            File[] list = dir.listFiles()
            for (File file : list) {
                saveFile(file)
            }
        } else {
            String path = dir.getPath().replace(rootPath, "")
            if (path.indexOf(".") != -1) {
                path = path.substring(0, path.indexOf(".")).replace("\\", "/")
                bean.list.add(path + ".")
                bean.list.add(path + "\$")
                log("find path= " + path)
            }
        }
    }

    private static void log(String str) {
        if (bean.isTest) {
            println(str)
        }
    }

    @Override
    String getName() {
        return this.class.name
    }

    @Override
    Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        log "--------------- ${this.class.name} visit start --------------- "
        println("start project path= " + rootPath)
        saveFile(new File(rootPath))
        log("config = " + new Gson().toJson(bean))
        def startTime = System.currentTimeMillis()
        Collection<TransformInput> inputs = transformInvocation.inputs
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        //删除之前的输出
        if (outputProvider != null)
            outputProvider.deleteAll()
        //遍历inputs
        inputs.each { TransformInput input ->
            //遍历directoryInputs
            input.directoryInputs.each { DirectoryInput directoryInput ->
                //处理directoryInputs
                handleDirectoryInput(directoryInput, outputProvider)
            }

            //遍历jarInputs
            input.jarInputs.each { JarInput jarInput ->
                //处理jarInputs
                handleJarInputs(jarInput, outputProvider)
            }
        }
        def cost = (System.currentTimeMillis() - startTime) / 1000
        log '--------------- MainPlugin visit end --------------- '
        log "MainPlugin cost ： $cost s"
    }

    private static boolean contains(String clazz) {
        for (String str : bean.list) {
            if (clazz.startsWith(str)) {
                return true
            }
        }
        return false
    }

    //处理Jar中的class文件
    static void handleJarInputs(JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {

            //重名名输出文件,因为可能同名,会覆盖
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            boolean isUpdate = false
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                if (entryName.endsWith(".class") && contains(entryName)) {
                    isUpdate = true
                }
            }

            if (isUpdate) {
                File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp3.jar")
                //避免上次的缓存被重复插入
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
                enumeration = jarFile.entries()
                //用于保存
                while (enumeration.hasMoreElements()) {
                    JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                    String entryName = jarEntry.getName()
                    ZipEntry zipEntry = new ZipEntry(entryName)
                    InputStream inputStream = jarFile.getInputStream(jarEntry)
                    //插桩class
                    //!entryName.endsWith(".class")
                    if (!contains(entryName)) {
                        jarOutputStream.putNextEntry(zipEntry)
                        jarOutputStream.write(IOUtils.toByteArray(inputStream))
                    } else {
                        log "exclude: $entryName"
                    }
                    jarOutputStream.closeEntry()
                }
                jarOutputStream.close()
                jarFile.close()
                def dest = outputProvider.getContentLocation(jarName + md5Name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(tmpFile, dest)
                tmpFile.delete()
                log("dest=${dest.getAbsolutePath()} jarInput.file=${jarInput.file.getAbsolutePath()}")
            } else {
                jarFile.close()
                File dest = outputProvider.getContentLocation(jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }

        }
    }
    // 处理文件目录下的class文件
    static void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        //是否是目录
//        if (directoryInput.file.isDirectory()) {
//            //列出目录所有文件（包含子文件夹，子文件夹内文件）
//            directoryInput.file.eachFileRecurse { File file ->
//                def name = file.name
//                if (name.endsWith(".class") && !name.startsWith("R\$")
//                        && !"R.class".equals(name) && !"BuildConfig.class".equals(name)
//                        && "android/support/v4/app/FragmentActivity.class".equals(name)) {
//                    println '----------- deal with "class" file <' + name + '> -----------'
//                    ClassReader classReader = new ClassReader(file.bytes)
//                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
//                    ClassVisitor cv = new LifecycleClassVisitor(classWriter)
//                    classReader.accept(cv, EXPAND_FRAMES)
//                    byte[] code = classWriter.toByteArray()
//                    FileOutputStream fos = new FileOutputStream(
//                            file.parentFile.absolutePath + File.separator + name)
//                    fos.write(code)
//                    fos.close()
//                }
//            }
//        }
        //处理完输入文件之后，要把输出给下一个任务
        def dest = outputProvider.getContentLocation(directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes,
                Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    static void handleJarInputs2(JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            //重名名输出文件,因为可能同名,会覆盖
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                //插桩class
                if (entryName.endsWith(".class") && !entryName.startsWith("R\$")
                        && !"R.class".equals(entryName) && !"BuildConfig.class".equals(entryName)
                        && "android/support/v4/app/FragmentActivity.class".equals(entryName)) {
                    //class文件处理
                    println '----------- deal with "jar" class file <' + entryName + '> -----------'
//                    jarOutputStream.putNextEntry(zipEntry)
//                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
//                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
//                    ClassVisitor cv = new LifecycleClassVisitor(classWriter)
//                    classReader.accept(cv, EXPAND_FRAMES)
//                    byte[] code = classWriter.toByteArray()
//                    jarOutputStream.write(code)
                } else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            //结束
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }
    }
}