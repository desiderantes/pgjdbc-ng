plugins {
  base
  id("com.gradleup.shadow") apply false
  alias(libs.plugins.docker.compose) apply false
}


allprojects {

  apply {
    plugin("idea")
  }

  group = "com.impossibl.pgjdbc-ng"
  version = "0.9-SNAPSHOT"

}

subprojects {

  repositories {
    mavenLocal()
    mavenCentral()
  }

}

