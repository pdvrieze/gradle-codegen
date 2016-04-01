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
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * This is an abstract class that contains a set of database tables.
 *
 * @property _version The version of the database schema. This can in the future be used for updating
 * @property _tables The actual tables defined in the database
 */

abstract class Database private constructor(val _version:Int, val _tables:List<out ImmutableTable>) {

  companion object {
    private fun tablesFromObjects(container:KClass<out Database>): List<ImmutableTable> {
      return container.nestedClasses.map { it.objectInstance as? ImmutableTable }.filterNotNull()
    }
  }
  
  /**
   * Constructor for creating a new Database implementation.
   *
   * @param version The version of the database configuration.
   * @param block The configuration block where the database tables are added.
   */
  constructor(version:Int, block:DatabaseConfiguration.()->Unit):this(version, DatabaseConfiguration().apply(block))

  private constructor(version:Int, config:DatabaseConfiguration): this(version, config.tables)

  constructor(version:Int): this(version, mutableListOf()) {
    (_tables as MutableList<ImmutableTable>).addAll(tablesFromObjects(javaClass.kotlin))
  }

  /**
   * Delegate function to be used to reference tables. Note that this requires the name of the property to match the name
   * of the table.
   */
  protected inline fun <T: ImmutableTable> ref(table: T)= TableDelegate(table)

  /**
   * Delegate function to be used to reference tables. This delegate allows for renaming, by removing the need for checking.
   */
  protected inline fun <T: ImmutableTable> rename(table: T)= TableDelegate(table, false)


  /**
   * Helper class that implements the actual delegation of table access.
   */
  protected class TableDelegate<T: ImmutableTable>(private val table: T, private var needsCheck:Boolean=true) {
    operator fun getValue(thisRef: Database, property: KProperty<*>): T {
      if (needsCheck) {
        if (table._name != property.name) throw IllegalArgumentException("The table names do not match (${table._name}, ${property.name})")
        needsCheck = false
      }
      return table
    }
  }


}

/** A reference to a table. */
interface TableRef {
  /** The name of the table. */
  val _name:String
}

/**
 * A reference to a column.
 *
 * @param table The table the column is a part of
 * @param name The name of the column
 * @param type The [ColumnType] of the column.
 */
interface ColumnRef<T:Any, S: ColumnType<T, S>> {
  val table: TableRef
  val name:String
  val type: S
}

class ColsetRef(val table:TableRef, val columns:List<out ColumnRef<*, *>>) {
  constructor(table:TableRef, col1: ColumnRef<*, *>, vararg cols:ColumnRef<*, *>): this(table, mutableListOf(col1).apply { addAll(cols) })
}

interface Column<T:Any, S: ColumnType<T, S>>: ColumnRef<T,S> {
  fun ref(): ColumnRef<T,S>
  val notnull: Boolean?
  val unique: Boolean
  val autoincrement: Boolean
  val default: T?
  val comment:String?
  val columnFormat: ColumnConfiguration.ColumnFormat?
  val storageFormat: ColumnConfiguration.StorageFormat?
  val references:ColsetRef?

  fun toDDL(): CharSequence
}

interface BoundedType {
  val maxLen:Int
}

sealed class ColumnType<T:Any, S: ColumnType<T, S>>(val typeName:String, type: KClass<T>) {

  @Suppress("UNCHECKED")
  fun cast(column: Column<*,*>): Column<T, S> {
    if (column.type.typeName == typeName) {
      return column as Column<T, S>
    } else {
      throw TypeCastException("The given column is not of the correct type")
    }
  }

  object BIT_T       : ColumnType<Boolean, BIT_T>("BIT", Boolean::class), BoundedType { override val maxLen = 64 }
  object BITFIELD_T  : ColumnType<Array<Boolean>, BITFIELD_T>("BIT", Array<Boolean>::class)
  object TINYINT_T   : ColumnType<Byte, TINYINT_T>("BIGINT", Byte::class)
  object SMALLINT_T  : ColumnType<Short, SMALLINT_T>("SMALLINT", Short::class)
  object MEDIUMINT_T : ColumnType<Int, MEDIUMINT_T>("MEDIUMINT", Int::class)
  object INT_T       : ColumnType<Int, INT_T>("INT", Int::class)
  object BIGINT_T    : ColumnType<Long, BIGINT_T>("BIGINT", Long::class)

