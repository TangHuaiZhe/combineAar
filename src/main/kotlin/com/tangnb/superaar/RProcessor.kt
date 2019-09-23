package com.tangnb.superaar

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.io.FileOutputStream

class RProcessor(
  private val mProject: Project,
  private val mVariant: LibraryVariant,
  private val mLibraries: Collection<AarLib>?,
  private val mGradlePluginVersion: String
) {

  private val symbolsMap: Map<String, HashMap<String, String>>
    get() {
      val file = mVersionAdapter.symbolFile
      LogUtil.green("get R file and deal: ${file.absolutePath}")
      if (!file.exists()) {
        throw IllegalAccessException("{$file.absolutePath} not found")
      }

      val map = hashMapOf<String, HashMap<String, String>>()
      file.forEachLine { line ->
        val (intNum, resType, resName, resValue) = line.split(' ')
        if (!map.containsKey(resType)) {
          map[resType] = hashMapOf(Pair(resName, intNum))
        } else {
          map[resType]!![resName] = intNum
        }
      }
      return map
    }

  private val mJavaDir: File = mProject.file(
      mProject.buildDir.toString() + "/intermediates/fat-R/r/" + mVariant.dirName)

  private val mClassDir: File = mProject.file(
      mProject.buildDir.toString() + "/intermediates/fat-R/r-class/" + mVariant
          .dirName)

  private val mJarDir: File = mProject.file(
      mProject.buildDir.toString() + "/outputs/aar-R/" + mVariant.dirName
          + "/libs")

  private val mAarUnZipDir: File
  private val mAarOutputDir: File
  private var mAarOutputPath: String
  private val mVersionAdapter: VersionAdapter =
      VersionAdapter(mProject, mVariant, mGradlePluginVersion)

  private fun deleteEmptyDir(file: File) {
    file.walk().forEach {
      if (it.isDirectory && it.listFiles()!!.isEmpty()) {
        it.delete()
      }
    }
  }

  init {
    // R.java dir
    // R.class compile dir
    // R.jar dir
    // aar zip file
    mAarUnZipDir = mJarDir.parentFile
    // aar output dir
    mAarOutputDir = mProject.file(mProject.buildDir.toString() + "/outputs/aar/")
    mAarOutputPath = mVariant.outputs.first().outputFile
        .absolutePath
    LogUtil.green("mAarOutputPath is $mAarOutputPath")
  }

  fun inject(bundleTask: Task) {
    val rFileTask = createRFileTask(mJavaDir)
    val rClassTask = createRClassTask(mJavaDir, mClassDir)
    val rJarTask = createRJarTask(mClassDir, mJarDir)
    val reBundleAar = createBundleAarTask(mAarUnZipDir, mAarOutputDir, mAarOutputPath)

    reBundleAar.doFirst {
      mProject.copy {
        it.from(mProject.zipTree(mAarOutputPath))
        it.into(mAarUnZipDir)
      }
      deleteEmptyDir(mAarUnZipDir)
    }

    reBundleAar.doLast {
      LogUtil.blue("target: $mAarOutputPath")
    }

    bundleTask.doFirst {
      val f = File(mAarOutputPath)
      if (f.exists()) {
        f.delete()
      }
      mJarDir.parentFile.delete()
      mJarDir.mkdirs()
    }

    bundleTask.doLast {
      // support gradle 5.1 && gradle plugin 3.4 before, the outputName is changed
      val file = File(mAarOutputPath)
      if (!file.exists()) {
        mAarOutputPath = mAarOutputDir.absolutePath + "/" + mProject.name + ".aar"
        reBundleAar.archiveName = File(mAarOutputPath).name
      }
    }

    bundleTask.finalizedBy(rFileTask)
    rFileTask.finalizedBy(rClassTask)
    rClassTask.finalizedBy(rJarTask)
    rJarTask.finalizedBy(reBundleAar)
  }

  private fun createRFile(
    library: AarLib, rFolder: File,
    symbolsMap: Map<String, HashMap<String, String>>
  ) {
    val libPackageName = mVariant.applicationId
    val aarPackageName = library.packageName

    val packagePath = aarPackageName!!.replace(".", "/")

    val rTxt = library.rFile
    val rMap: HashMap<String, HashMap<String, String>> = hashMapOf()

    if (rTxt.exists()) {
      rTxt.forEachLine { line ->
        val (intString, resType, resName, resValue) = line.split(' ')
        if (symbolsMap.containsKey(resType) && symbolsMap[resType]?.get(resName) == intString) {
          if (!rMap.containsKey(resType)) {
            rMap[resType] = hashMapOf(Pair(resName, intString))
          } else {
            rMap[resType]!![resName] = intString
          }
        }
      }
    }

    val sb = StringBuilder()
    sb.append("package $aarPackageName;\n\n")
    sb.append("public final class R {\n")

    rMap.forEach { (resType, values) ->
      sb.append("  public static final class $resType {\n")
      values.forEach { (resName, intString) ->
        sb.append(
            "    public static final $intString $resName = $libPackageName.R.$resType.$resName;\n")
      }
      sb.append("    }\n")
    }
    sb.append("    }\n")

    File("${rFolder.path}/$packagePath").mkdirs()

    val outputStream = FileOutputStream("${rFolder.path}/$packagePath/R.java")
    outputStream.write(sb.toString().toByteArray())
    outputStream.close()
  }

  /**
   * 在主工程中创建一个r-classes.jar文件，重定向每个embed模块的资源引用
   *
   * 因为将每个模块中的res文件全部都打包到了主工程中,最后生成的资源引用全都存在于主工程包名下的的R.txt中，
   * 导致原有被embed的模块中的资源引用无法找到，需要一个重定向的机制
   *
   * 如embed的模块包名为com.xx.xx
   * 主工程包名为com.aa.aa
   * 那么r-classes.jar中包含com.xx.xx.R:
   * 而且每个资源的引用重定向为类似格式:
   * public static final class resType {
   *   public static final int resName = com.aa.aa.R.resType.resName;
   * }
   *
   * 另一种思路是直接生成R.java文件到各工程目录
   */
  private fun createRFileTask(destFolder: File): Task {
    val task = mProject.tasks.create("createRFile${mVariant.name}")

    task.doLast {
      if (destFolder.exists()) {
        destFolder.delete()
      }
      if (mLibraries != null && mLibraries.isNotEmpty()) {
        val rMap = symbolsMap
        mLibraries.forEach { lib ->
          LogUtil.info("Generate R File, Library:${lib.name}")
          createRFile(lib, destFolder, rMap)
        }
      }
    }
    return task
  }

  private fun createRClassTask(sourceDir: File, destinationDir: File): Task {
    mProject.mkdir(destinationDir)

    val classpath = mVersionAdapter.rClassPath
    val taskName = "compileRs" + mVariant.name.capitalize()
    val task = mProject.tasks
        .create(taskName, JavaCompile::class.java) {
          it.setSource(sourceDir.path)
          val android = mProject.extensions.getByName("android") as LibraryExtension
          it.sourceCompatibility = android.compileOptions.sourceCompatibility.toString()
          it.targetCompatibility = android.compileOptions.targetCompatibility.toString()
          it.classpath = classpath
          it.destinationDir = destinationDir
        }

    task.doFirst {
      LogUtil.info("Compile R.class, Dir:${sourceDir.path}")
      LogUtil.info("Compile R.class, classpath:${classpath.first().absolutePath}")

      if (mGradlePluginVersion.gradleVersionBiggerOrEqualThan("3.3.0")) {
        mProject.copy {
          it.from(mProject.zipTree(mVersionAdapter.rClassPath.first().absolutePath + "/R.jar"))
          it.into(mVersionAdapter.rClassPath.first().absolutePath)
        }
      }
    }
    return task
  }

  private fun createRJarTask(fromDir: File, desFile: File): Task {
    val taskName = "createRsJar" + mVariant.name.capitalize()
    val task = mProject.tasks.create(taskName, Jar::class.java) {
      it.from(fromDir.path)
      it.archiveName = "r-classes.jar"
      it.destinationDir = desFile
    }
    task.doFirst {
      LogUtil.info("Generate R.jar, Dir：$fromDir")
    }
    return task
  }

  /**
   * aar重新打包任务
   */
  private fun createBundleAarTask(
    fromFile: File,
    destDir: File,
    filePath: String
  ): AbstractArchiveTask {
    val taskName = "reBundleAar" + mVariant.name.capitalize()

    return mProject.tasks.create(taskName, Zip::class.java) {
      it.from(fromFile)
      it.include("**")
      it.archiveName = File(filePath).name
      it.destinationDir = destDir
    }
  }

  private fun <Value> setProperty0(propOwner: Task, var1: String, var2: Value): Value {
    propOwner.setProperty(var1, var2)
    return var2
  }
}
