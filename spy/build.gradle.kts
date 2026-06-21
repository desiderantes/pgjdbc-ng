import com.impossibl.jdbc.spy.tools.SpyGen

plugins {
  `java-library`
  id("pgjdbc.compile-java")
  id("pgjdbc.packaging")
  id("pgjdbc.publishing")
  alias(libs.plugins.test.logger)
}

description = "PostgreSQL JDBC - NG - API Spy"


dependencies {
  testImplementation(libs.junit.jupiter.engine)
}

val genDir = layout.buildDirectory.dir("generated-spy").get().asFile

sourceSets {
  main {
    java.srcDirs(genDir)
  }
}

tasks {

  val genTask = register("generator") {
    description = "Generate SPY relay, listener & trace classes"

    outputs.dir(genDir)

    doLast {
      genDir.mkdirs()
      SpyGen().generateTo(genDir)
    }
  }

  compileJava {
    dependsOn(genTask)
    options.isDeprecation = true
  }

  javadoc {
    dependsOn(genTask)
  }

}

