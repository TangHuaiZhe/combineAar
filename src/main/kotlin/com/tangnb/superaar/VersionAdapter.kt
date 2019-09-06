package com.tangnb.superaar

import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.tasks.ManifestProcessorTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

class VersionAdapter(
    private val mProject: Project,
    private val mVariant: LibraryVariant,
    private val mGradlePluginVersion: String
) {

  // >= Versions 3.2.X
  // Versions 3.0.x and 3.1.x
  val classPathDirFiles: ConfigurableFileCollection
    get() {

      return when {
        SomeUtils.compareVersion(mGradlePluginVersion, "3.5.0") >= 0 ->
          mProject.files(mProject.buildDir.path + "/intermediates/"
              .plus("javac/" + mVariant.name + "/classes"))
        SomeUtils.compareVersion(mGradlePluginVersion, "3.2.0") >= 0 ->
          mProject.files(mProject.buildDir.path + "/intermediates/".plus(
              "javac/" + mVariant.name + "/compile" + mVariant.name.capitalize() +
                  "JavaWithJavac/classes"))
        else -> mProject.files(
            mProject.buildDir.path + "/intermediates/classes/" + mVariant.dirName)
      }
    }

  val rClassPath: ConfigurableFileCollection
    get() = when {
      SomeUtils.compareVersion(mGradlePluginVersion, "3.5.0") >= 0 ->
        mProject.files(
            mProject.buildDir.path + "/intermediates/"
                .plus("compile_only_not_namespaced_r_class_jar/").plus(mVariant.name))
      SomeUtils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0 ->
        mProject.files(
            mProject.buildDir.path + "/intermediates/"
                .plus("compile_only_not_namespaced_r_class_jar/").plus(
                    mVariant.name + "/generate" + mVariant.name.capitalize()
                        + "rFile"))
      else -> classPathDirFiles
    }

  val libsDirFile: File
    get() = if (SomeUtils.compareVersion(mGradlePluginVersion, "3.1.0") >= 0) {
      mProject.file(
          mProject.buildDir.path + "/intermediates/packaged-classes/" + mVariant
              .dirName + "/libs")
    } else {
      mProject.file(
          mProject.buildDir.path + "/intermediates/bundles/" + mVariant.dirName
              + "/libs")
    }

  val javaCompileTask: Task
    get() = if (SomeUtils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
      mVariant.javaCompileProvider.get()
    } else {
      mVariant.javaCompiler
    }

  val processManifest: ManifestProcessorTask
    get() = if (SomeUtils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
      mVariant.outputs.first().processManifestProvider.get()
    } else {
      mVariant.outputs.first().processManifest
    }

  val mergeAssets: Task
    get() = if (SomeUtils.compareVersion(mGradlePluginVersion, "3.3.0") >= 0) {
      mVariant.mergeAssetsProvider.get()
    } else {
      mVariant.mergeAssets
    }

  val symbolFile: File
    get() = if (SomeUtils.compareVersion(mGradlePluginVersion, "3.1.0") >= 0) {
      mProject.file(
          mProject.buildDir.path + "/intermediates/symbols/" + mVariant.dirName
              + "/R.txt")
    } else {
      mProject.file(
          mProject.buildDir.path + "/intermediates/bundles/" + mVariant.name
              + "/R.txt")
    }
}
