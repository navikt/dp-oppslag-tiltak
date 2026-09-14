package no.nav.dagpenger.oppslag.tiltak

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.oppslag.tiltak.rivers.DeltarIArbeidsmarkedstiltakBehovLøser
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltakshistorikkHttpKlient
import no.nav.helse.rapids_rivers.RapidApplication

private val logg = KotlinLogging.logger {}

fun main() {
    ApplicationBuilder(Config.asMap()).start()
}

internal class ApplicationBuilder(
    env: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val tiltakshistorikkKlient =
        TiltakshistorikkHttpKlient(
            url = Config.tiltakshistorikkApiUrl,
            tokenSupplier = { Config.tiltakshistorikkTokenClient.hentToken() },
        )

    private val rapidsConnection =
        RapidApplication
            .create(env)
            .apply {
                DeltarIArbeidsmarkedstiltakBehovLøser(
                    rapidsConnection = this,
                    tiltakshistorikkKlient = tiltakshistorikkKlient,
                    lookbackMåneder = Config.tiltakshistorikkLookbackMåneder,
                    dryRun = Config.dryRun,
                )
            }

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logg.info { "Starter dp-oppslag-tiltak" }
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        tiltakshistorikkKlient.close()
    }
}
