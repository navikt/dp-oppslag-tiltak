package no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson3.jackson
import tools.jackson.databind.DeserializationFeature
import java.io.Closeable
import java.time.LocalDate

private val log = KotlinLogging.logger { }

/**
 * Én periode hvor personen deltok i et arbeidsmarkedstiltak.
 */
data class TiltaksdeltakelsePeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
)

/**
 * Klient mot tiltakshistorikk (team-mulighetsrommet).
 */
interface TiltakshistorikkKlient {
    suspend fun hentTiltaksdeltakelser(ident: String): List<TiltaksdeltakelsePeriode>
}

/**
 * HTTP-implementasjon mot tiltakshistorikk sitt `POST /api/v1/historikk`
 * (se https://github.com/navikt/mulighetsrommet/blob/main/common/tiltakshistorikk-client/src/main/kotlin/no/nav/tiltak/historikk/TiltakshistorikkClient.kt),
 * med Azure AD app-to-app-autentisering (client credentials).
 *
 * Responsens `historikk`-liste inneholder tre diskriminerte deltakelsestyper
 * (ArenaDeltakelse/TeamKometDeltakelse/TeamTiltakAvtale) som alle deler felt
 * for start- og sluttdato. Vi bryr oss kun om disse periodene her, og lar
 * Jackson ignorere resten av de typespesifikke feltene.
 */
class TiltakshistorikkHttpKlient(
    private val url: String,
    private val tokenSupplier: suspend () -> String,
    private val httpClient: HttpClient = nyHttpClient(),
) : TiltakshistorikkKlient,
    Closeable {
    override suspend fun hentTiltaksdeltakelser(ident: String): List<TiltaksdeltakelsePeriode> {
        val respons: TiltakshistorikkV1Response =
            httpClient
                .post("$url/api/v1/historikk") {
                    bearerAuth(tokenSupplier())
                    contentType(ContentType.Application.Json)
                    setBody(TiltakshistorikkV1Request(identer = listOf(ident)))
                }.body()

        loggEventuelleMeldinger(respons.meldinger)
        return respons.historikk.tilPerioder()
    }

    private fun loggEventuelleMeldinger(meldinger: List<String>) {
        if (meldinger.isNotEmpty()) {
            log.warn { "Fikk meldinger fra tiltakshistorikk: $meldinger" }
        }
    }

    override fun close() = httpClient.close()

    companion object {
        fun nyHttpClient() =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson {
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
                    }
                }
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = 5)
                    constantDelay(millis = 100, randomizationMs = 0)
                }
            }
    }
}

private data class TiltakshistorikkV1Request(
    val identer: List<String>,
)

private data class TiltakshistorikkV1Response(
    val historikk: List<Tiltaksdeltakelse>,
    val meldinger: List<String> = emptyList(),
)

/**
 * Slanke felt felles for alle tre deltakelsestypene i `historikk` - de
 * typespesifikke feltene (arrangør, status, tiltakstype osv.) er ikke
 * relevante for å periodisere deltakelse og hoppes derfor over.
 */
private data class Tiltaksdeltakelse(
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
)

/**
 * Deltakelser uten [Tiltaksdeltakelse.startDato] kan ikke periodiseres og
 * forkastes (og logges), fremfor å la dem feile hele oppslaget.
 */
private fun List<Tiltaksdeltakelse>.tilPerioder(): List<TiltaksdeltakelsePeriode> =
    mapNotNull { deltakelse ->
        val startDato = deltakelse.startDato
        if (startDato == null) {
            log.warn { "Ignorerer tiltaksdeltakelse uten startDato" }
            return@mapNotNull null
        }
        TiltaksdeltakelsePeriode(fraOgMed = startDato, tilOgMed = deltakelse.sluttDato)
    }
