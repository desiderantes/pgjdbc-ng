package com.impossibl.postgres.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class StringsTest {

  @Test
  fun testToAsciiDoc() {
    assertEquals("<ul>\n  <li>Hello World</li>\n</ul>\n".toAsciiDoc(), "\n\n-  Hello World\n\n")
  }

}
