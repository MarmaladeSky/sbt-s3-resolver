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

import java.io.{File, FileInputStream, InputStream}
import java.net.{URI, URL}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.naming.{Context, NamingException}
import javax.naming.directory.{Attribute, Attributes, InitialDirContext}
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{CopyProgressEvent, CopyProgressListener, Message}
import software.amazon.awssdk.auth.credentials._
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.{RequestBody, ResponseTransformer}
import software.amazon.awssdk.http.apache.{ApacheHttpClient, ProxyConfiguration}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.matching.Regex

object S3URLHandler {
  private val DOT_SBT_DIR: File = new File(System.getProperty("user.home"), ".sbt")

  // This is for matching region names in URLs or host names
  private val RegionMatcher: Regex = Region.regions().asScala.map{ _.id }.sortBy{ -1 * _.length }.mkString("|").r

  private val ACCESS_KEY_ENV_VAR = "AWS_ACCESS_KEY_ID"
  private val SECRET_KEY_ENV_VAR = "AWS_SECRET_ACCESS_KEY"
  private val ACCESS_KEY_SYSTEM_PROPERTY = "aws.accessKeyId"
  private val SECRET_KEY_SYSTEM_PROPERTY = "aws.secretKey"

  private var bucketCredentialsProvider: String => AwsCredentialsProvider = makePropertiesFileCredentialsProvider

  private var bucketACLMap: Map[String, ObjectCannedACL] = Map()

  def registerBucketCredentialsProvider(provider: String => AwsCredentialsProvider): Unit = {
    bucketCredentialsProvider = provider
  }

  def registerBucketACLMap(aclMap: Map[String, ObjectCannedACL]): Unit = {
    bucketACLMap = aclMap
  }

  def getBucketCredentialsProvider: String => AwsCredentialsProvider = bucketCredentialsProvider

  private class S3URLInfo(available: Boolean, contentLength: Long, lastModified: Long) extends URLHandler.URLInfo(available, contentLength, lastModified)
  
  private class BucketSpecificSystemPropertiesCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    
    def AccessKeyName: String = ACCESS_KEY_SYSTEM_PROPERTY
    def SecretKeyName: String = SECRET_KEY_SYSTEM_PROPERTY

