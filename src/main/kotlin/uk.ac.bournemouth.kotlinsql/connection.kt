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

package uk.ac.bournemouth.util.kotlin.sql

import uk.ac.bournemouth.kotlinsql.*
import uk.ac.bournemouth.util.kotlin.sql.impl.gen._Statement1
import java.sql.*
import java.util.*
import java.util.concurrent.Executor
import javax.sql.DataSource

/**
 * Created by pdvrieze on 13/03/16.
 */

inline fun <R> DataSource.connection(db: Database, block: (DBConnection) -> R): R =
    this.getConnection().use {
      return DBConnection(connection, db).let(block)
    }


//inline fun <R> DataSource.connection(username: String, password: String, block: (DBConnection) -> R) = getConnection(username, password).use { connection(it, block) }

class DBConnection constructor(private val connection: Connection, val db: Database) {

  @Deprecated(message = "Do not use, this is there just for transitional purposes", level = DeprecationLevel.WARNING)
  fun __getConnection() = connection

  //    init {
  //        connection.autoCommit = false
  //    }

  fun <R> raw(block: (Connection) -> R): R = block(connection)
  fun <R> use(block: (DBConnection) -> R): R = useHelper({ it.connection.close() }) {
    return transaction(block)
  }

  fun <R> transaction(block: (DBConnection) -> R):R {
    connection.autoCommit=false
    try {
      return block(this).apply { commit() }
    } catch (e:Exception) {
      connection.rollback()
      throw e
    }
  }


  /**
   * @see [Connection.prepareStatement]
   */
  fun <R> prepareStatement(sql: String, block: StatementHelper.() -> R): R = connection.prepareStatement(sql).use { block(StatementHelper(it)) }


  /**
   * @see [Connection.commit]
   */
  fun commit() = connection.commit()

  fun getMetaData() = connection.getMetaData()

  private inline fun prepareCall(sql: String) = connection.prepareCall(sql)

  /** @see [Connection.autoCommit] */
  var autoCommit: Boolean
    get() = connection.autoCommit
    set(value) { connection.autoCommit = value }

  @Throws(SQLException::class)
  fun rollback() = connection.rollback()

  @Throws(SQLException::class)
  fun isClosed(): Boolean = connection.isClosed

  //======================================================================
  // Advanced features:

  @Throws(SQLException::class)
  fun setReadOnly(readOnly: Boolean) = connection.setReadOnly(readOnly)

  @Throws(SQLException::class)
  fun isReadOnly(): Boolean = connection.isReadOnly()

  @Throws(SQLException::class)
  fun setCatalog(catalog: String) = connection.setCatalog(catalog)

  @Throws(SQLException::class)
  fun getCatalog(): String = connection.getCatalog()

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_NONE", "java.sql.Connection"))
  val TRANSACTION_NONE = Connection.TRANSACTION_NONE

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_READ_UNCOMMITTED", "java.sql.Connection"))
  val TRANSACTION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_READ_COMMITTED", "java.sql.Connection"))
  val TRANSACTION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_REPEATABLE_READ", "java.sql.Connection"))
  val TRANSACTION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_SERIALIZABLE", "java.sql.Connection"))
  val TRANSACTION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE

  @Throws(SQLException::class)
  fun setTransactionIsolation(level: Int) = connection.setTransactionIsolation(level)

  @Throws(SQLException::class)
  fun getTransactionIsolation(): Int = connection.transactionIsolation

  /**
   * Retrieves the first warning reported by calls on this
   * `Connection` object.  If there is more than one
   * warning, subsequent warnings will be chained to the first one
   * and can be retrieved by calling the method
   * `SQLWarning.getNextWarning` on the warning
   * that was retrieved previously.
   *
   * This method may not be
   * called on a closed connection; doing so will cause an
   * `SQLException` to be thrown.

   * Note: Subsequent warnings will be chained to this
   * SQLWarning.

   * @return the first `SQLWarning` object or `null`
   * *         if there are none
   * *
   * @exception SQLException if a database access error occurs or
   * *            this method is called on a closed connection
   * *
   * @see SQLWarning
   */
  val warningsIt: Iterator<SQLWarning> get() = object : AbstractIterator<SQLWarning>() {
    override fun computeNext() {
      val w = connection.warnings
      if (w != null) {
        setNext(w)
      } else {
        done()
      }
    }
  }

  val warnings: Sequence<SQLWarning> get() = object: Sequence<SQLWarning> {
    override fun iterator(): Iterator<SQLWarning> = warningsIt
  }

  /**
   * Clears all warnings reported for this `Connection` object.
   * After a call to this method, the method `getWarnings`
   * returns `null` until a new warning is
   * reported for this `Connection` object.

   * @exception SQLException SQLException if a database access error occurs
   * * or this method is called on a closed connection
   */
  @Throws(SQLException::class)
  fun clearWarnings() = connection.clearWarnings()


