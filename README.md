# SBT S3 Resolver

[![Build and Tests](https://github.com/MarmaladeSky/sbt-s3-resolver/actions/workflows/build.yml/badge.svg)](https://github.com/MarmaladeSky/sbt-s3-resolver/actions/workflows/build.yml) [![sbt-s3-resolver Scala version support](https://index.scala-lang.org/MarmaladeSky/sbt-s3-resolver/sbt-s3-resolver/latest-by-scala-version.svg?targetType=Sbt)](https://index.scala-lang.org/MarmaladeSky/sbt-s3-resolver/sbt-s3-resolver)

This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

> **This is a maintained fork** of [fm-sbt-s3-resolver](https://github.com/tpunder/fm-sbt-s3-resolver)
> by Tim Underwood, continuing from version `0.23.0`. It is published under new
> coordinates — `digital.junkie` % `sbt-s3-resolver` — since the original
> `com.frugalmechanic` groupId is no longer maintained. Version numbering continues
> from upstream so the lineage stays obvious.

## SBT 2.X Support

Starting with `1.0.0` this plugin targets **SBT 2.x only**. It is built against
Scala 3 and no longer cross-builds for SBT 0.13 or 1.x.

```scala
addSbtPlugin("digital.junkie" % "sbt-s3-resolver" % "1.0.0")
```

If you are still on SBT 1.x, use upstream
[fm-sbt-s3-resolver](https://github.com/tpunder/fm-sbt-s3-resolver) `0.23.0`.

## Examples

### Resolving Dependencies via S3

Maven Style:

```scala
resolvers += "FrugalMechanic Snapshots" at "s3://fm-sbt-s3-resolver-example-bucket/snapshots"
```

Ivy Style:

```scala
resolvers += Resolver.uri("FrugalMechanic Snapshots", uri("s3://fm-sbt-s3-resolver-example-bucket/snapshots"))(using Resolver.ivyStylePatterns)
```

### Publishing to S3

Maven Style:

```scala
publishMavenStyle := true
publishTo := Some("FrugalMechanic Snapshots" at "s3://fm-sbt-s3-resolver-example-bucket/snapshots")
```

Ivy Style:

```scala
publishMavenStyle := false
publishTo := Some(Resolver.uri("FrugalMechanic Snapshots", uri("s3://fm-sbt-s3-resolver-example-bucket/snapshots"))(using Resolver.ivyStylePatterns))
```

### Valid s3:// URL Formats

The examples above are using the [Static Website Using a Custom Domain](http://docs.aws.amazon.com/AmazonS3/latest/dev/website-hosting-custom-domain-walkthrough.html) functionality of S3.

These would also be equivalent (for the **fm-sbt-s3-resolver-example-bucket** bucket):

    s3://s3-us-west-2.amazonaws.com/fm-sbt-s3-resolver-example-bucket/snapshots
    s3://fm-sbt-s3-resolver-example-bucket.s3-us-west-2.amazonaws.com/snapshots
    s3://fm-sbt-s3-resolver-example-bucket.s3.amazonaws.com/snapshots
    s3://s3.amazonaws.com/fm-sbt-s3-resolver-example-bucket/snapshots

All of these forms should work:

    s3://[BUCKET]/[OPTIONAL_PATH]
    s3://s3.amazonaws.com/[BUCKET]/[OPTIONAL_PATH]
    s3://[BUCKET].s3.amazonaws.com/[OPTIONAL_PATH]
    s3://s3-[REGION].amazonaws.com/[BUCKET]/[OPTIONAL_PATH]
    s3://[BUCKET].s3-[REGION].amazonaws.com/[OPTIONAL_PATH]

## Usage

### Add this to your project/plugins.sbt file:

```scala
addSbtPlugin("digital.junkie" % "sbt-s3-resolver" % "1.0.0")
```

### S3 Credentials

S3 Credentials are checked **in the following places and _order_** (e.g. bucket specific settings (\~/.sbt/.&lt;bucket_name&gt;_s3credentials) get resolved before global settings (\~/.sbt/.s3credentials)):

The authoritative order is `S3URLHandler.defaultCredentialsProviderChain` in
[S3URLHandler.scala](https://github.com/MarmaladeSky/sbt-s3-resolver/blob/master/src/main/scala/fm/sbt/S3URLHandler.scala).

#### Bucket Specific Environment Variables

    AWS_ACCESS_KEY_ID_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_ACCESS_KEY_ID
    AWS_SECRET_KEY_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_SECRET_KEY
    
**NOTE** - The following transforms are applied to the bucket name before looking up the environment variable:

1. The name is upper-cased
2. Dots (.) and dashes (-) are replaced with an underscore (_)
3. Everything other than A-Z, 0-9, and underscores are removed.
  
Example:

The bucket name "fm-sbt-s3-resolver-example-bucket" becomes "FM\_SBT\_S3\_RESOLVER\_EXAMPLE\_BUCKET":

```shell
AWS_ACCESS_KEY_ID_FM_SBT_S3_RESOLVER_EXAMPLE_BUCKET="XXXXXX" AWS_SECRET_KEY_FM_SBT_S3_RESOLVER_EXAMPLE_BUCKET="XXXXXX" sbt
```

#### Bucket Specific Java System Properties

```shell
-Daws.accessKeyId.<bucket_name>=XXXXXX -Daws.secretKey.<bucket_name>=XXXXXX
-D<bucket_name>.aws.accessKeyId=XXXXXX -D<bucket_name>.aws.secretKey=XXXXXX
```
    
Example:

```shell
SBT_OPTS="-Daws.accessKeyId.fm-sbt-s3-resolver-example-bucket=XXXXXX -Daws.secretKey.fm-sbt-s3-resolver-example-bucket=XXXXXX" sbt
```

#### Bucket Specific Property Files

```shell
~/.sbt/.<bucket_name>_s3credentials
~/.sbt/.s3credentials_<bucket_name>
```

#### Environment Variables

    AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY)
    AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)
    AWS_ROLE_ARN

Example:

```shell
// Basic Credentials
AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" sbt

// IAM Role Credentials
AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" AWS_ROLE_ARN="arn:aws:iam::123456789012:role/RoleName" sbt
```

#### Java System Properties

    // Basic Credentials
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX 

    // IAM Role
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.roleArn=arn:aws:iam::123456789012:role/RoleName


Example:
 
```shell
// Basic Credentials
SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX" sbt

// IAM Role Credentials
SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.roleArn=arn:aws:iam::123456789012:role/RoleName" sbt
```

#### Property File

```shell  
~/.sbt/.s3credentials
```
    
The property files should have the following format:
  
```ini
accessKey = XXXXXXXXXX
secretKey = XXXXXXXXXX
// Optional IAM Role
roleArn = arn:aws:iam::123456789012:role/RoleName
```

### Custom S3 Credentials

If the default credential providers do not work for you then you can specify your own AWSCredentialsProvider using the `s3CredentialsProvider` SettingKey in your `build.sbt` file:

```scala
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider

s3CredentialsProvider := { (bucket: String) =>
  new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("my_profile"),
    DefaultAWSCredentialsProviderChain.getInstance()
  )
}
```

If you are really lazy and want to provide static credentials using this in your `build.sbt` file will work:

```scala
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}

s3CredentialsProvider := { (bucket: String) =>
  new AWSStaticCredentialsProvider(new BasicAWSCredentials("your_accessKey", "your_secretKey"))
}
```

## IAM Policy Examples

I recommend that you create IAM Credentials for reading/writing your Maven S3 Bucket.
This is the read/write policy needed for publishing to our **fm-sbt-s3-resolver-example-bucket** bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::fm-sbt-s3-resolver-example-bucket"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:DeleteObject","s3:GetObject","s3:PutObject"],
      "Resource": ["arn:aws:s3:::fm-sbt-s3-resolver-example-bucket/*"]
    }
  ]
}
```

For a **read-only** policy, drop `s3:DeleteObject` and `s3:PutObject` from the last statement.

To keep **releases read-only and snapshots read/write**, split that last statement in two and
scope each by prefix: `.../releases/*` with `["s3:GetObject"]`, and `.../snapshots/*` with the
full `["s3:DeleteObject","s3:GetObject","s3:PutObject"]`.

## IAM Role Policy Examples

This is a simple example where a Host AWS Account, can create a Role with permissions for a Client AWS Account to access the Host maven bucket.

  1. Host AWS Account, creates an IAM Role named "ClientAccessRole" with policy:
<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::<b>[Client AWS Account Id]</b>:user/<b>[Client User Name]</b>"
        },
        "Action": "sts:AssumeRole"
    }
  ]
}
</pre>
  2. Associate the proper [IAM Policy Examples](#iam-policy-examples) to the Host Role
  3. Client AWS Account needs to create an AWS IAM User [Client User Name] and associated a policy to gives it permissions to AssumeRole from the Host AWS Account:
<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Resource": "arn:aws:iam::<b>[Host AWS Account Id]</b>:role/ClientAccessRole"
    }
  ]
}
</pre>

## S3 Server-Side Encryption
S3 supports <a href="http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingServerSideEncryption.html">server side encryption</a>.
The plugin will automatically detect if it needs to ask S3 to use SSE, based on the policies you have on your bucket. If
your bucket denies `PutObject` requests that aren't using SSE, the plugin will include the SSE header in future requests.

To make use of SSE, configure your bucket to enforce the SSE header for `PutObject` requests.

Example:
<pre>
{
  "Version": "2012-10-17",
  "Id": "PutObjPolicy",
  "Statement": [
    {
      "Sid": "DenyIncorrectEncryptionHeader",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<b>YOUR_BUCKET_HERE</b>/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption": "AES256"
        }
      }
    },
    {
      "Sid": "DenyUnEncryptedObjectUploads",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<b>YOUR_BUCKET_HERE</b>/*",
      "Condition": {
        "Null": {
          "s3:x-amz-server-side-encryption": "true"
        }
      }
    }
  ]
}
</pre>

## Maintainer

David Akermann (<a href="https://github.com/MarmaladeSky" rel="author">GitHub</a>) &lt;david@junkie.digital&gt;

## Original Author

Tim Underwood (<a href="https://github.com/tpunder" rel="author">GitHub</a>, <a href="https://www.linkedin.com/in/tpunder" rel="author">LinkedIn</a>, <a href="https://twitter.com/tpunder" rel="author">Twitter</a>), who wrote and maintained this plugin as
[fm-sbt-s3-resolver](https://github.com/tpunder/fm-sbt-s3-resolver) through version `0.23.0`.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
