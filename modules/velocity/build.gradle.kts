plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.gson)
    compileOnly(libs.annotations)

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

tasks.shadowJar {
    archiveBaseName.set("ProxyPunishments")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    mergeServiceFiles()

    minimize {
        exclude(dependency("org.spongepowered:configurate-yaml:.*"))
        exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
