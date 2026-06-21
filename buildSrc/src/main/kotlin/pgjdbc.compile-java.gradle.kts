plugins {
  java
}

val libs = the<VersionCatalogsExtension>().named("libs")
val javaVersionStr = libs.findVersion("java").get().requiredVersion
val javaVersion = JavaVersion.toVersion(javaVersionStr)

java {
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
  withSourcesJar()
  withJavadocJar()
}

val javaToolchains = extensions.getByName("javaToolchains") as JavaToolchainService

tasks.withType<JavaCompile>().configureEach {
  javaCompiler.set(javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(javaVersionStr))
  })
}

tasks.withType<Test>().configureEach {
  javaLauncher.set(javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(javaVersionStr))
  })
  systemProperty("user.timezone", "America/Los_Angeles")
}