    protected def getProp(names: String*): String = names.map{ System.getProperty }.flatMap{ Option(_) }.head.trim
  }
  
  private class BucketSpecificEnvironmentVariableCredentialsProvider(bucket: String) extends BucketSpecificCredentialsProvider(bucket) {
    def AccessKeyName: String = ACCESS_KEY_ENV_VAR
    def SecretKeyName: String = SECRET_KEY_ENV_VAR
    
    protected def getProp(names: String*): String = names.map{ toEnvironmentVariableName }.map{ System.getenv }.flatMap{ Option(_) }.head.trim
  }
  
  private abstract class BucketSpecificCredentialsProvider(bucket: String) extends AwsCredentialsProvider {
    def AccessKeyName: String
    def SecretKeyName: String
    
    def resolveCredentials(): AwsCredentials = {
      val accessKey: String = getProp(s"${AccessKeyName}.${bucket}", s"${bucket}.${AccessKeyName}")
      val secretKey: String = getProp(s"${SecretKeyName}.${bucket}", s"${bucket}.${SecretKeyName}")
      
      AwsBasicCredentials.create(accessKey, secretKey)
    }
    
    // This should throw an exception if the value is missing
    protected def getProp(names: String*): String
  }

  private abstract class RoleBasedCredentialsProvider(providerChain: AwsCredentialsProvider) extends AwsCredentialsProvider {
    def RoleArnKeyNames: Seq[String]

    // This should throw an exception if the value is missing
    protected def getRoleArn(keys: String*): String

    def resolveCredentials(): AwsCredentials = {
      val roleArn: String = getRoleArn(RoleArnKeyNames*)

      if (roleArn == null || roleArn == "") return null

      val securityTokenService: StsClient = StsClient.builder().credentialsProvider(providerChain).build()
      try {
        val roleRequest: AssumeRoleRequest = AssumeRoleRequest.builder()
          .roleArn(roleArn)
          .roleSessionName(System.currentTimeMillis.toString)
          .build()
        val credentials = securityTokenService.assumeRole(roleRequest).credentials()
        AwsSessionCredentials.create(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken())
      } finally securityTokenService.close()
    }
  }

  private class RoleBasedSystemPropertiesCredentialsProvider(providerChain: AwsCredentialsProvider)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "aws.roleArn"
    val RoleArnKeyNames: Seq[String] = Seq(RoleArnKeyName)

    protected def getRoleArn(keys: String*): String = keys.map( System.getProperty ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedEnvironmentVariableCredentialsProvider(providerChain: AwsCredentialsProvider)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "AWS_ROLE_ARN"
    val RoleArnKeyNames: Seq[String] = Seq("AWS_ROLE_ARN")

    protected def getRoleArn(keys: String*): String = keys.map( toEnvironmentVariableName ).map( System.getenv ).flatMap( Option(_) ).head.trim
  }

  private class RoleBasedPropertiesFileCredentialsProvider(providerChain: AwsCredentialsProvider, fileName: String)
      extends RoleBasedCredentialsProvider(providerChain) {

    val RoleArnKeyName: String = "roleArn"
    val RoleArnKeyNames: Seq[String] = Seq(RoleArnKeyName)

    protected def getRoleArn(keys: String*): String = {
      val file: File = new File(DOT_SBT_DIR, fileName)
      
      // This will throw if the file doesn't exist
      val is: InputStream = new FileInputStream(file)
      
      try {
        val props: Properties = new Properties()
        props.load(is)
        // This will throw if there is no matching properties
        RoleArnKeyNames.map{ props.getProperty }.flatMap{ Option(_) }.head.trim
      } finally is.close()
    }
  }

  private class BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(providerChain: AwsCredentialsProvider, bucket: String)
      extends RoleBasedSystemPropertiesCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }

  private class BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(providerChain: AwsCredentialsProvider, bucket: String)
      extends RoleBasedEnvironmentVariableCredentialsProvider(providerChain) {

    override val RoleArnKeyNames: Seq[String] = Seq(s"${RoleArnKeyName}.${bucket}", s"${bucket}.${RoleArnKeyName}")
  }
  
  private[sbt] def toEnvironmentVariableName(s: String): String = s.toUpperCase.replace('-','_').replace('.','_').replaceAll("[^A-Z0-9_]", "")

  private def makePropertiesFileCredentialsProvider(fileName: String): AwsCredentialsProvider = new AwsCredentialsProvider {
    def resolveCredentials(): AwsCredentials = {
      val file: File = new File(DOT_SBT_DIR, fileName)
      val is: InputStream = new FileInputStream(file)
      try {
        val props = new Properties()
        props.load(is)
        AwsBasicCredentials.create(props.getProperty("accessKey").trim, props.getProperty("secretKey").trim)
      } finally is.close()
    }
  }

  def defaultCredentialsProviderChain(bucket: String): AwsCredentialsProviderChain = {
    val basicProviders: Vector[AwsCredentialsProvider] = Vector(
      new BucketSpecificEnvironmentVariableCredentialsProvider(bucket),
      new BucketSpecificSystemPropertiesCredentialsProvider(bucket),
      makePropertiesFileCredentialsProvider(s".s3credentials_${bucket}"),
      makePropertiesFileCredentialsProvider(s".${bucket}_s3credentials"),
      DefaultCredentialsProvider.builder().build(),
      makePropertiesFileCredentialsProvider(".s3credentials"),
      InstanceProfileCredentialsProvider.create()
    )

    val basicProviderChain: AwsCredentialsProviderChain = AwsCredentialsProviderChain.builder().credentialsProviders(basicProviders.asJava).build()

    val roleBasedProviders: Vector[AwsCredentialsProvider] = Vector(
      new BucketSpecificRoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain, bucket),
      new BucketSpecificRoleBasedSystemPropertiesCredentialsProvider(basicProviderChain, bucket),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials_${bucket}"),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".${bucket}_s3credentials"),
      new RoleBasedEnvironmentVariableCredentialsProvider(basicProviderChain),
      new RoleBasedSystemPropertiesCredentialsProvider(basicProviderChain),
      new RoleBasedPropertiesFileCredentialsProvider(basicProviderChain, s".s3credentials")
    )

    AwsCredentialsProviderChain.builder().credentialsProviders((roleBasedProviders ++ basicProviders).asJava).build()
  }

  def getRegionNameFromDNS(bucket: String): Option[String] = {
    // maven.custom.s3.amazonaws.com. 21600 IN	CNAME	s3-1-w.amazonaws.com.
    //           s3-1-w.amazonaws.com.	39	IN	CNAME	s3-w.us-east-1.amazonaws.com.
    getDNSAliasesForBucket(bucket).flatMap { RegionMatcher.findFirstIn(_) }.headOption
  }

  private val dnsContext: InitialDirContext = {
    val env: Properties = new Properties()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory")
    new InitialDirContext(env)
  }

  def getDNSAliasesForBucket(bucket: String): Seq[String] = {
    getDNSAliasesForHost(bucket + ".s3.amazonaws.com")
  }

  def getDNSAliasesForHost(host: String): Seq[String] = getDNSAliasesForHost(host, Nil)

  @tailrec private def getDNSAliasesForHost(host: String, matches: List[String]): Seq[String] = {
    val cname: Option[String] = try {
      val attrs: Attributes = dnsContext.getAttributes(host, Array("CNAME"))
      Option(attrs.get("CNAME"))
        .flatMap{ (attr: Attribute) => Option(attr.get) }
        .collectFirst{ case res: String => res }
    } catch {
      case _: NamingException => None
    }

    if (cname.isEmpty || cname.exists{ matches.contains(_) }) matches
    else getDNSAliasesForHost(cname.get, cname.get :: matches)
  }
}

