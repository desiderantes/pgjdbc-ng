/*
 * Copyright (c) 2017, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package com.impossibl.postgres.jdbc.shared;

import com.impossibl.postgres.jdbc.TestUtil;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


public class GetObject310InfinityTests {
  private String expression;
  private String pgType;
  private Class<?> klass;
  private Object expectedValue;

  public void initGetObject310InfinityTests(String expression, String pgType, Class klass, Object expectedValue) {
    this.expression = expression;
    this.pgType = pgType;
    this.klass = klass;
    this.expectedValue = expectedValue;
  }

  public static Iterable<Object[]> data() throws IllegalAccessException {
    Collection<Object[]> ids = new ArrayList<>();
    for (String expression : Arrays.asList("-infinity", "infinity")) {
      for (String pgType : Arrays.asList("date", "timestamp", "timestamp with time zone")) {
        for (Class klass : Arrays.asList(LocalDate.class, LocalDateTime.class,
            OffsetDateTime.class)) {
          if (klass.equals(LocalDate.class) && !pgType.equals("date")) {
            continue;
          }
          if (klass.equals(LocalDateTime.class) && !pgType.startsWith("timestamp")) {
            continue;
          }
          if (klass.equals(OffsetDateTime.class) && !pgType.startsWith("timestamp with time zone")) {
            continue;
          }
          if (klass.equals(LocalDateTime.class) && pgType.equals("timestamp with time zone")) {
            // org.postgresql.util.PSQLException: Cannot convert the column of type TIMESTAMPTZ to requested type timestamp.
            continue;
          }
          Field field;
          try {
            field = klass.getField(expression.startsWith("-") ? "MIN" : "MAX");
          } catch (NoSuchFieldException e) {
            throw new IllegalStateException("No min/max field in " + klass, e);
          }
          Object expected = field.get(null);
          ids.add(new Object[]{expression, pgType, klass, expected});
        }
      }
    }
    return ids;
  }

  private Connection con;

  @BeforeEach
  public void setup() throws SQLException {
    con = TestUtil.openDB();
  }

  @AfterEach
  public void tearDown() throws SQLException {
    TestUtil.closeDB(con);
  }

  @MethodSource("data")
  @ParameterizedTest(name = "binary = {0}, expr = {1}, pgType = {2}, klass = {3}")
  public void test(String expression, String pgType, Class klass, Object expectedValue) throws SQLException {
    initGetObject310InfinityTests(expression, pgType, klass, expectedValue);
    PreparedStatement stmt = con.prepareStatement("select '" + expression + "'::" + pgType);
    ResultSet rs = stmt.executeQuery();
    rs.next();
    Object res = rs.getObject(1, klass);
    Assertions.assertEquals(expectedValue, res);
  }

}
