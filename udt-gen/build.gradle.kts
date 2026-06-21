import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import com.avast.gradle.dockercompose.ComposeExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  alias(libs.plugins.kotlin.jvm)
  id("pgjdbc.compile-java")
  id("pgjdbc.packaging")
  id("pgjdbc.publishing")
  alias(libs.plugins.test.logger)
  id("com.gradleup.shadow")
  alias(libs.plugins.docker.compose)
  `jvm-test-suite`
}


group = "com.impossibl.pgjdbc-ng.tools"
description = "PostgreSQL JDBC - NG - UDT Generator"


dependencies {

  implementation(project(":pgjdbc-ng"))
  implementation(libs.kotlin.argparser)
  implementation(libs.javapoet)
  implementation(libs.kotlin.stdlib)

}

kotlin {
  jvmToolchain(libs.versions.java.get().toInt())
}



// Inlined from src/build/testing.gradle.kts

val defaultPostgresVersion = "11"

val pgVersion = (project.findProperty("postgresVersions") as? String ?: defaultPostgresVersion)
   .split(',')
   .map { it.trim() }
   .first()

testing {
  suites {
    getByName<JvmTestSuite>("test") {
      useJUnitJupiter(libs.versions.junit.get())
      dependencies {
        implementation(libs.compiler.testing)
        runtimeOnly(libs.junit.platform.launcher)
      }
    }
  }
}

val testTask = tasks.named<Test>("test") {
  onlyIf {
    project.findProperty("noDocker")?.toString()?.toBoolean() != true
  }
  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
  }
}

if (project.findProperty("noDocker")?.toString()?.toBoolean() != true) {

  val compose = project.extensions.findByType<ComposeExtension>()?.also { compose ->
    compose.useComposeFiles = listOf("src/test/docker/postgres-services.yml")
    compose.startedServices = listOf("postgres")
    compose.environment.put("PG_VERSION", pgVersion)
    compose.captureContainersOutputToFiles = layout.buildDirectory.dir("test/containers").get().asFile
    compose.composeLogToFile = layout.buildDirectory.file("test/compose.log").get().asFile
    compose.setProjectName("udt-test")
    compose.isRequiredBy(testTask.get())
  }

  testTask.configure {
    description = "Runs the unit tests against PostgreSQL $pgVersion"
    doFirst {
      val pgInfo = compose?.servicesInfos["postgres"]!!.firstContainer
      systemProperty("pgjbdc.test.server", pgInfo.host)
      systemProperty("pgjdbc.test.port", pgInfo.ports[5432]!!)
    }
  }

}


// UBER JAR
val jarTask = tasks.named<Jar>("jar")
tasks.register<ShadowJar>("uberJar") {
  description = ""
    archiveAppendix.set("all")
  manifest {
    from(jarTask.get().manifest)
  }
  from(sourceSets.main)
  configurations = listOf(project.configurations["runtimeClasspath"])
  relocate("com.xenomachina", "com.impossibl.shadow.com.xenomachina")
  relocate("com.squareup", "com.impossibl.shadow.com.squareup")
  relocate("org.jetbrains.kotlin", "com.impossibl.shadow.org.jetbrains.kotlin")
  minimize()
  manifest {
    from()
    attributes(mapOf(
       "Main-Class" to "com.impossibl.postgres.tools.UDTGenerator"
    ))
  }
}

