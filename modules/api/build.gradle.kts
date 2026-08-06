dependencies {
    compileOnly(libs.adventure.api)
    compileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.adventure.api)
    testImplementation(libs.annotations)
    testRuntimeOnly(libs.junit.platform.launcher)
}