/**
 * This implements the Ivy URLHandler
 */
final class S3URLHandler extends URLHandler {
  import fm.sbt.S3URLHandler._
  import org.apache.ivy.util.url.URLHandler.{UNAVAILABLE, URLInfo}

  // Cache of Bucket Name => S3 Client Instance
  private val amazonS3ClientCache: ConcurrentHashMap[String,S3Client] = new ConcurrentHashMap()

  // Cache of Bucket Name => true/false (requires Server Side Encryption or not)
  private val bucketRequiresSSE: ConcurrentHashMap[String,Boolean] = new ConcurrentHashMap()

  def isReachable(url: URL): Boolean = getURLInfo(url).isReachable
  def isReachable(url: URL, timeout: Int): Boolean = getURLInfo(url, timeout).isReachable
  def getContentLength(url: URL): Long = getURLInfo(url).getContentLength
  def getContentLength(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getContentLength
  def getLastModified(url: URL): Long = getURLInfo(url).getLastModified
  def getLastModified(url: URL, timeout: Int): Long = getURLInfo(url, timeout).getLastModified
  def getURLInfo(url: URL): URLInfo = getURLInfo(url, 0)

  private def debug(msg: String): Unit = Message.debug("S3URLHandler."+msg)

  def getCredentialsProvider(bucket: String): AwsCredentialsProvider = {
    Message.info("S3URLHandler - Looking up AWS Credentials for bucket: "+bucket+" ...")

    val credentialsProvider: AwsCredentialsProvider = try {
      getBucketCredentialsProvider(bucket)
    } catch {
      case ex: RuntimeException =>
        Message.error("Unable to find AWS Credentials.")
        throw ex
    }

    Message.info("S3URLHandler - Using AWS Access Key Id: "+credentialsProvider.resolveCredentials().accessKeyId()+" for bucket: "+bucket)

    credentialsProvider
  }

  def getProxyConfiguration: Option[ProxyConfiguration] = for {
    proxyHost <- Option(System.getProperty("https.proxyHost"))
    proxyPort <- Option(System.getProperty("https.proxyPort")).map(_.toInt)
  } yield ProxyConfiguration.builder().endpoint(URI.create(s"http://$proxyHost:$proxyPort")).build()

  def getClientBucketAndKey(url: URL): (S3Client, String, String) = {
    val (bucket, key) = getBucketAndKey(url)

    var client: S3Client = amazonS3ClientCache.get(bucket)

    if (null == client) {
      // This allows you to change the S3 endpoint and signing region to point to a non-aws S3 implementation (e.g. LocalStack).
      val endpointOverride: Option[URI] = Option(System.getenv("S3_SERVICE_ENDPOINT")).map(URI.create)
      val signingRegion: Option[Region] = Option(System.getenv("S3_SIGNING_REGION")).map(Region.of)

      // Path Style Access is deprecated by Amazon S3 but LocalStack seems to want to use it
      val pathStyleAccess: Boolean = Option(System.getenv("S3_PATH_STYLE_ACCESS")).exists(_.toBoolean)

      // Rerouting can cause replacing the user custom endpoint with the S3 default one (s3.amazonaws.com). Default is true
      val forceGlobalBucketAccessEnabled: Boolean = Option(System.getenv("S3_FORCE_GLOBAL_BUCKET_ACCESS")).forall(_.toBoolean)

      val httpClient = getProxyConfiguration.fold(ApacheHttpClient.builder())(proxy => ApacheHttpClient.builder().proxyConfiguration(proxy))
      val tmp = S3Client.builder()
        .credentialsProvider(getCredentialsProvider(bucket))
        .httpClientBuilder(httpClient)
        .crossRegionAccessEnabled(forceGlobalBucketAccessEnabled)
        .forcePathStyle(pathStyleAccess)
        .region(signingRegion.getOrElse(getRegion(url, bucket)))
      endpointOverride.foreach(tmp.endpointOverride)
      client = tmp.build()

      amazonS3ClientCache.put(bucket, client)

      Message.info("S3URLHandler - Created S3 Client for bucket: "+bucket+" and region: "+signingRegion.getOrElse(getRegion(url, bucket)).id())
    }

    (client, bucket, key)
  }

  def getURLInfo(url: URL, timeout: Int): URLInfo = try {
    debug(s"getURLInfo($url, $timeout)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    
    val meta = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
    
    val available: Boolean = true
    val contentLength: Long = meta.contentLength()
    val lastModified: Long = meta.lastModified().toEpochMilli
    
    new S3URLInfo(available, contentLength, lastModified)
  } catch {
    case ex: S3Exception if ex.statusCode() == 404 => UNAVAILABLE
    case ex: java.net.URISyntaxException                  =>
      // We can hit this when given a URL that looks like:
      //   s3://maven.custom/releases/javax/ws/rs/javax.ws.rs-api/2.1/javax.ws.rs-api-2.1.${packaging.type}
      //
      // In that case we just ignore it and treat it as a 404.  It looks like this is really a bug in IVY that has
      // recently been fixed (as of 2018-03-12): https://issues.apache.org/jira/browse/IVY-1577
      //
      // Original Bug: https://github.com/frugalmechanic/fm-sbt-s3-resolver/issues/45
      // Original PR:  https://github.com/frugalmechanic/fm-sbt-s3-resolver/pull/46
      //
      Message.warn("S3URLHandler - " + ex.getMessage)

      UNAVAILABLE
  }
  
  def openStream(url: URL): InputStream = {
    debug(s"openStream($url)")
    
    val (client, bucket, key) = getClientBucketAndKey(url)
    client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
  }
  
  /**
   * A directory listing for keys/directories under this prefix
   */
  def list(url: URL): Seq[URL] = {
    debug(s"list($url)")
    
    val (client, bucket, key /* key is the prefix in this case */) = getClientBucketAndKey(url)
    
    // We want the prefix to have a trailing slash
    val prefix: String = key.stripSuffix("/") + "/"
    
    val request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).delimiter("/").build()
    val keys: Seq[String] = client.listObjectsV2Paginator(request).stream().iterator().asScala.flatMap { listing =>
      listing.commonPrefixes().asScala.map(_.prefix()) ++ listing.contents().asScala.map(_.key())
    }.toSeq
    
    val res: Seq[URL] = keys.map{ (k: String) =>
      new URL(url.toString.stripSuffix("/") + "/" + k.stripPrefix(prefix))
    }
    
    debug(s"list($url) => \n  "+res.mkString("\n  "))
    
    res
  }
  
  def download(src: URL, dest: File, l: CopyProgressListener): Unit = {
    debug(s"download($src, $dest)")
    
    val (client, bucket, key) = getClientBucketAndKey(src)
    
    val event: CopyProgressEvent = new CopyProgressEvent()
    if (null != l) l.start(event)
    
    val meta = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toFile(dest.toPath))
    dest.setLastModified(meta.lastModified().toEpochMilli)
    
    if (null != l) l.end(event) //l.progress(evt.update(EMPTY_BUFFER, 0, meta.getContentLength))
  }

