package no.nav.dagpenger.oppslag.tiltak.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.oppslag.tiltak.DeltarIArbeidsmarkedstiltakSvar
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltaksdeltakelsePeriode
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.dagpenger.oppslag.tiltak.toJson
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Lytter på `NyAvklaring` fra dp-behandling med kode `MeldekortMedUtdanning`,
 * slår opp personens tiltakshistorikk hos mulighetsrommet-tiltakshistorikk og
 * publiserer svar på opplysningsbehovet `deltarIArbeidsmarkedstiltakId`.
 *
 * `NyAvklaring` gir oss ingen pålitelig dato for hvilken meldeperiode som
 * faktisk vurderes (feltet `gjelderDato` er meldekortets innsendtTidspunkt,
 * ikke meldeperiodens fom/tom), så vi kan ikke periodisere svaret nøyaktig
 * mot behandlingen. Som en midlertidig forenkling avgrenser vi historikken
 * med et konfigurerbart lookback-vindu fra mottakstidspunktet, og tar alltid
 * med pågående deltakelser (uten sluttdato) uansett alder.
 *
 * Når [dryRun] er satt løses behovet og resultatet logges, men det
 * publiseres ikke noe svar - se [no.nav.dagpenger.oppslag.tiltak.Config.dryRun].
 */
internal class MeldekortMedUtdanningAvklaringLøser(
    rapidsConnection: RapidsConnection,
    private val tiltakshistorikkKlient: TiltakshistorikkKlient,
    private val lookbackMåneder: Long,
    private val dryRun: Boolean = true,
    private val clock: Clock = Clock.systemDefaultZone(),
) : River.PacketListener {
    companion object {
        private const val KODE_MELDEKORT_MED_UTDANNING = "MeldekortMedUtdanning"
        private val log = KotlinLogging.logger { }
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "NyAvklaring") }
                precondition { it.requireValue("kode", KODE_MELDEKORT_MED_UTDANNING) }
                validate { it.requireKey("ident") }
                validate { it.requireKey("behandlingId") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val ident = packet["ident"].asString()
        val behandlingId = UUID.fromString(packet["behandlingId"].asString())

        withLoggingContext("behandlingId" to behandlingId.toString()) {
            log.info { "Mottok NyAvklaring med kode $KODE_MELDEKORT_MED_UTDANNING" }
            val perioder = runBlocking { tiltakshistorikkKlient.hentTiltaksdeltakelser(ident) }
            val relevantePerioder = perioder.filtrerRelevante(cutoff = LocalDate.now(clock).minusMonths(lookbackMåneder))
            val svar = DeltarIArbeidsmarkedstiltakSvar(behandlingId, ident, relevantePerioder)
            publiser(svar, context)
        }
    }

    private fun publiser(
        svar: DeltarIArbeidsmarkedstiltakSvar,
        context: MessageContext,
    ) {
        if (dryRun) {
            log.info { "DRY_RUN: publiserer ikke svar, ville sendt: ${svar.toJson()}" }
            return
        }
        log.info { "Publiserer svar på opplysningsbehov om deltakelse i arbeidsmarkedstiltak" }
        context.publish(svar.toJson())
    }
}

/**
 * Beholder pågående deltakelser (uten sluttdato) uansett hvor gammel
 * startdatoen er, og forkaster avsluttede deltakelser som er eldre enn
 * [cutoff].
 */
private fun List<TiltaksdeltakelsePeriode>.filtrerRelevante(cutoff: LocalDate): List<TiltaksdeltakelsePeriode> =
    filter { it.tilOgMed == null || !it.tilOgMed.isBefore(cutoff) }
