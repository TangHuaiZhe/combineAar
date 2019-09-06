package com.tangnb.superaar

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.gradle.api.Project
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.math.min

object SomeUtils {

  private var mProjectRef: WeakReference<Project>? = null

  private const val ANSI_RESET = "\u001B[0m"
  private const val ANSI_GREEN = "\u001B[32m"
  private const val ANSI_YELLOW = "\u001B[33m"
  private const val ANSI_BLUE = "\u001B[34m"

  @JvmStatic
  fun logGreen(text: String) {
    println(ANSI_GREEN + text + ANSI_RESET)
  }

  @JvmStatic
  fun logYellow(text: String) {
    println(ANSI_YELLOW + text + ANSI_RESET)
  }

  @JvmStatic
  fun logBlue(text: String) {
    println(ANSI_BLUE + text + ANSI_RESET)
  }

  @JvmStatic
  fun setProject(p: Project) {
    mProjectRef = WeakReference(p)
  }

  @JvmStatic
  fun logError(msg: Any) {
    val p: Project? = mProjectRef!!.get()
    p?.logger?.error("[fat-aar]$msg")
  }

  @JvmStatic
  fun logInfo(msg: String) {
    val p: Project? = mProjectRef!!.get()
    p?.logger?.info("[fat-aar]$msg")
  }

  @JvmStatic
  fun logAnytime(msg: String) {
    println("[fat-aar]$msg")
  }

  @JvmStatic
  @Throws(IOException::class)
  fun showDir(indent: Int, file: File) {
    for (i in 0 until indent) print("-")
    DefaultGroovyMethods.println(this, file.name + " " + ResourceGroovyMethods.size(file))
    if (file.isDirectory) {
      val files = file.listFiles()
      for (i in files!!.indices) showDir(indent + 4, files[i])
    }
  }

  @JvmStatic
  fun compareVersion(v1: String, v2: String): Int {
    if (v1 == v2) {
      return 0
    }

    val version1Array = v1.split("[._]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val version2Array = v2.split("[._]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var index = 0
    val minLen = min(version1Array.size, version2Array.size)
    val diff: Long = version1Array[index].toLong() - version2Array[index].toLong()
    while (index < minLen && diff == 0L) {
      index++
    }

    if (diff == 0L) {
      for (i in index until version1Array.size) {
        if (java.lang.Long.parseLong(version1Array[i]) > 0) {
          return 1
        }
      }

      for (i in index until version2Array.size) {
        if (java.lang.Long.parseLong(version2Array[i]) > 0) {
          return -1
        }
      }
      return 0
    } else {
      return if (diff > 0) 1 else -1
    }
  }
}
