plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))

    compileOnly(libs.paper.api)
    compileOnly(libs.luckperms.api)
    compileOnly(libs.gson)
    compileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    testImplementation(libs.paper.api)
    testImplementation(libs.luckperms.api)
    testImplementation(libs.gson)
    testImplementation(libs.annotations)
    testImplementation(libs.adventure.plain)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.shadowJar {
    archiveBaseName.set("ProxyPunishments-Paper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    mergeServiceFiles()

    // Other plugins bundle Jedis too; relocating keeps two copies from fighting over the same classes.
    relocate("redis.clients", "net.vanfek.proxypunishments.libs.redis")
    relocate("org.apache.commons.pool2", "net.vanfek.proxypunishments.libs.pool2")

    minimize {
        exclude(dependency("org.spongepowered:configurate-yaml:.*"))
        exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
