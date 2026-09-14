package no.nav.dagpenger.oppslag.tiltak.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltaksdeltakelsePeriode
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltakshistorikkKlient
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class DeltarIArbeidsmarkedstiltakBehovLøserTest {
    private val behandlingId = UUID.fromString("1048a8da-d591-4d2e-81c0-6f4545f97a66")
    private val ident = "11109233444"
    private val klokke = Clock.fixed(Instant.parse("2026-03-15T00:00:00Z"), ZoneOffset.UTC)

    private val perioder =
        listOf(
            TiltaksdeltakelsePeriode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
            TiltaksdeltakelsePeriode(LocalDate.of(2026, 3, 1), null),
        )

    private val tiltakshistorikkKlient: TiltakshistorikkKlient =
        mockk<TiltakshistorikkKlient>().also {
            coEvery { it.hentTiltaksdeltakelser(ident) } returns perioder
        }

    private val testRapid =
        TestRapid().also {
            DeltarIArbeidsmarkedstiltakBehovLøser(it, tiltakshistorikkKlient, lookbackMåneder = 6L, dryRun = false, clock = klokke)
        }

    @Test
    fun `løser NyAvklaring med kode MeldekortMedUtdanning og publiserer periodisert svar`() {
        testRapid.sendTestMessage(nyAvklaringJson())

        val inspektør = testRapid.inspektør
        inspektør.size shouldBe 1

        val svar = inspektør.message(0)
        svar["@event_name"].asString() shouldBe "behov"
        svar["@final"].asBoolean() shouldBe true
        svar["@opplysningsbehov"].asBoolean() shouldBe true
        svar["ident"].asString() shouldBe ident
        svar["behandlingId"].asString() shouldBe behandlingId.toString()

        val løsning = svar["@løsning"]["Deltar i arbeidsmarkedstiltak"]
        løsning.size() shouldBe 2
        løsning[0]["verdi"].asBoolean() shouldBe true
        løsning[0]["gyldigFraOgMed"].asString() shouldBe "2026-01-01"
        løsning[0]["gyldigTilOgMed"].asString() shouldBe "2026-01-31"
        løsning[1]["gyldigFraOgMed"].asString() shouldBe "2026-03-01"
        løsning[1].has("gyldigTilOgMed") shouldBe false
    }

    @Test
    fun `publiserer ikke svar når dry-run er skrudd på`() {
        val rapid =
            TestRapid().also {
                DeltarIArbeidsmarkedstiltakBehovLøser(it, tiltakshistorikkKlient, lookbackMåneder = 6L, dryRun = true, clock = klokke)
            }

        rapid.sendTestMessage(nyAvklaringJson())

        rapid.inspektør.size shouldBe 0
    }

    @Test
    fun `filtrerer bort avsluttede deltakelser eldre enn lookback-vinduet, men beholder pågående deltakelser`() {
        val gammelAvsluttet = TiltaksdeltakelsePeriode(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30))
        val gammelPågående = TiltaksdeltakelsePeriode(LocalDate.of(2015, 1, 1), null)
        val innenforVinduet = TiltaksdeltakelsePeriode(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31))
        val klient: TiltakshistorikkKlient =
            mockk<TiltakshistorikkKlient>().also {
                coEvery { it.hentTiltaksdeltakelser(ident) } returns listOf(gammelAvsluttet, gammelPågående, innenforVinduet)
            }
        val rapid =
            TestRapid().also {
                DeltarIArbeidsmarkedstiltakBehovLøser(it, klient, lookbackMåneder = 6L, dryRun = false, clock = klokke)
            }

        rapid.sendTestMessage(nyAvklaringJson())

        val løsning = rapid.inspektør.message(0)["@løsning"]["Deltar i arbeidsmarkedstiltak"]
        løsning.size() shouldBe 2
        løsning[0]["gyldigFraOgMed"].asString() shouldBe "2015-01-01"
        løsning[1]["gyldigFraOgMed"].asString() shouldBe "2025-12-01"
    }

    @Test
    fun `plukker ikke opp avklaring med annen kode`() {
        testRapid.sendTestMessage(nyAvklaringJson(kode = "AnnenKode"))
        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `plukker ikke opp annen hendelsestype`() {
        testRapid.sendTestMessage(nyAvklaringJson(eventName = "EndretAvklaring"))
        testRapid.inspektør.size shouldBe 0
    }

    // language=JSON
    private fun nyAvklaringJson(
        eventName: String = "NyAvklaring",
        kode: String = "MeldekortMedUtdanning",
    ) = """
        {
          "@event_name": "$eventName",
          "ident": "$ident",
          "avklaringId": "01a09fe7-3844-7789-a41c-fa6802093625",
          "kode": "$kode",
          "behandlingId": "$behandlingId",
          "gjelderDato": "2026-09-14",
          "meldekortId": "01a09fe7-3801-712d-a4f7-1c8e078e9cf5"
        }
        """.trimIndent()
}
