/*
 * Copyright 2019 Serge Merzliakov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.installmation.model

import io.mockk.every
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.installmation.model.binary.JPackageExecutable
import org.junit.Test
import java.io.File


class JPackageExecutableTest {

   companion object {
      const val JDK_14_BUILD49 = "14-jpackage"
   }
   
   @Test
   fun shouldGetVersionEarlyAccessJdk14() {
      val mockPackage = spyk(JPackageExecutable(File("ignored")))
      every { mockPackage.execute() }.returns(listOf("WARNING: Using experimental tool jpackage", JDK_14_BUILD49))
      assertThat(mockPackage.getVersion()).isEqualTo(JDK_14_BUILD49)
   }
}