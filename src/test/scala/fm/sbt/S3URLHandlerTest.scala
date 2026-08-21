/*
 * Copyright 2014 Frugal Mechanic
 * Copyright 2026 David Akermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fm.sbt

import com.amazonaws.SDKGlobalConfiguration.{ACCESS_KEY_SYSTEM_PROPERTY, SECRET_KEY_SYSTEM_PROPERTY}
import fm.sbt.S3URLHandler.toEnvironmentVariableName

final class S3URLHandlerTest extends munit.FunSuite {

  private val bucket: String = "fm-sbt-s3-resolver-example-bucket"

  test("toEnvironmentVariableName") {
    assertEquals(toEnvironmentVariableName(bucket), "FM_SBT_S3_RESOLVER_EXAMPLE_BUCKET")
  }

  test("bucket specific system properties keep the bucket name unchanged") {
    assertEquals(
      s"$ACCESS_KEY_SYSTEM_PROPERTY.$bucket",
      "aws.accessKeyId.fm-sbt-s3-resolver-example-bucket"
    )
    assertEquals(
      s"$SECRET_KEY_SYSTEM_PROPERTY.$bucket",
      "aws.secretKey.fm-sbt-s3-resolver-example-bucket"
    )
  }
}