  object FLOAT_T     : ColumnType<Float, FLOAT_T>("FLOAT", Float::class)
  object DOUBLE_T    : ColumnType<Double, DOUBLE_T>("DOUBLE", Double::class)

  object DECIMAL_T   : ColumnType<BigDecimal, DECIMAL_T>("DECIMAL", BigDecimal::class)
  object NUMERIC_T   : ColumnType<BigDecimal, NUMERIC_T>("NUMERIC", BigDecimal::class)

  object DATE_T      : ColumnType<java.sql.Date, DATE_T>("DATE", java.sql.Date::class)
  object TIME_T      : ColumnType<java.sql.Time, TIME_T>("TIME", java.sql.Time::class)
  object TIMESTAMP_T : ColumnType<java.sql.Timestamp, TIMESTAMP_T>("TIMESTAMP", java.sql.Timestamp::class)
  object DATETIME_T  : ColumnType<java.sql.Timestamp, DATETIME_T>("TIMESTAMP", java.sql.Timestamp::class)
  object YEAR_T      : ColumnType<java.sql.Date, YEAR_T>("YEAR", java.sql.Date::class)

  object CHAR_T      : ColumnType<String, CHAR_T>("CHAR", String::class), BoundedType { override val maxLen = 255 }
  object VARCHAR_T   : ColumnType<String, VARCHAR_T>("VARCHAR", String::class), BoundedType { override val maxLen = 0xffff }

  object BINARY_T    : ColumnType<ByteArray, BINARY_T>("BINARY", ByteArray::class), BoundedType { override val maxLen = 255 }
  object VARBINARY_T : ColumnType<ByteArray, VARBINARY_T>("VARBINARY", ByteArray::class), BoundedType { override val maxLen = 0xffff }
  object TINYBLOB_T  : ColumnType<ByteArray, TINYBLOB_T>("TINYBLOB", ByteArray::class), BoundedType { override val maxLen = 255 }
  object BLOB_T      : ColumnType<ByteArray, BLOB_T>("BLOB", ByteArray::class), BoundedType { override val maxLen = 0xffff }
  object MEDIUMBLOB_T: ColumnType<ByteArray, MEDIUMBLOB_T>("MEDIUMBLOB", ByteArray::class), BoundedType { override val maxLen = 0xffffff }
  object LONGBLOB_T  : ColumnType<ByteArray, LONGBLOB_T>("LONGBLOB", ByteArray::class), BoundedType { override val maxLen = Int.MAX_VALUE /*Actually it would be more*/}

  object TINYTEXT_T  : ColumnType<String, TINYTEXT_T>("TINYTEXT", String::class), BoundedType { override val maxLen = 255 }
  object TEXT_T      : ColumnType<String, TEXT_T>("TEXT", String::class), BoundedType { override val maxLen = 0xffff }
  object MEDIUMTEXT_T: ColumnType<String, MEDIUMTEXT_T>("MEDIUMTEXT", String::class), BoundedType { override val maxLen = 0xffffff }
  object LONGTEXT_T  : ColumnType<String, LONGTEXT_T>("LONGTEXT", String::class), BoundedType { override val maxLen = Int.MAX_VALUE /*Actually it would be more*/}

  /*
  ENUM(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
  SET(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
   */

}

open class ColumnConfiguration<T:Any, S: ColumnType<T,S>,C:Column<T,S>>(val table: TableRef, val name: String, val type: S) {

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
  inline fun REFERENCES(table:TableRef, col1:ColumnRef<*,*>, vararg columns: ColumnRef<*,*>) { references=ColsetRef(table, col1, *columns) }

}

open class NumberColumnConfiguration<T:Any, S: ColumnType<T,S>, C:NumericColumn<T,S>>(table: TableRef, name: String, type: S): ColumnConfiguration<T, S, C>(table, name, type) {
  var unsigned: Boolean = false
  var zerofill: Boolean = false
  var displayLength: Int = -1

  val UNSIGNED:Unit get() { unsigned = true }

  val ZEROFILL:Unit get() { unsigned = true }

}

interface NumericColumn<T:Any, S: ColumnType<T, S>>: Column<T,S> {
  val unsigned: Boolean
  val zerofill: Boolean
  val displayLength: Int
}


interface CharColumn<T:Any, S: ColumnType<T, S>>: Column<T, S> {
  val charset: String?
  val collation: String?
  val binary: Boolean
}


