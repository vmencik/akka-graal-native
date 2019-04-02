# Akka HTTP + GraalVM native
Example project with simple Akka HTTP server compiled with GraalVM native-image.

## Pre-requisites
  * SBT
  * [GraalVM](https://github.com/oracle/graal/releases)
  * `native-image` from `GRAAL_HOME/bin` in `PATH`
  
Note that the following example refers to *Graal VM version 1.0.0-rc14*. If you use a different version you should update also *com.oracle.substratevm* dependancy version in `build.sbt`.
  
Suggested environment variables:

    export GRAAL_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-1.0.0-rc14/Contents/Home
    export PATH=$PATH:${GRAAL_HOME}/bin
  
## Compiling
    
    sbt graalvm-native-image:packageBin
    
It might take a few minutes to compile.
   
## Running
    
    ./target/graalvm-native-image/akka-graal-native -Djava.library.path=${GRAAL_HOME}/jre/lib
    
Because the project is compiled with
[Java Crypto enabled](https://github.com/oracle/graal/blob/master/substratevm/JCA-SECURITY-SERVICES.md)
for the native image (to support HTTPS) `java.library.path` system property must be set at runtime
to point to a directory where the dynamic library for the SunEC provider is located.

After the server starts you can access http://localhost:8086/graal-hp-size which will make an HTTP
request to the GraalVM home page using Akka HTTP client and return the size of the response.

## How it works

### Reflection configuration
See [SubstrateVM docs](https://github.com/oracle/graal/blob/master/substratevm/REFLECTION.md)
for details.

The `reflectconf-akka.json` configuration file is based on [previous work by jrudolph](https://github.com/jrudolph/akka-graal)
with some modifications.

Note that reflective access to `context` and `self` fields must be configured for every actor
that is monitored with `context.watch` (observed empirically).
Otherwise you'll get [an error from Akka's machinery](https://github.com/akka/akka/blob/v2.5.21/akka-actor/src/main/scala/akka/actor/ActorCell.scala#L711).

### HTTPS
Passing the `--enable-url-protocols=https` option to `native-image` enables JCE features.

### Lightbend Config
To ensure that environment variables and Java system properties defined at *runtime* are used
to resolve the configuration static initializers of `com.typesafe.config.impl.ConfigImpl$EnvVariablesHolder`
and `com.typesafe.config.impl.ConfigImpl$SystemPropertiesHolder` need to be re-run at runtime.
Otherwise the environment from image build time will be baked in to the configuration.

### Akka Scheduler and sun.misc.Unsafe
To make the default Akka scheduler work with SubstrateVM it is necessary to recalculate the field
offset that it uses with sun.misc.Unsafe.
This is done by using SubstrateVM API in `AkkaSubstitutions.java`.

For more details see the section about Unsafe in this [blog post](https://medium.com/graalvm/instant-netty-startup-using-graalvm-native-image-generation-ed6f14ff7692).

### Serialization
The Serialization extension is disabled in `application.conf`. It is not required with just local
actors and SubstrateVM does not support Java serialization yet.

### Logging
It is currently not easy to get Logback working because of its Groovy dependencies and incomplete
classpath problems with `native-image` so `java.util.logging` is used instead.