  //--------------------------JDBC 2.0-----------------------------

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, resultSetType: Int,
                                  resultSetConcurrency: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, resultSetType, resultSetConcurrency).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun getTypeMap() = connection.typeMap

  @Throws(SQLException::class)
  fun setTypeMap(map: Map<String, Class<*>>) = connection.setTypeMap(map)

  //--------------------------JDBC 3.0-----------------------------

  enum class Holdability(internal val jdbc:Int) {
    HOLD_CURSORS_OVER_COMMIT(ResultSet.HOLD_CURSORS_OVER_COMMIT),
    CLOSE_CURSORS_AT_COMMIT(ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /**
   * @see [Connection.getHoldability]
   */
  var holdability:Holdability
    get() = when (connection.holdability) {
      ResultSet.HOLD_CURSORS_OVER_COMMIT -> Holdability.HOLD_CURSORS_OVER_COMMIT
      ResultSet.CLOSE_CURSORS_AT_COMMIT -> Holdability.CLOSE_CURSORS_AT_COMMIT
      else -> throw IllegalArgumentException()
    }
    set(value) { connection.holdability = value.jdbc }

  @Throws(SQLException::class)
  fun setHoldability(holdability: Int) = connection.setHoldability(holdability)

  @Throws(SQLException::class)
  fun getHoldability() = connection.holdability

  @Throws(SQLException::class)
  fun setSavepoint(): Savepoint = connection.setSavepoint()

  @Throws(SQLException::class)
  fun setSavepoint(name: String): Savepoint = connection.setSavepoint(name)

  @Throws(SQLException::class)
  fun rollback(savepoint: Savepoint) = connection.rollback(savepoint)

  @Throws(SQLException::class)
  fun releaseSavepoint(savepoint: Savepoint) = connection.releaseSavepoint(savepoint)

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, resultSetType: Int,
                                  resultSetConcurrency: Int, resultSetHoldability: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, autoGeneratedKeys: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, autoGeneratedKeys).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, columnIndexes: IntArray, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, columnIndexes).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, columnNames: Array<out String>, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, columnNames).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun createClob(): Clob = connection.createClob()

  @Throws(SQLException::class)
  fun createBlob(): Blob = connection.createBlob()

  @Throws(SQLException::class)
  fun createNClob(): NClob = connection.createNClob()

  @Throws(SQLException::class)
  fun createSQLXML(): SQLXML = connection.createSQLXML()

  @Throws(SQLException::class)
  fun isValid(timeout: Int): Boolean = connection.isValid(timeout)

  @Throws(SQLClientInfoException::class)
  fun setClientInfo(name: String, value: String) = connection.setClientInfo(name, value)

  @Throws(SQLClientInfoException::class)
  fun setClientInfo(properties: Properties) = connection.setClientInfo(properties)

  @Throws(SQLException::class)
  fun getClientInfo(name: String): String = connection.getClientInfo(name)

  @Throws(SQLException::class)
  fun getClientInfo() = connection.clientInfo

  @Throws(SQLException::class)
  fun createArrayOf(typeName: String, elements: Array<Any>) = connection.createArrayOf(typeName, elements)

  @Throws(SQLException::class)
  fun createStruct(typeName: String, attributes: Array<Any>): Struct = connection.createStruct(typeName, attributes)

  //--------------------------JDBC 4.1 -----------------------------

  @Throws(SQLException::class)
  fun setSchema(schema: String) = connection.setSchema(schema)

  @Throws(SQLException::class)
  fun getSchema(): String = connection.schema

  @Throws(SQLException::class)
  fun abort(executor: Executor) = connection.abort(executor)

  @Throws(SQLException::class)
  fun setNetworkTimeout(executor: Executor, milliseconds: Int) = connection.setNetworkTimeout(executor, milliseconds)


  @Throws(SQLException::class)
  fun getNetworkTimeout(): Int = connection.networkTimeout

}

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * @param block a function to process this closable resource.
 * @return the result of [block] function on this closable resource.
 */
public inline fun <T : Connection, R> T.use(block: (T) -> R) = useHelper({ it.close() }, block)

public inline fun <T : Connection, R> T.useTransacted(block: (T) -> R): R = useHelper({ it.close() }) {
  it.autoCommit = false
  try {
    val result = block(it)
    it.commit()
    return result
  } catch (e: Exception) {
    it.rollback()
    throw e
  }

}


public inline fun  <T, R> T.useHelper(close: (T) -> Unit, block: (T) -> R): R {
  var closed = false
  try {
    return block(this)
  } catch (e: Exception) {
    closed = true
    try {
      close(this)
    } catch (closeException: Exception) {
      // drop for now.
    }
    throw e
  } finally {
    if (!closed) {
      close(this)
    }
  }
}