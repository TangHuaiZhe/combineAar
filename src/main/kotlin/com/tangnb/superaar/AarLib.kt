package com.tangnb.superaar

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

/**
 * android aar
 */
class AarLib(
  private val mProject: Project,
  private val mArtifact: ResolvedArtifact,
  private val mVariantName: String
) {

  init {
    require("aar" == mArtifact.type) { "artifact must be aar type!" }
  }

  val group: String
    get() = mArtifact.moduleVersion.id.group

  val name: String
    get() = mArtifact.moduleVersion.id.name

  val version: String
    get() = mArtifact.moduleVersion.id.version

  /**
   * 解压Aar之后放置aar内文件的位置
   */
  val rootExplodedFolder: File
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
    get() = File(rootExplodedFolder, "aidl")

  val assetsFolder: File
    get() = File(rootExplodedFolder, "assets")

  val classesJarFile: File
    get() = File(rootExplodedFolder, "classes.jar")

  val localJars: Collection<File>
    get() {
      val localJars = ArrayList<File>()
      val jarList = File(rootExplodedFolder, "libs").listFiles()
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
    get() = File(rootExplodedFolder, "jni")

  val resFolder: File
    get() = File(rootExplodedFolder, "res")

  val manifest: File
    get() = File(rootExplodedFolder, "AndroidManifest.xml")

  val lintJar: File
    get() = File(rootExplodedFolder, "lint.jar")

  val proguardRules: List<File>
    get() {
      val list = ArrayList<File>()
      list.add(File(rootExplodedFolder, "proguard-rules.pro"))
      list.add(File(rootExplodedFolder, "proguard-project.txt"))
      return list
    }

  val rFile: File
    get() = File(rootExplodedFolder, "R.txt")

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
