package com.tangnb.superaar

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class AndroidArchiveLibrary(
    private val mProject: Project,
    private val mArtifact: ResolvedArtifact,
    private val mVariantName: String
) {

  init {
    if ("aar" != mArtifact.type) {
      throw IllegalArgumentException("artifact must be aar type!")
    }
  }

  val group: String
    get() = mArtifact.moduleVersion.id.group

  val name: String
    get() = mArtifact.moduleVersion.id.name

  val version: String
    get() = mArtifact.moduleVersion.id.version

  val rootFolder: File
    get() {
      val explodedRootDir = mProject.file(
          mProject.buildDir.toString() + "/intermediates" + "/exploded-aar/")
      val id = mArtifact.moduleVersion.id
      return mProject.file(explodedRootDir.toString()
          + "/" + id.group
          + "/" + id.name
          + "/" + id.version
          + "/" + mVariantName)
    }

  val aidlFolder: File
    get() = File(rootFolder, "aidl")

  val assetsFolder: File
    get() = File(rootFolder, "assets")

  val classesJarFile: File
    get() = File(rootFolder, "classes.jar")

  val localJars: Collection<File>
    get() {
      val localJars = ArrayList<File>()
      val jarList = File(rootFolder, "libs").listFiles()
      if (jarList != null) {
        for (jars in jarList) {
          if (jars.isFile && jars.name.endsWith(".jar")) {
            localJars.add(jars)
          }
        }
      }
      return localJars
    }

  val jniFolder: File
    get() = File(rootFolder, "jni")

  val resFolder: File
    get() = File(rootFolder, "res")

  val manifest: File
    get() = File(rootFolder, "AndroidManifest.xml")

  val lintJar: File
    get() = File(rootFolder, "lint.jar")

  val proguardRules: List<File>
    get() {
      val list = ArrayList<File>()
      list.add(File(rootFolder, "proguard-rules.pro"))
      list.add(File(rootFolder, "proguard-project.txt"))
      return list
    }

  val rFile: File
    get() = File(rootFolder, "R.txt")

  val packageName: String?
    get() {
      var packageName: String? = null
      val manifestFile = manifest
      if (manifestFile.exists()) {
        try {
          val dbf = DocumentBuilderFactory.newInstance()
          val doc = dbf.newDocumentBuilder().parse(manifestFile)
          val element = doc.documentElement
          packageName = element.getAttribute("package")
        } catch (e: Exception) {
          e.printStackTrace()
        }

      } else {
        throw RuntimeException("$name module's AndroidManifest not found")
      }
      return packageName
    }
}
