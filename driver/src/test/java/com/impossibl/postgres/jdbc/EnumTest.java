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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumTest {

  private Connection conn;

  @BeforeEach
  public void before() throws Exception {
    conn = TestUtil.openDB();
    TestUtil.createEnum(conn, "testtype", "A", "B", "C");
    TestUtil.createTable(conn, "testtable", "val testtype");
  }

  @AfterEach
  public void after() throws SQLException {
    TestUtil.dropType(conn, "testtype");
    TestUtil.dropTable(conn, "testtable");
    TestUtil.closeDB(conn);
  }

  @Test
  public void testInsert() throws SQLException {

    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO testtable VALUES (?)")) {
      stmt.setString(1, "A");
      assertEquals(1, stmt.executeUpdate());
    }

    checkValue("A");
  }

  @Test
  public void testUpdate() throws SQLException {

    testInsert();

    try (PreparedStatement stmt = conn.prepareStatement("UPDATE testtable SET val = ?")) {
      stmt.setString(1, "B");
      assertEquals(1, stmt.executeUpdate());
    }

    checkValue("B");
  }

  @Test
  public void testInvalid() {
    assertThrows(SQLException.class, () -> {

      try (PreparedStatement stmt = conn.prepareStatement("UPDATE testtable SET val = ?")) {
        stmt.setString(1, "D");
        stmt.executeUpdate();
      }
    });
  }

  void checkValue(String val) throws SQLException {

    try (Statement stmt = conn.createStatement()) {

      try (ResultSet rs = stmt.executeQuery("SELECT * FROM testtable")) {
        assertTrue(rs.next());
        assertEquals(rs.getString(1), val);
      }

    }
  }

}
