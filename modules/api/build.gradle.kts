dependencies {
    compileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.annotations)
    testRuntimeOnly(libs.junit.platform.launcher)
}
