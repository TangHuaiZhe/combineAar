package com.tangnb.superaar

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import groovy.lang.Reference
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import java.util.*

class FatLibraryPlugin : Plugin<Project> {

  private lateinit var project: Project
  private lateinit var embedConf: Configuration
  private var artifacts: Set<DefaultResolvedArtifact>? = null
  private var unResolveArtifact: Set<ResolvedDependency>? = null

  override fun apply(project: Project) {
    println("this is combineAar ,dealing with " + project
        .name)
    this.project = project
    SomeUtils.setProject(project)
    checkAndroidPlugin()
    createConfiguration()
    project.afterEvaluate {
      resolveArtifacts()
      dealUnResolveArtifacts()
      val android = project.extensions.getByName("android") as LibraryExtension
      android.libraryVariants.filter {
        val currentFlavor = it.flavorName + it.buildType.name.capitalize()
        // WKDevDebug
//        SomeUtils.logGreen("variant FlavorName+BuildType: $currentFlavor")

        // taskNames: assembleWkDevDebug
//        SomeUtils.logGreen(
//            "variant taskNames: ${gradle.startParameter.taskNames.first().substringAfter(":")}")
        project.gradle.startParameter.taskNames.isNotEmpty() && project.gradle.startParameter.taskNames.first().contains(currentFlavor, true)
      }.forEach {
        SomeUtils.logBlue("start process: ${it.flavorName}${it.buildType.name.capitalize()}")
        processVariant(it)
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
    embedConf = project.configurations.create("embed")
    embedConf.isVisible = true
    embedConf.isTransitive = false

    project.gradle.addListener(object : DependencyResolutionListener {

      override fun beforeResolve(resolvableDependencies: ResolvableDependencies) {
        embedConf.dependencies.forEach { dependency ->
          project.dependencies.add("compileOnly", dependency)
          SomeUtils.logBlue("beforeResolve, add dependencies:$dependency")
        }
        project.gradle.removeListener(this)
      }

      override fun afterResolve(resolvableDependencies: ResolvableDependencies) {
        SomeUtils.logBlue("afterResolve resolvableDependencies:$resolvableDependencies")
      }
    })
  }

  private fun resolveArtifacts() {
    val set = HashSet<DefaultResolvedArtifact>()
    embedConf.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      // jar file wouldn't be here
      if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
        SomeUtils.logAnytime("[embed detected][${artifact.type}] ${artifact.moduleVersion.id}")
      } else {
        throw ProjectConfigurationException("Only support embed aar and jar dependencies!",
            Throwable())
      }
      set.add(artifact as DefaultResolvedArtifact)
    }
    artifacts = Collections.unmodifiableSet(set)
  }

  private fun processVariant(variant: LibraryVariant) {
    SomeUtils.logGreen("processVariant $variant")
    val processor = VariantProcessor(project, variant)
    if(artifacts != null){
      processor.addArtifacts(artifacts!!)
      SomeUtils.logGreen("processor.addArtifacts $artifacts")
    }else{
      SomeUtils.logGreen("processor.addArtifacts failed,artifacts == null")
    }
//    artifacts?.let { processor.addArtifacts(it) }
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
        SomeUtils.logAnytime("[unResolve dependency detected][ + ${dependency.name} + ]")
        dependencySet.add(dependency)
      }
    }
    unResolveArtifact = Collections.unmodifiableSet(dependencySet)
  }

  companion object {

    const val ARTIFACT_TYPE_AAR = "aar"
    const val ARTIFACT_TYPE_JAR = "jar"

    private fun <T> setGroovyRef(ref: Reference<T>, newValue: T): T {
      ref.set(newValue)
      return newValue
    }
  }
}