open class CharColumnConfiguration<T:Any, S: ColumnType<T,S>, C:CharColumn<T,S>>(table: TableRef, name: String, type: S): ColumnConfiguration<T, S, C>(table, name, type) {
  var charset: String? = null
  var collation: String? = null
  var binary:Boolean = false

  val BINARY:Unit get() { binary = true }

  inline fun CHARACTER_SET(charset:String) { this.charset = charset }
  inline fun COLLATE(collation:String) { this.collation = collation }
}


interface LengthCharColumn<T:Any, S: ColumnType<T, S>>: CharColumn<T, S>,LengthColumn<T, S>


final class LengthCharColumnConfiguration<T:Any, S: ColumnType<T,S>>(table: TableRef, name: String, type: S, override val length: Int): CharColumnConfiguration<T, S, LengthCharColumn<T,S>>(table, name, type), LengthColumnConfiguration<T, S, LengthCharColumn<T,S>>


interface DecimalColumn<T:Any, S: ColumnType<T, S>>: NumericColumn<T, S> {
  val precision:Int
  val scale:Int
}


final class DecimalColumnConfiguration<T:Any, S: ColumnType<T,S>>(table: TableRef, name: String, type: S, val precision: Int, val scale: Int): NumberColumnConfiguration<T, S, DecimalColumn<T,S>>(table, name, type) {
  val defaultPrecision=10
  val defaultScale=0
}

interface LengthColumn<T:Any, S: ColumnType<T, S>>: Column<T, S> {
  val length:Int
}

interface LengthColumnConfiguration<T:Any, S: ColumnType<T,S>, C:LengthColumn<T,S>> {
  val length:Int
}

final class SimpleLengthColumnConfiguration<T:Any, S: ColumnType<T,S>>(table: TableRef, name: String, type: S, override val length: Int): ColumnConfiguration<T, S, LengthColumn<T,S>>(table, name, type), LengthColumnConfiguration<T, S, LengthColumn<T,S>>

class ForeignKey constructor(private val fromCols:List<ColumnRef<*,*>>, private val toTable:TableRef, private val toCols:List<ColumnRef<*,*>>) {
  internal fun toDDL(): CharSequence {
    val transform: (ColumnRef<*,*>) -> CharSequence = { it.name }
    val result = fromCols.joinTo(StringBuilder(), "`, `", "FOREIGN KEY (`", "`) REFERENCES ", transform = transform)
    result.append(toTable._name)
    return toCols.joinTo(result, "`, `", "`)", transform = transform)
  }
}

/**
 * The main class that caries a lot of the load for the class.
 */
@Suppress("NOTHING_TO_INLINE")
class TableConfiguration(override val _name:String, val extra:String?=null):TableRef {

  val cols = mutableListOf<Column<*, *>>()
  var primaryKey: List<ColumnRef<*,*>>? = null
  val foreignKeys = mutableListOf<ForeignKey>()
  val uniqueKeys = mutableListOf<List<ColumnRef<*,*>>>()
  val indices = mutableListOf<List<ColumnRef<*,*>>>()

  inline fun <T :Any, S: ColumnType<T,S>, C:Column<T,S>, CONF_T :ColumnConfiguration<T, S, C>> CONF_T.add(block: CONF_T.() ->Unit):ColumnRef<T,S> {
    // The casting to C is necessary to make stuff compile
    return (ColumnImpl(this.apply(block)) as C).let { it -> cols.add(it); it.ref() }
  }

