package no.nav.dagpenger.oppslag.tiltak.rivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltaksdeltakelsePeriode
import no.nav.dagpenger.oppslag.tiltak.tiltakshistorikk.TiltakshistorikkKlient
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class DeltarIArbeidsmarkedstiltakBehovLøserTest {
    private val ident = "11109233444"
    private val behovId = "01a09fe7-3844-7789-a41c-fa6802093625"

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
            DeltarIArbeidsmarkedstiltakBehovLøser(it, tiltakshistorikkKlient, dryRun = false)
        }

    @Test
    fun `svarer true når prøvingsdato faller innenfor en avsluttet periode`() {
        testRapid.sendTestMessage(behovJson(prøvingsdato = "2026-01-15"))

        val inspektør = testRapid.inspektør
        inspektør.size shouldBe 1

        val svar = inspektør.message(0)
        svar["@løsning"]["Arbeidsmarkedstiltak"]["verdi"].asBoolean() shouldBe true
    }

    @Test
    fun `svarer true når prøvingsdato faller innenfor en pågående periode`() {
        testRapid.sendTestMessage(behovJson(prøvingsdato = "2026-06-01"))

        val svar = testRapid.inspektør.message(0)
        svar["@løsning"]["Arbeidsmarkedstiltak"]["verdi"].asBoolean() shouldBe true
    }

    @Test
    fun `svarer false når prøvingsdato ikke faller innenfor noen periode`() {
        testRapid.sendTestMessage(behovJson(prøvingsdato = "2026-02-15"))

        val svar = testRapid.inspektør.message(0)
        svar["@løsning"]["Arbeidsmarkedstiltak"]["verdi"].asBoolean() shouldBe false
    }

    @Test
    fun `publiserer ikke svar når dry-run er skrudd på`() {
        val rapid =
            TestRapid().also {
                DeltarIArbeidsmarkedstiltakBehovLøser(it, tiltakshistorikkKlient, dryRun = true)
            }

        rapid.sendTestMessage(behovJson(prøvingsdato = "2026-01-15"))

        rapid.inspektør.size shouldBe 0
    }

    @Test
    fun `plukker ikke opp andre behov`() {
        testRapid.sendTestMessage(behovJson(behov = "AnnetBehov"))
        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `plukker ikke opp pakker som allerede har løsning`() {
        testRapid.sendTestMessage(behovJson(medLøsning = true))
        testRapid.inspektør.size shouldBe 0
    }

    // language=JSON
    private fun behovJson(
        prøvingsdato: String = "2026-01-15",
        behov: String = "Arbeidsmarkedstiltak",
        medLøsning: Boolean = false,
    ) = """
        {
          "@event_name": "behov",
          "@behov": ["$behov"],
          "@behovId": "$behovId",
          "ident": "$ident",
          "behandlingId": "1048a8da-d591-4d2e-81c0-6f4545f97a66",
          "$behov": {
            "Prøvingsdato": "$prøvingsdato"
          }
          ${if (medLøsning) """, "@løsning": {}""" else ""}
        }
        """.trimIndent()
}
