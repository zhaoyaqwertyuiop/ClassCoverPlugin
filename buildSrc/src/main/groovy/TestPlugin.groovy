//import org.gradle.api.Plugin
//import org.gradle.api.Project
//
//import com.android.build.gradle.api.ApkVariant
//import org.gradle.api.Plugin
//import org.gradle.api.Project
//import org.gradle.api.Task
//import org.gradle.api.file.DirectoryProperty
//
//class MyPlugin implements Plugin<Project> {
//
//    Project project
//
//    @Override
//    void apply(Project project) {
//        this.project = project
//        println "DexParserPlugin start"
//        if (!project.plugins.hasPlugin("com.android.application")) {
//            throw new Exception(
//                    "'com.android.application' plugin must be applied", null)
//        }
//        project.afterEvaluate {
//            project.android.applicationVariants.all { ApkVariant variant ->
//                project.tasks.findAll {
//                    println "all task: ${it.name}"
//                }
//                checkDexTask(variant, project, "mergeDex")
//                checkDexTask(variant, project, "mergeExtDex")
//                checkDexTask(variant, project, "mergeLibDex")
//                variant.outputs.each { variantOutput ->
//                    if (variantOutput != null && variantOutput.getOutputFile() != null && variantOutput.getOutputFile().exists()) {
//                        def api = variant.getPackageApplicationProvider().get().getTargetApi()
//                        println "api:${api}"
//                        // api 需要根据 dex 的编译版本传参
//                        // dex 文件的 magic numbers 确认 api 是哪个版本，如： number: 37 -> api: 25
//                        headerItem = DexFileUtil.loadDexFile(variantOutput.getOutputFile().getAbsolutePath(), api != null ? api : 25)
//                        int methodCount = headerItem.getMethodCount()
//                        int methodOffset = headerItem.getMethodOffset()
//                        int headerSize = headerItem.getHeaderSize()
//                        int classCount = headerItem.getClassCount()
//                        System.out.println("loadDexFile: apk methodCount: $methodCount, methodOffset:$methodOffset, headerSize:$headerSize, classCount:$classCount")
//                    }
//                }
//            }
//        }
//    }
//
//    void checkDexTask(ApkVariant variant, Project project, String taskName) {
//        def mergeDexTaskName = "$taskName${variant.name.capitalize()}"
//        Task mergeDexTask = project.tasks.findByName(mergeDexTaskName)
//        if (mergeDexTask != null) {
//            mergeDexTask.doLast {
//                DirectoryProperty outputDir = mergeDexTask.getOutputDir()
//                def outputGet = outputDir.get()
//                File asFile = outputGet.asFile
//                if (asFile.isDirectory()) {
//                    String[] dexPaths = asFile.list()
//                    dexPaths.each { dexPath ->
//                        String dexAbsPath = asFile.getPath() + File.separator + dexPath
//                        HeaderItem headerItem = (HeaderItem) DexFileUtil.loadDexFile(dexAbsPath, 25)
//                        int methodCount = headerItem.getMethodCount()
//                        int methodOffset = headerItem.getMethodOffset()
//                        int headerSize = headerItem.getHeaderSize()
//                        int classCount = headerItem.getClassCount()
//                        System.out.println("loadDexFile:${dexAbsPath},\n methodCount: $methodCount, methodOffset:$methodOffset, headerSize:$headerSize, classCount:$classCount")
//                    }
//                }
//            }
//        }
//    }
//}