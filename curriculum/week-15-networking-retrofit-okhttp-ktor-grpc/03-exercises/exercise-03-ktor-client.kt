// Exercise 3 — The same client in Ktor (and the same NetworkResult)
//
// Goal: Build a Ktor Client that does the SAME job as the Retrofit service from
//       exercise 1 — typed GET, kotlinx-serialization, returning the SAME
//       NetworkResult sealed type — and test it against Ktor's MockEngine (no real
//       network). The point is to feel that the DISCIPLINE is identical (typed
//       client, safe parsing, sealed result) and only the ENGINE changes. This is
//       why the capstone's KMP shared core can use Ktor: same code shape, more
//       platforms.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a plain JVM test (`./gradlew test`). Ktor's MockEngine lets you
// script HTTP responses (a 200 with a JSON body, a 500, a malformed body) without
// a real server, so the failure mapping is deterministic and offline.
//
//   1. Add this file to src/test/kotlin. Depends on ktor-client-core,
//      ktor-client-mock, ktor-client-content-negotiation, and
//      ktor-serialization-kotlinx-json.
//   2. Run with `./gradlew test`.
//   3. The assertions enforce: a 200 -> Success(parsed), a 500 -> HttpError(500),
//      a malformed body -> SerializationError.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] You can name the ONE decisive reason to choose Ktor over Retrofit
//       (multiplatform: it compiles in commonMain).
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.net.week15

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// The SAME @Serializable DTO you'd use with Retrofit. kotlinx-serialization is
// the shared serializer across Retrofit AND Ktor — these DTOs are reusable.
// ----------------------------------------------------------------------------

@Serializable
data class ForecastDto(
    val city: String,
    @SerialName("temp_c") val temperatureC: Double,
    val condition: String
)

// ----------------------------------------------------------------------------
// A Ktor-flavoured safeApiCall mapping Ktor's exceptions to the SAME NetworkResult
// cases as exercise 2. ServerResponseException = 5xx, ClientRequestException = 4xx.
// ----------------------------------------------------------------------------

suspend fun <T> safeKtorCall(block: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(block())
    } catch (e: ClientRequestException) {          // 4xx
        NetworkResult.HttpError(e.response.status.value)
    } catch (e: ServerResponseException) {         // 5xx
        NetworkResult.HttpError(e.response.status.value)
    } catch (e: SerializationException) {
        NetworkResult.SerializationError(e)
    } catch (e: IOException) {
        NetworkResult.NetworkError(e)
    }

// ----------------------------------------------------------------------------
// The Ktor client. ContentNegotiation + json() is Ktor's equivalent of Retrofit's
// converter factory. expectSuccess = true makes Ktor THROW on non-2xx so our
// safeKtorCall can map it (otherwise non-2xx is a normal response).
// ----------------------------------------------------------------------------

class WeatherKtorClient(engine: MockEngine) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun forecast(city: String): NetworkResult<ForecastDto> = safeKtorCall {
        client.get("https://api.weather.example/forecast") {
            parameter("city", city)
        }.body<ForecastDto>()
    }
}

// ----------------------------------------------------------------------------
// Tests, each with a MockEngine scripting one HTTP outcome.
// ----------------------------------------------------------------------------

class KtorClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun ok_response_maps_to_Success_with_parsed_dto() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"city":"Lisbon","temp_c":21.5,"condition":"Sunny"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val result = WeatherKtorClient(engine).forecast("Lisbon")
        assertTrue(result is NetworkResult.Success)
        assertEquals("Lisbon", result.data.city)
        assertEquals(21.5, result.data.temperatureC)
    }

    @Test
    fun server_500_maps_to_HttpError() = runTest {
        val engine = MockEngine {
            respond(content = "boom", status = HttpStatusCode.InternalServerError)
        }
        val result = WeatherKtorClient(engine).forecast("Lisbon")
        assertTrue(result is NetworkResult.HttpError && result.code == 500)
    }

    @Test
    fun client_404_maps_to_HttpError() = runTest {
        val engine = MockEngine {
            respond(content = "not found", status = HttpStatusCode.NotFound)
        }
        val result = WeatherKtorClient(engine).forecast("Atlantis")
        assertTrue(result is NetworkResult.HttpError && result.code == 404)
    }

    @Test
    fun malformed_body_maps_to_SerializationError() = runTest {
        val engine = MockEngine {
            // Missing the required temp_c field -> a parse failure.
            respond(
                content = """{"city":"Lisbon","condition":"Sunny"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val result = WeatherKtorClient(engine).forecast("Lisbon")
        assertTrue(result is NetworkResult.SerializationError)
    }
}

// ----------------------------------------------------------------------------
// The ONE decisive reason to choose Ktor over Retrofit (write before reading):
//
//   Ktor Client compiles in commonMain — the SAME client code runs on Android
//   (OkHttp engine) and iOS (Darwin engine) in a Kotlin Multiplatform module.
//   Retrofit is JVM/Android-only. So when the capstone shares its data layer
//   between Android and iOS, the client must be Ktor. For an Android-only app,
//   Retrofit's ecosystem usually wins. Notice everything else here — the
//   @Serializable DTO, the NetworkResult, the safe* wrapper, ignoreUnknownKeys —
//   is IDENTICAL to the Retrofit path. The discipline is constant; the engine
//   changes.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - expectSuccess = true is what makes Ktor THROW ClientRequestException /
//   ServerResponseException on non-2xx. Without it, get() returns the error
//   response normally and your safeKtorCall never sees an exception to map.
//
// - MockEngine { respond(...) } scripts the HTTP response. The lambda runs per
//   request; for retry tests you can branch on a call counter like exercise 2's
//   FakeApi.
//
// - .body<ForecastDto>() is where ContentNegotiation parses the JSON. A missing
//   required field throws SerializationException there — that's the malformed-body
//   test path.
//
// - The DTO and NetworkResult are reused from the Retrofit exercises ON PURPOSE.
//   If you find yourself rewriting them differently for Ktor, step back — the
//   shared shape is the lesson.
//
// ----------------------------------------------------------------------------
