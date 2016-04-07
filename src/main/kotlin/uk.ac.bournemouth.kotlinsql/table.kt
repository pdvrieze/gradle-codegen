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

package uk.ac.bournemouth.kotlinsql

import uk.ac.bournemouth.util.kotlin.sql.impl.gen.DatabaseMethods
import java.util.*

/**
 * Created by pdvrieze on 03/04/16.
 */

/**
 * A interface for tables. The properties have underscored names to reduce conflicts with members.
 *
 * @property _cols The list of columns in the table.
 * @property _primaryKey The primary key of the table (if defined)
 * @property _foreignKeys The foreign keys defined on the table.
 * @property _uniqueKeys Unique keys / uniqueness constraints defined on the table
 * @property _indices Additional indices defined on the table
 * @property _extra Extra table configuration to be appended after the definition. This contains information such as the
 *                  engine or charset to use.
 */
interface Table:TableRef {
  val _cols: List<Column<*, *, *>>
  val _primaryKey: List<Column<*, *, *>>?
  val _foreignKeys: List<ForeignKey>
  val _uniqueKeys: List<List<Column<*, *, *>>>
  val _indices: List<List<Column<*, *, *>>>
  val _extra: String?

  fun column(name:String): Column<*,*,*>?
  fun ref(): TableRef
  fun resolve(ref: ColumnRef<*,*,*>): Column<*,*,*>

  interface FieldAccessor<T:Any, S: ColumnType<T,S,C>, C:Column<T,S,C>> {
    operator fun getValue(thisRef: Table, property: kotlin.reflect.KProperty<*>): C
  }

  fun appendDDL(appendable: Appendable)

}

operator fun Table.get(name:String) = this.column(name) ?: throw NoSuchElementException("The column with the name ${name} does not exist")
