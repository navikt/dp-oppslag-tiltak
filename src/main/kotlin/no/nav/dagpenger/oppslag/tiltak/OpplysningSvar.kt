package no.nav.dagpenger.oppslag.tiltak

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltaksdeltakelsePeriode
import java.util.UUID

/**
 * Nøkkelen `@løsning` besvares under, for opplysningstypen
 * `deltarIArbeidsmarkedstiltakId` (UUID [OPPLYSNING_ID]) i dp-behandling. Må
 * matche `behovId` (default: beskrivelsen) slik den er registrert i
 * dp-behandling sin `OpplysningsTyper.kt`/`Utdanning.kt`.
 */
internal const val DELTAR_I_ARBEIDSMARKEDSTILTAK_BEHOV = "Deltar i arbeidsmarkedstiltak"
internal val DELTAR_I_ARBEIDSMARKEDSTILTAK_OPPLYSNING_ID: UUID = UUID.fromString("0194881f-9445-734c-a7ee-045edf29b527")

/**
 * Svar på opplysningsbehovet `deltarIArbeidsmarkedstiltakId`: én periodisert
 * verdi (`true`) per periode personen har deltatt i et arbeidsmarkedstiltak,
 * hentet fra mulighetsrommet-tiltakshistorikk.
 */
data class DeltarIArbeidsmarkedstiltakSvar(
    val behandlingId: UUID,
    val ident: String,
    val perioder: List<TiltaksdeltakelsePeriode>,
)

/**
 * Serialiserer via [JsonMessage] (samme oppsett som resten av
 * meldingsflyten), til formatet dp-behandling sin `OpplysningSvarMottak`
 * forventer for et opplysningsbehov.
 */
fun DeltarIArbeidsmarkedstiltakSvar.toJson(): String =
    JsonMessage
        .newMessage(
            mapOf(
                "@event_name" to "behov",
                "@final" to true,
                "@opplysningsbehov" to true,
                "ident" to ident,
                "behandlingId" to behandlingId,
                "@løsning" to
                    mapOf(
                        DELTAR_I_ARBEIDSMARKEDSTILTAK_BEHOV to perioder.map { it.tilLøsningsperiode() },
                    ),
            ),
        ).toJson()

private fun TiltaksdeltakelsePeriode.tilLøsningsperiode(): Map<String, Any> =
    buildMap {
        put("verdi", true)
        put("gyldigFraOgMed", fraOgMed.toString())
        tilOgMed?.let { put("gyldigTilOgMed", it.toString()) }
    }
