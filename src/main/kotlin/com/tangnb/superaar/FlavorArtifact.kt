package com.tangnb.superaar

import com.android.build.gradle.api.LibraryVariant
import groovy.lang.Reference
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Factory
import org.gradle.internal.component.model.DefaultIvyArtifactName
import java.io.File
import java.util.*

object FlavorArtifact {

  fun createFlavorArtifact(
      project: Project,
      variant: LibraryVariant, unResolvedArtifact: ResolvedDependency, version: String
  ): DefaultResolvedArtifact {

    val identifier =
        createModuleVersionIdentifier(unResolvedArtifact)
    val artifactName = createArtifactName(unResolvedArtifact)
    val artifactProject =
        getArtifactProject(project, unResolvedArtifact)
    val artifactFile =
        createArtifactFile(artifactProject!!, variant,
            unResolvedArtifact,
            version)

    val fileFactory = Factory { artifactFile }
    val taskDependency =
        createTaskDependency(artifactProject, variant)
    val artifactIdentifier =
        createComponentIdentifier(artifactFile)

    return DefaultResolvedArtifact(identifier, artifactName, artifactIdentifier, taskDependency,
        fileFactory)
  }

  private fun createModuleVersionIdentifier(
      unResolvedArtifact: ResolvedDependency
  ): ModuleVersionIdentifier {
    return DefaultModuleVersionIdentifier.newId(unResolvedArtifact.moduleGroup,
        unResolvedArtifact.moduleName, unResolvedArtifact.moduleVersion)
  }

  private fun createArtifactName(unResolvedArtifact: ResolvedDependency): DefaultIvyArtifactName {
    return DefaultIvyArtifactName(unResolvedArtifact.moduleName, "aar", "")
  }

  private fun createComponentIdentifier(artifactFile: File): ComponentArtifactIdentifier {
    return object : ComponentArtifactIdentifier {
      override fun getComponentIdentifier(): ComponentIdentifier? {
        return null
      }

      override fun getDisplayName(): String {
        return artifactFile.name
      }

    }
  }

  private fun getArtifactProject(
      project: Project,
      unResolvedArtifact: ResolvedDependency
  ): Project? {
    for (p in project.rootProject.allprojects) {
      if (unResolvedArtifact.moduleName == p.name) {
        return p
      }

    }

    return null
  }

  private fun createArtifactFile(
      project: Project, variant: LibraryVariant,
      unResolvedArtifact: ResolvedDependency, version: String
  ): File {
    val buildPath = project.buildDir.path
//    val outputName = if (project.gradle.gradleVersion.gradleVersionBiggerOrEqualThan("5.1.0") && version.gradleVersionBiggerOrEqualThan("3.4.0")

    val outputName = if (project.gradle.gradleVersion.gradleVersionBiggerOrEqualThan("5.1.0")
        && version.gradleVersionLitterThan("3.4")) {
      buildPath + "/outputs/aar/" + unResolvedArtifact.moduleName + ".aar"
    } else {
      buildPath + "/outputs/aar/" + unResolvedArtifact.moduleName + "-" + variant
          .flavorName + "-" + variant.buildType.name + ".aar"
    }

    return File(outputName)
  }

  private fun createTaskDependency(project: Project, variant: LibraryVariant): TaskDependency {
    val taskPath = Reference(
        "bundle" + variant.name.capitalize())
    val bundleTask = Reference(
        project.tasks.findByPath(taskPath.get()))
    if (bundleTask.get() == null) {
      taskPath.set("bundle" + variant.name.capitalize() + "Aar")
      bundleTask.set(project.tasks.findByPath(taskPath.get()))
    }

    if (bundleTask.get() == null) {
      throw RuntimeException("Can not find task " + taskPath.get() + "!")
    }

    return TaskDependency {
      val set = HashSet<Task>()
      val task = bundleTask.get()!!
      set.add(task)
      set
    }
  }
}
