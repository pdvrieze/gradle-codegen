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

import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Created by pdvrieze on 31/03/16.
 */

abstract class Database private constructor(val version:Int, val tables:List<out Table>) {
  constructor(version:Int, block:DatabaseConfiguration.()->Unit):this(version, DatabaseConfiguration().apply(block))

  private constructor(version:Int, config:DatabaseConfiguration): this(version, config.tables)


  protected inline fun <T:Table> ref(table: T)= TableDelegate<T>(table)


  protected class TableDelegate<T:Table>(private val table: T) {
    var needsCheck=true
    operator fun getValue(thisRef: Database, property: KProperty<*>): T {
      if (needsCheck) {
        if (table.name != property.name) throw IllegalArgumentException("The table names do not match (${table.name}, ${property.name})")
        needsCheck = false
      }
      return table
    }
  }


}

interface TableRef {
  val name:String
}

interface ColumnRef<T:Any> {
  val table: TableRef
  val name:String
  val type: ColumnType<T>
}

class ColsetRef(val table:TableRef, val columns:List<out ColumnRef<*>>) {
  constructor(table:TableRef, col1: ColumnRef<*>, vararg cols:ColumnRef<*>): this(table, mutableListOf(col1).apply { addAll(cols) })
}

interface Column<T:Any>: ColumnRef<T> {
  fun ref(): ColumnRef<T>
}

interface BoundedType {
  val maxLen:Int
}

sealed class ColumnType<T:Any>(val typeName:String, type: KClass<T>) {

  @Suppress("UNCHECKED")
  fun cast(column: Column<*>): Column<T> {
    if (column.type.typeName == typeName) {
      return column as Column<T>
    } else {
      throw TypeCastException("The given column is not of the correct type")
    }
  }

  object BIT_T       : ColumnType<Boolean>("BIT", Boolean::class), BoundedType { override val maxLen = 64 }
  object BITFIELD_T  : ColumnType<Array<Boolean>>("BIT", Array<Boolean>::class)
  object TINYINT_T   : ColumnType<Byte>("BIGINT", Byte::class)
  object SMALLINT_T  : ColumnType<Short>("SMALLINT", Short::class)
  object MEDIUMINT_T : ColumnType<Int>("MEDIUMINT", Int::class)
  object INT_T       : ColumnType<Int>("INT", Int::class)
  object BIGINT_T    : ColumnType<Long>("BIGINT", Long::class)

  object FLOAT_T     : ColumnType<Float>("FLOAT", Float::class)
  object DOUBLE_T    : ColumnType<Double>("DOUBLE", Double::class)

  object DECIMAL_T   : ColumnType<BigDecimal>("DECIMAL", BigDecimal::class)
  object NUMERIC_T   : ColumnType<BigDecimal>("NUMERIC", BigDecimal::class)

  object DATE_T      : ColumnType<java.sql.Date>("DATE", java.sql.Date::class)
  object TIME_T      : ColumnType<java.sql.Time>("TIME", java.sql.Time::class)
  object TIMESTAMP_T : ColumnType<java.sql.Timestamp>("TIMESTAMP", java.sql.Timestamp::class)
  object DATETIME_T  : ColumnType<java.sql.Timestamp>("TIMESTAMP", java.sql.Timestamp::class)
  object YEAR_T      : ColumnType<java.sql.Date>("YEAR", java.sql.Date::class)

  object CHAR_T      : ColumnType<String>("CHAR", String::class), BoundedType { override val maxLen = 255 }
  object VARCHAR_T   : ColumnType<String>("VARCHAR", String::class), BoundedType { override val maxLen = 0xffff }

