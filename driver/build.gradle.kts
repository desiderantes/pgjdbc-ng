import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.*
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  id("pgjdbc.compile-java")
  id("pgjdbc.packaging")
  id("pgjdbc.publishing")
  alias(libs.plugins.test.logger)
  id("com.gradleup.shadow")
  alias(libs.plugins.docker.compose)
  checkstyle
}

description = "PostgreSQL JDBC - NG - Driver"


dependencies {

  annotationProcessor(project(":settings-gen"))

  implementation(project(":spy"))
  implementation(libs.netty.common)
  implementation(libs.netty.buffer)
  implementation(libs.netty.transport)
  implementation(libs.netty.codec)
  implementation(libs.netty.handler)
  implementation(libs.netty.transport.native.unix.common)
  implementation(libs.netty.transport.native.kqueue)
  implementation(libs.netty.transport.native.epoll)

  checkstyle(libs.checkstyle)

  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.junit.vintage.engine)
  testImplementation(libs.junit.classic)
  testImplementation(libs.guava)
  testRuntimeOnly(libs.junit.platform.launcher)

}




tasks {
  compileJava {
    outputs.dir(layout.buildDirectory.dir("generated/docs"))
  }
  processResources {
    inputs.property("name", project.name)
    inputs.property("version", project.version)
    expand(mapOf(
       "name" to project.name,
       "version" to project.version
    ))
  }
}

// Inlined from src/build/testing.gradle.kts

val defaultPostgresVersions = "13, 12, 11, 10, 9.6, 9.5"

val testTask = tasks.named<Test>("test") {
  onlyIf {
    project.findProperty("noDocker")?.toString()?.toBoolean() != true
  }
  useJUnitPlatform()
  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
  }
  exclude(
     "**/RequiredTests.*",
     "**/DateTimeTests.*",
     "**/PerformanceTest.*",
     "**/GiantBlobTest.*",
     "**/ServerDisconnectTest.*"
  )
}

if (project.findProperty("noDocker")?.toString()?.toBoolean() != true) {

  val pgVersions = ((project.findProperty("postgresVersions") ?: defaultPostgresVersions) as String)
     .split(',')
     .map { it.trim() }

  val downAllTask = tasks.register("composeDownAll")

  val testAllTask = tasks.register("testAllPostgresVersions") {
    group = "verification"
    description = "Runs the unit tests against Postgres versions $pgVersions"
  }

  val allowSSL = checkServerKeyPermissions()
  val serviceName = if (allowSSL) "postgres" else "postgres-nossl"

  for ((index, pgVersion) in pgVersions.withIndex()) {

    val pgVersionSafe = pgVersion.replace('.', '_')

    val curTestTask =
       if (index > 0) {

         tasks.register<Test>("testPostgres$pgVersion") {
           group = "verification"
           useJUnitPlatform()
           exclude(testTask.get().excludes)
         }

       }
       else
         testTask

    curTestTask.configure {
      description = "Runs the unit tests against Postgres $pgVersion"
    }

    testAllTask.configure {
      dependsOn(curTestTask)
    }

    val composeProjectName = "driver_test_$pgVersionSafe"

    dockerCompose {

      val compose = createNested("postgres$pgVersion")
      compose.useComposeFiles = listOf("src/test/docker/postgres-services.yml")
      compose.startedServices = listOf(serviceName)
      compose.environment.put("PG_VERSION", pgVersion)
      compose.captureContainersOutputToFiles = layout.buildDirectory.dir("test/$pgVersion/containers").get().asFile
      compose.composeLogToFile = layout.buildDirectory.file("test/$pgVersion/compose.log").get().asFile
      compose.setProjectName(composeProjectName)
      compose.isRequiredBy(curTestTask.get())

      curTestTask.configure {
        doFirst {
          val pgInfo = compose.servicesInfos[serviceName]!!.firstContainer
          systemProperty("pgjbdc.test.server", pgInfo.host)
          systemProperty("pgjdbc.test.port", pgInfo.ports[5432]!!)
        }
      }

    }

    downAllTask.configure {
      dependsOn(tasks["postgres${pgVersion}ComposeDown"])
    }

    tasks.named("postgres${pgVersion}ComposeUp") {
      doLast {
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE testnoexts OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION hstore; CREATE EXTENSION citext;", "test")

        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE hostdb OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION sslinfo;", "hostdb")

        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE hostssldb OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION sslinfo;", "hostssldb")

        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE hostnossldb OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION sslinfo;", "hostnossldb")

        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE hostsslcertdb OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION sslinfo;", "hostsslcertdb")

        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE DATABASE certdb OWNER test;", "test")
        execPSQL(pgVersion, serviceName, composeProjectName, "CREATE EXTENSION sslinfo;", "certdb")
      }
    }

  }

  // Force update of task description (because we
  // "configured" a plugin created task)
  testTask.get()

}


