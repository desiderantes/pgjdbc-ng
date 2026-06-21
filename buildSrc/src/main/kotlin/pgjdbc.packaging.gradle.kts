plugins {
  java
}

java {
  withSourcesJar()
  withJavadocJar()
}

// Make sourcesJar depend on classes to ensure generated sources are created first
tasks.named("sourcesJar") {
  dependsOn(tasks.named("classes"))
}

/**
 * STD JAR
 */

tasks.named<Jar>("jar") {

  // Prefix "sub" modules with project name
  if (archiveBaseName.orNull != "pgjdbc-ng") {
    archiveBaseName.set("pgjdbc-ng-${archiveBaseName.get()}")
  }

  manifest {
    attributes(
       "Implementation-Title" to (project.description ?: ""),
       "Implementation-Version" to project.version.toString(),
       "Implementation-Vendor-Id" to project.group.toString(),
       "Implementation-Vendor" to ProjectInfo.Organization.NAME,
       "Implementation-URL" to ProjectInfo.URL,
       "Created-By" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
    )
  }
}



/**
 * JAVADOC
 */

val libs = the<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("java").get().requiredVersion

tasks.named<Javadoc>("javadoc") {
  options {
    title = "${project.name} $version"
    encoding = "UTF-8"
    (this as StandardJavadocDocletOptions).apply {
      addBooleanOption("Xdoclint:none", true)
      if (JavaVersion.current().isJava9Compatible) {
        addBooleanOption("html5", true)
      }
      source(javaVersion)
      links("https://docs.oracle.com/en/java/javase/$javaVersion/docs/api/")
      use(true)
      noTimestamp(true)
    }
  }
  isFailOnError = false
}
