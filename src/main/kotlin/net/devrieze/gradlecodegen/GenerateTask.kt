/*
 * Copyright (c) 2021.
 *
 * This file is part of gradle-codegen.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
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

package net.devrieze.gradlecodegen

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.Writer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.Callable

open class GenerateTask : DefaultTask() {

    init {
        group = "generate"
        outputs.upToDateWhen { task -> false } // do something smarter
    }

    @OutputDirectory
    var outputDir: Any = project.file(DEFAULT_GEN_DIR)

    @get:InputFiles
    var classpath: FileCollection? = null

    @get:Input
    val dirGenerator = GenerateDirSpec()

    @Suppress("unused")
            /** Configuration function that allows the dirGenerator to be configured with a closure. */
    fun dirGenerator(closure: Closure<Any?>?) {
        project.configure(dirGenerator, closure)
    }

    fun classpath(params: Any) {
        classpath = project.files(params)
    }

    @get:Input
    internal lateinit var container: NamedDomainObjectContainer<GenerateSpec>

    /**
     * This performs the actual action of the task.
     */
    @TaskAction
    fun generate() {
        URLClassLoader(combinedClasspath(null)).use { joinedLoader ->
            container.all { spec: GenerateSpec ->
                val specClasspath = spec.classpath
                if (specClasspath == null || specClasspath.isEmpty) {
                    generateFile(spec, joinedLoader)
                } else {
                    URLClassLoader(combinedClasspath(spec.classpath)).use { classLoader ->
                        generateFile(spec, classLoader)
                    }
                }
            }

            if (dirGenerator.generator != null) {
                val outDir =
                    if (dirGenerator.outputDir == null) project.file(outputDir) else resolveFile(dirGenerator.outputDir!!)
                if (dirGenerator.classpath != null) {
                    URLClassLoader(combinedClasspath(dirGenerator.classpath)).use {
                        generateDir(outDir, it)
                    }
                } else {
                    generateDir(outDir, joinedLoader)
                }
            }

        }
    }

    private fun generateDir(outDir: File, classLoader: ClassLoader) {
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) throw InvalidUserDataException("The output directory $outDir could not be created")
        } // ensure the output directory exists
        if (!outDir.canWrite()) throw InvalidUserDataException("The output directory $outDir is not writeable")

        val generatorClass = classLoader.loadClass(dirGenerator.generator)

        val baseError = """
              Directory generators must have a unique public method "doGenerate(File, [Object])"
              where the second parameter is optional iff the input is null. If not a static
              method, the class must have a noArg constructor. """.trimIndent()

        generatorClass.execute(outDir, dirGenerator.input, baseError)
    }

    private fun resolveFile(fileName: String): File {
        return File(fileName).let {
            if (it.isAbsolute) it
            else File(project.file(outputDir), fileName)
        }
    }

    private fun generateFile(spec: GenerateSpec, classLoader: ClassLoader) {
        if (spec.output != null) {
            val outFile = resolveFile(spec.output!!)

            if (spec.generator != null) {
                val gname = spec.generator
                val generatorClass = classLoader.loadClass(gname)
                if (outFile.isDirectory) throw InvalidUserDataException("The output can not be a directory, it must be a file ($outFile)")
                if (!outFile.exists()) {
                    outFile.parentFile.apply { if (!exists()) mkdirs() || throw InvalidUserDataException("The target directory for the output file $outFile could not be created") }
                    outFile.createNewFile()
                }
                if (!outFile.canWrite()) throw InvalidUserDataException("The output file ($outFile) is not writeable.")

                if (project.logger.isInfoEnabled) {
                    project.logger.info("Generating ${spec.name} as '${spec.output}' as '$outFile'")
                } else {
                    project.logger.lifecycle("Generating ${spec.name} as '${spec.output}'")
                }

                val baseError = """
              Generators must have a unique public method "doGenerate(Writer|Appendable, [Object])"
              where the second parameter is optional iff the input is null. If not a static
              method, the class must have a noArg constructor.""".trimIndent()

                generatorClass.execute({ outFile.writer() }, spec.input, baseError)

            } else {
                throw InvalidUserDataException("Missing output code for generateSpec ${spec.name}, no generator provided")
            }
        }
    }

    private fun Class<*>.getGeneratorMethods(firstParamWriter: Boolean, input: Any?): List<Method> {
        return methods.asSequence()
            .filter { it.name == "doGenerate" }
            .filter { Modifier.isPublic(it.modifiers) }
            .filter { if (input == null) it.parameterCountCompat in 1..2 else it.parameterCountCompat == 2 }
            .filter {
                if (firstParamWriter) {
                    Appendable::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[0].isAssignableFrom(
                        Writer::class.java)
                } else {
                    File::class.java == it.parameterTypes[0]
                }
            }.toList()
    }

    private fun Class<out Any>.execute(firstParam: Any, input: Any?, baseErrorMsg: String) {
        getGeneratorMethods(firstParam !is File, input).let { candidates ->
            try {
                var resolvedInput = input
                var methodIterator = candidates
                    .asSequence()
                    .filter { if (it.parameterCountCompat == 1) true else isSecondParameterCompatible(input, it) }
                    .iterator()

                if (input is Callable<*> && !methodIterator.hasNext()) {
                    resolvedInput = input.call()
                    methodIterator = candidates.asSequence()
                        .filter { isSecondParameterCompatible(resolvedInput, it) }.iterator()
                }

                if (!methodIterator.hasNext()) throw InvalidUserDataException(errorMsg("No candidate method found",
                                                                                       candidates,
                                                                                       baseErrorMsg,
                                                                                       input))

                val m = methodIterator.next()

                if (methodIterator.hasNext()) {
                    throw InvalidUserCodeException(ambiguousChoice(candidates, baseErrorMsg, input))
                }
                m.doInvoke(this, firstParam, resolvedInput)
                return
            } catch (e: Exception) {
                throw InvalidUserDataException("Could not execute the generator code", e)
            }
        }
    }

    private fun ambiguousChoice(candidates: Iterable<Method>, baseErrorMsg: String, input: Any?): String {
        return errorMsg("More than 1 valid candidate found.", candidates, baseErrorMsg, input)
    }

    private fun errorMsg(error: String, candidates: Iterable<Method>, baseErrorMsg: String, input: Any?): String {
        return buildString {
            appendLine(error)
            appendLine(baseErrorMsg).appendLine()
            appendLine("Candidates were: (with input = ${input?.javaClass?.name ?: "null"}):").append("    ")
            candidates.joinTo(this, "\n    ") { candidates.toString() }
        }
    }

    private fun isSecondParameterCompatible(input: Any?, method: Method): Boolean {
        if (input == null) {
            return (method.parameterAnnotations[1].none { annotation -> annotation is NotNull })
        } else {
            return method.parameterTypes[1].isInstance(input)
        }
    }

    private fun combinedClasspath(others: FileCollection?): Array<out URL> {

        fun Iterable<File>.toUrls(): Sequence<URL> = asSequence().map { it.toURI().toURL() }

        return mutableListOf<URL>().apply {
            classpath?.let { it.toUrls().forEach { add(it) } }
            others?.let { it.toUrls().forEach { add(it) } }
        }.toTypedArray().apply { project.logger.debug("Classpath for generator: ${Arrays.toString(this)}") }
    }

}