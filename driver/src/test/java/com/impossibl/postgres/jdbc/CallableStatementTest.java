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
/*-------------------------------------------------------------------------
 *
 * Copyright (c) 2004-2011, PostgreSQL Global Development Group
 *
 *
 *-------------------------------------------------------------------------
 */
package com.impossibl.postgres.jdbc;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/*
 * CallableStatement tests.
 * @author Paul Bethe
 */
public class CallableStatementTest {

  private Connection con;

  @BeforeEach
  public void setUp() throws Exception {
    con = TestUtil.openDB();
    TestUtil.createTable(con, "int_table", "id int");
    Statement stmt = con.createStatement();
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getString (varchar) "
        + "RETURNS varchar AS ' DECLARE inString alias for $1; begin "
        + "return ''bob''; end; ' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getDouble (float) "
        + "RETURNS float AS ' DECLARE inString alias for $1; begin "
        + "return 42.42; end; ' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getVoid (float) "
        + "RETURNS void AS ' DECLARE inString alias for $1; begin "
        + " return; end; ' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getInt (int) RETURNS int "
        + " AS 'DECLARE inString alias for $1; begin "
        + "return 42; end;' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getShort (int2) RETURNS int2 "
        + " AS 'DECLARE inString alias for $1; begin "
        + "return 42; end;' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getNumeric (numeric) "
        + "RETURNS numeric AS ' DECLARE inString alias for $1; "
        + "begin return 42; end; ' LANGUAGE plpgsql;");

    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getNumericWithoutArg() "
        + "RETURNS numeric AS '  "
        + "begin return 42; end; ' LANGUAGE plpgsql;");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__getarray() RETURNS int[] as 'SELECT ''{1,2}''::int[];' LANGUAGE sql");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__raisenotice() RETURNS int as 'BEGIN RAISE NOTICE ''hello'';  RAISE NOTICE ''goodbye''; RETURN 1; END;' LANGUAGE plpgsql");
    stmt.execute("CREATE OR REPLACE FUNCTION testspg__insertInt(int) RETURNS int as 'BEGIN INSERT INTO int_table(id) VALUES ($1); RETURN 1; END;' LANGUAGE plpgsql");


    stmt.execute("create temp table numeric_tab (MAX_VAL NUMERIC(30,15), MIN_VAL NUMERIC(30,15), NULL_VAL NUMERIC(30,15) NULL)");
    stmt.execute("insert into numeric_tab values ( 999999999999999,0.000000000000001, null)");
    stmt.execute("CREATE OR REPLACE FUNCTION myiofunc(a INOUT int, b OUT int) AS 'BEGIN b := a; a := 1; END;' LANGUAGE plpgsql");
    stmt.execute("CREATE OR REPLACE FUNCTION myif(a INOUT int, b IN int) AS 'BEGIN a := b; END;' LANGUAGE plpgsql");

    stmt.execute("create or replace function "
                                         + "Numeric_Proc( OUT IMAX NUMERIC(30,15), OUT IMIN NUMERIC(30,15), OUT INUL NUMERIC(30,15))  as "
                                         + "'begin "
                                         +         "select max_val into imax from numeric_tab;"
                                         +         "select min_val into imin from numeric_tab;"
                                         +         "select null_val into inul from numeric_tab;"

                                         + " end;' "
                                         + "language plpgsql;");

    stmt.execute("CREATE OR REPLACE FUNCTION test_somein_someout("
            + "pa IN int4,"
            + "pb OUT varchar,"
            + "pc OUT int8)"
            + " AS "
            + "'begin "
            + "pb := ''out'';"
            + "pc := pa + 1;"
            + "end;'"
            + "LANGUAGE plpgsql VOLATILE;");
    stmt.execute("CREATE OR REPLACE FUNCTION test_somein_someout2("
        + "pb OUT varchar,"
        + "pc OUT int8,"
        + "pa IN int4)"
        + " AS "
        + "'begin "
        + "pb := ''out'';"
        + "pc := pa + 1;"
        + "end;'"
        + "LANGUAGE plpgsql VOLATILE;");
    stmt.execute("CREATE OR REPLACE FUNCTION test_somein_someout3("
        + "pb OUT varchar,"
        + "pa IN int4,"
        + "pc OUT int8)"
        + " AS "
        + "'begin "
        + "pb := ''out'';"
        + "pc := pa + 1;"
        + "end;'"
        + "LANGUAGE plpgsql VOLATILE;");
    stmt.execute("CREATE OR REPLACE FUNCTION test_allinout("
            + "pa INOUT int4,"
            + "pb INOUT varchar,"
            + "pc INOUT int8)"
            + " AS "
            + "'begin "
            + "pa := pa + 1;"
            + "pb := ''foo out'';"
            + "pc := pa + 1;"
            + "end;'"
            + "LANGUAGE plpgsql VOLATILE;");

    stmt.close();
  }

  @AfterEach
  public void tearDown() throws Exception {
    Statement stmt = con.createStatement();
    TestUtil.dropTable(con, "int_table");
    stmt.execute("drop FUNCTION testspg__getString (varchar);");
    stmt.execute("drop FUNCTION testspg__getDouble (float);");
    stmt.execute("drop FUNCTION testspg__getVoid(float);");
    stmt.execute("drop FUNCTION testspg__getInt (int);");
    stmt.execute("drop FUNCTION testspg__getShort(int2)");
    stmt.execute("drop FUNCTION testspg__getNumeric (numeric);");

    stmt.execute("drop FUNCTION testspg__getNumericWithoutArg ();");
    stmt.execute("DROP FUNCTION testspg__getarray();");
    stmt.execute("DROP FUNCTION testspg__raisenotice();");
    stmt.execute("DROP FUNCTION testspg__insertInt(int);");
    stmt.close();
    TestUtil.closeDB(con);
  }

  @Test
  public void testUseAsPreparedStatement() throws Exception {
    try (CallableStatement stmt = con.prepareCall("SELECT current_schema()")) {
      try (ResultSet rs = stmt.executeQuery()) {
        assertTrue(rs.next());
        assertEquals("public", rs.getString(1));
      }
    }
  }

  @Test
  public void testUseAsPreparedStatement2() throws Exception {
    try (CallableStatement stmt = con.prepareCall("SELECT testspg__getString FROM testspg__getString(?)")) {
      stmt.setString(1, "");
      try (ResultSet rs = stmt.executeQuery()) {
        assertTrue(rs.next());
        assertEquals("bob", rs.getString(1));
      }
    }
  }

  final String func = "{ ? = call ";
  final String pkgName = "testspg__";

  @Test
  public void testGetUpdateCount() throws SQLException {
    CallableStatement call = con.prepareCall(func + pkgName + "getDouble (?) }");
    call.setDouble(2, 3.04);
    call.registerOutParameter(1, Types.DOUBLE);
    call.execute();
    assertEquals(-1, call.getUpdateCount());
    assertNull(call.getResultSet());
    assertEquals(42.42, call.getDouble(1), 0.00001);
    call.close();

    // test without an out parameter
    call = con.prepareCall("{ call " + pkgName + "getDouble(?) }");
    call.setDouble(1, 3.04);
    call.execute();
    assertEquals(-1, call.getUpdateCount());
    ResultSet rs = call.getResultSet();
    assertNotNull(rs);
    assertTrue(rs.next());
    assertEquals(42.42, rs.getDouble(1), 0.00001);
    assertFalse(rs.next());
    rs.close();

    assertEquals(-1, call.getUpdateCount());
    assertFalse(call.getMoreResults());
    call.close();
  }

  @Test
  public void testGetDouble() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getDouble (?) }");
    call.setDouble(2, 3.04);
    call.registerOutParameter(1, Types.DOUBLE);
    call.execute();
    assertEquals(42.42, call.getDouble(1), 0.00001);
    call.close();

    // test without an out parameter
    call = con.prepareCall("{ call " + pkgName + "getDouble(?) }");
    call.setDouble(1, 3.04);
    call.execute();
    call.close();

    call = con.prepareCall("{ call " + pkgName + "getVoid(?) }");
    call.setDouble(1, 3.04);
    call.execute();
    call.close();
  }

  @Test
  public void testGetInt() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getInt (?) }");
    call.setInt(2, 4);
    call.registerOutParameter(1, Types.INTEGER);
    call.execute();
    assertEquals(42, call.getInt(1));
    call.close();
  }

  @Test
  public void testGetShort() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getShort (?) }");
    call.setShort(2, (short) 4);
    call.registerOutParameter(1, Types.SMALLINT);
    call.execute();
    assertEquals(42, call.getShort(1));
    call.close();
  }

  @Test
  public void testGetNumeric() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getNumeric (?) }");
    call.setBigDecimal(2, new java.math.BigDecimal(4));
    call.registerOutParameter(1, Types.NUMERIC);
    call.execute();
    assertEquals(new java.math.BigDecimal(42), call.getBigDecimal(1));
    call.close();
  }

  @Test
  public void testGetNumericWithoutArg() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getNumericWithoutArg () }");
    call.registerOutParameter(1, Types.NUMERIC);
    call.execute();
    assertEquals(new java.math.BigDecimal(42), call.getBigDecimal(1));
    call.close();
  }

  @Test
  public void testGetString() throws Throwable {
    CallableStatement call = con.prepareCall(func + pkgName + "getString (?) }");
    call.setString(2, "foo");
    call.registerOutParameter(1, Types.VARCHAR);
    call.execute();
    assertEquals("bob", call.getString(1));
    call.close();
  }

  @Test
  public void testGetArray() throws SQLException {
    CallableStatement call = con.prepareCall(func + pkgName + "getarray()}");
    call.registerOutParameter(1, Types.ARRAY);
    call.execute();
    Array arr = call.getArray(1);
    ResultSet rs = arr.getResultSet();
    assertTrue(rs.next());
    assertEquals(1, rs.getInt(1));
    assertTrue(rs.next());
    assertEquals(2, rs.getInt(1));
    assertFalse(rs.next());
    arr.free();
    rs.close();
    call.close();
  }

  @Test
  public void testRaiseNotice() throws SQLException {
    // Ensure RAISE works in all environments
    try (Statement statement = con.createStatement()) {
      statement.execute("SET SESSION client_min_messages = 'NOTICE'");
    }
    CallableStatement call = con.prepareCall(func + pkgName + "raisenotice()}");
    call.registerOutParameter(1, Types.INTEGER);
    call.execute();
    SQLWarning warn = call.getWarnings();
    assertNotNull(warn);
    assertEquals("hello", warn.getMessage());
    warn = warn.getNextWarning();
    assertNotNull(warn);
    assertEquals("goodbye", warn.getMessage());
    assertEquals(1, call.getInt(1));
    call.close();
  }

  @Test
  public void testWasNullBeforeFetch() throws SQLException {
    CallableStatement cs = con.prepareCall("{? = call lower(?)}");
    cs.registerOutParameter(1, Types.VARCHAR);
    cs.setString(2, "Hi");
    try {
      cs.wasNull();
      fail("expected exception");
    }
    catch (Exception e) {
      assertTrue(e instanceof SQLException);
    }
    cs.close();
  }

  @Test
  public void testFetchBeforeExecute() throws SQLException {
    CallableStatement cs = con.prepareCall("{? = call lower(?)}");
    cs.registerOutParameter(1, Types.VARCHAR);
    cs.setString(2, "Hi");
    try {
      cs.getString(1);
      fail("expected exception");
    }
    catch (Exception e) {
      assertTrue(e instanceof SQLException);
    }
    cs.close();
  }

  @Test
  public void testFetchWithNoResults() throws SQLException {
    CallableStatement cs = con.prepareCall("{call now()}");
    cs.execute();
    try {
      cs.getObject(1);
      fail("expected exception");
    }
    catch (Exception e) {
      assertTrue(e instanceof SQLException);
    }
    cs.close();
  }

  @Test
  public void testBadStmt() throws Throwable {
    tryOneBadStmt("{ ?= " + pkgName + "getString (?) }");
    tryOneBadStmt("{ ?= call getString (?) ");
    tryOneBadStmt("{ = ? call getString (?); }");
  }

  protected void tryOneBadStmt(String sql) throws SQLException {
    try (Statement cs = con.prepareCall(sql)) {
      fail("Bad statement (" + sql + ") was not caught.");
    }
    catch (SQLException e) {
      // Expected...
    }
  }

  @Test
  public void testBatchCall() throws SQLException {
    CallableStatement call = con.prepareCall("{ call " + pkgName + "insertInt(?) }");
    call.setInt(1, 1);
    call.addBatch();
    call.setInt(1, 2);
    call.addBatch();
    call.setInt(1, 3);
    call.addBatch();
    call.executeBatch();
    call.close();

    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT id FROM int_table ORDER BY id");
    assertTrue(rs.next());
    assertEquals(1, rs.getInt(1));
    assertTrue(rs.next());
    assertEquals(2, rs.getInt(1));
    assertTrue(rs.next());
    assertEquals(3, rs.getInt(1));
    assertFalse(rs.next());
    rs.close();
    stmt.close();
  }

  @Test
  public void testSomeInOut() throws Throwable {
    CallableStatement call = con.prepareCall("{ call test_somein_someout(?,?,?) }");

    call.setInt(1, 20);
    call.registerOutParameter(2, Types.VARCHAR);
    call.registerOutParameter(3, Types.BIGINT);
    call.execute();
    Assertions.assertEquals("out", call.getString(2));
    Assertions.assertEquals(21, call.getInt(3));
    call.close();
  }

  @Test
  public void testSomeInOut2() throws Throwable {
    CallableStatement call = con.prepareCall("{ call test_somein_someout2(?,?,?) }");

    call.registerOutParameter(1, Types.VARCHAR);
    call.registerOutParameter(2, Types.BIGINT);
    call.setInt(3, 20);
    call.execute();
    Assertions.assertEquals("out", call.getString(1));
    Assertions.assertEquals(21, call.getInt(2));
    call.close();
  }

  @Test
  public void testSomeInOut3() throws Throwable {
    CallableStatement call = con.prepareCall("{ call test_somein_someout3(?,?,?) }");

    call.registerOutParameter(1, Types.VARCHAR);
    call.setInt(2, 20);
    call.registerOutParameter(3, Types.BIGINT);
    call.execute();
    Assertions.assertEquals("out", call.getString(1));
    Assertions.assertEquals(21, call.getInt(3));
    call.close();
  }

  @Test
  public void testNotEnoughParameters() throws Throwable {

    CallableStatement cs = con.prepareCall("{call myiofunc(?,?)}");
    cs.setInt(1, 2);
    cs.registerOutParameter(2, Types.INTEGER);
    try {
      cs.execute();
      fail("Should throw an exception ");
    }
    catch (SQLException ex) {
      // Expected...
    }
    cs.close();
  }

  @Test
  public void testTooManyParameters() throws Throwable {

    CallableStatement cs = con.prepareCall("{call myif(?,?)}");
    try {
      cs.setInt(1, 1);
      cs.setInt(2, 2);
      cs.registerOutParameter(1, Types.INTEGER);
      cs.registerOutParameter(2, Types.INTEGER);
      cs.execute();
      fail("should throw an exception");
    }
    catch (SQLException ex) {
      // Expected...
    }
    cs.close();
  }

  @Test
  public void testAllInOut() throws Throwable {

    CallableStatement call = con.prepareCall("{ call test_allinout(?,?,?) }");

    call.registerOutParameter(1, Types.INTEGER);
    call.registerOutParameter(2, Types.VARCHAR);
    call.registerOutParameter(3, Types.BIGINT);
    call.setInt(1, 20);
    call.setString(2, "hi");
    call.setInt(3, 123);
    call.execute();
    call.getInt(1);
    call.getString(2);
    call.getLong(3);

    call.close();
  }

  @Test
  public void testNumeric() throws Throwable {

    CallableStatement call = con.prepareCall("{ call Numeric_Proc(?,?,?) }");

    call.registerOutParameter(1, Types.NUMERIC, 15);
    call.registerOutParameter(2, Types.NUMERIC, 15);
    call.registerOutParameter(3, Types.NUMERIC, 15);

    call.executeUpdate();
    java.math.BigDecimal ret = call.getBigDecimal(1);
    assertEquals(ret, new BigDecimal("999999999999999.000000000000000"), "correct return from getNumeric () should be 999999999999999.000000000000000 but returned " + ret.toString());

    ret = call.getBigDecimal(2);
    assertEquals(ret, new BigDecimal("0.000000000000001"), "correct return from getNumeric ()");
    try {
      ret = call.getBigDecimal(3);
    }
    catch (NullPointerException ex) {
      assertTrue(call.wasNull(), "This should be null");
    }

    call.close();
  }

  @Test
  public void testGetObjectDecimal() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table decimal_tab ( max_val numeric(30,15), min_val numeric(30,15), nul_val numeric(30,15) )");
      stmt.execute("insert into decimal_tab values (999999999999999.000000000000000,0.000000000000001,null)");
      stmt.execute("create or replace function "
          + "decimal_proc( OUT pmax numeric, OUT pmin numeric, OUT nval numeric)  as "
          + "'begin "
          + "select max_val into pmax from decimal_tab;"
          + "select min_val into pmin from decimal_tab;"
          + "select nul_val into nval from decimal_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call decimal_proc(?,?,?) }");
      cstmt.registerOutParameter(1, Types.DECIMAL);
      cstmt.registerOutParameter(2, Types.DECIMAL);
      cstmt.registerOutParameter(3, Types.DECIMAL);
      cstmt.executeUpdate();
      BigDecimal val = (BigDecimal) cstmt.getObject(1);
      assertEquals(0, val.compareTo(new BigDecimal("999999999999999.000000000000000")));
      val = (BigDecimal) cstmt.getObject(2);
      assertEquals(0, val.compareTo(new BigDecimal("0.000000000000001")));
      val = (BigDecimal) cstmt.getObject(3);
      assertNull(val);
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try {
        Statement dstmt = con.createStatement();
        dstmt.execute("drop function decimal_proc()");
        dstmt.close();
      }
      catch (Exception ex) {
        // Expected...
      }
    }
  }

  @Test
  public void testVarcharBool() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table vartab( max_val text, min_val text)");
      stmt.execute("insert into vartab values ('a','b')");
      stmt.execute("create or replace function "
          + "updatevarchar( in imax text, in imin text)  returns int as "
          + "'begin " + "update vartab set max_val = imax;"
          + "update vartab set min_val = imin;" + "return 0;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call updatevarchar(?,?) }");
      cstmt.setObject(1, Boolean.TRUE, Types.VARCHAR);
      cstmt.setObject(2, Boolean.FALSE, Types.VARCHAR);

      cstmt.executeUpdate();
      cstmt.close();

      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select * from vartab");
      assertTrue(rs.next());
      assertEquals("t", rs.getString(1));

      assertEquals("f", rs.getString(2));
      rs.close();
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function updatevarchar(text,text)");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testInOut() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createBitTab);
      stmt.execute(insertBitTab);
      stmt.execute("create or replace function "
          + "insert_bit( inout IMAX boolean, inout IMIN boolean, inout INUL boolean)  as "
          + "'begin "
          + "insert into bit_tab values( imax, imin, inul);"
          + "select max_val into imax from bit_tab;"
          + "select min_val into imin from bit_tab;"
          + "select null_val into inul from bit_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call insert_bit(?,?,?) }");
      cstmt.setObject(1, "true", Types.BIT);
      cstmt.setObject(2, "false", Types.BIT);
      cstmt.setNull(3, Types.BIT);
      cstmt.registerOutParameter(1, Types.BIT);
      cstmt.registerOutParameter(2, Types.BIT);
      cstmt.registerOutParameter(3, Types.BIT);
      cstmt.executeUpdate();

      assertEquals(true, cstmt.getBoolean(1));
      assertEquals(false, cstmt.getBoolean(2));
      cstmt.getBoolean(3);
      assertTrue(cstmt.wasNull());

      cstmt.close();
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function insert_bit(boolean, boolean, boolean)");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  private final String createBitTab = "create temp table bit_tab ( max_val boolean, min_val boolean, null_val boolean )";
  private final String insertBitTab = "insert into bit_tab values (true,false,null)";

  @Test
  public void testSetObjectBit() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createBitTab);
      stmt.execute(insertBitTab);
      stmt.execute("create or replace function "
          + "update_bit( in IMAX boolean, in IMIN boolean, in INUL boolean) returns int as "
          + "'begin "
          + "update bit_tab set  max_val = imax;"
          + "update bit_tab set  min_val = imin;"
          + "update bit_tab set  min_val = inul;"
          + " return 0;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call update_bit(?,?,?) }");
      cstmt.setObject(1, "true", Types.BIT);
      cstmt.setObject(2, "false", Types.BIT);
      cstmt.setNull(3, Types.BIT);
      cstmt.executeUpdate();
      cstmt.close();

      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select * from bit_tab");

      assertTrue(rs.next());
      assertEquals(true, rs.getBoolean(1));
      assertEquals(false, rs.getBoolean(2));
      rs.getBoolean(3);
      assertTrue(rs.wasNull());

      rs.close();
      stmt.close();
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function update_bit(boolean, boolean, boolean)");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetObjectLongVarchar() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table longvarchar_tab ( t text, null_val text )");
      stmt.execute("insert into longvarchar_tab values ('testdata',null)");
      stmt.execute("create or replace function "
          + "longvarchar_proc( OUT pcn text, OUT nval text)  as "
          + "'begin "
          + "select t into pcn from longvarchar_tab;"
          + "select null_val into nval from longvarchar_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.execute("create or replace function "
          + "lvarchar_in_name( IN pcn text) returns int as "
          + "'begin "
          + "update longvarchar_tab set t=pcn;"
          + "return 0;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call longvarchar_proc(?,?) }");
      cstmt.registerOutParameter(1, Types.LONGVARCHAR);
      cstmt.registerOutParameter(2, Types.LONGVARCHAR);
      cstmt.executeUpdate();
      String val = (String) cstmt.getObject(1);
      assertEquals("testdata", val);
      val = (String) cstmt.getObject(2);
      assertNull(val);
      cstmt.close();
      cstmt = con.prepareCall("{ call lvarchar_in_name(?) }");
      String maxFloat = "3.4E38";
      cstmt.setObject(1, Float.valueOf(maxFloat), Types.LONGVARCHAR);
      cstmt.executeUpdate();
      cstmt.close();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select * from longvarchar_tab");
      assertTrue(rs.next());
      String rval = (String) rs.getObject(1);
      assertEquals(rval.trim(), maxFloat.trim());
      rs.close();
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function longvarchar_proc()");
        dstmt.execute("drop function lvarchar_in_name(text)");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetBytes01() throws Throwable {
    byte[] testdata = "TestData".getBytes();
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table varbinary_tab ( vbinary bytea, null_val bytea )");
      stmt.execute("create or replace function "
          + "varbinary_proc( OUT pcn bytea, OUT nval bytea)  as "
          + "'begin " + "select vbinary into pcn from varbinary_tab;"
          + "select null_val into nval from varbinary_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
      PreparedStatement pstmt = con.prepareStatement("insert into varbinary_tab values (?,?)");
      pstmt.setBytes(1, testdata);
      pstmt.setBytes(2, null);

      pstmt.executeUpdate();
      pstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call varbinary_proc(?,?) }");
      cstmt.registerOutParameter(1, Types.VARBINARY);
      cstmt.registerOutParameter(2, Types.VARBINARY);
      cstmt.executeUpdate();
      byte[] retval = cstmt.getBytes(1);
      for (int i = 0; i < testdata.length; i++) {
        assertEquals(testdata[i], retval[i]);
      }

      retval = cstmt.getBytes(2);
      assertNull(retval);

      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function varbinary_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  private final String createDecimalTab = "create temp table decimal_tab ( max_val float, min_val float, null_val float )";
  private final String insertDecimalTab = "insert into decimal_tab values (1.0E125,1.0E-130,null)";
  private final String createFloatProc = "create or replace function "
      + "float_proc( OUT IMAX float, OUT IMIN float, OUT INUL float)  as "
      + "'begin "
      + "select max_val into imax from decimal_tab;"
      + "select min_val into imin from decimal_tab;"
      + "select null_val into inul from decimal_tab;"
      + " end;' "
      + "language plpgsql;";
  private final String createUpdateFloat = "create or replace function "
      + "updatefloat_proc ( IN maxparm float, IN minparm float ) returns int as "
      + "'begin "
      + "update decimal_tab set max_val=maxparm;"
      + "update decimal_tab set min_val=minparm;"
      + "return 0;"
      + " end;' "
      + "language plpgsql;";
  private final String createRealTab = "create temp table real_tab ( max_val float(25), min_val float(25), null_val float(25) )";
  private final String insertRealTab = "insert into real_tab values (1.0E37,1.0E-37, null)";
  private final String dropFloatProc = "drop function float_proc()";
  private final String createUpdateReal = "create or replace function "
      + "update_real_proc ( IN maxparm float(25), IN minparm float(25) ) returns int as "
      + "'begin "
      + "update real_tab set max_val=maxparm;"
      + "update real_tab set min_val=minparm;"
      + "return 0;"
      + " end;' "
      + "language plpgsql;";
  private final String dropUpdateReal = "drop function update_real_proc(float, float)";
  private final double[] doubleValues = {1.0E125, 1.0E-130};
  private final int[] intValues = {2147483647, -2147483648};

  @Test
  public void testUpdateReal() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createRealTab);
      stmt.execute(createUpdateReal);
      stmt.execute(insertRealTab);
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call update_real_proc(?,?) }");
      BigDecimal val = new BigDecimal(intValues[0]);
      val.floatValue();
      cstmt.setObject(1, val, Types.REAL);
      val = new BigDecimal(intValues[1]);
      cstmt.setObject(2, val, Types.REAL);
      cstmt.executeUpdate();
      cstmt.close();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select * from real_tab");
      assertTrue(rs.next());
      Float oVal = (float) intValues[0];
      Float rVal = Float.valueOf(rs.getObject(1).toString());
      assertEquals(oVal, rVal);
      oVal = (float) intValues[1];
      rVal = Float.valueOf(rs.getObject(2).toString());
      assertEquals(oVal, rVal);
      rs.close();
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute(dropUpdateReal);
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testUpdateDecimal() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createDecimalTab);
      stmt.execute(createUpdateFloat);
      stmt.close();
      PreparedStatement pstmt = con.prepareStatement("insert into decimal_tab values (?,?)");
      // note these are reversed on purpose
      pstmt.setDouble(1, doubleValues[1]);
      pstmt.setDouble(2, doubleValues[0]);

      pstmt.executeUpdate();
      pstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call updatefloat_proc(?,?) }");
      cstmt.setDouble(1, doubleValues[0]);
      cstmt.setDouble(2, doubleValues[1]);
      cstmt.executeUpdate();
      cstmt.close();
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select * from decimal_tab");
      assertTrue(rs.next());
      assertEquals(rs.getDouble(1), doubleValues[0], 0.0);
      assertEquals(rs.getDouble(2), doubleValues[1], 0.0);
      rs.close();
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function updatefloat_proc(float, float)");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetBytes02() throws Throwable {
    byte[] testdata = "TestData".getBytes();
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table longvarbinary_tab ( vbinary bytea, null_val bytea )");
      stmt.execute("create or replace function "
          + "longvarbinary_proc( OUT pcn bytea, OUT nval bytea)  as "
          + "'begin "
          + "select vbinary into pcn from longvarbinary_tab;"
          + "select null_val into nval from longvarbinary_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
      PreparedStatement pstmt = con.prepareStatement("insert into longvarbinary_tab values (?,?)");
      pstmt.setBytes(1, testdata);
      pstmt.setBytes(2, null);

      pstmt.executeUpdate();
      pstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call longvarbinary_proc(?,?) }");
      cstmt.registerOutParameter(1, Types.LONGVARBINARY);
      cstmt.registerOutParameter(2, Types.LONGVARBINARY);
      cstmt.executeUpdate();
      byte[] retval = cstmt.getBytes(1);
      for (int i = 0; i < testdata.length; i++) {
        assertEquals(testdata[i], retval[i]);
      }

      retval = cstmt.getBytes(2);
      assertNull(retval);

      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function longvarbinary_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetObjectFloat() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createDecimalTab);
      stmt.execute(insertDecimalTab);
      stmt.execute(createFloatProc);
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call float_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.FLOAT);
      cstmt.registerOutParameter(2, java.sql.Types.FLOAT);
      cstmt.registerOutParameter(3, java.sql.Types.FLOAT);
      cstmt.executeUpdate();
      Double val = (Double) cstmt.getObject(1);
      assertEquals(val.doubleValue(), doubleValues[0], 0.0);

      val = (Double) cstmt.getObject(2);
      assertEquals(val.doubleValue(), doubleValues[1], 0.0);

      val = (Double) cstmt.getObject(3);
      assertTrue(cstmt.wasNull());

      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute(dropFloatProc);
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetDouble01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table d_tab ( max_val float, min_val float, null_val float )");
      stmt.execute("insert into d_tab values (1.0E125,1.0E-130,null)");
      stmt.execute("create or replace function "
          + "double_proc( OUT IMAX float, OUT IMIN float, OUT INUL float)  as "
          + "'begin "
          + "select max_val into imax from d_tab;"
          + "select min_val into imin from d_tab;"
          + "select null_val into inul from d_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call double_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.DOUBLE);
      cstmt.registerOutParameter(2, java.sql.Types.DOUBLE);
      cstmt.registerOutParameter(3, java.sql.Types.DOUBLE);
      cstmt.executeUpdate();
      assertEquals(1.0E125, cstmt.getDouble(1), 0.0);
      assertEquals(1.0E-130, cstmt.getDouble(2), 0.0);
      cstmt.getDouble(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function double_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetDoubleAsReal() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table d_tab ( max_val float, min_val float, null_val float )");
      stmt.execute("insert into d_tab values (3.4E38,1.4E-45,null)");
      stmt.execute("create or replace function "
          + "double_proc( OUT IMAX float, OUT IMIN float, OUT INUL float)  as "
          + "'begin "
          + "select max_val into imax from d_tab;"
          + "select min_val into imin from d_tab;"
          + "select null_val into inul from d_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call double_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.REAL);
      cstmt.registerOutParameter(2, java.sql.Types.REAL);
      cstmt.registerOutParameter(3, java.sql.Types.REAL);
      cstmt.executeUpdate();
      assertEquals(3.4E38f, cstmt.getFloat(1), 0.0);
      assertEquals(1.4E-45f, cstmt.getFloat(2), 0.0);
      cstmt.getFloat(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function double_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetRealAsFloat() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table r_tab ( max_val float8, min_val float8, null_val float8 )");
      stmt.execute("insert into r_tab values ( 1.0E37,1.0E-37, null )");
      stmt.execute("create or replace function "
          + "real_proc( OUT IMAX float8, OUT IMIN float8, OUT INUL float8)  as "
          + "'begin "
          + "select max_val into imax from r_tab;"
          + "select min_val into imin from r_tab;"
          + "select null_val into inul from r_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call real_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.REAL);
      cstmt.registerOutParameter(2, java.sql.Types.REAL);
      cstmt.registerOutParameter(3, java.sql.Types.REAL);
      cstmt.executeUpdate();
      assertEquals(1.0E37f, ((Float) cstmt.getObject(1)), 0.0);
      assertEquals(1.0E-37f, ((Float) cstmt.getObject(2)), 0.0);
      assertNull(cstmt.getObject(3));
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement rstmt = con.createStatement()) {
        rstmt.execute("drop function real_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetShort01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table short_tab ( max_val int2, min_val int2, null_val int2 )");
      stmt.execute("insert into short_tab values (32767,-32768,null)");
      stmt.execute("create or replace function "
          + "short_proc( OUT IMAX int2, OUT IMIN int2, OUT INUL int2)  as "
          + "'begin "
          + "select max_val into imax from short_tab;"
          + "select min_val into imin from short_tab;"
          + "select null_val into inul from short_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call short_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.SMALLINT);
      cstmt.registerOutParameter(2, java.sql.Types.SMALLINT);
      cstmt.registerOutParameter(3, java.sql.Types.SMALLINT);
      cstmt.executeUpdate();
      assertEquals(32767, cstmt.getShort(1));
      assertEquals(-32768, cstmt.getShort(2));
      cstmt.getShort(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function short_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetInt01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table i_tab ( max_val int, min_val int, null_val int )");
      stmt.execute("insert into i_tab values (2147483647,-2147483648,null)");
      stmt.execute("create or replace function "
          + "int_proc( OUT IMAX int, OUT IMIN int, OUT INUL int)  as "
          + "'begin "
          + "select max_val into imax from i_tab;"
          + "select min_val into imin from i_tab;"
          + "select null_val into inul from i_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call int_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.INTEGER);
      cstmt.registerOutParameter(2, java.sql.Types.INTEGER);
      cstmt.registerOutParameter(3, java.sql.Types.INTEGER);
      cstmt.executeUpdate();
      assertEquals(2147483647, cstmt.getInt(1));
      assertEquals(-2147483648, cstmt.getInt(2));
      cstmt.getInt(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function int_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetLong01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table l_tab ( max_val int8, min_val int8, null_val int8 )");
      stmt.execute("insert into l_tab values (9223372036854775807,-9223372036854775808,null)");
      stmt.execute("create or replace function "
          + "bigint_proc( OUT IMAX int8, OUT IMIN int8, OUT INUL int8)  as "
          + "'begin "
          + "select max_val into imax from l_tab;"
          + "select min_val into imin from l_tab;"
          + "select null_val into inul from l_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call bigint_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.BIGINT);
      cstmt.registerOutParameter(2, java.sql.Types.BIGINT);
      cstmt.registerOutParameter(3, java.sql.Types.BIGINT);
      cstmt.executeUpdate();
      assertEquals(9223372036854775807L, cstmt.getLong(1));
      assertEquals(-9223372036854775808L, cstmt.getLong(2));
      cstmt.getLong(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function bigint_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetBoolean01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute(createBitTab);
      stmt.execute(insertBitTab);
      stmt.execute("create or replace function "
          + "bit_proc( OUT IMAX boolean, OUT IMIN boolean, OUT INUL boolean)  as "
          + "'begin "
          + "select max_val into imax from bit_tab;"
          + "select min_val into imin from bit_tab;"
          + "select null_val into inul from bit_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call bit_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.BIT);
      cstmt.registerOutParameter(2, java.sql.Types.BIT);
      cstmt.registerOutParameter(3, java.sql.Types.BIT);
      cstmt.executeUpdate();
      assertTrue(cstmt.getBoolean(1));
      assertEquals(false, cstmt.getBoolean(2));
      cstmt.getBoolean(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function bit_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testGetByte01() throws Throwable {
    try {
      Statement stmt = con.createStatement();
      stmt.execute("create temp table byte_tab ( max_val int2, min_val int2, null_val int2 )");
      stmt.execute("insert into byte_tab values (127,-128,null)");
      stmt.execute("create or replace function "
          + "byte_proc( OUT IMAX int2, OUT IMIN int2, OUT INUL int2)  as "
          + "'begin "
          + "select max_val into imax from byte_tab;"
          + "select min_val into imin from byte_tab;"
          + "select null_val into inul from byte_tab;"
          + " end;' "
          + "language plpgsql;");
      stmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
      throw ex;
    }
    try {
      CallableStatement cstmt = con.prepareCall("{ call byte_proc(?,?,?) }");
      cstmt.registerOutParameter(1, java.sql.Types.TINYINT);
      cstmt.registerOutParameter(2, java.sql.Types.TINYINT);
      cstmt.registerOutParameter(3, java.sql.Types.TINYINT);
      cstmt.executeUpdate();
      assertEquals(127, cstmt.getByte(1));
      assertEquals(-128, cstmt.getByte(2));
      cstmt.getByte(3);
      assertTrue(cstmt.wasNull());
      cstmt.close();
    }
    catch (Exception ex) {
      fail(ex.getMessage());
    }
    finally {
      try (Statement dstmt = con.createStatement()) {
        dstmt.execute("drop function byte_proc()");
      }
      catch (Exception ex) {
        // Expected...
      }
      // Ignore
    }
  }

  @Test
  public void testMultipleOutExecutions() throws SQLException {
    CallableStatement cs = con.prepareCall("{call myiofunc(?, ?)}");
    for (int i = 0; i < 10; i++) {
      cs.registerOutParameter(1, Types.INTEGER);
      cs.registerOutParameter(2, Types.INTEGER);
      cs.setInt(1, i);
      cs.execute();
      assertEquals(1, cs.getInt(1));
      assertEquals(i, cs.getInt(2));
      cs.clearParameters();
    }
    cs.close();
  }

  @Test
  public void testCallFunctionWithoutParentheses() throws SQLException {
    CallableStatement cs = con.prepareCall("{?=call current_timestamp}");
    cs.registerOutParameter(1, Types.TIMESTAMP);
    cs.execute();
    assertNotNull(cs.getTimestamp(1));
    cs.close();
  }

  @Test
  public void testLotsOfOutParameters() throws SQLException, MalformedURLException {

    try (Statement statement = con.createStatement()) {

      statement.execute("""
          \
          CREATE OR REPLACE FUNCTION fn_test_in_out_index(
              IN txt text,
              OUT a text,
              OUT b text,
              OUT c timestamp without time zone,
              OUT d timestamp with time zone,
              OUT e time without time zone,
              OUT f time with time zone,
              OUT g date,
              OUT h decimal,
              OUT i bytea,
              OUT j xml,
              OUT k uuid,
              OUT l text,
              OUT m text,
              OUT n bigint)
            RETURNS record AS
          $BODY$
          DECLARE\s
          BEGIN
          a := 'a-test';\s
          b := 'b-test';\s
          c := LOCALTIMESTAMP;
          d := LOCALTIMESTAMP;
          e := LOCALTIME;
          f := LOCALTIME;
          g := current_date;
          h := 9999999.12233445566778899;
          i := 'yo'::bytea;
          j := '<a></a>';
          k := '42009FB2-2FE5-4ED5-BE10-F3C9894018AB'::uuid;
          l := 'http://example.com';
          m := 'hi nikhil';
          n := 111;
          END;
          $BODY$
          LANGUAGE plpgsql VOLATILE COST 100;""");
      try {

        // Procedure call
        try (CallableStatement cstmt = con
            .prepareCall("{call fn_test_in_out_index(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {

          cstmt.setString(1, "test");

          cstmt.registerOutParameter(2, Types.VARCHAR);
          cstmt.registerOutParameter(3, Types.VARCHAR);
          cstmt.registerOutParameter(4, Types.TIMESTAMP);
          cstmt.registerOutParameter(5, Types.TIMESTAMP_WITH_TIMEZONE);
          cstmt.registerOutParameter(6, Types.TIME);
          cstmt.registerOutParameter(7, Types.TIME_WITH_TIMEZONE);
          cstmt.registerOutParameter(8, Types.DATE);
          cstmt.registerOutParameter(9, Types.DECIMAL);
          cstmt.registerOutParameter(10, Types.VARBINARY);
          cstmt.registerOutParameter(11, Types.SQLXML);
          cstmt.registerOutParameter(12, Types.OTHER);
          cstmt.registerOutParameter(13, Types.VARCHAR);
          cstmt.registerOutParameter(14, Types.VARCHAR);
          cstmt.registerOutParameter(15, Types.BIGINT);

          cstmt.execute();

          assertTrue(cstmt.getObject(2) instanceof String);
          assertEquals("a-test", cstmt.getString(2));
          assertTrue(cstmt.getObject(3) instanceof String);
          assertEquals("b-test", cstmt.getString(3));
          assertTrue(cstmt.getObject(4) instanceof Timestamp);
          assertNotNull(cstmt.getTimestamp(4));
          assertTrue(cstmt.getObject(5) instanceof Timestamp);
          assertNotNull(cstmt.getTimestamp(5));
          assertTrue(cstmt.getObject(6) instanceof Time);
          assertNotNull(cstmt.getTime(6));
          assertTrue(cstmt.getObject(7) instanceof Time);
          assertNotNull(cstmt.getTime(7));
          assertTrue(cstmt.getObject(8) instanceof Date);
          assertNotNull(cstmt.getDate(8));
          assertTrue(cstmt.getObject(9) instanceof BigDecimal);
          assertEquals(new BigDecimal("9999999.12233445566778899"), cstmt.getBigDecimal(9));
          assertArrayEquals("yo".getBytes(UTF_8), cstmt.getBytes(10));
          assertTrue(cstmt.getObject(11) instanceof SQLXML);
          assertTrue(cstmt.getObject(12) instanceof UUID);
          assertEquals(UUID.fromString("42009FB2-2FE5-4ED5-BE10-F3C9894018AB"), cstmt.getObject(12));
          assertTrue(cstmt.getObject(13) instanceof String);
          assertEquals(new URL("http://example.com"), cstmt.getURL(13));
          assertTrue(cstmt.getObject(14) instanceof String);
          assertEquals("hi nikhil", cstmt.getString(14));
          assertTrue(cstmt.getObject(15) instanceof Long);
          assertEquals(111L, cstmt.getLong(15));
        }
      }
      finally {
        statement.execute("DROP FUNCTION fn_test_in_out_index(text);");
      }

    }
  }

}
