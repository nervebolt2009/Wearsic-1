package com.wearsic.server

import io.ktor.http.HttpStatusCode
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

/** HTTP status + user-facing message for a NewPipe extraction failure. */
data class ExtractionError(val status: HttpStatusCode, val message: String)

/**
 * Maps NewPipe extraction failures to clean JSON errors instead of raw HTTP 500s.
 * Order matters: the specific bot-check/video-state exceptions subclass
 * ExtractionException, so they must be matched before the generic branch.
 */
fun mapExtractionError(error: Throwable): ExtractionError = when (error) {
    is SignInConfirmNotBotException -> ExtractionError(
        HttpStatusCode.ServiceUnavailable,
        "YouTube requires authentication for audio extraction. Add your YouTube cookie in the app Settings."
    )
    is ContentNotAvailableException -> ExtractionError(
        HttpStatusCode.NotFound,
        "This video is not available or has been removed."
    )
    is ReCaptchaException -> ExtractionError(
        HttpStatusCode.ServiceUnavailable,
        "YouTube is challenging this server. Try again in a few minutes or configure a YouTube cookie."
    )
    is ExtractionException -> ExtractionError(
        HttpStatusCode.BadGateway,
        "YouTube audio extraction failed. If this keeps happening, update the extractor or configure a YouTube cookie."
    )
    else -> ExtractionError(
        HttpStatusCode.BadGateway,
        "Audio extraction failed. Check the server logs and YouTube cookie configuration."
    )
}
