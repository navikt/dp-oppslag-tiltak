package no.nav.dagpenger.oppslag.tiltak

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.longType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.oppslag.tiltak.auth.NaisTokenClient

internal object Config {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "KAFKA_CONSUMER_GROUP_ID" to "dp-oppslag-tiltak-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "latest",
                "TILTAKSHISTORIKK_SCOPE" to "api://dev-gcp.team-mulighetsrommet.tiltakshistorikk/.default",
                "TILTAKSHISTORIKK_API_URL" to "http://tiltakshistorikk.team-mulighetsrommet",
                // Vi har ingen pålitelig kilde til hvilken meldeperiode avklaringen faktisk
                // gjelder (se MeldekortMedUtdanningAvklaringLøser), så vi bruker et enkelt
                // lookback-vindu fra mottakstidspunktet som en midlertidig forenkling.
                "TILTAKSHISTORIKK_LOOKBACK_MAANEDER" to "6",
                // Skrur på dry-run som default inntil vi har mer tillit til periodiseringen
                // (lookback-vindu og manglende meldeperiode-signal) - se MeldekortMedUtdanningAvklaringLøser.
                "DRY_RUN" to "true",
            ),
        )
    private val prodProperties =
        ConfigurationMap(
            mapOf(
                "TILTAKSHISTORIKK_SCOPE" to "api://prod-gcp.team-mulighetsrommet.tiltakshistorikk/.default",
            ),
        )

    val properties: Configuration by lazy {
        val systemAndEnvProperties = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
        when (System.getenv().getOrDefault("NAIS_CLUSTER_NAME", "LOCAL")) {
            "prod-gcp" -> systemAndEnvProperties overriding prodProperties overriding defaultProperties
            else -> systemAndEnvProperties overriding defaultProperties
        }
    }

    val tiltakshistorikkScope by lazy { properties[Key("TILTAKSHISTORIKK_SCOPE", stringType)] }

    val tiltakshistorikkApiUrl by lazy { properties[Key("TILTAKSHISTORIKK_API_URL", stringType)] }

    val tiltakshistorikkLookbackMåneder by lazy { properties[Key("TILTAKSHISTORIKK_LOOKBACK_MAANEDER", longType)] }

    val dryRun by lazy { properties[Key("DRY_RUN", booleanType)] }

    val tiltakshistorikkTokenClient by lazy {
        NaisTokenClient(
            tokenEndpoint = properties[Key("NAIS_TOKEN_ENDPOINT", stringType)],
            target = tiltakshistorikkScope,
        )
    }

    fun asMap(): Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }
}
