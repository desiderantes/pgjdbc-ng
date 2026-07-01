/**
 * Copyright (c) 2013, impossibl.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of impossibl.com nor the names of its contributors may
 *    be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.impossibl.postgres.jdbc;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.github.dockerjava.api.DockerClient;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;

@ParameterizedClass
@MethodSource("com.impossibl.postgres.jdbc.TestUtil#pgVersions")
public class ParameterizedVersionTest {

  record ConnectionDetails (String server, int port, String database, String user, String password) {
    public String jdbcUrl() {
      return "jdbc:pgsql://" + server + ":" + port + "/" + database;
    }
    public Properties authProps() {
      Properties props = new Properties();
      props.setProperty("user", user);
      props.setProperty("password", password);
      return props;
    }
  }
  private static final Map<String, Pair<ComposeContainer, ConnectionDetails>> containerMap = new HashMap<>();

  @Parameter
  String versionString;

  @BeforeParameterizedClassInvocation
  static void beforeInvocation(String version) {
    System.err.println("DEBUG: beforeInvocation running for version: " + version);

    boolean firstTime = !containerMap.containsKey(version);

    if (!firstTime) {
      var connDetails = containerMap.get(version).getValue();
      System.setProperty("pgjdbc.test.server", connDetails.server);
      System.setProperty("pgjdbc.test.port", String.valueOf(connDetails.port));
      return;
    }

    File initialComposeFile = new File("driver/src/integrationTest/resources/docker/postgres-services.yml");
    if (!initialComposeFile.exists()) {
      initialComposeFile = new File("src/integrationTest/resources/docker/postgres-services.yml");
    }
    final File composeFile = initialComposeFile;

    String serviceName = "postgres";

    var containerAndDetails = containerMap.computeIfAbsent(version, v -> {
          var c = new ComposeContainer(composeFile)
              .withEnv("PG_VERSION", version)
              .withServices(serviceName);
          c.start();
          ContainerState containerState = c.getContainerByServiceName(serviceName)
              .orElseThrow(() -> new RuntimeException("Could not find container for service: " + serviceName));
          String containerId = containerState.getContainerId();
          DockerClient client = DockerClientFactory.instance().client();
          com.github.dockerjava.api.command.InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
          com.github.dockerjava.api.model.Ports.Binding[] binding = inspect.getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432));
          if (binding == null || binding.length == 0) {
            throw new RuntimeException("PostgreSQL container port 5432 not mapped to host");
          }
          int hostPort = Integer.parseInt(binding[0].getHostPortSpec());
          var details = new ConnectionDetails(
              containerState.getHost(),
              hostPort,
              "test",
              "test",
              "test"
          );
          return Pair.of(c, details);
        }
    );

    var container = containerAndDetails.getKey();
    var connDetails = containerAndDetails.getValue();

    System.setProperty("pgjdbc.test.server", connDetails.server);
    System.setProperty("pgjdbc.test.port", String.valueOf(connDetails.port));

    // Wait for port to be open and accepting connection
    boolean connected = false;
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 30000) {
      try (Connection conn = DriverManager.getConnection(connDetails.jdbcUrl(), connDetails.authProps())) {
        connected = true;
        break;
      }
      catch (Exception e) {
        try {
          Thread.sleep(500);
        }
        catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    if (!connected) {
      throw new RuntimeException("Database did not become ready in 30 seconds");
    }

    if (firstTime) {
      // Initialize databases via container execution to bypass network / authentication / SSL restrictions
      try {
        DockerClient client = DockerClientFactory.instance().client();
        String containerId = container.getContainerByServiceName(serviceName).map(ContainerState::getContainerId).orElseThrow(() -> new RuntimeException("Could not find container for service: " + serviceName));
        String[] dbs = {"testnoexts", "hostdb", "hostssldb", "hostnossldb", "hostsslcertdb", "certdb"};
        for (String db : dbs) {
          execInContainer(client, containerId, "psql", "-U", "test", "-c", "DROP DATABASE IF EXISTS " + db + ";");
          execInContainer(client, containerId, "psql", "-U", "test", "-c", "CREATE DATABASE " + db + " OWNER test;");
        }

        String[] dbsWithSslInfo = {"hostdb", "hostssldb", "hostnossldb", "hostsslcertdb", "certdb"};
        for (String db : dbsWithSslInfo) {
          execInContainer(client, containerId, "psql", "-U", "test", "-d", db, "-c", "CREATE EXTENSION IF NOT EXISTS sslinfo;");
        }

        execInContainer(client, containerId, "psql", "-U", "test", "-d", "test", "-c", "CREATE EXTENSION IF NOT EXISTS hstore;");
        execInContainer(client, containerId, "psql", "-U", "test", "-d", "test", "-c", "CREATE EXTENSION IF NOT EXISTS citext;");
      }
      catch (Exception e) {
        throw new RuntimeException("Failed to initialize databases via container execution", e);
      }
    }
  }

  private static void execInContainer(DockerClient client, String containerId, String... command) {
    try {
      com.github.dockerjava.api.command.ExecCreateCmdResponse exec = client.execCreateCmd(containerId)
          .withAttachStdout(true)
          .withAttachStderr(true)
          .withCmd(command)
          .exec();
      client.execStartCmd(exec.getId()).exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
        @Override
        public void onNext(com.github.dockerjava.api.model.Frame frame) {
          System.err.print(new String(frame.getPayload(), java.nio.charset.StandardCharsets.UTF_8));
        }
      }).awaitCompletion();

      com.github.dockerjava.api.command.InspectExecResponse inspect = client.inspectExecCmd(exec.getId()).exec();
      Integer exitCode = inspect.getExitCode();
      if (exitCode != null && exitCode != 0) {
        throw new RuntimeException("Command inside container returned exit code " + exitCode + " for: " + java.util.Arrays.toString(command));
      }
    }
    catch (Exception e) {
      throw new RuntimeException("Failed to execute command in container: " + java.util.Arrays.toString(command), e);
    }
  }

  @AfterParameterizedClassInvocation
  static void afterInvocation(String version) {
    // Keep container running to reuse it across different test classes
  }
}
