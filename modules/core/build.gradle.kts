dependencies {
    api(project(":api"))

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.gson)
    compileOnly(libs.annotations)
    compileOnly(libs.slf4j.api)

    api(libs.caffeine)
    api(libs.configurate.yaml)

    // Only the RESP connection and the pub/sub loop are used; gson, slf4j and org.json are excluded
    // because both platforms already ship them and shading them again only grows the jar.
    api(libs.jedis) {
        exclude(group = "com.google.code.gson")
        exclude(group = "org.slf4j")
        exclude(group = "org.json")
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.luckperms.api)
    testImplementation(libs.gson)
    testImplementation(libs.annotations)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.adventure.plain)
    testRuntimeOnly(libs.junit.platform.launcher)
}
