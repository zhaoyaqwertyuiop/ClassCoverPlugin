import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

/**
 * 出自： https://blog.51cto.com/u_16213681/7126596
 */
class SimplePlugin extends Transform implements Plugin<Project> {

    Project project

    @Override
    void apply(Project project) {
        println "plugin start: ${this.class.name}"

        this.project = project
//        project.extensions.create("TraceVisitor", TraceVisitor.class)
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(this)
    }

    //transform的名称
    //transformClassesWithSimplePluginForDebug 运行时的名字
    //transformClassesWith + getName() + For + Debug或Release
    @Override
    String getName() {
        return this.class.name
    }

    //需要处理的数据类型，有两种枚举类型
    //CLASSES和RESOURCES，CLASSES代表处理的java的class文件，RESOURCES代表要处理java的资源
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    //    指Transform要操作内容的范围，官方文档Scope有7种类型：
    //
    //    EXTERNAL_LIBRARIES        只有外部库
    //    PROJECT                       只有项目内容
    //    PROJECT_LOCAL_DEPS            只有项目的本地依赖(本地jar)
    //    PROVIDED_ONLY                 只提供本地或远程依赖项
    //    SUB_PROJECTS              只有子项目。
    //    SUB_PROJECTS_LOCAL_DEPS   只有子项目的本地依赖项(本地jar)。
    //    TESTED_CODE                   由当前变量(包括依赖项)测试的代码
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    //指明当前Transform是否支持增量编译
    @Override
    boolean isIncremental() {
        return false
    }

