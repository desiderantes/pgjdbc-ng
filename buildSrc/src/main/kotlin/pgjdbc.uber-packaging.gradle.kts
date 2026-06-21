import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("pgjdbc.packaging")
  id("com.gradleup.shadow")
}

/**
 * UBER JAR
 */

val jar by tasks.existing(Jar::class)
val mainSourceSet = the<SourceSetContainer>()["main"]!!

tasks.register<ShadowJar>("uberJar") {
  archiveAppendix.set("all")
  manifest {
    from(jar.get().manifest)
  }
  from(mainSourceSet.output)
  configurations = listOf(project.configurations["runtimeClasspath"])
}
