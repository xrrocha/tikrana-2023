package plenix.tikrana.webserver

import arrow.core.left
import arrow.core.right
import plenix.tikrana.util.ApplicationFailure

object ContentTypes {

    private val ContentTypeRegex = "[^/]+/[^/]+".toRegex()

    fun isValid(contentType: ContentType) = ContentTypeRegex.matches(contentType)

    fun validate(contentType: ContentType) =
        if (isValid((contentType))) contentType.right()
        else ApplicationFailure("Invalid content type: $contentType").left()
}