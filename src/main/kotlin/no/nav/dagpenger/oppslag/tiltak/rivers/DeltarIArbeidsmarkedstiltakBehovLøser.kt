package no.nav.dagpenger.oppslag.tiltak.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltaksdeltakelsePeriode
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltakshistorikkKlient
import java.time.LocalDate

/**
 * Lytter på det ordinære behovet `Arbeidsmarkedstiltak` fra dp-behandling
 * (via `BehovMediator`, med `ident` og `Prøvingsdato` som kontekst), slår opp
 * personens tiltakshistorikk hos tiltakshistorikk og svarer på om personen
 * deltar i et arbeidsmarkedstiltak på prøvingsdatoen.
 *
 * I motsetning til [MeldekortMedUtdanningAvklaringLøser] - som løser ut
 * `NyAvklaring` uten noen pålitelig dato å periodisere mot - får denne
 * behovløseren en konkret prøvingsdato med behovet, og kan derfor svare
 * presist for akkurat den datoen i stedet for å anta et lookback-vindu.
 *
 * Når [dryRun] er satt løses behovet og resultatet logges, men det
 * publiseres ikke noe svar - se [no.nav.dagpenger.oppslag.tiltak.Config.dryRun].
 */
internal class DeltarIArbeidsmarkedstiltakBehovLøser(
    rapidsConnection: RapidsConnection,
    private val tiltakshistorikkKlient: TiltakshistorikkKlient,
    private val dryRun: Boolean = true,
) : River.PacketListener {
    companion object {
        internal const val BEHOV = "Arbeidsmarkedstiltak"
        private const val DATO_KEY = "$BEHOV.Prøvingsdato"
        private val log = KotlinLogging.logger { }
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(BEHOV))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("ident", DATO_KEY)
                    it.interestedIn("@behovId")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val ident = packet["ident"].asString()
        val prøvingsdato = packet[DATO_KEY].asLocalDate()

        withLoggingContext("behovId" to packet["@behovId"].asString()) {
            log.info { "Skal løse behov '$BEHOV'" }
            val deltar =
                runBlocking {
                    tiltakshistorikkKlient.hentTiltaksdeltakelser(ident).deltarPå(prøvingsdato)
                }
            publiser(packet, deltar, context)
        }
    }

    private fun publiser(
        packet: JsonMessage,
        deltar: Boolean,
        context: MessageContext,
    ) {
        if (dryRun) {
            log.info { "DRY_RUN: løser ikke behov '$BEHOV', ville svart verdi=$deltar" }
            return
        }
        packet["@løsning"] = mapOf(BEHOV to mapOf("verdi" to deltar))
        log.info { "Løste behov '$BEHOV' med verdi=$deltar" }
        context.publish(packet.toJson())
    }
}

/**
 * Personen deltar på [dato] dersom minst én periode dekker den - enten fordi
 * den fortsatt er pågående (uten sluttdato), eller fordi [dato] ligger
 * mellom start- og sluttdato.
 */
private fun List<TiltaksdeltakelsePeriode>.deltarPå(dato: LocalDate): Boolean =
    any { periode -> !periode.fraOgMed.isAfter(dato) && (periode.tilOgMed == null || !periode.tilOgMed.isBefore(dato)) }
