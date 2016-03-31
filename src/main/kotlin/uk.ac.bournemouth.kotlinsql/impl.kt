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

/**
 * Implementation for the database API
 */

class DatabaseImpl private constructor(val version:Int):Database {
  constructor(version:Int, configuration:DatabaseConfiguration):this(version)
}

open class ColumnImpl<T:Any>(val type: ColumnType<T>, val name: String, val notnull: Boolean, val unique: Boolean): Column {
  constructor(configuration: ColumnConfiguration<T>):
      this(type=configuration.type,
           name=configuration.name,
           notnull=configuration.notnull,
           unique=configuration.unique) {

  }
  override fun ref() = ColumnRef(name)
}




