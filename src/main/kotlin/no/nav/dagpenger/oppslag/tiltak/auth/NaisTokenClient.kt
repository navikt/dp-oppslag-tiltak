package no.nav.dagpenger.oppslag.tiltak.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.jackson3.jackson

/**
 * Henter Entra ID M2M-token via Nais sitt token-endepunkt (texas-sidecaren),
 * se https://doc.nais.io/auth/entra-id/how-to/consume-m2m/.
 */
class NaisTokenClient(
    private val tokenEndpoint: String,
    private val target: String,
    private val httpClient: HttpClient = nyHttpClient(),
) {
    suspend fun hentToken(): String = hentNyttToken()

    private suspend fun hentNyttToken(): String {
        val respons: TokenResponse =
            httpClient
                .submitForm(
                    url = tokenEndpoint,
                    formParameters =
                        Parameters.build {
                            append("identity_provider", "entra_id")
                            append("target", target)
                        },
                ).body()
        return respons.access_token
    }

    // Feltnavnene speiler JSON-responsen fra Nais' token-endepunkt direkte.
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Long,
        val token_type: String,
    )

    companion object {
        private fun nyHttpClient() =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson()
                }
            }
    }
}
