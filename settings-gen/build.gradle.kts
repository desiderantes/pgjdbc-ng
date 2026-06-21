
plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.test.logger)
  `jvm-test-suite`
}


group = "com.impossibl.pgjdbc-ng.tools"
description = "PostgreSQL JDBC - NG - Settings Processor"


dependencies {
  implementation(libs.javapoet)
  implementation(libs.kotlin.stdlib)
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

kotlin {
  jvmToolchain(libs.versions.java.get().toInt())
}