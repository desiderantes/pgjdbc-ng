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

import com.impossibl.postgres.api.jdbc.PGConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * TestCase to test the internal functionality of
 * org.postgresql.jdbc2.DatabaseMetaData's various properties.
 * Methods which return a ResultSet are tested elsewhere.
 * This avoids a complicated setUp/tearDown for something like
 * assertTrue(dbmd.nullPlusNonNullIsNull());
 */
public class DatabaseMetaDataPropertiesTest extends ParameterizedVersionTest {

  private Connection con;

  @BeforeEach
  public void before() throws Exception {
    con = TestUtil.openDB();
  }

  @AfterEach
  public void after() throws Exception {
    TestUtil.closeDB(con);
  }

  /*
   * The spec says this may return null, but we always do!
   */
  @Test
  public void testGetMetaData() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
  }

  /*
   * Test default capabilities
   */
  @Test
  public void testCapabilities() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertTrue(dbmd.allProceduresAreCallable());
    assertTrue(dbmd.allTablesAreSelectable()); // not true all the time

    // This should always be false for postgresql (at least for 7.x)
    assertFalse(dbmd.isReadOnly());

    // we support multiple resultsets via multiple statements in one execute()
    // now
    assertTrue(dbmd.supportsMultipleResultSets());

    // yes, as multiple backends can have transactions open
    assertTrue(dbmd.supportsMultipleTransactions());

    assertTrue(dbmd.supportsMinimumSQLGrammar());
    assertTrue(dbmd.supportsCoreSQLGrammar());
    assertFalse(dbmd.supportsExtendedSQLGrammar());
    assertTrue(dbmd.supportsANSI92EntryLevelSQL());
    assertFalse(dbmd.supportsANSI92IntermediateSQL());
    assertFalse(dbmd.supportsANSI92FullSQL());

    assertTrue(dbmd.supportsIntegrityEnhancementFacility());

  }

  @Test
  public void testJoins() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertTrue(dbmd.supportsOuterJoins());
    assertTrue(dbmd.supportsFullOuterJoins());
    assertTrue(dbmd.supportsLimitedOuterJoins());
  }

  @Test
  public void testCursors() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

//TODO: reconcile against mainstream driver
//    assertTrue(!dbmd.supportsPositionedDelete());
//    assertTrue(!dbmd.supportsPositionedUpdate());
  }

  @Test
  public void testValues() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
    int indexMaxKeys = dbmd.getMaxColumnsInIndex();
    assertEquals(32, indexMaxKeys);
  }

  @Test
  public void testNulls() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertFalse(dbmd.nullsAreSortedAtStart());
    assertFalse(dbmd.nullsAreSortedAtEnd());
    assertTrue(dbmd.nullsAreSortedHigh());
    assertFalse(dbmd.nullsAreSortedLow());

    assertTrue(dbmd.nullPlusNonNullIsNull());

    assertTrue(dbmd.supportsNonNullableColumns());
  }

  @Test
  public void testLocalFiles() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertFalse(dbmd.usesLocalFilePerTable());
    assertFalse(dbmd.usesLocalFiles());
  }

  @Test
  public void testIdentifiers() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertFalse(dbmd.supportsMixedCaseIdentifiers()); // always false
    assertTrue(dbmd.supportsMixedCaseQuotedIdentifiers()); // always true

    assertFalse(dbmd.storesUpperCaseIdentifiers()); // always false
    assertTrue(dbmd.storesLowerCaseIdentifiers()); // always true
    assertFalse(dbmd.storesUpperCaseQuotedIdentifiers()); // always false
    assertFalse(dbmd.storesLowerCaseQuotedIdentifiers()); // always false
    assertFalse(dbmd.storesMixedCaseQuotedIdentifiers()); // always false

    assertEquals("\"", dbmd.getIdentifierQuoteString());

  }

  @Test
  public void testTables() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    // we can add columns
    assertTrue(dbmd.supportsAlterTableWithAddColumn());

    // we can drop columns
    assertTrue(dbmd.supportsAlterTableWithDropColumn());
  }

  @Test
  public void testSelect() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    // yes we can?: SELECT col a FROM a;
    assertTrue(dbmd.supportsColumnAliasing());

    // yes we can have expressions in ORDERBY
    assertTrue(dbmd.supportsExpressionsInOrderBy());

    // Yes, an ORDER BY clause can contain columns that are not in the
    // SELECT clause.
    assertTrue(dbmd.supportsOrderByUnrelated());

    assertTrue(dbmd.supportsGroupBy());
    assertTrue(dbmd.supportsGroupByUnrelated());
    assertTrue(dbmd.supportsGroupByBeyondSelect()); // needs checking
  }

  @Test
  public void testDBParams() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertEquals(dbmd.getURL(), TestUtil.getURL());
    assertEquals(dbmd.getUserName(), TestUtil.getUser());
  }

  @Test
  public void testDbProductDetails() throws SQLException {
    assertNotNull(con.unwrap(PGConnection.class));

    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertEquals("PostgreSQL", dbmd.getDatabaseProductName());
  }

  @Test
  public void testDriverVersioning() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);

    assertEquals(dbmd.getDriverVersion(), PGDriver.VERSION.toString());
    assertEquals(dbmd.getDriverMajorVersion(), PGDriver.VERSION.getMajor());
    assertEquals(dbmd.getDriverMinorVersion(), (int) PGDriver.VERSION.getMinor());
  }
}
