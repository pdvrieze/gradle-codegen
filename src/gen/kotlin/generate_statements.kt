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

package gen.statements

const val count=10

/*
  class _Statement1<T:Any, S:IColumnType<T,S,C>, C:Column<T,S,C>>(override val select:_Select1<T,S,C>, where:WhereClause):_StatementBase(where)
 */

fun main(args:Array<String>) {
  for(n in 1..count) {
    print("  class _Statement$n<")
    (1..n).joinToString(",\n                    ") { m -> "T$m:Any, S$m:IColumnType<T$m,S$m,C$m>, C$m: Column<T$m, S$m, C$m>" }.apply { print(this) }
    print(">(override val select:_Select$n<")
    (1..n).joinToString(","){ m -> "T$m,S$m,C$m" }.apply {print(this)}
    println(">, where:WhereClause):_StatementBase(where)")
    println()
  }
}