/**
 * PostgreSQL requires that a server.key has specific permissions or it will fail to start. This causes
 * the docker container to fail with no relevant error.
 *
 * This function checks for the required permissions on the file and prints a relevant warning if the
 * permission are not correct.
 *
 * @returns true if required permissions are set, false otherwise.
 */
fun checkServerKeyPermissions(): Boolean {
  val invalidPerms = setOf(OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE, GROUP_WRITE, GROUP_EXECUTE)
  val serverKey = "src/test/resources/certdir/server/server.key"
  try {
    val serverKeyPath = projectDir.toPath().resolve(serverKey)
    val owner = Files.getAttribute(serverKeyPath, "posix:owner").toString()
    val ownerUid = Files.getAttribute(serverKeyPath, "unix:uid").toString()
    val perms = Files.getPosixFilePermissions(serverKeyPath)
    if ((ownerUid != "70" && owner != System.getProperty("user.name")) || perms.intersect(invalidPerms).isNotEmpty()) {
      project.logger.warn(
         "DISABLING SSL, $serverKey has invalid owner or permissions to execute PostgreSQL with SSL. " +
            "Make sure it's owned by the user executing Gradle (if root) or set to UID=70, and its permissions are set to 0600"
      )
      return false
    }
    return true
  }
  catch(x: IOException) {
    project.logger.warn(
       "DISABLING SSL, unable to determine required owner/permissions for $serverKey"
    )
    return false
  }
}

fun execPSQL(pgVersion: String, serviceName: String, projectName: String, cmd: String, db: String) {
  project.providers.exec {
    executable = "docker-compose"
    args = listOf(
       "-f", "$projectDir/src/test/docker/postgres-services.yml", "-p", projectName,
       "exec", "-T", serviceName,
       "psql", "-c", cmd, "-U", "test", "-d", db
    )
    standardOutput = ByteArrayOutputStream()
    environment(mapOf("PG_VERSION" to pgVersion))
  }.result.get()
}

val mainSourceSet = the<SourceSetContainer>()["main"]!!

// UBER JAR
val jarTask = tasks.named<Jar>("jar")
tasks.register<ShadowJar>("uberJar") {
  archiveAppendix.set("all")
  manifest {
    from(jarTask.get().manifest)
  }
  from(mainSourceSet.output)
  configurations = listOf(project.configurations["runtimeClasspath"])
  relocate("io.netty", "com.impossibl.shadow.io.netty")
  minimize()
}

tasks.named<ProcessResources>("processTestResources") {
  exclude("**/server/*.*")
}

configurations {
  create("docs")
}

val docsTask = tasks.register<Tar>("docs") {
  compression = Compression.GZIP
  archiveClassifier.set("docs")
  from(layout.buildDirectory.dir("generated/docs"))
  dependsOn(tasks.named("classes"))
}

artifacts {
  add("docs", docsTask)
}

tasks.named<JavaCompile>("compileJava") {
  options.compilerArgs.add("-Adoc.dir=${layout.buildDirectory.dir("generated/docs").get().asFile.absolutePath}/")
  options.isDeprecation = true
  options.generatedSourceOutputDirectory.set(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main"))
}

tasks.named<JavaCompile>("compileTestJava") {
  options.compilerArgs.add("-parameters")
  options.isDeprecation = true
}

checkstyle {
  val configDir = "$rootDir/config/checkstyle"
  configFile = file("$configDir/checkstyle.xml")
  configProperties = mapOf("configDir" to configDir)
}

tasks.named<Checkstyle>("checkstyleMain") { exclude("**/guava/**") }
tasks.named<Checkstyle>("checkstyleTest") { exclude("**/jdbc/shared/**") }