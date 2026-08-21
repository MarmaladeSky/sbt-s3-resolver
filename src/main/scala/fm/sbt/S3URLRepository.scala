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

import org.apache.ivy.plugins.repository.url.URLRepository

import java.net.URI
import java.util
import java.util.List
import scala.jdk.CollectionConverters.*

final class S3URLRepository extends URLRepository {
  private val s3: S3URLHandler = new S3URLHandler()

  override def list(parent: String): util.List[String] = {
    if (parent.startsWith("s3")) {
      s3.list(URI.create(parent).toURL).map { _.toExternalForm }.asJava
    } else {
      super.list(parent)
    }
  }
}
