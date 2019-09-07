name := "akka-graal-native"

organization := "com.github.vmencik"

version := "0.1"

scalaVersion := "2.13.0"

val akkaVersion = "2.5.25"
val akkaHttpVersion = "10.1.8"
val graalAkkaVersion = "0.5.0"

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.slf4j" % "slf4j-jdk14" % "1.7.26", // java.util.logging works mostly out-of-the-box with SubstrateVM

  "com.github.vmencik" %% "graal-akka-http" % graalAkkaVersion,
  "com.github.vmencik" %% "graal-akka-slf4j" % graalAkkaVersion

)

enablePlugins(GraalVMNativeImagePlugin)

graalVMNativeImageOptions ++= Seq(
  "-H:IncludeResources=.*\\.properties",
  "-H:ReflectionConfigurationFiles=" + baseDirectory.value / "graal" / "reflectconf-jul.json",
  "--initialize-at-build-time",
  "--initialize-at-run-time=" +
    "akka.protobuf.DescriptorProtos," +
    "com.typesafe.config.impl.ConfigImpl$EnvVariablesHolder," +
    "com.typesafe.config.impl.ConfigImpl$SystemPropertiesHolder",
  "--no-fallback",
  "--allow-incomplete-classpath"
)
