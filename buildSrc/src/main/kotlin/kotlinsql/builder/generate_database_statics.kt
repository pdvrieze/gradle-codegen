/*
 * Copyright (c) 2016.
 *
 * This file is part of kotlinsql.
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

/**
 * Created by pdvrieze on 04/04/16.
 */

package kotlinsql.builder

import org.gradle.api.file.FileCollection
import java.io.Writer

class GenerateDatabaseBaseKt(val count:Int): GenerateImpl {
  override fun doGenerate(output: Writer, input: FileCollection?) {
    output.apply {

      appendCopyright()
      appendln()
      appendln("package uk.ac.bournemouth.util.kotlin.sql.impl.gen")
      appendln()
      appendln("import uk.ac.bournemouth.kotlinsql.Column")
      appendln("import uk.ac.bournemouth.kotlinsql.ColumnType")
      appendln("import uk.ac.bournemouth.kotlinsql.Database")
      appendln()
      appendln("open class DatabaseMethods {")
      appendln("  companion object {")

      for(n in 1..count) {
        appendln("    @JvmStatic")
        append("    fun <")

        run {
          val indent = " ".repeat(if (n<9) 9 else 10)
          (1..n).joinToString(",\n${indent}") { m -> "T$m:Any, S$m:ColumnType<T$m,S$m,C$m>, C$m: Column<T$m, S$m, C$m>" }.apply { append(this) }
        }
        append("> SELECT(")
        (1..n).joinToString{ m -> "col$m: C$m" }.apply {append(this)}
        appendln(")=")
        if (n==1) {
          append("        Database._Select$n(")
        } else {
          append("        _Select$n(")
        }
        (1..n).joinToString{ m -> "col$m" }.apply {append(this)}
        appendln(")")
        appendln()
      }
      appendln("  }")
      appendln("}")
    }
  }
}

fun main(args:Array<String>) {
}