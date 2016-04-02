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

import kotlin.reflect.KProperty
import uk.ac.bournemouth.kotlinsql.ColumnType.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractNumberColumnConfiguration.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractCharColumnConfiguration.*



internal val LINE_SEPARATOR: String by lazy { System.getProperty("line.separator")!! }

/**
 * Implementation for the database API
 */

internal abstract class ColumnImpl<T:Any, S: BaseColumnType<T, S>, C:Column<T,S>> internal constructor (
      override val table: TableRef,
      override val type: S,
      override val name: String,
      override val notnull: Boolean?,
      override val unique: Boolean,
      override val autoincrement: Boolean,
      override val default: T?,
      override val comment: String?,
      override val columnFormat: AbstractColumnConfiguration.ColumnFormat?,
      override val storageFormat: AbstractColumnConfiguration.StorageFormat?,
      override val references: ColsetRef?,
      val unsigned:Boolean = false,
      val zerofill: Boolean = false,
      val displayLength: Int = -1,
      val precision: Int = -1,
      val scale:Int = -1,
      val charset:String? = null,
      val collation:String? = null,
      val binary:Boolean = false,
      val length:Int = -1
):Column<T,S> {


  @Suppress("UNCHECKED_CAST")
  override fun ref():C {
    return this as C
  }

  override fun toDDL(): CharSequence {
    val result = StringBuilder()
    result.apply {
      append('`').append(name).append("` ").append(type.typeName)
      if (this@ColumnImpl.length>0) append('(').append(this@ColumnImpl.length).append(')')
      else if (displayLength>0) append('(').append(displayLength).append(')')
      if (unsigned) append(" UNSIGNED")
      if (zerofill) append(" ZEROFILL")
      if (binary) append(" BINARY")
      charset?.let { append(" CHARACTER SET ").append(it)}
      collation?.let { append(" COLLATE ").append(it)}
      notnull?.let { append(if(it) " NOT NULL" else "NULL") }
      default?.let { append(" DEFAULT ")
        if (it is CharSequence)
          append('\'').append(it).append('\'')
        else append(it)
      }
      if (autoincrement) append(" AUTO_INCREMENT")
      if (unique) append(" UNIQUE")
      comment?.let { append(" '").append(comment).append('\'') }
      columnFormat?.let { append(" COLUMN_FORMAT ").append(it.name)}
      storageFormat?.let { append(" STORAGE ").append(it.name)}
      references?.let { append(" REFERENCES ").append(toDDL(it.table._name, it.columns)) }

    }

    return result
  }
}

internal class NormalColumnImpl<T:Any, S: SimpleColumnType<T, S>>(name:String, configuration: NormalColumnConfiguration<T, S>):
      ColumnImpl<T, S, SimpleColumn<T, S>> (table = configuration.table,
                                            type = configuration.type,
                                            name = name,
                                            notnull = configuration.notnull,
                                            unique = configuration.unique,
                                            autoincrement = configuration.autoincrement,
                                            default = configuration.default,
                                            comment = configuration.comment,
                                            columnFormat = configuration.columnFormat,
                                            storageFormat = configuration.storageFormat,
                                            references = configuration.references), SimpleColumn<T, S> {
  override fun copyConfiguration(newName:String?, owner: Table) = NormalColumnConfiguration(owner, newName ?: name, type)
}

internal class LengthColumnImpl<T:Any, S: LengthColumnType<T, S>>(name:String, configuration: LengthColumnConfiguration<T, S>):
      ColumnImpl<T,S, LengthColumn<T,S>>(table=configuration.table,
                                         type=configuration.type,
                                         name=name,
                                         notnull=configuration.notnull,
                                         unique=configuration.unique,
                                         autoincrement=configuration.autoincrement,
                                         default=configuration.default,
                                         comment=configuration.comment,
                                         columnFormat=configuration.columnFormat,
                                         storageFormat=configuration.storageFormat,
                                         references=configuration.references,
                                         length = configuration.length), LengthColumn<T,S> {
  override fun copyConfiguration(newName:String?, owner: Table) = LengthColumnConfiguration(owner, newName ?: name, type, length)
}

internal class NumberColumnImpl<T:Any, S: NumericColumnType<T, S>>(name:String, configuration: NumberColumnConfiguration<T, S>):
      ColumnImpl<T, S, NumericColumn<T, S>> (table = configuration.table,
                                             type = configuration.type,
                                             name = name,
                                             notnull = configuration.notnull,
                                             unique = configuration.unique,
                                             autoincrement = configuration.autoincrement,
                                             default = configuration.default,
                                             comment = configuration.comment,
                                             columnFormat = configuration.columnFormat,
                                             storageFormat = configuration.storageFormat,
                                             references = configuration.references,
                                             unsigned = configuration.unsigned,
                                             zerofill = configuration.zerofill,
                                             displayLength = configuration.displayLength), NumericColumn<T, S> {
  override fun copyConfiguration(newName:String?, owner: Table) = NumberColumnConfiguration(owner, newName ?: name, type)
}


internal class CharColumnImpl<T:Any, S: CharColumnType<T, S>>(name:String, configuration: CharColumnConfiguration<T, S>):
      ColumnImpl<T, S, CharColumn<T, S>> (table = configuration.table,
                                          type = configuration.type,
                                          name = name,
                                          notnull = configuration.notnull,
                                          unique = configuration.unique,
                                          autoincrement = configuration.autoincrement,
                                          default = configuration.default,
                                          comment = configuration.comment,
                                          columnFormat = configuration.columnFormat,
                                          storageFormat = configuration.storageFormat,
                                          references = configuration.references,
                                          binary = configuration.binary,
                                          charset = configuration.charset,
                                          collation = configuration.collation), CharColumn<T, S> {
  override fun copyConfiguration(newName:String?, owner: Table) = CharColumnConfiguration(owner, newName ?: name, type)
}


