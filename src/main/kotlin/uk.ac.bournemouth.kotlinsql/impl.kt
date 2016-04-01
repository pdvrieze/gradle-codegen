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

import java.util.*
import kotlin.reflect.KProperty

/**
 * Implementation for the database API
 */

open class ColumnImpl<T:Any>(override val table:TableRef,
                             override val type: ColumnType<T>,
                             override val name: String,
                             override val notnull: Boolean?,
                             override val unique: Boolean,
                             override val autoincrement: Boolean,
                             override val default: T?,
                             override val comment:String?,
                             override val columnFormat: ColumnConfiguration.ColumnFormat?,
                             override val storageFormat: ColumnConfiguration.StorageFormat?,
                             override val references:ColsetRef?): Column<T> {
  constructor(configuration: ColumnConfiguration<T>):
      this(table=configuration.table,
           type=configuration.type,
           name=configuration.name,
           notnull=configuration.notnull,
           unique=configuration.unique,
           autoincrement=configuration.autoincrement,
           default=configuration.default,
           comment=configuration.comment,
           columnFormat=configuration.columnFormat,
           storageFormat=configuration.storageFormat,
           references=configuration.references)
  
  override fun ref():ColumnRef<T> = this
}

private fun List<Column<*>>.resolve(ref: ColumnRef<*>) = find { it.name == ref.name } ?: throw NoSuchElementException("No column with the name ${ref.name} could be found")
private fun List<Column<*>>.resolve(refs: List<ColumnRef<*>>) = refs.map { resolve(it) }

/**
 * A base class for table declarations. Users of the code are expected to use this with a configuration closure to create
 * database tables. for typed columns to be available they need to be declared using `by [type]` or `by [name]`.
 *
 * A sample use is:
 * ```
 *    object peopleTable:Table("people", "ENGINE=InnoDB CHARSET=utf8", {
 *      val firstname = [VARCHAR]("firstname", 50)
 *      val familyname = [VARCHAR]("familyname", 30) { NOT_NULL }
 *      [DATE]("birthdate")
 *      [PRIMARY_KEY](firstname, familyname)
 *    }) {
 *      val firstname by [type]([ColumnType.VARCHAR_T])
 *      val surname by [name]("familyname", [ColumnType.VARCHAR_T])
 *      val birthdate by [type]([ColumnType.DATE_T])
 *    }
 * ```
 *
 * Note that the definition of typed fields through delegates is mainly to aid programmatic access. Values within the
 * configurator body are only visible there, and mainly serve to make definition of keys easy. Values that are not used
 * (such as birthdate) do not need to be stored. Their mere declaration adds them to the table configuration.
 *
 * @property _cols The list of columns in the table.
 * @property _primaryKey The primary key of the table (if defined)
 * @property _foreignKeys The foreign keys defined on the table.
 * @property _uniqueKeys Unique keys / uniqueness constraints defined on the table
 * @property _indices Additional indices defined on the table
 * @property _extra Extra table configuration to be appended after the definition. This contains information such as the
 *                  engine or charset to use.
 */
abstract class Table private constructor(override val _name: String,
                                         val _cols: List<Column<*>>,
                                         val _primaryKey: List<Column<*>>?,
                                         val _foreignKeys: List<ForeignKey>,
                                         val _uniqueKeys: List<List<Column<*>>>,
                                         val _indices: List<List<Column<*>>>,
                                         val _extra: String?) : TableRef {

  private constructor(c:TableConfiguration):this(c._name, c.cols, c.primaryKey?.let {c.cols.resolve(it)}, c.foreignKeys, c.uniqueKeys.map({c.cols.resolve(it)}), c.indices.map({c.cols.resolve(it)}), c.extra)

  /**
   * The main use of this class is through inheriting this constructor.
   */
  constructor(name:String, extra: String? = null, block:TableConfiguration.()->Unit): this(TableConfiguration(name, extra).apply(block)  )

  fun resolve(ref:ColumnRef<*>) : Column<*> = (_cols.find {it.name==ref.name}) !!

  fun ref(): TableRef = TableRefImpl(_name)

  fun field(name:String) = _cols.firstOrNull {it.name==name}

  operator fun <T:Any> get(thisRef: Table, property:KProperty<out Column<T>>):Column<T> {
    return field(property.name) as Column<T>
  }

  protected fun <T:Any> type(type: ColumnType<T>) = FieldAccessor<T>(type)

  open protected class FieldAccessor<T:Any>(val type: ColumnType<T>) {
    lateinit var value:Column<T>
    open fun name(property:KProperty<*>) = property.name
    operator fun getValue(thisRef: Table, property:KProperty<*>):Column<T> {
      if (value==null) {
        value = type.cast(thisRef.field(property.name)?: throw IllegalArgumentException("There is no field with the given name ${property.name}"))
      }
      return value
    }
  }

  protected fun <T:Any> name(name:String, type: ColumnType<T>) = NamedFieldAccessor<T>(name, type)

  final protected class NamedFieldAccessor<T:Any>(val name:String, type: ColumnType<T>): FieldAccessor<T>(type) {
    override fun name(property: KProperty<*>): String = this.name
  }

}

class TableRefImpl(override val _name: String) : TableRef {}




