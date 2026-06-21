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


import java.sql.SQLException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SQLTextTests {

  String[][] sqlTransformTests = new String[][] {
    new String[] {

      //Input
        """
select "somthing" -- This is a SQL comment ?WTF?
 from
   test
 where
   'a string with a ?' =  ?""",

      //Output
        """
select "somthing" -- This is a SQL comment ?WTF?
 from
   test
 where
   'a string with a ?' =  $1"""
    },
    new String[] {

      //Input
        """
insert into "somthing" -- This is a SQL comment ?WTF?
 (a, "b", "c", "d")
 values /* a nested
 /* comment with  */ a ? */\
 (?,'a string with a ?', "another ?", ?, ?)""",

      //Output
        """
insert into "somthing" -- This is a SQL comment ?WTF?
 (a, "b", "c", "d")
 values /* a nested
 /* comment with  */ a ? */\
 ($1,'a string with a ?', "another ?", $2, $3)""",
    },
    new String[] {

      //Input
        """
insert into "somthing" -- This is a SQL comment ?WTF?
 (a, "b", "c", "d")
 values /* a nested
 /* comment with  */ a ? */\
 (?,'a string with a ?', "another "" ?", {fn concat('{fn '' some()}', {fn char(?)})}, ?)""",

      //Output
        """
insert into "somthing" -- This is a SQL comment ?WTF?
 (a, "b", "c", "d")
 values /* a nested
 /* comment with  */ a ? */\
 ($1,'a string with a ?', "another "" ?", ('{fn '' some()}'||chr($2)), $3)""",
    },
    new String[] {
      "select {fn abs(-10)} as absval, {fn user()}, {fn concat(x,y)} as val from {oj tblA left outer join tblB on x=y}",
      "select abs(-10) as absval, user, (x||y) as val from tblA left OUTER JOIN tblB ON x=y",
    },
    new String[] {
      "--\n--",
      "--\n--",
    },
  };

  /**
   * Tests transforming JDBC SQL input into PostgreSQL's wire
   * protocol format.
   * @throws SQLException
   * @throws ParseException
   *
   * @see SQLTextUtils.getProtocolSQLText
   */
  @Test
  public void testPostgreSQLText() throws SQLException, ParseException {

    for (String[] test : sqlTransformTests) {

      String expected = test[1];

      SQLText sqlText = new SQLText(test[0]);

      SQLTextEscapes.processEscapes(sqlText, null);

      assertThat(sqlText.toString(), is(equalTo(expected)));
    }
  }

  @Test
  public void testTruncate() throws SQLException, ParseException {
    String sql = """
        SELECT
              folder.entity_id  AS folder_id
            , archive_pers.entity_id AS person_id
        /*
            , archive_pers.initials AS person_initials
        */
            ,ARRAY(WITH RECURSIVE t AS (SELECT
                                           1 AS level,
                                           p.entity_id,
                                           p.id,
                                           p.parent_id,
                                           p.name
                                       FROM pants_krank_project p
                                       WHERE p.entity_id = 1
                                       UNION ALL
                                       SELECT
                                           t.level + 1,
                                           c.entity_id,
                                           c.id,
                                           c.parent_id,
                                           c.name
                                       FROM pants_krank_project c JOIN t ON c.id = t.parent_id)
                  SELECT
                      t.name
                  FROM t
                  ORDER BY level DESC)
              AS project_name_array
        FROM
            fishy_email_delivery del JOIN fishy_email_folder_message fm ON fm.delivery_id = del.entity_id
            JOIN fishy_email_folder folder ON folder.entity_id = fm.folder_id
            JOIN pants_krank_entity ent ON ent.entity_id = folder.owner_id
            CROSS JOIN pants_krank_relation archive_comp
            LEFT OUTER JOIN pants_krank_person archive_pers ON archive_pers.entity_id = folder.owner_id
        WHERE 1 = 1
              AND NOT EXISTS(SELECT * FROM
            fishy_email_folder ef JOIN fishy_email_mailbox em ON ef.entity_id = em.folder_id
        WHERE ef.entity_id = folder.entity_id)
              AND folder.owner_id IN (
            SELECT
                archive_comp.entity_id
            WHERE archive_comp.entity_id = folder.owner_id
            UNION ALL SELECT
                          pers.entity_id
                      FROM pants_krank_person pers
                      WHERE pers.relation_id = archive_comp.entity_id AND folder.owner_id = pers.entity_id
            UNION ALL SELECT
                          pr.entity_id
                      FROM pants_krank_project pr
                      WHERE pr.relation_id = archive_comp.entity_id AND folder.owner_id = pr.entity_id
            UNION ALL SELECT
                          1
                      FROM fishy_project_phase ph
                          JOIN fishy_project_phase_category cat ON 1 = cat.entity_id
                          JOIN pants_krank_project pr ON cat.project_id = pr.entity_id
                      WHERE pr.relation_id = archive_comp.entity_id AND folder.owner_id = 3
        )
              AND archive_comp.entity_id = 2
        --          AND proj.entity_id = 890
        ORDER BY del.received_timestamp DESC""";

    SQLText sqlText = new SQLText(sql);
    SQLTextEscapes.processEscapes(sqlText, null);
  }
}
