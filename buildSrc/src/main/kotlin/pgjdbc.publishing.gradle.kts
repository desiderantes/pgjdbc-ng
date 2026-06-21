import org.gradle.api.publish.maven.MavenPom
import java.net.URI

plugins {
  `maven-publish`
  signing
}

val version = project.property("version") as String
val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
val isRelease = (project.findProperty("release")?.toString() ?: "false").toBoolean()

val repositoryUrl =
   URI.create(
      if (isRelease)
        "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
      else
        "https://oss.sonatype.org/content/repositories/snapshots/"
   )!!

val pomCfg: MavenPom.() -> Unit = {

  name.set(project.name)
  description.set(project.description)

  url.set(ProjectInfo.URL)

  organization {
    name.set(ProjectInfo.Organization.NAME)
    url.set(ProjectInfo.Organization.URL)
  }

  issueManagement {
    system.set("GitHub")
    url.set(ProjectInfo.ISSUES_URL)
  }

  licenses {
    license {
      name.set(ProjectInfo.License.LICENSE_NAME)
      url.set(ProjectInfo.License.LICENSE_URL)
      distribution.set("repo")
    }
  }

  scm {
    url.set(ProjectInfo.URL)
    connection.set(ProjectInfo.SCM_URL)
    developerConnection.set(ProjectInfo.SCM_GIT_URL)
  }

  developers {
    ProjectInfo.DEVELOPERS.forEach { dev ->
      developer {
        id.set(dev.id)
        name.set(dev.name)
      }
    }
  }

}

afterEvaluate {
  if (isSnapshot || isRelease) {

    publishing {

      publications {

        register<MavenPublication>("std") {

          pom(pomCfg)

          from(components["java"])

        }

        tasks.findByName("uberJar")?.let { task ->

          register<MavenPublication>("uber") {

            val archiveBaseName = (task.property("archiveBaseName") as Property<*>).get() as String
            val archiveAppendix = (task.property("archiveAppendix") as Property<*>).get() as String
            artifactId = "$archiveBaseName-$archiveAppendix"

            pom(pomCfg)

            artifact(task)

            artifact(tasks.named<Jar>("sourcesJar").get())
            artifact(tasks.named<Jar>("javadocJar").get())
          }

        }

      }

      repositories {
        maven {
          url = repositoryUrl
          credentials {
            username = project.findProperty("ossrhUsername") as? String ?: ""
            password = project.findProperty("ossrhPassword") as? String ?: ""
          }
        }
      }

    }

    if (isRelease) {

      signing {
        sign(publishing.publications["std"])
        if (tasks.findByPath("uberJar") != null) {
          sign(publishing.publications["uber"])
        }
      }

    }

  }
}
