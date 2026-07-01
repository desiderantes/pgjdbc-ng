package com.impossibl.postgres.tools.test

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects
import com.impossibl.postgres.tools.UDTGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.ContainerState
import java.io.File
import java.sql.DriverManager
import java.util.*
import javax.tools.Diagnostic
import kotlin.random.Random

class UDTGeneratorTest {

  companion object {

    private val container = ComposeContainer(File("src/test/docker/postgres-services.yml"))
      .apply {
      val pgVersion = (System.getProperty("postgresVersions") ?: System.getenv("POSTGRES_VERSIONS") ?: "14")
        .split(",")
        .first()
        .trim()
      withEnv("PG_VERSION", pgVersion)
      withServices("postgres")
    }

    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      container.start()
      val pgContainer: ContainerState = container.getContainerByServiceName("postgres")
        .orElseThrow { RuntimeException("Could not find container for service: postgres") }
      val containerId = pgContainer.containerId
      val client = org.testcontainers.DockerClientFactory.instance().client()
      val inspect = client.inspectContainerCmd(containerId).exec()
      val binding = inspect.networkSettings.ports.bindings[com.github.dockerjava.api.model.ExposedPort.tcp(5432)]
      if (binding.isNullOrEmpty()) {
        throw RuntimeException("PostgreSQL container port 5432 not mapped to host")
      }
      val host = pgContainer.host
      val port = binding[0].hostPortSpec
      System.setProperty("pgjdbc.test.server", host)
      System.setProperty("pgjdbc.test.port", port)

      // Wait for port to be open and accepting connection
      var connected = false
      val start = System.currentTimeMillis()
      val url = "jdbc:pgsql://$host:$port/test"
      val props = Properties().apply {
        setProperty("user", "test")
        setProperty("password", "test")
      }
      while (System.currentTimeMillis() - start < 30000) {
        try {
          DriverManager.getConnection(url, props).use {
            connected = true
          }
          break
        } catch (e: Exception) {
          Thread.sleep(500)
        }
      }
      if (!connected) {
        throw RuntimeException("Database did not become ready in 30 seconds")
      }
    }

    @JvmStatic
    @AfterAll
    fun afterAll() {
      container.stop()
    }

    private val server get() = System.getProperty("pgjdbc.test.server", "localhost")
    private val port get() = System.getProperty("pgjdbc.test.port", "5432")
    private val db get() = System.getProperty("pgjdbc.test.db", "test")
    private val url get() = "jdbc:pgsql://$server:$port/$db"

    private val props get() = Properties().apply {
      setProperty("user", System.getProperty("pgjdbc.test.user", "test"))
      setProperty("password", System.getProperty("pgjdbc.test.password", "test"))
    }

  }

  @Test
  fun testCompile() {
    println("Generating code from $url")

    DriverManager.getConnection(url, props).use { connection ->

      val schemaName = "test${Random.nextInt(0, 0xffffff).toString(16)}"
      try {

        connection.createStatement().use { stmt ->
          stmt.execute("CREATE SCHEMA $schemaName")
          stmt.execute("CREATE TYPE $schemaName.title as enum ('mr', 'mrs', 'ms', 'dr')")
          stmt.execute("CREATE TYPE $schemaName.address as (street varchar, city text, state char(2), zip char(5))")
          stmt.execute("CREATE TYPE $schemaName.v_card as (id int4, name text, title $schemaName.title, addresses $schemaName.address[])")
          stmt.execute("SET SEARCH_PATH = public, $schemaName")
        }

        val pkgName = "udt.test"

        val files = UDTGenerator(connection, pkgName, listOf("$schemaName.v_card", "address", "title"))
           .generate()
           .map { it.toJavaFileObject() }

        val result = javac()
           .compile(files + JavaFileObjects.forResource("VCardTest.java"))

        assertEquals(result.errors(), emptyList<Diagnostic<*>>())
        assertEquals(result.status(), Compilation.Status.SUCCESS)

      }
      finally {
        connection.createStatement().use {
          it.execute("DROP SCHEMA $schemaName CASCADE")
        }
      }
    }

  }

  @Test
  fun testGenerate() {
    println("Generating code from $url")

    DriverManager.getConnection(url, props).use { connection ->

      val schemaName = "test${Random.nextInt(0, 0xffffff).toString(16)}"
      try {

        connection.createStatement().use { stmt ->
          stmt.execute("CREATE SCHEMA $schemaName")
          stmt.execute("CREATE TYPE $schemaName.\"TITLE\" as enum ('mr', 'mrs', 'ms', 'dr')")
          stmt.execute("CREATE TYPE $schemaName.address as (street text, city text, state char(2), zip char(5))")
          stmt.execute("CREATE TYPE $schemaName.v_card as (id int4, name text, title $schemaName.\"TITLE\", addresses $schemaName.address[])")
          stmt.execute("SET SEARCH_PATH = public, $schemaName")
        }

        val outDirectory = File("build/test/generated")
        outDirectory.deleteRecursively()

        val pkgName = "udt.test"

        UDTGenerator(connection, pkgName, listOf("$schemaName.v_card", "address", "\"$schemaName\".\"TITLE\""))
           .generate(outDirectory)

        val pkgFileNames = File(outDirectory, pkgName.replace('.', '/'))
           .listFiles()!!
           .map { it.name }

        assertEquals(pkgFileNames.size, 3)
        assertTrue(pkgFileNames.containsAll(listOf("Title.java", "Address.java", "VCard.java")))

      }
      finally {
        connection.createStatement().use {
          it.execute("DROP SCHEMA $schemaName CASCADE")
        }
      }
    }

  }

}