  def upload(src: File, dest: URL, l: CopyProgressListener): Unit = {
    debug(s"upload($src, $dest)")

    val event: CopyProgressEvent = new CopyProgressEvent()
    if (null != l) l.start(event)

    val (client, bucket, key) = getClientBucketAndKey(dest)

    // Nested helper method for performing the actual PUT
    def putImpl(serverSideEncryption: Boolean): PutObjectResponse = {
      val request = PutObjectRequest.builder().bucket(bucket).key(key)
      if (serverSideEncryption) request.serverSideEncryption(ServerSideEncryption.AES256)
      bucketACLMap.get(bucket).foreach(request.acl)
      client.putObject(request.build(), RequestBody.fromFile(src.toPath))
    }

    // Do we know for sure that this bucket requires SSE?
    val requiresSSE: Boolean = bucketRequiresSSE.containsKey(bucket)

    if (requiresSSE) {
      // We know we require SSE
      putImpl(true)
    } else {
      try {
        // We either don't require SSE or don't know yet so we try without SSE enabled
        putImpl(false)
      } catch {
        case ex: S3Exception if ex.statusCode() == 403 =>
          debug(s"upload($src, $dest) failed with a 403 status code.  Retrying with Server Side Encryption Enabled.")

          // Retry with SSE
          val res: PutObjectResponse = putImpl(true)

          // If that succeeded then save the fact that we require SSE for future requests
          bucketRequiresSSE.put(bucket, true)

          Message.info(s"S3URLHandler - Enabled Server Side Encryption (SSE) for bucket: $bucket")

          res
      }
    }

    if (null != l) l.end(event)
  }

