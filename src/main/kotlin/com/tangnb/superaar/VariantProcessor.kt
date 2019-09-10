package com.tangnb.superaar

import android.annotation.SuppressLint
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.tasks.MergeFileTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskDependency
import java.io.File

@SuppressLint("DefaultLocale")
internal class VariantProcessor(
    private val mProject: Project,
    private val mVariant: LibraryVariant
) {

  private val mResolvedArtifacts = ArrayList<DefaultResolvedArtifact>()

  private val mAndroidArchiveLibraries = ArrayList<AarLib>()//项目aar list

  private val mJarFiles = ArrayList<File>()

  private val mExplodeTasks = ArrayList<Task>()

  private var mGradlePluginVersion: String = ""

  private val mVersionAdapter: VersionAdapter

  private val android = mProject.extensions.getByName("android") as LibraryExtension

  init {
    // gradle version
    mProject.rootProject.buildscript.configurations.getByName("classpath")
        .dependencies.forEach { dep ->
      LogUtil.blue("${dep.group} ${dep.name} ${dep.version}")
      if (dep.group == "com.android.tools.build" && dep.name == "gradle") {
        mGradlePluginVersion = dep.version!!
      }
    }
    if (mGradlePluginVersion.isEmpty()) {
      throw IllegalStateException(
          "com.android.tools.build:gradle is no set in the root build.gradle file!!")
    }
    mVersionAdapter = VersionAdapter(mProject, mVariant, mGradlePluginVersion)
  }

  fun addArtifacts(resolvedArtifacts: Set<DefaultResolvedArtifact>) {
    mResolvedArtifacts.addAll(resolvedArtifacts)
    resolvedArtifacts.forEach {
      LogUtil.green("addArtifacts $it")
    }
  }

  private fun addAndroidArchiveLibrary(library: AarLib) {
    mAndroidArchiveLibraries.add(library)
  }

  fun addUnResolveArtifact(dependencies: Set<ResolvedDependency>?) {
    dependencies?.forEach {
      val artifact =
          FlavorArtifact.createFlavorArtifact(mProject, mVariant, it, mGradlePluginVersion)
      LogUtil.green("addUnResolveArtifact $artifact")
      mResolvedArtifacts.add(artifact)
    }
  }

  private fun addJarFile(jar: File) {
    mJarFiles.add(jar)
  }

  fun processVariant() {
    // pre Build task:preWKDevDebugBuild
    val prepareTask = getPreBuildTask()

    // bundle task :bundleWKDevDebugAar
    val bundleTask = getBundleArrTask()

    processCache()

    processArtifacts(prepareTask, bundleTask)

    processClassesAndJars(bundleTask)

    if (mAndroidArchiveLibraries.isEmpty()) {
      return
    }
    processManifest()
    processResourcesAndR()
    processAssets()
    processJniLibs()
//    processProguardTxt(prepareTask) //todo 等待支持
    val rProcessor =
        RProcessor(mProject, mVariant, mAndroidArchiveLibraries, mGradlePluginVersion)
    rProcessor.inject(bundleTask)
  }

  private fun getBundleArrTask(): Task {
    var bundleTaskName = "bundle" + mVariant.name.capitalize()
    var bundleTask = mProject.tasks.findByPath(bundleTaskName)
    if (bundleTask == null) {
      bundleTaskName = "bundle" + mVariant.name.capitalize() + "Aar"//找不到添加aar后再找，版本兼容性问题
      bundleTask = mProject.tasks.findByPath(bundleTaskName)
    }
    println("bundleTask is $bundleTask")
    if (bundleTask == null) {
      throw RuntimeException("Can not find task $bundleTask!")
    }
    return bundleTask
  }

  private fun getPreBuildTask(): Task {
    val preTaskName = "pre" + mVariant.name.capitalize() + "Build"
    val prepareTask = mProject.tasks.findByPath(preTaskName) ?: throw RuntimeException(
        "Can not find task $preTaskName!")
    println("preBuildTask is $prepareTask")
    return prepareTask
  }

  private fun processCache() {
    if (mGradlePluginVersion.gradleVersionBiggerOrEqualThan("3.5.0")) {
      mVersionAdapter.libsDirFile.delete()
      mVersionAdapter.classPathDirFiles.first().delete()
    }
  }

  /**
   * exploded artifact files
   */
  private fun processArtifacts(prepareTask: Task, bundleTask: Task) {
    for (artifact in mResolvedArtifacts) {
      LogUtil.green("processArtifacts $artifact")
      if (FatLibraryPlugin.ARTIFACT_TYPE_JAR == artifact.type) {
        addJarFile(artifact.file)
      } else if (FatLibraryPlugin.ARTIFACT_TYPE_AAR == artifact.type) {
        //artifact:BaseCommon-WKPre-debug.aar
        val archiveLibrary = AarLib(mProject, artifact, mVariant.name)
        addAndroidArchiveLibrary(archiveLibrary)
        //todo 找到当前任务的依赖task
        val buildDependencies =
            artifact.getBuildDependencies().getDependencies(null)
        for (dep in buildDependencies) {
          LogUtil.green("$artifact dep is $dep")
        }
        archiveLibrary.rootFolder.delete()

        val zipFolder = archiveLibrary.rootFolder
        zipFolder.mkdirs()

        val group = artifact.moduleVersion.id.group.capitalize()
        val name = artifact.name.capitalize()
        val taskName = "explode$group$name${mVariant.name.capitalize()}"

        val explodeTask = mProject.tasks.create(taskName) {
          println("explodeTask $taskName")
        }.dependsOn(buildDependencies.first()).mustRunAfter(buildDependencies.first())
        buildDependencies.first().finalizedBy(explodeTask)

//        val copyTarget = findAarPath(explodeTask, artifact)

        var copyTarget = ""
        explodeTask.doFirst {
          LogUtil.green("explodeTask doFirst")
          //todo 找到编译出来的module的aar
          val path = artifact.file.absolutePath.substringBeforeLast("/")
          File(path).walk().filter { it.isFile }.forEach {
            if (it.name.replace("-", "").toLowerCase() == artifact.toString().replace("-",
                    "").toLowerCase()) {
              copyTarget = it.absolutePath
            }
          }
          if (copyTarget.isEmpty()) {
            LogUtil.yellow("找不到aar文件1: ${artifact.file.absolutePath}")
          }
        }

        explodeTask.doLast {
          LogUtil.green("explodeTask doLast")
          // 复制操作
          if (File(copyTarget).exists()) {
            copyAar(mProject.zipTree(copyTarget), zipFolder)
            LogUtil.green("复制完成: ${artifact.file.absolutePath}")
          } else {
            LogUtil.yellow("找不到aar文件2: ${artifact.file.absolutePath}")
          }
        }

//        if (buildDependencies.size == 0) {
//          explodeTask.dependsOn(prepareTask)
//        }
//        else {
//          SomeUtils.green("$explodeTask dependsOn ${buildDependencies.first()}")
//          explodeTask.dependsOn(buildDependencies.first())
//        }
        val javacTask = mVersionAdapter.javaCompileTask
        javacTask.dependsOn(explodeTask)
        bundleTask.dependsOn(explodeTask)
        mExplodeTasks.add(explodeTask)
      }
    }
  }

  /**
   * merge manifest
   */
  private fun processManifest() {
    val processManifestTask = mVersionAdapter.processManifest
    val manifestOutputBackup: File
    manifestOutputBackup =
        if (mGradlePluginVersion.gradleVersionBiggerOrEqualThan(
                "3.3.0")) {
          mProject.file(
              "${mProject.buildDir.path}/intermediates/library_manifest/${mVariant.name}/AndroidManifest.xml")
        } else {
          mProject.file(
              processManifestTask.manifestOutputDirectory.get().asFile.absolutePath + "/AndroidManifest.xml")
        }
    val manifestsMergeTask = mProject.tasks.create("merge${mVariant.name.capitalize()}Manifest",
        LibraryManifestMerger::class.java)

    manifestsMergeTask.setGradleVersion(mProject.gradle.gradleVersion)
    manifestsMergeTask.setGradlePluginVersion(mGradlePluginVersion)
    manifestsMergeTask.variantName = mVariant.name
    manifestsMergeTask.mainManifestFile = manifestOutputBackup
    val list = ArrayList<File>()
    for (archiveLibrary in mAndroidArchiveLibraries) {
      list.add(archiveLibrary.manifest)
    }
    manifestsMergeTask.secondaryManifestFiles = list
    manifestsMergeTask.outputFile = manifestOutputBackup
    manifestsMergeTask.dependsOn(processManifestTask)
    manifestsMergeTask.doFirst {
      val existFiles = ArrayList<File>()
      manifestsMergeTask.secondaryManifestFiles.forEach {
        if (it.exists()) {
          existFiles.add(it)
        }
      }
      manifestsMergeTask.secondaryManifestFiles = existFiles
    }

    mExplodeTasks.forEach {
      manifestsMergeTask.dependsOn(it)
    }

    processManifestTask.finalizedBy(manifestsMergeTask)
  }

  private fun handleClassesMergeTask(isMinifyEnabled: Boolean): Task {
    val task = mProject.tasks.create(
        "mergeClasses" + mVariant.name.capitalize())
    task.doFirst {
      val dustDir = mVersionAdapter.classPathDirFiles.first()
      if (isMinifyEnabled) {
        ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, dustDir)
        ExplodedHelper.processLibsIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles,
            dustDir)
      } else {
        ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, dustDir)
      }
    }
    return task
  }

  private fun handleJarMergeTask(): Task {
    val task = mProject.tasks.create("mergeJars" + mVariant.name.capitalize())
    task.doFirst {
      ExplodedHelper.processLibsIntoLibs(mProject, mAndroidArchiveLibraries, mJarFiles,
          mVersionAdapter.libsDirFile)
    }
    return task
  }

  /**
   * merge classes and jars
   */
  private fun processClassesAndJars(bundleTask: Task) {
    val isMinifyEnabled = mVariant.buildType.isMinifyEnabled
    dealMinify(isMinifyEnabled)

    val taskPath = "transformClassesAndResourcesWithSyncLibJarsFor" + mVariant.name.capitalize()
    val syncLibTask = mProject.tasks.findByPath(taskPath) ?: throw RuntimeException(
        "Can not find task $taskPath!")

    val javacTask = mVersionAdapter.javaCompileTask
    val mergeClasses = handleClassesMergeTask(isMinifyEnabled)
    syncLibTask.dependsOn(mergeClasses)
    mExplodeTasks.forEach {
      mergeClasses.dependsOn(it)
    }
    mergeClasses.dependsOn(javacTask)
    bundleTask.dependsOn(mergeClasses)//修复多个embed丢失的代码问题

    if (!isMinifyEnabled) {
      val mergeJars = handleJarMergeTask()
      mergeJars.mustRunAfter(syncLibTask)
      bundleTask.dependsOn(mergeJars)
      mExplodeTasks.forEach {
        mergeJars.dependsOn(it)
      }
      mergeJars.dependsOn(javacTask)
    }
  }

  private fun dealMinify(isMinifyEnabled: Boolean) {
    if (isMinifyEnabled) {
      //merge proguard file
      for (archiveLibrary in mAndroidArchiveLibraries) {
        val thirdProguardFiles = archiveLibrary.proguardRules
        for (file in thirdProguardFiles) {
          if (file.exists()) {
            LogUtil.info("add proguard file: " + file.absolutePath)
            android.defaultConfig.proguardFile(file)
          }
        }
      }
    }
  }

  /**
   * merge R.txt(actually is to fix issue caused by provided configuration) and res
   *
   * Here I have to inject res into "main" instead of "variant.name".
   * To avoid the res from embed dependencies being used, once they have the same res Id with main res.
   *
   * Now the same res Id will cause a build exception: Duplicate resources, to encourage you to change res Id.
   * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
   */
  private fun processResourcesAndR() {
    val taskPath = "generate" + mVariant.name.capitalize() + "Resources"
    val resourceGenTask = mProject.tasks.findByPath(taskPath) ?: throw RuntimeException(
        "Can not find task $taskPath!")

    resourceGenTask.doFirst {
      for (archiveLibrary in mAndroidArchiveLibraries) {
        android.sourceSets.forEach { androidSourceSet ->
          if (androidSourceSet.name == mVariant.name) {
            LogUtil.green("Merge resource，Library res：${archiveLibrary.resFolder}")
            androidSourceSet.res.srcDir(archiveLibrary.resFolder)
          }
        }
      }
    }

    mExplodeTasks.forEach {
      resourceGenTask.dependsOn(it)
    }
  }

  /**
   * merge assets
   *
   * AaptOptions.setIgnoreAssets and AaptOptions.setIgnoreAssetsPattern will work as normal
   */
  private fun processAssets() {
    val assetsTask = mVersionAdapter.mergeAssets

    assetsTask.doFirst {
      for (archiveLibrary in mAndroidArchiveLibraries) {
        if (archiveLibrary.assetsFolder.exists()) {
          android.sourceSets.forEach {
            if (it.name == mVariant.name) {
              it.assets.srcDir(archiveLibrary.assetsFolder)
            }
          }
        }
      }
    }

    mExplodeTasks.forEach {
      assetsTask.dependsOn(it)
    }
  }

  /**
   * merge jniLibs
   */
  private fun processJniLibs() {
    val taskPath = "merge" + mVariant.name.capitalize() + "JniLibFolders"
    val mergeJniLibsTask = mProject.tasks.findByPath(taskPath) ?: throw RuntimeException(
        "Can not find task $taskPath!")

    mergeJniLibsTask.doFirst {
      for (archiveLibrary in mAndroidArchiveLibraries) {
        if (archiveLibrary.jniFolder.exists()) {
          android.sourceSets.forEach {
            if (it.name == mVariant.name) {
              it.jniLibs.srcDir(archiveLibrary.jniFolder)
            }
          }
        }
      }
    }
    mExplodeTasks.forEach {
      mergeJniLibsTask.dependsOn(it)
    }
  }

  /**
   * fixme
   * merge proguard.txt
   */
  private fun processProguardTxt(prepareTask: Task) {
    val taskPath = "merge" + mVariant.name.capitalize() + "ConsumerProguardFiles"
    val mergeFileTask = (mProject.tasks.findByPath(taskPath) ?: throw RuntimeException(
        "Can not find task $taskPath!")) as MergeFileTask
    for (archiveLibrary in mAndroidArchiveLibraries) {
      val thirdProguardFiles = archiveLibrary.proguardRules
      for (file in thirdProguardFiles) {
        if (file.exists()) {
          LogUtil.info("add proguard file: " + file.absolutePath)
          mergeFileTask.inputs.file(file)
        }
      }
    }
    mergeFileTask.doFirst {
      val proguardFiles = mergeFileTask.inputFiles
      for (archiveLibrary in mAndroidArchiveLibraries) {
        val thirdProguardFiles = archiveLibrary.proguardRules
        for (file in thirdProguardFiles) {
          if (file.exists()) {
            LogUtil.info("add proguard file: " + file.absolutePath)
            proguardFiles.plus(file)
          }
        }
      }
      mergeFileTask.dependsOn(prepareTask)
    }
  }

  private fun DefaultResolvedArtifact.getBuildDependencies(): TaskDependency {
    val field = this.javaClass.declaredFields
        .toList().first { it.name == "buildDependencies" }
    field.isAccessible = true
    val value = field.get(this)
    return value as TaskDependency
  }

  @TaskAction
  fun copyAar(from: Any, destDir: Any) {
    mProject.copy { copySpec ->
      LogUtil.green(" === copyAar from $from \n to $destDir===")
      copySpec.from(from)
      copySpec.into(destDir)
    }
  }
}