  /* Versions with configuration closure. */
  inline fun BIT(name:String, block: ColumnConfiguration<Boolean, BIT_T, Column<Boolean, BIT_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BIT_T).add(block)
  inline fun BIT(name:String, length:Int, block: LengthColumnConfiguration<Array<Boolean>, BITFIELD_T, LengthColumn<Array<Boolean>,BITFIELD_T>>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.BITFIELD_T, length).add(block)
  inline fun TINYINT(name:String, block: NumberColumnConfiguration<Byte, TINYINT_T, NumericColumn<Byte, TINYINT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.TINYINT_T).add(block)
  inline fun SMALLINT(name:String, block: NumberColumnConfiguration<Short, SMALLINT_T, NumericColumn<Short, SMALLINT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.SMALLINT_T).add(block)
  inline fun MEDIUMINT(name:String, block: NumberColumnConfiguration<Int, MEDIUMINT_T, NumericColumn<Int, MEDIUMINT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.MEDIUMINT_T).add(block)
  inline fun INT(name:String, block: NumberColumnConfiguration<Int, INT_T, NumericColumn<Int, INT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.INT_T).add(block)
  inline fun BIGINT(name:String, block: NumberColumnConfiguration<Long, BIGINT_T, NumericColumn<Long, BIGINT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.BIGINT_T).add(block)
  inline fun FLOAT(name:String, block: NumberColumnConfiguration<Float, FLOAT_T, NumericColumn<Float, FLOAT_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.FLOAT_T).add(block)
  inline fun DOUBLE(name:String, block: NumberColumnConfiguration<Double, DOUBLE_T, NumericColumn<Double, DOUBLE_T>>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.DOUBLE_T).add(block)
  inline fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal, DECIMAL_T>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.DECIMAL_T, precision, scale).add(block)
  inline fun NUMERIC(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal, NUMERIC_T>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.NUMERIC_T, precision, scale).add(block)
  inline fun DATE(name:String, block: ColumnConfiguration<java.sql.Date, DATE_T, Column<java.sql.Date, DATE_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATE_T).add(block)
  inline fun TIME(name:String, block: ColumnConfiguration<java.sql.Time, TIME_T, Column<java.sql.Time, TIME_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIME_T).add(block)
  inline fun TIMESTAMP(name:String, block: ColumnConfiguration<java.sql.Timestamp, TIMESTAMP_T, Column<java.sql.Timestamp, TIMESTAMP_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIMESTAMP_T).add(block)
  inline fun DATETIME(name:String, block: ColumnConfiguration<java.sql.Timestamp, DATETIME_T, Column<java.sql.Timestamp, DATETIME_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATETIME_T).add(block)
  inline fun YEAR(name:String, block: ColumnConfiguration<java.sql.Date, YEAR_T, Column<java.sql.Date, YEAR_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.YEAR_T).add(block)
  inline fun CHAR(name:String, length:Int = -1, block: LengthCharColumnConfiguration<String, CHAR_T>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.CHAR_T, length).add(block)
  inline fun VARCHAR(name:String, length:Int, block: LengthCharColumnConfiguration<String, VARCHAR_T>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.VARCHAR_T, length).add(block)
  inline fun BINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray, BINARY_T, LengthColumn<ByteArray, BINARY_T>>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.BINARY_T, length).add(block)
  inline fun VARBINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray, VARBINARY_T, LengthColumn<ByteArray, VARBINARY_T>>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.VARBINARY_T, length).add(block)
  inline fun TINYBLOB(name:String, block: ColumnConfiguration<ByteArray, TINYBLOB_T, Column<ByteArray, TINYBLOB_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TINYBLOB_T).add(block)
  inline fun BLOB(name:String, block: ColumnConfiguration<ByteArray, BLOB_T, Column<ByteArray, BLOB_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BLOB_T).add(block)
  inline fun MEDIUMBLOB(name:String, block: ColumnConfiguration<ByteArray, MEDIUMBLOB_T, Column<ByteArray, MEDIUMBLOB_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.MEDIUMBLOB_T).add(block)
  inline fun LONGBLOB(name:String, block: ColumnConfiguration<ByteArray, LONGBLOB_T, Column<ByteArray, LONGBLOB_T>>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.LONGBLOB_T).add(block)
  inline fun TINYTEXT(name:String, block: CharColumnConfiguration<String, TINYTEXT_T, CharColumn<String, TINYTEXT_T>>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TINYTEXT_T).add(block)
  inline fun TEXT(name:String, block: CharColumnConfiguration<String, TEXT_T, CharColumn<String, TEXT_T>>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TEXT_T).add(block)
  inline fun MEDIUMTEXT(name:String, block: CharColumnConfiguration<String, MEDIUMTEXT_T, CharColumn<String, MEDIUMTEXT_T>>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.MEDIUMTEXT_T).add(block)
  inline fun LONGTEXT(name:String, block: CharColumnConfiguration<String, LONGTEXT_T, CharColumn<String, LONGTEXT_T>>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.LONGTEXT_T).add(block)

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

  inline fun INDEX(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { indices.add(mutableListOf(col1).apply { addAll(cols) })}
  inline fun UNIQUE(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { uniqueKeys.add(mutableListOf(col1).apply { addAll(cols) })}
  inline fun PRIMARY_KEY(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { primaryKey = mutableListOf(col1).apply { addAll(cols) }}

  class __FOREIGN_KEY__6<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>, T6:Any, S6:ColumnType<T6,S6>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>, val col6:ColumnRef<T6, S6>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>, ref6:ColumnRef<T6, S6>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5, col6), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5, ref6).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>, T6:Any, S6:ColumnType<T6,S6>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>, col6:ColumnRef<T6, S6>) =
      __FOREIGN_KEY__6(this, col1,col2,col3,col4,col5,col6)


  class __FOREIGN_KEY__5<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>) =
      __FOREIGN_KEY__5(this, col1,col2,col3,col4,col5)

  class __FOREIGN_KEY__4<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4), ref1.table, listOf(ref1, ref2, ref3, ref4)))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>) =
      __FOREIGN_KEY__4(this, col1,col2,col3,col4)

  class __FOREIGN_KEY__3<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3), ref1.table, listOf(ref1, ref2, ref3).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>) =
      __FOREIGN_KEY__3(this, col1,col2,col3)

  class __FOREIGN_KEY__2<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2), ref1.table, listOf(ref1, ref2).apply { require(ref2.table==ref1.table) }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>) =
      __FOREIGN_KEY__2(this, col1,col2)

  class __FOREIGN_KEY__1<T1:Any, S1:ColumnType<T1,S1>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1), ref1.table, listOf(ref1)))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>> FOREIGN_KEY(col1: ColumnRef<T1, S1>) =
      __FOREIGN_KEY__1(this, col1)

}