    //    Transform中的核心方法，
    //    inputs中是传过来的输入流，其中有两种格式，一种是jar包格式一种是目录格式。
    //    outputProvider 获取到输出目录，最后将修改的文件复制到输出目录，这一步必须做不然编译会报错
    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        super.transform(context, inputs, referencedInputs, outputProvider, isIncremental)
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        println("==============================${this.class.name} transform start========================================")
        def isIncremental = transformInvocation.isIncremental()
        def outputProvider = transformInvocation.outputProvider
        if (!isIncremental && outputProvider != null) { // OutputProvider管理输出路径，如果消费型输入为空，你会发现OutputProvider == null
            outputProvider.deleteAll() // 不需要增量编译，先清除全部
        }
        transformInvocation.inputs.forEach{ input ->
            input.jarInputs.forEach{jarInput ->
                // 处理Jar
                println("fix Jar name = ${jarInput.name}")
                processJarInput(jarInput, outputProvider, isIncremental)
            }

            input.directoryInputs.forEach{directoryInput ->
                // 处理文件
                println("fix file name = ${directoryInput.name}")
                processDirectoryInput(directoryInput, outputProvider, isIncremental)
            }
        }
        println("==============================${this.class.name} transform end========================================")
    }

    //============================================jar文件修改总入口=======================================================================
    //jar输入文件 修改
    void processJarInput(JarInput jarInput, TransformOutputProvider outputProvider, boolean isIncremental) {
        def dest = outputProvider.getContentLocation(jarInput.file.absolutePath, jarInput.contentTypes, jarInput.scopes, Format.JAR)
        if (isIncremental) { // 处理增量编译
            processJarInputIncremental(jarInput, dest)
        } else { // 不处理增量编译
            processJarInputNoIncremental(jarInput, dest)
        }
    }

    //jar 增量的修改
    void processJarInputIncremental(JarInput jarInput, File dest) {
        switch (jarInput.status) {
            case Status.NOTCHANGED:
                break
            case Status.ADDED:
                //真正transform的地方
                transformJarInput(jarInput, dest)
                break
            case Status.CHANGED:
                //Changed的状态需要先删除之前的
                if (dest.exists()) {
                    FileUtils.forceDelete(dest)
                }
                //真正transform的地方
                transformJarInput(jarInput, dest)
                break
            case Status.REMOVED:
                //移除Removed
                if (dest.exists()) {
                    FileUtils.forceDelete(dest)
                }
                break
        }
    }

    //jar 没有增量的修改
    void processJarInputNoIncremental(JarInput jarInput, File dest) {
        transformJarInput(jarInput, dest)
    }

    //真正执行jar修改的函数
    void transformJarInput(JarInput jarInput, File dest) {
        FileUtils.copyFile(jarInput.file, dest)
    }

    //============================================================文件及文件夹修改总入口======================================================================
    void processDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider, boolean isIncremental) {
        def dest = outputProvider.getContentLocation(directoryInput.file.absolutePath, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
        println("fix processDirectoryInput isIncremental=" + isIncremental)
        if (isIncremental) { //处理增量编译
            processDirectoryInputIncremental(directoryInput, dest)
        } else { // 不处理增量编译
            processDirectoryInputNoIncremental(directoryInput, dest)
        }
    }

    //文件无增量修改
    void processDirectoryInputNoIncremental(DirectoryInput directoryInput, File dest) {
        println("fix processDirectoryInputNoIncremental ")
        transformDirectoryInput(directoryInput, dest)
    }

    //文件增量修改
    void processDirectoryInputIncremental(DirectoryInput directoryInput, File dest) {
        println("fix processDirectoryInputIncremental ")
        FileUtils.forceMkdir(dest)
        def srcDirPath = directoryInput.file.absolutePath
        def destDirPath = dest.absolutePath
        def fileStatusMap = directoryInput.changedFiles
        fileStatusMap.forEach { entry ->
            def inputFile = entry.key
            def status = entry.value
            def destFilePath = inputFile.absolutePath.replace(srcDirPath, destDirPath)
            def destFile = File(destFilePath)

            switch (status) {
                case Status.NOTCHANGED:

                    break;
                case Status.ADDED:
                    //真正transform的地方
                    transformDirectoryInput(directoryInput, dest)
                    break;
                case Status.CHANGED:
                    //处理有变化的
                    FileUtils.touch(destFile)
                    //Changed的状态需要先删除之前的
                    if (dest.exists()) {
                        FileUtils.forceDelete(dest)
                    }
                    //真正transform的地方
                    transformDirectoryInput(directoryInput, dest)
                    break;
                case Status.REMOVED:
                    //移除Removed
                    if (destFile.exists()) {
                        FileUtils.forceDelete(destFile)
                    }
                    break;
            }
        }
    }

    //真正执行文件修改的地方
    void transformDirectoryInput(DirectoryInput directoryInput, File dest) {
        println("fix transformDirectoryInput ")
//        directoryInput.forEach { directoryInput: DirectoryInput? ->
        //是否是目录
        if (directoryInput.file.isDirectory()) {
            println("fix transformDirectoryInput isDirectory ")
            List<File> files = new ArrayList<>()
            findAllFiles(directoryInput.file.listFiles(), files)
            for (File file : files) {
                def name = file.name
                //在这里进行代码处理
                if (name.endsWith(".class") && !name.startsWith("R\$")
                        && "R.class" != name && "BuildConfig.class" != name) {

                    def classReader = new ClassReader(file.readBytes())
                    def classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    def className = name.split(".class")[0]
                    println("class fix chazhuang  " + className)
                    def classVisitor = new TraceVisitor(className, classWriter)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    def code = classWriter.toByteArray()
                    FileOutputStream fos = new FileOutputStream(file.parentFile.absoluteFile.toString() + File.separator + name)
                    fos.write(code)
                    fos.close()
                }
            }
        } else {
            println("fix transformDirectoryInput isFile ")
            def name = directoryInput.file.name
            //在这里进行代码处理
            if (name.endsWith(".class") && !name.startsWith("R\$")
                    && "R.class" != name && "BuildConfig.class" != name) {

//                    ClassReader classReader = ClassReader(file.readBytes())
//                    ClassWriter classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
//                    def className = name.split(".class")[0]
                println("class fix hahahaha  " + name)
            }
        }

        //将修改过的字节码copy到dest，就可以实现编译期间干预字节码的目的了
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    void findAllFiles(File[] files, List<File> outFiles) {
        if (null == outFiles) {
            return
        }
        for (File out : files) {
            if (out.isDirectory()) {
                findAllFiles(out.listFiles(), outFiles)
            } else {
                outFiles.add(out)
            }
        }
    }

    /**
     * 对继承自AppCompatActivity的Activity进行插桩
     */
    public class TraceVisitor extends ClassVisitor{

        public TraceVisitor(String className, ClassVisitor classVisitor) {
            super(Opcodes.ASM7, classVisitor)
        }

        /**
         * 类名
         */
        private String className;

        /**
         * 父类名
         */
        private String superName;

        /**
         * 该类实现的接口
         */
        private String[] interfaces;

        /**
         * ASM进入到类的方法时进行回调
         *
         * @param access
         * @param name       方法名
         * @param desc
         * @param signature
         * @param exceptions
         * @return
         */
        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
                                         String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);

            methodVisitor = new AdviceAdapter(Opcodes.ASM5, methodVisitor, access, name, desc) {

                private boolean isInject() {
                    //如果父类名是AppCompatActivity则拦截这个方法,实际应用中可以换成自己的父类例如BaseActivity
                    if (superName.contains("AppCompatActivity")) {
                        return true
                    }
                    return false
                }

                @Override
                public void visitCode() {
                    super.visitCode();
                }

                /**
                 * 方法开始之前回调
                 */
                @Override
                protected void onMethodEnter() {
                    if (isInject()) {
                        println("onMethodEnter(): ${className}.${name}")
                        if ("onCreate".equals(name)) {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC,
                                    "com/zy/transform/ASMTestUtil",
                                    "onCreateLog", "(Landroid/app/Activity;)V",
                                    false);
                        } else if ("onDestroy".equals(name)) {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC, "com/zy/transform/ASMTestUtil",
                                    "onDestoryLog", "(Landroid/app/Activity;)V", false);
                        } else if ("onStart".equals(name)) {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC, "com/zy/transform/ASMTestUtil",
                                    "onStartLog", "(Landroid/app/Activity;)V", false);
                        } else if ("onStop".equals(name)) {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKESTATIC, "com/zy/transform/ASMTestUtil",
                                    "onStopLog", "(Landroid/app/Activity;)V", false);
                        }
                    }
                }

                /**
                 * 方法结束时回调
                 * @param i
                 */
                @Override
                protected void onMethodExit(int i) {
                    super.onMethodExit(i);
                }
            };
            return methodVisitor;

        }

        /**
         * 当ASM进入类时回调
         *
         * @param version
         * @param access
         * @param name       类名
         * @param signature
         * @param superName  父类名
         * @param interfaces 实现的接口名
         */
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            this.superName = superName;
            this.interfaces = interfaces;
        }
    }
}