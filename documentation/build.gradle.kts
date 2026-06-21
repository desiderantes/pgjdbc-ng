plugins {
  alias(libs.plugins.asciidoctor.convert)
  alias(libs.plugins.git.publish)
}

val isSnapshot = project.version.toString().endsWith("SNAPSHOT")

val docsRepoUri = (project.findProperty("docsRepoUri") ?: "git@github.com:impossibl/pgjdbc-ng.git").toString()

val javadocs: Configuration = configurations.create("javadocs")
val docs: Configuration = configurations.create("docs")

dependencies {

  docs(project(":pgjdbc-ng", "docs"))
  javadocs(project(":pgjdbc-ng"))
}

tasks {


  val aggregateJavadocs = register<Javadoc>("aggregateJavadocs") {
    setDestinationDir(layout.buildDirectory.dir("javadoc").get().asFile)
    val javaVersion = libs.versions.java.get()
    options {
      title = "PGJDBC-NG $version"
      encoding = "UTF-8"
      (this as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:none", true)
        addBooleanOption("html5", true)
        source(javaVersion)
        links("https://docs.oracle.com/en/java/javase/$javaVersion/docs/api/")
        use(true)
        noTimestamp(true)
      }
    }

    rootProject.subprojects.filter { project != it }.forEach { otherProject ->

      otherProject.tasks.withType(Javadoc::class.java).forEach { task ->
        dependsOn(task)
        source += task.source
        classpath += task.classpath
        excludes += task.excludes
        includes += task.includes
      }
    }
  }



  val collectDocs = register<Sync>("collectDocs") {
    from(docs)
    from(docs.map { tarTree(it) })
    into(layout.buildDirectory.dir("tmp/docs"))
  }

  asciidoctorj {
    setVersion(libs.versions.asciidoctorJ.get())
  }

  asciidoctor {

    val docsDir = "$projectDir/src/docs/asciidoc"
    val examplesDir = "$projectDir/src/docs/examples"
    val includeDocsDir = layout.buildDirectory.dir("tmp/docs").get().asFile.absolutePath
    val docinfosDir = "$docsDir/docinfos"

    setSourceDir(file(docsDir))
    sources(delegateClosureOf<PatternSet> {
      include("**/index.adoc")
    })

    setOutputDir(layout.buildDirectory.dir("docs/html5").get().asFile)
    outputOptions {
      backends("html5")
    }

    baseDirFollowsSourceFile()

    inputs.dir(examplesDir)
    inputs.dir(includeDocsDir)
    inputs.dir(docinfosDir)

    val driverPrj = rootProject.project(":pgjdbc-ng")
    val spyPrj = rootProject.project(":spy")
    val udtPrj = rootProject.project(":udt-gen")

    attributes(mapOf(
       "toc" to "left",
       "incdir" to includeDocsDir,
       "exdir" to examplesDir,
       "docinfodir" to docinfosDir,
       "icons" to "font",
       "revnumber" to version,
       "driverdepgroup" to driverPrj.group.toString(),
       "driverdepname" to driverPrj.name,
       "driverdepver" to driverPrj.version,
       "spydepgroup" to spyPrj.group.toString(),
       "spydepname" to spyPrj.name,
       "spydepver" to spyPrj.version,
       "udtdepgroup" to udtPrj.group.toString(),
       "udtdepname" to udtPrj.name,
       "udtdepver" to udtPrj.version,
       "ubersuffix" to "all",
       "mavenrepo" to if (isSnapshot) "snapshots" else "releases",
       "maintainers" to loadMaintainers(docsDir),
       "source-highlighter" to "coderay",
       "favicon" to "../../../images/ng-logo.png"
    ))

    dependsOn(collectDocs)
  }

  gitPublish {

    repoUri.set(docsRepoUri)
    branch.set("gh-pages")

    contents {
      from(asciidoctor) {
        into("docs/$version")
      }
      from (aggregateJavadocs) {
        into("docs/$version/javadoc")
      }
      if (!isSnapshot) {
        from(asciidoctor) {
          into("docs/current")
        }
        from (aggregateJavadocs) {
          into("docs/current/javadoc")
        }
      }
    }

    preserve { include("**/*") }
  }

  build {
    dependsOn(aggregateJavadocs)
    dependsOn(asciidoctor)
  }

  gitPublishPush {
    dependsOn(build)
  }

}


fun loadMaintainers(docsDir: String): List<String> =
   File("$docsDir/maintainers.txt").readLines()