class DatabaseConfiguration {

  private class __AnonymousTable(name:String, extra: String?, block:TableConfiguration.()->Unit): ImmutableTable(name, extra, block)

  val tables = mutableListOf<ImmutableTable>()

  fun table(name:String, extra: String? = null, block:TableConfiguration.()->Unit):TableRef {
    return __AnonymousTable(name, extra, block)
  }

  inline fun table(t: ImmutableTable):TableRef {
    tables.add(t); return t.ref()
  }

}
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
  val _cols: List<Column<*, *>>
  val _primaryKey: List<Column<*, *>>?
  val _foreignKeys: List<ForeignKey>
  val _uniqueKeys: List<List<Column<*, *>>>
  val _indices: List<List<Column<*, *>>>
  val _extra: String?

  fun field(name:String): Column<*,*>?
  fun ref(): TableRef
  fun resolve(ref: ColumnRef<*, *>): Column<*, *>

  interface FieldAccessor<T:Any, S:ColumnType<T,S>, C:Column<T,S>> {
    operator fun getValue(thisRef: Table, property: kotlin.reflect.KProperty<*>): C
  }

  fun appendDDL(appendable: Appendable)
}

/**
 * A base class for table declarations. Users of the code are expected to use this with a configuration closure to create
 * database tables. for typed columns to be available they need to be declared using `by [type]` or `by [name]`.
 *
 * A sample use is:
 * ```
 *    object peopleTable:[ImmutableTable]("people", "ENGINE=InnoDB CHARSET=utf8", {
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
abstract class ImmutableTable private constructor(override val _name: String,
                                                  override val _cols: List<Column<*, *>>,
                                                  override val _primaryKey: List<Column<*, *>>?,
                                                  override val _foreignKeys: List<ForeignKey>,
                                                  override val _uniqueKeys: List<List<Column<*, *>>>,
                                                  override val _indices: List<List<Column<*, *>>>,
                                                  override val _extra: String?) : AbstractTable() {

  private constructor(c: TableConfiguration):this(c._name, c.cols, c.primaryKey?.let {c.cols.resolve(it)}, c.foreignKeys, c.uniqueKeys.map({c.cols.resolve(it)}), c.indices.map({c.cols.resolve(it)}), c.extra)

  /**
   * The main use of this class is through inheriting this constructor.
   */
  constructor(name:String, extra: String? = null, block: TableConfiguration.()->Unit): this(
        TableConfiguration(name, extra).apply(block)  )

  protected fun <T:Any, S: ColumnType<T, S>, C:Column<T,S>> type(type: ColumnType<T, S>) = TypeFieldAccessor<T, S, C>(
        type)

}

