/*
 * Copyright (c) 2016.
 *
 * This file is part of kotlinsql.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at6uHXpFWIZR
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package kotlinsql.builder

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.util.*

val Project.sourceSets: SourceSetContainer
  get() = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

operator fun SourceSetContainer.get(name:String):SourceSet = getByName(name)

open class GenerateTask: DefaultTask() {

  init {
    group = "generate"
  }

//  @InputFiles
  var inputFiles: FileCollection = project.files()

//  @OutputDirectory
  var outputDir:File = project.file(DEFAULT_GEN_DIR)
//
//  @OutputFiles
//  val outputFiles  = project.files().apply {
//      container?.all { spec: GenerateSpec ->
//        if (spec.output != null) {
//          this.add(project.files(File(outputDir, spec.output)))
//        }
//      }
//    }

//  @Input
  internal var container: NamedDomainObjectContainer<GenerateSpec>? = null

  @TaskAction
  private fun generate() {

    container?.all { spec: GenerateSpec ->
      if (spec.output!=null) {
        val outFile = File(outputDir, spec.output)
        if (project.logger.isInfoEnabled) {
          project.logger.info("Generating ${spec.name} as '${spec.output}' as '${outFile}'")
        } else {
          project.logger.lifecycle("Generating ${spec.name} as '${spec.output}'")
        }
        spec.generator?.let { g ->
          if (! outFile.isFile) {
            outFile.parentFile.mkdirs()
            outFile.createNewFile()
          }
          outFile.writer().use { writer ->
            g.doGenerate(writer, spec.input)
          }

        } ?: logger.quiet("Missing output code for generateSpec ${spec.name}")
      }
    }
  }

  fun doGenerate(output: Writer, classPath: Iterable<File>, input:Iterable<File>) {
    project.logger.quiet("output code missing for ${output.toString()}")
  }
}

private const val OUTPUT_SOURCE_SET = "generatedSources"
private const val DEFAULT_GEN_DIR = "gen/kotlin"

interface GenerateImpl {
  fun doGenerate(output: Writer, input: FileCollection?)
}

class GenerateSpec(val name: String) {
  var output: String?=null
  var generator: GenerateImpl?=null
  var input: FileCollection?=null
}



class BuilderPlugin: Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(JavaPlugin::class.java)
    val sourceSets = project.sourceSets
    val generateSourceSet: SourceSet = sourceSets.create("generatedSources")

    val generateExt = project.container(GenerateSpec::class.java)
    project.extensions.add("generate", generateExt)

    val configuration = project.configurations.create("generate")

    val outputDir = project.file(DEFAULT_GEN_DIR)
    val task = project.tasks.create("generate", GenerateTask::class.java).apply {
      dependsOn(configuration)
      this.outputDir = outputDir
      container = generateExt
    }
    sourceSets.get("main").java.srcDirs.add(outputDir)

    project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn.add(task)

    project.afterEvaluate {
      project.extensions.getByType(IdeaModel::class.java)?.let { ideaModel ->
        ideaModel.module.generatedSourceDirs.add(task.outputDir)
      }
    }

//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
  }
}
