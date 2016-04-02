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

import org.testng.annotations.Test
import kotlin.reflect.KProperty

/**
 * Created by pdvrieze on 02/04/16.
 */
class testTableDefs {

  @Test
  fun testSimpleDefine() {
    val test = object: MutableTable("Testq", null) {

      val bit by BIT("bit")

      override fun init() {
        throw UnsupportedOperationException()
      }

    }
  }

  class SimpleDelegate() {

    init {
      System.out.println("Creating delegate object")
    }

    operator fun getValue(thisref:Any, property:KProperty<*>): String {
      return "bar"
    }
    operator fun setValue(thisref:Any, property:KProperty<*>, value:String) {
      System.out.println("Attempting to set value ${value}")
    }
  }

  @Test
  fun testDelegate() {



    val test = object {

      val simpleDelegate:SimpleDelegate get() {
        System.out.println("Initializing a delegate by function")
        return SimpleDelegate()
      }

      val bar by SimpleDelegate()
      val baz by simpleDelegate
    }
  }

}