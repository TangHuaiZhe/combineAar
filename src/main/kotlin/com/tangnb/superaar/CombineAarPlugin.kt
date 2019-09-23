package com.tangnb.superaar

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
import java.util.Collections
import java.util.HashSet

class CombineAarPlugin : Plugin<Project> {

  private lateinit var mProject: Project
  private lateinit var embedConf: Configuration

  /**
   * 已经确定的依赖
   */
  private var resolvedArtifacts: Set<DefaultResolvedArtifact> = HashSet()

  /**
   * 未确定的依赖？
   */
  private var unResolveArtifact: Set<ResolvedDependency> = HashSet()

  override fun apply(project: Project) {

    LogUtil.setDebug(true)

    LogUtil.info("this is combineAar ,dealing with " + project
        .name)

    val taskList = project.gradle.startParameter.taskNames
    for (task in taskList) {
      LogUtil.green("your are execute gradle task: $task")
    }

    this.mProject = project

    checkAndroidPlugin()

    createConfiguration()

    project.afterEvaluate {

      resolveLocalArtifacts()

      dealUnResolveArtifacts()

      val android = project.extensions.getByName("android") as LibraryExtension
      var taskFounded = false

      android.libraryVariants.filter {
        // 过滤掉不需要的task
        val currentFlavor = it.flavorName + it.buildType.name.capitalize()
        taskList.isNotEmpty() && taskList.first().contains(
            currentFlavor, true)
      }.forEach {
        LogUtil.blue("start process: ${it.flavorName}${it.buildType.name.capitalize()}")
        taskFounded = true
        // 开始处理
        processVariant(it)
      }

      if (!taskFounded && taskList.isNotEmpty()) {
        LogUtil.info(
            "CombineAarPlugin has no talk with current task: ${taskList.first()}")
      }
    }
  }

  private fun checkAndroidPlugin() {
    if (!mProject.plugins.hasPlugin("com.android.library")) {
      throw ProjectConfigurationException(
          "combine-aar-plugin must be applied in mProject that has android library plugin!",
          Throwable())
    }
  }

  private fun createConfiguration() {
    embedConf = mProject.configurations.create("embed").extendsFrom()
    embedConf.isVisible = true
    embedConf.isTransitive = false

    mProject.gradle.addListener(object : DependencyResolutionListener {

      override fun beforeResolve(resolvableDependencies: ResolvableDependencies) {
        embedConf.dependencies.forEach { dependency ->
          mProject.dependencies.add("compileOnly", dependency)
          LogUtil.blue("beforeResolve, add dependencies:$dependency")
        }
        mProject.gradle.removeListener(this)
      }

      override fun afterResolve(resolvableDependencies: ResolvableDependencies) {
        LogUtil.blue("afterResolve resolvableDependencies:$resolvableDependencies")
      }
    })
  }

  /**
   * 处理本地embed aar和jar包
   */
  private fun resolveLocalArtifacts() {
    val resolvedLocalArtifactSet = HashSet<DefaultResolvedArtifact>()
    embedConf.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      LogUtil.green("embedConf.resolvedConfiguration.resolveLocalArtifacts is $artifact")
      // jar file wouldn't be here
      if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
        LogUtil.green("[embed detected][${artifact.type}] ${artifact.moduleVersion.id}")
      } else {
        throw ProjectConfigurationException("Only support embed aar and jar dependencies!",
            Throwable())
      }
      resolvedLocalArtifactSet.add(artifact as DefaultResolvedArtifact)
    }
    if (resolvedLocalArtifactSet.isEmpty()) {
      LogUtil.yellow("没有embed配置的resolvedConfiguration")
    } else {
      resolvedArtifacts = Collections.unmodifiableSet(resolvedLocalArtifactSet)
    }
  }

  /**
   * 开始处理当前variant
   */
  private fun processVariant(variant: LibraryVariant) {
    LogUtil.green("processVariant ${variant.flavorName}")

    val processor = VariantProcessor(mProject, variant)

    if (resolvedArtifacts.isNotEmpty()) {
      processor.addArtifacts(resolvedArtifacts)
      LogUtil.blue("processor.addArtifacts $resolvedArtifacts")
    } else {
      LogUtil.green("processor.addArtifact,but resolvedArtifacts is empty")
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
      resolvedArtifacts.forEach { artifact ->
        if (dependency.moduleName == artifact.name) {
          match = true
        }
      }
      if (!match) {
        LogUtil.yellow("[unResolve dependency detected][ + ${dependency.name} + ]")
        dependencySet.add(dependency)
      }
    }
    if (dependencySet.isEmpty()) {
      LogUtil.yellow("没有embedConf配置的unResolve dependency")
    } else {
      unResolveArtifact = Collections.unmodifiableSet(dependencySet)
    }
  }

  companion object {
    const val ARTIFACT_TYPE_AAR = "aar"
    const val ARTIFACT_TYPE_JAR = "jar"
  }
}
