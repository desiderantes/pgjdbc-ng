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

import com.impossibl.postgres.api.data.ACLItem;

import java.text.ParseException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ACLItemTest {

  @Test
  public void testRightsParsing() {

    ACLItem.Right[] allRightsWithOptions =
        ACLItem.ALL_PRIVILEGES.chars()
            .mapToObj(p -> new ACLItem.Right((char)p, true))
            .toArray(ACLItem.Right[]::new);

    String allRightsWithOptionsStr =
        ACLItem.ALL_PRIVILEGES.chars()
            .mapToObj(p -> (char)p + "*")
            .collect(Collectors.joining());

    ACLItem.Right[] allRightsWithOptionsResult = assertDoesNotThrow(() -> ACLItem.rightsOf(allRightsWithOptionsStr));
    assertArrayEquals(allRightsWithOptions, allRightsWithOptionsResult);

    ACLItem.Right[] allRightsWithoutOptions =
        ACLItem.ALL_PRIVILEGES.chars()
            .mapToObj(p -> new ACLItem.Right((char)p, false))
            .toArray(ACLItem.Right[]::new);

    ACLItem.Right[] allRightsWithoutOptionsResult = assertDoesNotThrow(() -> ACLItem.rightsOf(ACLItem.ALL_PRIVILEGES));
    assertArrayEquals(allRightsWithoutOptions, allRightsWithoutOptionsResult);

    ACLItem.Right[] singleRight = new ACLItem.Right[]{new ACLItem.Right('a', false)};
    ACLItem.Right[] singleRightResult = assertDoesNotThrow(() -> ACLItem.rightsOf("a"));
    assertArrayEquals(singleRight, singleRightResult);

    ACLItem.Right[] singleRightWithOption = new ACLItem.Right[]{new ACLItem.Right('a', true)};
    ACLItem.Right[] singleRightWithOptionResult = assertDoesNotThrow(() -> ACLItem.rightsOf("a*"));
    assertArrayEquals(singleRightWithOption, singleRightWithOptionResult);

    ACLItem.Right[] multipleRightsNoOptions = new ACLItem.Right[]{
        new ACLItem.Right('a', false), new ACLItem.Right('D', false)
    };
    ACLItem.Right[] multipleRightsNoOptionsResult = assertDoesNotThrow(() -> ACLItem.rightsOf("aD"));
    assertArrayEquals(multipleRightsNoOptions, multipleRightsNoOptionsResult);

    ACLItem.Right[] noRights = new ACLItem.Right[0];
    ACLItem.Right[] noRightsResult = assertDoesNotThrow(() -> ACLItem.rightsOf(""));
    assertArrayEquals(noRights, noRightsResult);

    ACLItem.Right[] nullResult = assertDoesNotThrow(() -> ACLItem.rightsOf(null));
    assertArrayEquals(null, nullResult);
  }

  @Test
  public void testInvalidRightsParsing1() {
    assertThrows(ParseException.class, () -> ACLItem.rightsOf("*"));
  }

  @Test
  public void testInvalidRightsParsing2() {
    assertThrows(ParseException.class, () -> ACLItem.rightsOf("a**"));
  }

  @Test
  public void testInvalidRightsParsing3() {
    assertThrows(ParseException.class, () -> ACLItem.rightsOf("q"));
  }

  @Test
  public void testInvalidRightsParsing4() {
    assertThrows(ParseException.class, () -> ACLItem.rightsOf("*a"));
  }

}
