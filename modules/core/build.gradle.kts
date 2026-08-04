dependencies {
    api(project(":api"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.gson)
    compileOnly(libs.annotations)

    api(libs.caffeine)
    api(libs.configurate.yaml)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    testImplementation(libs.velocity.api)
    testImplementation(libs.luckperms.api)
    testImplementation(libs.gson)
    testImplementation(libs.annotations)
    testImplementation(libs.adventure.plain)
    testRuntimeOnly(libs.junit.platform.launcher)
}
