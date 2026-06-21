plugins {
  `kotlin-dsl`
  `jvm-test-suite`
}

repositories {
  mavenLocal()
  mavenCentral()
  gradlePluginPortal()
}

val javaVersion = libs.versions.java.get().toInt()

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(javaVersion))
  }
}

kotlin {
  jvmToolchain(javaVersion)
}

dependencies {
  implementation(libs.javapoet)
  implementation(libs.javaparser.core)
  implementation(libs.kotlin.stdlib)

  // Declare dependency on the shadow plugin so precompiled script plugins can reference it
  implementation(libs.plugins.shadow.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" })
}

testing {
  suites {
    getByName<JvmTestSuite>("test") {
      useJUnitJupiter(libs.versions.junit.get())
      dependencies {
        implementation(libs.compiler.testing)
      }
    }
  }
}