  object BINARY_T    : ColumnType<ByteArray>("BINARY", ByteArray::class), BoundedType { override val maxLen = 255 }
  object VARBINARY_T : ColumnType<ByteArray>("VARBINARY", ByteArray::class), BoundedType { override val maxLen = 0xffff }
  object TINYBLOB_T  : ColumnType<ByteArray>("TINYBLOB", ByteArray::class), BoundedType { override val maxLen = 255 }
  object BLOB_T      : ColumnType<ByteArray>("BLOB", ByteArray::class), BoundedType { override val maxLen = 0xffff }
  object MEDIUMBLOB_T: ColumnType<ByteArray>("MEDIUMBLOB", ByteArray::class), BoundedType { override val maxLen = 0xffffff }
  object LONGBLOB_T  : ColumnType<ByteArray>("LONGBLOB", ByteArray::class), BoundedType { override val maxLen = Int.MAX_VALUE /*Actually it would be more*/}

  object TINYTEXT_T  : ColumnType<String>("TINYTEXT", String::class), BoundedType { override val maxLen = 255 }
  object TEXT_T      : ColumnType<String>("TEXT", String::class), BoundedType { override val maxLen = 0xffff }
  object MEDIUMTEXT_T: ColumnType<String>("MEDIUMTEXT", String::class), BoundedType { override val maxLen = 0xffffff }
  object LONGTEXT_T  : ColumnType<String>("LONGTEXT", String::class), BoundedType { override val maxLen = Int.MAX_VALUE /*Actually it would be more*/}

  /*
  ENUM(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
  SET(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
   */

}

open class ColumnConfiguration<T:Any>(val table: TableRef, val name: String, val type: ColumnType<T>) {

  enum class ColumnFormat { FIXED, MEMORY, DEFAULT }
  enum class StorageFormat { DISK, MEMORY, DEFAULT }

  var notnull: Boolean? = null
  var unique: Boolean = false
  var autoincrement: Boolean = false
  var default: T? = null
  var comment:String? = null
  var columnFormat: ColumnFormat? = null
  var storageFormat: StorageFormat? = null
  var references:ColsetRef? = null

  val NULL:Unit get() { notnull=false }
  val NOT_NULL:Unit get() { notnull = true }
  val AUTO_INCREMENT:Unit get() { autoincrement = true }
  val UNIQUE:Unit get() { unique = true }

  inline fun DEFAULT(value:T) { default=value }
  inline fun COMMENT(comment:String) { this.comment = comment }
  inline fun COLUMN_FORMAT(format:ColumnFormat) { columnFormat = format }
  inline fun STORAGE(format:StorageFormat) { storageFormat = format }
  inline fun REFERENCES(table:TableRef, col1:ColumnRef<*>, vararg columns: ColumnRef<*>) { references=ColsetRef(table, col1, *columns) }

}

open class NumberColumnConfiguration<T:Any>(table: TableRef, name: String, type: ColumnType<T>): ColumnConfiguration<T>(table, name, type) {
  var unsigned: Boolean = false
  var zerofill: Boolean = false
  var displayLength: Int = -1

  val UNSIGNED:Unit get() { unsigned = true }

  val ZEROFILL:Unit get() { unsigned = true }

}

open class CharColumnConfiguration<T:Any>(table: TableRef, name: String, type: ColumnType<T>): ColumnConfiguration<T>(table, name, type) {
  var charset: String? = null
  var collation: String? = null
  var binary:Boolean = false

  val BINARY:Unit get() { binary = true }

  inline fun CHARACTER_SET(charset:String) { this.charset = charset }
  inline fun COLLATE(collation:String) { this.collation = collation }
}

final class LengthCharColumnConfiguration<T:Any>(table: TableRef, name: String, type: ColumnType<T>, override val length: Int): CharColumnConfiguration<T>(table, name, type), LengthColumnConfiguration<T>

final class DecimalColumnConfiguration<T:Any>(table: TableRef, name: String, type: ColumnType<T>, val precision: Int, val scale: Int): NumberColumnConfiguration<T>(table, name, type) {
  val defaultPrecision=10
  val defaultScale=0
}

interface LengthColumnConfiguration<T:Any> {
  val length:Int
}

class SimpleLengthColumnConfiguration<T:Any>(table: TableRef, name: String, type: ColumnType<T>, override val length: Int): ColumnConfiguration<T>(table, name, type), LengthColumnConfiguration<T>

