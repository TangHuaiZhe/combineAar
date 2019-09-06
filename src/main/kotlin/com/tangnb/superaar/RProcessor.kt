package com.tangnb.superaar

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.StringGroovyMethods
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
    private val mLibraries: Collection<AndroidArchiveLibrary>?,
    private val mGradlePluginVersion: String
) {

  private val symbolsMap: Map<String, HashMap<String, String>>
    get() {
      val file = mVersionAdapter.symbolFile
      if (!file.exists()) {
        throw IllegalAccessException("{$file.absolutePath} not found")
      }

      val map = hashMapOf<String, HashMap<String, String>>()
      file.forEachLine { line ->
        val (intNum, resType, resName, resValue) = line.split(' ')
        map[resType][resName] = resValue
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

  private fun deleteEmptyDir(file: File) {RProcessor
    file.walk().forEach { x ->
      if (x.isDirectory && x.listFiles()!!.isEmpty()) {
        x.delete()
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
    mAarOutputPath = DefaultGroovyMethods.first<BaseVariantOutput>(mVariant.outputs).getOutputFile()
        .getAbsolutePath()
  }

  fun inject(bundleTask: Task) {
    val RFileTask = createRFileTask(mJavaDir)
    val RClassTask = createRClassTask(mJavaDir, mClassDir)
    val RJarTask = createRJarTask(mClassDir, mJarDir)
    val reBundleAar = createBundleAarTask(mAarUnZipDir, mAarOutputDir, mAarOutputPath)

    reBundleAar.doFirst {
      mProject.copy {
        it.from(mProject.zipTree(mAarOutputPath))
        it.into(mAarUnZipDir)
      }
      deleteEmptyDir(mAarUnZipDir)
    }

    reBundleAar.doLast {
      SomeUtils.logAnytime("target: $mAarOutputPath")
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

    bundleTask.finalizedBy(RFileTask)
    RFileTask.finalizedBy(RClassTask)
    RClassTask.finalizedBy(RJarTask)
    RJarTask.finalizedBy(reBundleAar)
  }

  private fun createRFile(
      library: AndroidArchiveLibrary, rFolder: File,
      symbolsMap: Map<String, HashMap<String, String>>
  ) {
    val libPackageName = mVariant.applicationId
    val aarPackageName = library.packageName

    val packagePath = aarPackageName!!.replace(".", "/")

    val rTxt = library.rFile
    val rMap: Map<String, HashMap<String, String>> = hashMapOf()

    if (rTxt.exists()) {
      rTxt.forEachLine { line ->
        val (intString, resType, resName, resValue) = line.split(' ')
        if (symbolsMap.containsKey(resType) && symbolsMap[resType]?.get(resName) == intString) {
          rMap[resType]?.set(resName, resValue)
        }
      }
    }

    val sb: StringBuilder = StringBuilder()
    sb.append("package $aarPackageName;'\n\n'")
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

  private fun createRFileTask(destFolder: File): Task {
    val task = mProject.tasks.create("createRFile${mVariant.name}")

    task.doLast {
      if (destFolder.exists()) {
        destFolder.delete()
      }
      if (mLibraries != null && mLibraries.isNotEmpty()) {
        val symbolsMap = symbolsMap
        mLibraries.forEach {
          SomeUtils.logInfo("Generate R File, Library:${it.name}")
          createRFile(it, destFolder, symbolsMap)
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
          it.destinationDir
        }

    task.doFirst {
      SomeUtils.logInfo("Compile R.class, Dir:${sourceDir.path}")
      SomeUtils.logInfo("Compile R.class, classpath:${classpath.first().absolutePath}")

      if (SomeUtils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
        mProject.copy {
          it.from(mProject.zipTree(mVersionAdapter.rClassPath.first().absolutePath + "/R.jar"))
          it.into(mVersionAdapter.rClassPath.first().absolutePath)
        }
      }
    }
    return task
  }

  private fun createRJarTask(fromDir: File, desFile: File): Task {
    val taskName = "createRsJar" + StringGroovyMethods.capitalize(mVariant.name)
    val task = mProject.tasks.create(taskName, Jar::class.java) {
      it.from(fromDir.path)
      it.archiveName = "r-classes.jar"
      it.destinationDir = desFile
    }
    task.doFirst {
      SomeUtils.logInfo("Generate R.jar, Dir：$fromDir")
    }
    return task
  }

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
