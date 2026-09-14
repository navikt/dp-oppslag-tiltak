# dp-oppslag-tiltak

Tjeneste som lytter på avklaringer fra dp-behandling med kode `MeldekortMedUtdanning`, slår opp
personens tiltakshistorikk hos mulighetsrommet-tiltakshistorikk og svarer med en periodisert liste
over deltakelse i arbeidsmarkedstiltak, som opplysningen `deltarIArbeidsmarkedstiltakId`
(`0194881f-9445-734c-a7ee-045edf29b527`) på Kafka-rapiden, slik at dp-behandling sin
`OpplysningSvarMottak` kan ta den i mot.

## Komme i gang

Gradle brukes som byggverktøy og er bundlet inn.

`./gradlew build`

---

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
