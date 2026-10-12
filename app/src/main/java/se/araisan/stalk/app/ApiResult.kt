package se.araisan.stalk.app

import android.content.Context
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

// RFC 9457 error body (application/problem+json) sent by stalk-api for every error.
@Serializable
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    /** Present on 400 validation errors, one entry per invalid field. */
    val errors: List<FieldError>? = null,
)

@Serializable
data class FieldError(
    /** JSON pointer into the request body, e.g. "#/latitude". */
    val pointer: String,
    val detail: String,
)

sealed class ApiResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : ApiResult<T>()

    data class HttpError(
        val code: Int,
        val problem: ProblemDetails?,
    ) : ApiResult<Nothing>()

    data class NetworkError(
        val cause: Throwable,
    ) : ApiResult<Nothing>()

    fun userMessage(context: Context): String =
        when (this) {
            is Success -> context.getString(R.string.api_ok)
            is NetworkError -> context.getString(R.string.api_network_error)
            is HttpError -> httpErrorMessage(context, code, problem)
        }
}

private fun httpErrorMessage(
    context: Context,
    code: Int,
    problem: ProblemDetails?,
): String {
    val errors = problem?.errors.orEmpty()
    return when {
        // The server says "Missing bearer token" or "Invalid bearer token".
        code == HTTP_UNAUTHORIZED && problem?.detail?.startsWith("Missing") == true -> {
            context.getString(R.string.api_key_missing)
        }

        code == HTTP_UNAUTHORIZED -> {
            context.getString(R.string.api_key_invalid)
        }

        code == HTTP_BAD_REQUEST && errors.isNotEmpty() -> {
            context.getString(
                R.string.api_invalid_location,
                errors.joinToString("; ") { "${it.pointer.removePrefix("#/")} ${it.detail}" },
            )
        }

        problem?.detail != null -> {
            problem.detail
        }

        else -> {
            context.getString(R.string.api_request_failed, code)
        }
    }
}
