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
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


public class PseudoRecordTest extends ParameterizedVersionTest {

  Connection conn;

  @BeforeEach
  public void before() throws Exception {
    conn = TestUtil.openDB();
    TestUtil.createTable(conn, "cs", "name text, val int");
    TestUtil.createTable(conn, "os", "name text, val int");
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("""
          CREATE OR REPLACE FUNCTION get4() RETURNS RECORD AS
          $BODY$
          DECLARE
            r1     RECORD;
            r2     RECORD;
            result RECORD;
          BEGIN
            SELECT array_agg(c.*) AS arr
            FROM cs AS c
            INTO r1;

            SELECT array_agg(o.*) AS arr
            FROM os AS o
            INTO r2;

            SELECT
              r1.arr,
              r2.arr
            INTO result;

            RETURN result;

          END;
          $BODY$
          LANGUAGE plpgsql STABLE;""");
    }
    TestUtil.closeDB(conn);
    conn = TestUtil.openDB();
  }

  @AfterEach
  public void after() throws Exception {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("DROP FUNCTION get4()");
    }
    TestUtil.closeDB(conn);
  }

  @Test
  public void testFunctionWithPseudoType()  {
    assertDoesNotThrow(() -> {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("SELECT get4()");
        try (ResultSet resultSet = stmt.getResultSet()) {
          resultSet.next();
          Object o = resultSet.getObject(1);
          o.toString();
        }
      }
    });

  }

}
