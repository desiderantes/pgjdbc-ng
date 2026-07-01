import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  alias(libs.plugins.kotlin.jvm)
  id("pgjdbc.compile-java")
  id("pgjdbc.packaging")
  id("pgjdbc.publishing")
  alias(libs.plugins.test.logger)
  id("com.gradleup.shadow")
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

testing {
  suites {
    getByName<JvmTestSuite>("test") {
      useJUnitJupiter(libs.versions.junit.get())
      dependencies {
        implementation(libs.compiler.testing)
        implementation(libs.testcontainers.junit.jupiter)
        runtimeOnly(libs.junit.platform.launcher)
      }

      targets {
        all {
          testTask.configure {
            testLogging {
              exceptionFormat = TestExceptionFormat.FULL
            }

            val pgVersions = (project.findProperty("postgresVersions") as String)
              .split(',')
              .map { it.trim() }
            environment("POSTGRES_VERSIONS", pgVersions.joinToString(","))
            systemProperty("postgresVersions", pgVersions.joinToString(","))
          }
        }
      }
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

