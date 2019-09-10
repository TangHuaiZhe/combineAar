package com.tangnb.superaar

import android.annotation.SuppressLint
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import java.util.*

@SuppressLint("DefaultLocale")
class FatLibraryPlugin : Plugin<Project> {

  private lateinit var project: Project
  private lateinit var embedConf: Configuration
  private var artifacts: Set<DefaultResolvedArtifact>? = null
  private var unResolveArtifact: Set<ResolvedDependency>? = null

  override fun apply(project: Project) {
    println("this is combineAar ,dealing with " + project
        .name)
    this.project = project

    checkAndroidPlugin()

    createConfiguration()

    project.afterEvaluate {

      resolveArtifacts()

      dealUnResolveArtifacts()

      val android = project.extensions.getByName("android") as LibraryExtension
      var taskFounded = false

      android.libraryVariants.filter {
        // 过滤掉不需要的task
        val currentFlavor = it.flavorName + it.buildType.name.capitalize()
        project.gradle.startParameter.taskNames.isNotEmpty() && project.gradle.startParameter.taskNames.first().contains(
            currentFlavor, true)
      }.forEach {
        // 开始处理
        LogUtil.blue("start process: ${it.flavorName}${it.buildType.name.capitalize()}")
        taskFounded = true
        processVariant(it)
      }

      if (!taskFounded && project.gradle.startParameter.taskNames.isNotEmpty()) {
        LogUtil.yellow(
            "FatLibraryPlugin ${project.gradle.startParameter.taskNames.first()} not found")
      }
    }
  }

  private fun checkAndroidPlugin() {
    if (!project.plugins.hasPlugin("com.android.library")) {
      throw ProjectConfigurationException(
          "fat-aar-plugin must be applied in project that has android library plugin!", Throwable())
    }
  }

  private fun createConfiguration() {
    embedConf = project.configurations.create("embed").extendsFrom()
    embedConf.isVisible = true
    embedConf.isTransitive = false

    project.gradle.addListener(object : DependencyResolutionListener {

      override fun beforeResolve(resolvableDependencies: ResolvableDependencies) {
        embedConf.dependencies.forEach { dependency ->
          project.dependencies.add("compileOnly", dependency)
          LogUtil.blue("beforeResolve, add dependencies:$dependency")
        }
        project.gradle.removeListener(this)
      }

      override fun afterResolve(resolvableDependencies: ResolvableDependencies) {
        LogUtil.blue("afterResolve resolvableDependencies:$resolvableDependencies")
      }
    })
  }

  private fun resolveArtifacts() {
    val resolvedArtifactSet = HashSet<DefaultResolvedArtifact>()
    embedConf.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      LogUtil.green("embedConf.resolvedConfiguration.resolvedArtifacts is $artifact")
      // jar file wouldn't be here
      if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
        LogUtil.green("[embed detected][${artifact.type}] ${artifact.moduleVersion.id}")
      } else {
        throw ProjectConfigurationException("Only support embed aar and jar dependencies!",
            Throwable())
      }
      resolvedArtifactSet.add(artifact as DefaultResolvedArtifact)
    }
    artifacts = Collections.unmodifiableSet(resolvedArtifactSet)
  }

  /**
   * 开始处理当前variant
   */
  private fun processVariant(variant: LibraryVariant) {
    LogUtil.green("processVariant ${variant.flavorName}")

    val processor = VariantProcessor(project, variant)

    //todo artifacts列表为空 如何处理
    if (artifacts != null && artifacts!!.isNotEmpty()) {
      processor.addArtifacts(artifacts!!)
      LogUtil.green("processor.addArtifacts $artifacts")
    } else {
      LogUtil.green("processor.addArtifacts failed,artifacts == null")
    }
    processor.addUnResolveArtifact(unResolveArtifact)
    processor.processVariant()
  }

  private fun dealUnResolveArtifacts() {
    val dependencies = Collections
        .unmodifiableSet(embedConf.resolvedConfiguration.firstLevelModuleDependencies)
    val dependencySet = HashSet<ResolvedDependency>()

    dependencies.forEach { dependency ->
      var match = false
      artifacts!!.forEach { artifact ->
        if (dependency.moduleName == artifact.name) {
          match = true
        }
      }
      if (!match) {
        LogUtil.yellow("[unResolve dependency detected][ + ${dependency.name} + ]")
        dependencySet.add(dependency)
      }
    }
    unResolveArtifact = Collections.unmodifiableSet(dependencySet)
  }

  companion object {
    const val ARTIFACT_TYPE_AAR = "aar"
    const val ARTIFACT_TYPE_JAR = "jar"
  }
}
