package com.tangnb.superaar

import org.gradle.api.Project
import java.io.File
import java.util.*

/**
 * process jars and classes Created by Vigi on 2017/1/20. Modified by kezong on 2018/12/18
 */
object ExplodedHelper {

  fun processLibsIntoLibs(
      project: Project,
      androidLibraries: Collection<AndroidArchiveLibrary>, jarFiles: Collection<File>,
      folderOut: File
  ) {
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        SomeUtils.logInfo("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }

      if (androidLibrary.localJars.isEmpty()) {
        SomeUtils.logInfo("Not found jar file, Library:" + androidLibrary.name)
      } else {
        SomeUtils.logInfo(
            "Merge " + androidLibrary.name + " jar file, Library:" + androidLibrary.name)
      }

      androidLibrary.localJars.forEach {
        SomeUtils.logInfo(it.path)
      }

      project.copy {
        it.from(androidLibrary.localJars)
        it.into(folderOut)
      }
    }

    for (jarFile in jarFiles) {
      if (!jarFile.exists()) {
        SomeUtils.logInfo("[warning]$jarFile not found!")
        continue
      }

      SomeUtils.logInfo("copy jar from: " + jarFile + " to " + folderOut.absolutePath)

      project.copy {
        it.from(jarFile)
        it.into(folderOut)
      }
    }
  }

  fun processClassesJarInfoClasses(
      project: Project,
      androidLibraries: Collection<AndroidArchiveLibrary>, folderOut: File
  ) {
    SomeUtils.logInfo("Merge ClassesJar")
    val allJarFiles = ArrayList<File>()
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        SomeUtils.logInfo("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }

      allJarFiles.add(androidLibrary.classesJarFile)
    }

    for (jarFile in allJarFiles) {
      project.copy {
        it.from(project.zipTree(jarFile))
        it.into(folderOut)
        it.exclude("META-INF/")
      }
    }
  }

  fun processLibsIntoClasses(
      project: Project,
      androidLibraries: Collection<AndroidArchiveLibrary>, jarFiles: Collection<File>,
      folderOut: File
  ) {
    SomeUtils.logInfo("Merge Libs")
    val allJarFiles = ArrayList<File>()
    for (androidLibrary in androidLibraries) {
      if (!androidLibrary.rootFolder.exists()) {
        SomeUtils.logInfo("[warning]" + androidLibrary.rootFolder + " not found!")
        continue
      }

      SomeUtils.logInfo("[androidLibrary]" + androidLibrary.name)
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