internal class LengthCharColumnImpl<T:Any, S: LengthCharColumnType<T, S>>(name:String, configuration: LengthCharColumnConfiguration<T, S>):
      ColumnImpl<T, S, LengthCharColumn<T, S>> (table = configuration.table,
                                                type = configuration.type,
                                                name = name,
                                                notnull = configuration.notnull,
                                                unique = configuration.unique,
                                                autoincrement = configuration.autoincrement,
                                                default = configuration.default,
                                                comment = configuration.comment,
                                                columnFormat = configuration.columnFormat,
                                                storageFormat = configuration.storageFormat,
                                                references = configuration.references,
                                                length = configuration.length,
                                                binary = configuration.binary,
                                                charset = configuration.charset,
                                                collation = configuration.collation), LengthCharColumn<T, S> {
  override fun copyConfiguration(newName:String?, owner: Table) = LengthCharColumnConfiguration(owner, newName ?: name, type, length)
}


internal class DecimalColumnImpl<T:Any, S: DecimalColumnType<T, S>>(name:String, configuration: DecimalColumnConfiguration<T, S>):
      ColumnImpl<T, S, DecimalColumn<T, S>> (table = configuration.table,
                                             type = configuration.type,
                                             name = configuration.name,
                                             notnull = configuration.notnull,
                                             unique = configuration.unique,
                                             autoincrement = configuration.autoincrement,
                                             default = configuration.default,
                                             comment = configuration.comment,
                                             columnFormat = configuration.columnFormat,
                                             storageFormat = configuration.storageFormat,
                                             references = configuration.references,
                                             unsigned = configuration.unsigned,
                                             zerofill = configuration.zerofill,
                                             displayLength = configuration.displayLength,
                                             precision = configuration.precision,
                                             scale = configuration.scale), DecimalColumn<T,S> {
  override fun copyConfiguration(newName:String?, owner: Table) = DecimalColumnConfiguration(owner, newName ?: name, type, precision, scale)
}



class TableRefImpl(override val _name: String) : TableRef {}


abstract class AbstractTable: Table {

  companion object {

    fun List<Column<*, *>>.resolve(ref: ColumnRef<*,*>) = find { it.name == ref.name } ?: throw java.util.NoSuchElementException(
          "No column with the name ${ref.name} could be found")

    fun List<Column<*, *>>.resolve(refs: List<ColumnRef<*,*>>) = refs.map { resolve(it) }

    fun List<Column<*, *>>.resolve(refs: Array<out ColumnRef<*,*>>) = refs.map { resolve(it) }

  }

  override fun resolve(ref: ColumnRef<*,*>) : Column<*, *> = (_cols.find {it.name==ref.name}) !!

  override fun ref(): TableRef = TableRefImpl(_name)

  override fun column(name:String) = _cols.firstOrNull {it.name==name}

  operator fun <T:Any, S: BaseColumnType<T, S>> getValue(thisRef: ImmutableTable, property: KProperty<*>): Column<T, S> {
    return column(property.name) as Column<T, S>
  }

  open protected class TypeFieldAccessor<T:Any, S: ColumnType<T, S, C>, C:Column<T,S>>(val type: BaseColumnType<T, S>): Table.FieldAccessor<T, S, C> {
    private var value: C? = null
    open fun name(property: kotlin.reflect.KProperty<*>) = property.name
    override operator fun getValue(thisRef: Table, property: kotlin.reflect.KProperty<*>): C {
      if (value==null) {
        val field = thisRef.column(property.name) ?: throw IllegalArgumentException("There is no field with the given name ${property.name}")
        value = type.cast(field) as C
      }
      return value!!
    }
  }

  /** Property delegator to access database columns by name and type. */
  protected fun <T:Any, S: ColumnType<T, S, C>, C: Column<T,S>> name(name:String, type: BaseColumnType<T, S>) = NamedFieldAccessor<T,S,C>(
        name,
        type)

  final protected class NamedFieldAccessor<T:Any, S: ColumnType<T, S, C>, C:Column<T,S>>(val name:String, type: BaseColumnType<T, S>): TypeFieldAccessor<T, S, C>(type) {
    override fun name(property: kotlin.reflect.KProperty<*>): String = this.name
  }

  override fun appendDDL(appendable: Appendable) {
    appendable.appendln("CREATE TABLE `${_name}` (")
    sequenceOf(_cols.asSequence().map {it.toDDL()},
               _primaryKey?.let {sequenceOf( toDDL("PRIMARY KEY", it))},
               _indices.asSequence().map { toDDL("INDEX", it)},
               _uniqueKeys.asSequence().map { toDDL("UNIQUE", it) },
               _foreignKeys.asSequence().map { it.toDDL() })
          .filterNotNull()
          .flatten()
          .joinTo(appendable, ",${LINE_SEPARATOR}  ", "  ")
    appendable.appendln().append(')')
    _extra?.let { appendable.append(' ').append(_extra)}
    appendable.append(';')
  }
}


internal fun toDDL(first:CharSequence, cols: List<ColumnRef<*,*>>):CharSequence {
  return StringBuilder(first).append(" (`").apply { cols.joinTo(this, "`, `", transform = {it.name}) }.append("`)")
}
