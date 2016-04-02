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

import uk.ac.bournemouth.kotlinsql.ColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.NumericColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.SimpleColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.CharColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthCharColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.DecimalColumnType.*

/**
 * Created by pdvrieze on 01/04/16.
 */
sealed abstract class AbstractColumnConfiguration<T:Any, S: BaseColumnType<T, S>,out C: Column<T, S>, out CONF_T>(val table: TableRef, val name: String, val type: S) {

  enum class ColumnFormat { FIXED, MEMORY, DEFAULT }
  enum class StorageFormat { DISK, MEMORY, DEFAULT }

  var notnull: Boolean? = null
  var unique: Boolean = false
  var autoincrement: Boolean = false
  var default: T? = null
  var comment:String? = null
  var columnFormat: ColumnFormat? = null
  var storageFormat: StorageFormat? = null
  var references: ColsetRef? = null

  val NULL:Unit get() { notnull=false }
  val NOT_NULL:Unit get() { notnull = true }
  val AUTO_INCREMENT:Unit get() { autoincrement = true }
  val UNIQUE:Unit get() { unique = true }

  inline fun DEFAULT(value:T) { default=value }
  inline fun COMMENT(comment:String) { this.comment = comment }
  inline fun COLUMN_FORMAT(format: ColumnFormat) { columnFormat = format }
  inline fun STORAGE(format: StorageFormat) { storageFormat = format }
  inline fun REFERENCES(table: TableRef, col1: ColumnRef<*, *>, vararg columns: ColumnRef<*, *>) {
    references= ColsetRef(table, col1, *columns)
  }

  abstract fun newColumn():C

  final class NormalColumnConfiguration<T:Any, S: SimpleColumnType<T, S>>(table: TableRef, name: String, type: S): AbstractColumnConfiguration<T, S, SimpleColumn<T, S>, NormalColumnConfiguration<T,S>>(table, name, type) {
    override fun newColumn(): SimpleColumn<T, S> = NormalColumnImpl(name, this)
  }

  sealed abstract class AbstractNumberColumnConfiguration<T:Any, S: INumericColumnType<T, S,C>, C: INumericColumn<T, S, C>, CONF_T>(table: TableRef, name: String, type: S): AbstractColumnConfiguration<T, S, C, CONF_T>(table, name, type) {
    var unsigned: Boolean = false
    var zerofill: Boolean = false
    var displayLength: Int = -1

    val UNSIGNED:Unit get() { unsigned = true }

    val ZEROFILL:Unit get() { unsigned = true }

    final class NumberColumnConfiguration<T:Any, S: NumericColumnType<T, S>>(table: TableRef, name: String, type: S): AbstractNumberColumnConfiguration<T, S, NumericColumn<T, S>, NumberColumnConfiguration<T,S>>(table, name, type) {
      override fun newColumn(): NumericColumn<T, S> = NumberColumnImpl(name, this)

    }

    final class DecimalColumnConfiguration<T:Any, S: DecimalColumnType<T, S>>(table: TableRef, name: String, type: S, val precision: Int, val scale: Int): AbstractNumberColumnConfiguration<T, S, DecimalColumn<T, S>, DecimalColumnConfiguration<T,S>>(table, name, type) {
      val defaultPrecision=10
      val defaultScale=0

      override fun newColumn(): DecimalColumn<T, S> = DecimalColumnImpl(name, this)
    }

  }

  sealed abstract class AbstractCharColumnConfiguration<T:Any, S: ICharColumnType<T, S, C>, C: ICharColumn<T, S, C>, CONF_T>(table: TableRef, name: String, type: S): AbstractColumnConfiguration<T, S, C, CONF_T>(table, name, type) {
    var charset: String? = null
    var collation: String? = null
    var binary:Boolean = false

    val BINARY:Unit get() { binary = true }

    inline fun CHARACTER_SET(charset:String) { this.charset = charset }
    inline fun COLLATE(collation:String) { this.collation = collation }

    final class CharColumnConfiguration<T:Any, S: CharColumnType<T, S>>(table: TableRef, name: String, type: S): AbstractCharColumnConfiguration<T, S, CharColumn<T, S>, CharColumnConfiguration<T,S>>(table, name, type) {
      override fun newColumn(): CharColumn<T, S> = CharColumnImpl<T,S>(name, this)
    }

    final class LengthCharColumnConfiguration<T:Any, S: LengthCharColumnType<T, S>>(table: TableRef, name: String, type: S, override val length: Int): AbstractCharColumnConfiguration<T, S, LengthCharColumn<T, S>,LengthCharColumnConfiguration<T,S>>(table, name, type), BaseLengthColumnConfiguration<T, S, LengthCharColumn<T, S>> {
      override fun newColumn(): LengthCharColumn<T, S> = LengthCharColumnImpl(name, this)
    }
  }

  interface BaseLengthColumnConfiguration<T:Any, S: ILengthColumnType<T,S, C>, C:ILengthColumn<T,S,C>> {
    val length:Int
  }

  final class LengthColumnConfiguration<T:Any, S: LengthColumnType<T, S>>(table: TableRef, name: String, type: S, override val length: Int): AbstractColumnConfiguration<T, S, LengthColumn<T, S>, LengthColumnConfiguration<T,S>>(table, name, type), BaseLengthColumnConfiguration<T, S, LengthColumn<T, S>> {
    override fun newColumn(): LengthColumn<T, S> = LengthColumnImpl(name, this)
  }
}

