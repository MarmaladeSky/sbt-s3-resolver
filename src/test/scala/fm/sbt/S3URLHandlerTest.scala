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

import fm.sbt.S3URLHandler.toEnvironmentVariableName
import fm.sbt.s3.Handler
import java.net.URL

final class S3URLHandlerTest extends munit.FunSuite {

  private val bucket: String = "fm-sbt-s3-resolver-example-bucket"

  test("toEnvironmentVariableName") {
    assertEquals(toEnvironmentVariableName(bucket), "FM_SBT_S3_RESOLVER_EXAMPLE_BUCKET")
  }

  test("bucket specific system properties keep the bucket name unchanged") {
    assertEquals(
      s"aws.accessKeyId.$bucket",
      "aws.accessKeyId.fm-sbt-s3-resolver-example-bucket"
    )
    assertEquals(
      s"aws.secretKey.$bucket",
      "aws.secretKey.fm-sbt-s3-resolver-example-bucket"
    )
  }

  test("parses documented S3 URL formats") {
    val expected = (bucket, "snapshots/example.jar")
    val urls = Seq(
      s"s3://$bucket/snapshots/example.jar",
      s"s3://s3.amazonaws.com/$bucket/snapshots/example.jar",
      s"s3://$bucket.s3.amazonaws.com/snapshots/example.jar",
      s"s3://s3-us-west-2.amazonaws.com/$bucket/snapshots/example.jar",
      s"s3://$bucket.s3-us-west-2.amazonaws.com/snapshots/example.jar"
    )
    urls.foreach { value =>
      val url = new URL(null, value, new Handler())
      assertEquals(new S3URLHandler().getBucketAndKey(url), expected)
    }
  }
}
