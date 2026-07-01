import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.kotlin.dsl.main
import org.gradle.kotlin.dsl.sourceSets

plugins {
  `java-library`
  id("pgjdbc.compile-java")
  id("pgjdbc.packaging")
  id("pgjdbc.publishing")
  alias(libs.plugins.test.logger)
  id("com.gradleup.shadow")
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
}

testing {
  suites {
    getByName<JvmTestSuite>("test") {
      useJUnitJupiter(libs.versions.junit.get())

      dependencies {
        implementation(libs.junit.jupiter.engine)
        implementation(libs.junit.jupiter.params)
        implementation(libs.guava)
        runtimeOnly(libs.junit.platform.launcher)
      }

      targets {
        all {
          testTask.configure {
            testLogging {
              exceptionFormat = TestExceptionFormat.FULL
            }
          }
        }
      }
    }

    register<JvmTestSuite>("integrationTest") {
      useJUnitJupiter(libs.versions.junit.get())

      dependencies {
        implementation(project())
        implementation(libs.junit.jupiter.engine)
        implementation(libs.junit.jupiter.params)
        implementation(libs.junit.platform.suite.api)
        implementation(libs.guava)
        implementation(libs.testcontainers.junit.jupiter)
        implementation(libs.testcontainers.jdbc)
        runtimeOnly(libs.junit.platform.launcher)
      }

      targets {
        all {
          testTask.configure {
            onlyIf {
              project.findProperty("noDocker")?.toString()?.toBoolean() != true
            }
            testLogging {
              exceptionFormat = TestExceptionFormat.FULL
            }
            val pgVersions = (project.findProperty("postgresVersions") as String)
               .split(',')
               .map { it.trim() }
            environment("POSTGRES_VERSIONS", pgVersions.joinToString(","))
            systemProperty("postgresVersions", pgVersions.joinToString(","))
            systemProperty("user.timezone", "America/Los_Angeles")
            exclude(
               "**/RequiredTests.*",
               "**/DateTimeTests.*",
               "**/PerformanceTest.*",
               "**/GiantBlobTest.*",
               "**/ServerDisconnectTest.*"
            )
          }
        }
      }
    }
  }
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

// UBER JAR
val jarTask = tasks.named<Jar>("jar")
tasks.register<ShadowJar>("uberJar") {
  archiveAppendix.set("all")
  manifest {
    from(jarTask.get().manifest)
  }
  from(sourceSets.main.get().output)
  configurations = listOf(project.configurations["runtimeClasspath"])
  relocate("io.netty", "com.impossibl.shadow.io.netty")
  minimize()
}

tasks.named<ProcessResources>("processTestResources") {
  exclude("**/server/*.*")
}

tasks.named<ProcessResources>("processIntegrationTestResources") {
  exclude("**/server/*.*")
}

configurations {
  create("docs")
  getByName("integrationTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
  }
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
tasks.named<Checkstyle>("checkstyleIntegrationTest") { exclude("**/jdbc/shared/**") }