  // I don't think we care what this is set to
  def setRequestMethod(requestMethod: Int): Unit = debug(s"setRequestMethod($requestMethod)")
  
  // Try to get the region of the S3 URL so we can set it on the S3Client
  def getRegion(url: URL, bucket: String): Region = {
    getRegionNameFromURL(url).toOptionalRegion orElse
      getRegionNameFromDNS(bucket).toOptionalRegion orElse
      Try(new DefaultAwsRegionProviderChain().getRegion).toOption getOrElse
      Region.US_EAST_1
  }

  private implicit class RichStringOption(s: Option[String]) {
    def toOptionalRegion: Option[Region] = s.flatMap{ _.toOptionalRegion }
  }

  private implicit class RichString(s: String) {
    def toOptionalRegion: Option[Region] = Try{ Region.of(s) }.toOption
  }

  def getRegionNameFromURL(url: URL): Option[String] = {
    RegionMatcher.findFirstIn(url.toString)
  }

  def getBucketAndKey(url: URL): (String, String) = {
    val host = url.getHost
    val path = url.getPath.stripPrefix("/")
    val pathStyleHost = host == "s3.amazonaws.com" || host.matches("s3[.-][a-z0-9-]+\\.amazonaws\\.com")
    if (pathStyleHost) {
      val parts = path.split("/", 2)
      (parts.head, parts.lift(1).getOrElse(""))
    } else {
      val virtualHosted = "^(.+)\\.s3(?:[.-][a-z0-9-]+)?\\.amazonaws\\.com$".r
      host match {
        case virtualHosted(bucket) => (bucket, path)
        case _ => (host, path)
      }
    }
  }
}
