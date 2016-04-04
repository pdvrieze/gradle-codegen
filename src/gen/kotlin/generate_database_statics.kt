/*
 * Copyright (c) 2016.
 *
 * This file is part of ProcessManager.
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

package gen.statics

const val count=10

/*
    @JvmStatic
  fun <T1:Any, S1:ColumnType<T1,S1,C1>, C1: Column<T1, S1,C1>,
       T2:Any, S2:ColumnType<T2,S2,C2>, C2: Column<T2, S2,C2>
        > SELECT(col1: C1, col2:C2)= Database._Select2(col1, col2)

 */

fun main(args:Array<String>) {
  for(n in 1..count) {
    println("    @JvmStatic")
    print("    fun <")
    (1..n).joinToString(",\n         ") { m -> "T$m:Any, S$m:ColumnType<T$m,S$m,C$m>, C$m: Column<T$m, S$m, C$m>" }.apply { print(this) }
    print("> SELECT(")
    (1..n).joinToString{ m -> "col$m: C$m" }.apply {print(this)}
    println(")=")
    print("        Database._Select$n(")
    (1..n).joinToString{ m -> "col$m" }.apply {print(this)}
    println(")")
    println()
  }
}