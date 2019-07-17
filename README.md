# Akka HTTP + GraalVM native
Example project with simple Akka HTTP server compiled with GraalVM native-image.

## Pre-requisites
  * SBT
  * [GraalVM](https://github.com/oracle/graal/releases)
  * `native-image` from `GRAAL_HOME/bin` in `PATH`
  
Note that the following example refers to *Graal VM version 19.0.2*. If you use a different version you should update also *com.oracle.substratevm* dependency version in `build.sbt`.
  
Suggested environment variables:

    export GRAAL_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-19.0.2/Contents/Home
    export PATH=$PATH:${GRAAL_HOME}/bin
    
[Install native-image](https://www.graalvm.org/docs/reference-manual/aot-compilation/#install-native-image):

    gu install native-image
  
## Compiling
    
    sbt graalvm-native-image:packageBin
    
It might take a few minutes to compile.
   
## Running
    
    ./target/graalvm-native-image/akka-graal-native -Djava.library.path=${GRAAL_HOME}/jre/lib/amd64
    
Because the project is compiled with
[Java Crypto enabled](https://github.com/oracle/graal/blob/master/substratevm/JCA-SECURITY-SERVICES.md)
for the native image (to support HTTPS) `java.library.path` system property must be set at runtime
to point to a directory where the dynamic library for the SunEC provider is located.

After the server starts you can access [http://localhost:8086/graal-hp-size](http://localhost:8086/graal-hp-size)
which will make an HTTPS request to the GraalVM home page using Akka HTTP client and return the size of the response.

## How it works
Most of the Akka-specific configuration for `native-image` is provided by [akka-graal-config](https://github.com/vmencik/akka-graal-config)
repository which publishes a set of jar artifacts that contain the necessary configuration resources
for `native-image` to compile Akka modules. Just having these jars in the classpath is enough
for `native-image` to pick up this configuration.
See [this blog post](https://medium.com/graalvm/simplifying-native-image-generation-with-maven-plugin-and-embeddable-configuration-d5b283b92f57)
for more details on how that mechanism works.

### Reflection configuration
See [SubstrateVM docs](https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md)
for details.

Configuration for Akka itself is provided by [akka-graal-config](https://github.com/vmencik/akka-graal-config)
dependencies. This repo contains only reflection configuration to get java.util.logging working.

Note however that reflective access to `context` and `self` fields must be configured for every actor
that is monitored with `context.watch` (observed empirically).
Otherwise you'll get [an error from Akka's machinery](https://github.com/akka/akka/blob/v2.5.21/akka-actor/src/main/scala/akka/actor/ActorCell.scala#L711).

### HTTPS
Passing the `--enable-url-protocols=https` option to `native-image` enables JCE features.
This configuration option is enabled by configuration from `graal-akka-http` dependency.

### Affinity Pool
`akka.dispatch.affinity.AffinityPool` is using MethodHandles which causes errors like this one
during native image build:

    Error: com.oracle.graal.pointsto.constraints.UnsupportedFeatureException: Invoke with MethodHandle argument could not be reduced to at most a single call: java.lang.invoke.MethodHandle.bindTo(Object)
    
The workaround is to initialize affected classes (and we actually do this for the whole classpath)
at build time using the `--initalize-at-build-time` option.

### Lightbend Config
Static initializers of `com.typesafe.config.impl.ConfigImpl$EnvVariablesHolder`
and `com.typesafe.config.impl.ConfigImpl$SystemPropertiesHolder` need to be run at runtime using
the `--initialize-at-run-time` option.
Otherwise the environment from image build time will be baked in to the configuration.

### Akka Scheduler and sun.misc.Unsafe
To make the default Akka scheduler work with SubstrateVM it is necessary to recalculate the field
offset that it uses with sun.misc.Unsafe.
This is done by using SubstrateVM API in `AkkaSubstitutions` class from `graal-akka-actor` dependency.

For more details see the section about Unsafe in this [blog post](https://medium.com/graalvm/instant-netty-startup-using-graalvm-native-image-generation-ed6f14ff7692).

Note that this substitution is only necessary with Scala 2.12. Curiously with Scala 2.13 `native-image`
can make the substitution itself automatically. Probably due to some difference in the emitted
bytecode.

### MethodHandle in scala.runtime.Statics
In Scala 2.13 a MethodHandle is used in `Statics.releaseFence()` to invoke either
`java.lang.invoke.VarHandle.releaseFence()` if running in Java 9 VM or `sun.misc.Unsafe.storeFence()`
if on Java 8. As noted above, MethodHandles are a problem with `native-image` but since GraalVM is
currently based on Java 8 `Statics.releaseFence()` can be substituted to always call Unsafe without
using MethodHandle. This is done by `ScalaSubstitutions` present in Scala 2.13 version of
`graal-akka-actor`.

### Serialization
SubstrateVM does not support Java serialization yet so anything that depends on
`akka.serialization.JavaSerializer` will not work with `native-image`.

### Logging
It is currently not easy to get Logback working because of its Groovy dependencies and incomplete
classpath problems with `native-image` so `java.util.logging` is used instead.
