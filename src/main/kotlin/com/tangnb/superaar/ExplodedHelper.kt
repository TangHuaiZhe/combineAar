package com.tangnb.superaar

import org.gradle.api.Project
import java.io.File
import java.util.*

/**
 * process jars and classes
 */
object ExplodedHelper {

  /**
   *  将androidLibraries中的jar文件 复制到主工程的libs目录下
   *
   *  将jarFiles(java工程生成的jar包) 复制到主工程的libs目录下
   */
  fun processLibsIntoLibs(
      project: Project,
      androidLibraries: Collection<AarLib>, jarFiles: Collection<File>,
      mainLibsDir: File
  ) {

    copyAndroidLibJarToMainLibs(androidLibraries, project, mainLibsDir)

    copyJarFilesToMainLibs(jarFiles, mainLibsDir, project)
  }

  private fun copyJarFilesToMainLibs(
      jarFiles: Collection<File>,
      libsDir: File,
      project: Project
  ) {
    for (jarFile in jarFiles) {
      if (!jarFile.exists()) {
        LogUtil.info("[warning]$jarFile not found!")
        continue
      }

      LogUtil.info("copy jar from: " + jarFile + " to " + libsDir.absolutePath)

      project.copy {
        it.from(jarFile)
        it.into(libsDir)
      }
    }
  }

  private fun copyAndroidLibJarToMainLibs(
      androidLibraries: Collection<AarLib>,
      project: Project,
      libsDir: File
  ) {
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        LogUtil.info("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }

      if (androidLibrary.localJars.isEmpty()) {
        LogUtil.info("Not found jar file, Library:" + androidLibrary.name)
      } else {
        LogUtil.info(
            "Merge " + androidLibrary.name + " jar file, Library:" + androidLibrary.name)
      }

      androidLibrary.localJars.forEach {
        LogUtil.info(it.path)
      }

      project.copy {
        it.from(androidLibrary.localJars)
        it.into(libsDir)
      }
    }
  }

  /**
   * 将embed工程的class.jar 解压为class文件之后 整体复制到壳工程buildInterClassPathDir目录下
   * 如:xxx/build/intermediates/javac/WKDevDebug/classes
   */
  fun processClassesJarInfoClasses(
      project: Project,
      androidLibraries: Collection<AarLib>, buildInterClassPathDir: File
  ) {
    LogUtil.info("Merge ClassesJar")
    val allJarFiles = ArrayList<File>()
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        LogUtil.yellow("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }
      LogUtil.green(
          "processClassesJarInfoClasses androidLibrary.classesJarFile is ${androidLibrary.classesJarFile.absolutePath}")
      allJarFiles.add(androidLibrary.classesJarFile)
    }
    copyClassesToMainBuildInterClassDir(project, allJarFiles, buildInterClassPathDir)
  }

  private fun copyClassesToMainBuildInterClassDir(
      project: Project,
      allJarFiles: ArrayList<File>,
      buildInterClassPathDir: File
  ) {
    for (jarFile in allJarFiles) {
      project.copy {
        LogUtil.blue(
            "copy from ${jarFile.absolutePath} to ${buildInterClassPathDir.absolutePath}")
        //jar包解压为class文件 在复制过去
        it.from(project.zipTree(jarFile))
        it.into(buildInterClassPathDir)
        it.exclude("META-INF/")
      }
    }
  }

  fun processLibsIntoClasses(
      project: Project,
      androidLibraries: Collection<AarLib>, jarFiles: Collection<File>,
      folderOut: File
  ) {
    LogUtil.info("Merge Libs")
    val allJarFiles = ArrayList<File>()
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        LogUtil.info("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }

      LogUtil.info("[androidLibrary]" + androidLibrary.name)
      allJarFiles.addAll(androidLibrary.localJars)
    }

    for (jarFile in jarFiles) {
      if (!jarFile.exists()) {
        continue
      }

      allJarFiles.add(jarFile)
    }

    for (jarFile in allJarFiles) {
      project.copy {
        it.from(project.zipTree(jarFile))
        it.into(folderOut)
        it.exclude("META-INF/")
      }
    }

  }

}