class ForeignKey constructor(private val fromCols:List<ColumnRef<*>>, private val toTable:TableRef, private val toCols:List<ColumnRef<*>>)

@Suppress("NOTHING_TO_INLINE")
class TableConfiguration(override val name:String, val extra:String?=null):TableRef {

  val cols = mutableListOf<Column<*>>()
  var primaryKey: List<ColumnRef<*>>? = null
  val foreignKeys = mutableListOf<ForeignKey>()
  val uniqueKeys = mutableListOf<List<ColumnRef<*>>>()
  val indices = mutableListOf<List<ColumnRef<*>>>()

  inline fun <V:Any,T:ColumnConfiguration<V>> T.add(block: T.() ->Unit) = ColumnImpl(apply(block)).apply { cols.add(this)}. ref()

  /* Versions with configuration closure. */
  inline fun BIT(name:String, block: ColumnConfiguration<Boolean>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BIT_T).add(block)
  inline fun BIT(name:String, length:Int, block: ColumnConfiguration<Array<Boolean>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BITFIELD_T).add(block)
  inline fun TINYINT(name:String, block: NumberColumnConfiguration<Byte>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.TINYINT_T).add(block)
  inline fun SMALLINT(name:String, block: NumberColumnConfiguration<Short>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.SMALLINT_T).add(block)
  inline fun MEDIUMINT(name:String, block: NumberColumnConfiguration<Int>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.MEDIUMINT_T).add(block)
  inline fun INT(name:String, block: NumberColumnConfiguration<Int>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.INT_T).add(block)
  inline fun BIGINT(name:String, block: NumberColumnConfiguration<Long>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.BIGINT_T).add(block)
  inline fun FLOAT(name:String, block: NumberColumnConfiguration<Float>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.FLOAT_T).add(block)
  inline fun DOUBLE(name:String, block: NumberColumnConfiguration<Double>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.DOUBLE_T).add(block)
  inline fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.DECIMAL_T, precision, scale).add(block)
  inline fun NUMERIC(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.NUMERIC_T, precision, scale).add(block)
  inline fun DATE(name:String, block: ColumnConfiguration<java.sql.Date>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATE_T).add(block)
  inline fun TIME(name:String, block: ColumnConfiguration<java.sql.Time>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIME_T).add(block)
  inline fun TIMESTAMP(name:String, block: ColumnConfiguration<java.sql.Timestamp>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIMESTAMP_T).add(block)
  inline fun DATETIME(name:String, block: ColumnConfiguration<java.sql.Timestamp>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATETIME_T).add(block)
  inline fun YEAR(name:String, block: ColumnConfiguration<java.sql.Date>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.YEAR_T).add(block)
  inline fun CHAR(name:String, length:Int = -1, block: LengthCharColumnConfiguration<String>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.CHAR_T, length).add(block)
  inline fun VARCHAR(name:String, length:Int, block: LengthCharColumnConfiguration<String>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.VARCHAR_T, length).add(block)
  inline fun BINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.BINARY_T, length).add(block)
  inline fun VARBINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.VARBINARY_T, length).add(block)
  inline fun TINYBLOB(name:String, block: ColumnConfiguration<ByteArray>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TINYBLOB_T).add(block)
  inline fun BLOB(name:String, block: ColumnConfiguration<ByteArray>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BLOB_T).add(block)
  inline fun MEDIUMBLOB(name:String, block: ColumnConfiguration<ByteArray>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.MEDIUMBLOB_T).add(block)
  inline fun LONGBLOB(name:String, block: ColumnConfiguration<ByteArray>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.LONGBLOB_T).add(block)
  inline fun TINYTEXT(name:String, block: CharColumnConfiguration<String>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TINYTEXT_T).add(block)
  inline fun TEXT(name:String, block: CharColumnConfiguration<String>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TEXT_T).add(block)
  inline fun MEDIUMTEXT(name:String, block: CharColumnConfiguration<String>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.MEDIUMTEXT_T).add(block)
  inline fun LONGTEXT(name:String, block: CharColumnConfiguration<String>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.LONGTEXT_T).add(block)

  /* Versions without configuration closure */
  inline fun BIT(name:String) = ColumnConfiguration(this, name, ColumnType.BIT_T).add({})
  inline fun BIT(name:String, length:Int) = ColumnConfiguration(this, name, ColumnType.BITFIELD_T).add({})
  inline fun TINYINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.TINYINT_T).add({})
  inline fun SMALLINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.SMALLINT_T).add({})
  inline fun MEDIUMINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.MEDIUMINT_T).add({})
  inline fun INT(name:String) = NumberColumnConfiguration(this, name, ColumnType.INT_T).add({})
  inline fun BIGINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.BIGINT_T).add({})
  inline fun FLOAT(name:String) = NumberColumnConfiguration(this, name, ColumnType.FLOAT_T).add({})
  inline fun DOUBLE(name:String) = NumberColumnConfiguration(this, name, ColumnType.DOUBLE_T).add({})
  inline fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, ColumnType.DECIMAL_T, precision, scale).add({})
  inline fun NUMERIC(name:String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, ColumnType.NUMERIC_T, precision, scale).add({})
  inline fun DATE(name:String) = ColumnConfiguration(this, name, ColumnType.DATE_T).add({})
  inline fun TIME(name:String) = ColumnConfiguration(this, name, ColumnType.TIME_T).add({})
  inline fun TIMESTAMP(name:String) = ColumnConfiguration(this, name, ColumnType.TIMESTAMP_T).add({})
  inline fun DATETIME(name:String) = ColumnConfiguration(this, name, ColumnType.DATETIME_T).add({})
  inline fun YEAR(name:String) = ColumnConfiguration(this, name, ColumnType.YEAR_T).add({})
  inline fun CHAR(name:String, length:Int = -1) = LengthCharColumnConfiguration(this, name, ColumnType.CHAR_T, length).add({})
  inline fun VARCHAR(name:String, length:Int) = LengthCharColumnConfiguration(this, name, ColumnType.VARCHAR_T, length).add({})
  inline fun BINARY(name:String, length:Int) = SimpleLengthColumnConfiguration(this, name, ColumnType.BINARY_T, length).add({})
  inline fun VARBINARY(name:String, length:Int) = SimpleLengthColumnConfiguration(this, name, ColumnType.VARBINARY_T, length).add({})
  inline fun TINYBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.TINYBLOB_T).add({})
  inline fun BLOB(name:String) = ColumnConfiguration(this, name, ColumnType.BLOB_T).add({})
  inline fun MEDIUMBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.MEDIUMBLOB_T).add({})
  inline fun LONGBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.LONGBLOB_T).add({})
  inline fun TINYTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.TINYTEXT_T).add({})
  inline fun TEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.TEXT_T).add({})
  inline fun MEDIUMTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.MEDIUMTEXT_T).add({})
  inline fun LONGTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.LONGTEXT_T).add({})
  
  inline fun INDEX(col1: ColumnRef<*>, vararg cols: ColumnRef<*>) { indices.add(mutableListOf(col1).apply { addAll(cols) })}
  inline fun UNIQUE(col1: ColumnRef<*>, vararg cols: ColumnRef<*>) { uniqueKeys.add(mutableListOf(col1).apply { addAll(cols) })}
  inline fun PRIMARY_KEY(col1: ColumnRef<*>, vararg cols: ColumnRef<*>) { primaryKey = mutableListOf(col1).apply { addAll(cols) }}

  class __FOREIGN_KEY__6<T1:Any, T2:Any, T3:Any, T4:Any, T5:Any, T6:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>, val col2:ColumnRef<T2>, val col3:ColumnRef<T3>, val col4:ColumnRef<T4>, val col5:ColumnRef<T5>, val col6:ColumnRef<T6>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>, ref2:ColumnRef<T2>, ref3:ColumnRef<T3>, ref4:ColumnRef<T4>, ref5:ColumnRef<T5>, ref6:ColumnRef<T6>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5, col6), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5, ref6).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, T2:Any, T3:Any, T4:Any, T5:Any, T6:Any> FOREIGN_KEY(col1: ColumnRef<T1>, col2:ColumnRef<T2>, col3:ColumnRef<T3>, col4: ColumnRef<T4>, col5:ColumnRef<T5>, col6:ColumnRef<T6>) =
      __FOREIGN_KEY__6(this, col1,col2,col3,col4,col5,col6)


  class __FOREIGN_KEY__5<T1:Any, T2:Any, T3:Any, T4:Any, T5:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>, val col2:ColumnRef<T2>, val col3:ColumnRef<T3>, val col4:ColumnRef<T4>, val col5:ColumnRef<T5>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>, ref2:ColumnRef<T2>, ref3:ColumnRef<T3>, ref4:ColumnRef<T4>, ref5:ColumnRef<T5>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, T2:Any, T3:Any, T4:Any, T5:Any> FOREIGN_KEY(col1: ColumnRef<T1>, col2:ColumnRef<T2>, col3:ColumnRef<T3>, col4: ColumnRef<T4>, col5:ColumnRef<T5>) =
      __FOREIGN_KEY__5(this, col1,col2,col3,col4,col5)

  class __FOREIGN_KEY__4<T1:Any, T2:Any, T3:Any, T4:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>, val col2:ColumnRef<T2>, val col3:ColumnRef<T3>, val col4:ColumnRef<T4>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>, ref2:ColumnRef<T2>, ref3:ColumnRef<T3>, ref4:ColumnRef<T4>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4), ref1.table, listOf(ref1, ref2, ref3, ref4)))
    }
  }

  inline fun <T1:Any, T2:Any, T3:Any, T4:Any> FOREIGN_KEY(col1: ColumnRef<T1>, col2:ColumnRef<T2>, col3:ColumnRef<T3>, col4: ColumnRef<T4>) =
      __FOREIGN_KEY__4(this, col1,col2,col3,col4)

  class __FOREIGN_KEY__3<T1:Any, T2:Any, T3:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>, val col2:ColumnRef<T2>, val col3:ColumnRef<T3>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>, ref2:ColumnRef<T2>, ref3:ColumnRef<T3>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3), ref1.table, listOf(ref1, ref2, ref3).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, T2:Any, T3:Any> FOREIGN_KEY(col1: ColumnRef<T1>, col2:ColumnRef<T2>, col3:ColumnRef<T3>) =
      __FOREIGN_KEY__3(this, col1,col2,col3)

  class __FOREIGN_KEY__2<T1:Any, T2:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>, val col2:ColumnRef<T2>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>, ref2:ColumnRef<T2>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2), ref1.table, listOf(ref1, ref2).apply { require(ref2.table==ref1.table) }))
    }
  }

  inline fun <T1:Any, T2:Any> FOREIGN_KEY(col1: ColumnRef<T1>, col2:ColumnRef<T2>) =
      __FOREIGN_KEY__2(this, col1,col2)

  class __FOREIGN_KEY__1<T1:Any>(val configuration:TableConfiguration, val col1:ColumnRef<T1>) {
    inline fun REFERENCES(ref1:ColumnRef<T1>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1), ref1.table, listOf(ref1)))
    }
  }

  inline fun <T1:Any> FOREIGN_KEY(col1: ColumnRef<T1>) =
      __FOREIGN_KEY__1(this, col1)

  fun toTable(): Table {
    return Table(name, cols, primaryKey, foreignKeys, uniqueKeys, indices, extra)
  }

}

class DatabaseConfiguration {

  val tables = mutableListOf<Table>()

  inline fun table(name:String, extra: String? = null, block:TableConfiguration.()->Unit):TableRef {
    return TableConfiguration(name, extra).apply { block() }.toTable().apply { tables.add(this) }.ref()
  }

  inline fun table(t:Table):TableRef {
    tables.add(t); return t.ref()
  }

}
