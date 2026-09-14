plugins {
    id("common")
    application
}

application {
    mainClass.set("no.nav.dagpenger.oppslag.tiltak.AppKt")
}

dependencies {
    implementation(libs.rapids.and.rivers)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.bundles.jackson)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation(libs.bundles.kotest.assertions)
}
