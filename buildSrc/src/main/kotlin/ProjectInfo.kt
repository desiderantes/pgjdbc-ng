
object ProjectInfo {
    object Organization{
        const val NAME = "impossibl"
        const val URL = "https://github.com/$NAME"
    }

    val NAME = "pgjdbc-ng"

    val URL = "${Organization.URL}/pgjdbc-ng"

    val ISSUES_URL = "$URL/issues"
    val SCM_URL = "scm:$URL.git"
    val SCM_GIT_URL = "scm:git@github.com:${Organization.NAME}/pgjdbc-ng.git"
    val GROUP_ID = "com.impossibl"

    val DEVELOPERS = listOf (
        Developer (
            id = "kdubb0",
            name = "Kevin Wooten"
        ),
        Developer (
            id = "brettwooldridge",
            name = "Brett Wooldridge"
        ),
        Developer (
            id = "jesperpedersen",
            name = "Jesper Pedersen",
        )
    )

    object License {
        val LICENSE_NAME = "Apache-2.0"
        val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    }

    data class Developer (
        val id: String,
        val name: String,
        val organization: String? = null,
        val email: String? = null,
        val roles: List<String>? = null,
        val url: String? = null,
    )
}