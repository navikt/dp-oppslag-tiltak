package no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class TiltakshistorikkHttpKlientTest {
    private val token = "et-fint-access-token"

    @Test
    fun `sender riktig url, metode, body og Authorization-header`() {
        var mottattRequest: HttpRequestData? = null

        val httpClient =
            HttpClient(
                MockEngine { request ->
                    mottattRequest = request
                    respond(
                        content = """{"historikk": [], "meldinger": []}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { jackson() }
            }

        val klient =
            TiltakshistorikkHttpKlient(
                url = "http://tiltakshistorikk.team-mulighetsrommet",
                tokenSupplier = { token },
                httpClient = httpClient,
            )

        runBlocking { klient.hentTiltaksdeltakelser("11109233444") }

        val request = requireNotNull(mottattRequest)
        request.url.toString() shouldBe "http://tiltakshistorikk.team-mulighetsrommet/api/v1/historikk"
        request.method shouldBe HttpMethod.Post
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer $token"

        val body = String(runBlocking { request.body.toByteArray() })
        body shouldContain "\"identer\""
        body shouldContain "11109233444"
    }

    @Test
    fun `mapper historikk til perioder og hopper over deltakelse uten startDato`() {
        val httpClient =
            HttpClient(
                MockEngine {
                    respond(
                        content =
                            """
                            {
                              "historikk": [
                                { "type": "ArenaDeltakelse", "startDato": "2026-01-01", "sluttDato": "2026-01-31" },
                                { "type": "TeamKometDeltakelse", "startDato": "2026-03-01", "sluttDato": null },
                                { "type": "TeamTiltakAvtale", "startDato": null, "sluttDato": null }
                              ],
                              "meldinger": []
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { jackson() }
            }

        val klient =
            TiltakshistorikkHttpKlient(
                url = "http://tiltakshistorikk.team-mulighetsrommet",
                tokenSupplier = { token },
                httpClient = httpClient,
            )

        val perioder = runBlocking { klient.hentTiltaksdeltakelser("11109233444") }

        perioder shouldBe
            listOf(
                TiltaksdeltakelsePeriode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                TiltaksdeltakelsePeriode(LocalDate.of(2026, 3, 1), null),
            )
    }
}

private suspend fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray =
    when (this) {
        is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
        is io.ktor.http.content.OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readByteArray()
        is io.ktor.http.content.OutgoingContent.WriteChannelContent -> {
            val channel =
                io.ktor.utils.io
                    .ByteChannel()
            writeTo(channel)
            channel.close()
            channel.readRemaining().readByteArray()
        }
        else -> error("Ustøttet body-type i test: $this")
    }
