plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

application {
    mainClass.set("com.f1analytics.MainKt")
}

group = "com.f1analytics"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.9"
val coroutinesVersion = "1.8.0"
val exposedVersion = "0.48.0"

dependencies {
    // Ktor WebSocket client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Date/time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Exposed ORM + SQLite
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-websockets:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    doFirst {
        val frontend = ProcessBuilder("npm", "run", "dev")
            .directory(file("frontend"))
            .inheritIO()
            .start()
        Runtime.getRuntime().addShutdownHook(Thread { frontend.destroy() })
    }
}

// F-08.1: Build frontend and embed in JAR
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    group = "f1analytics"
    description = "Build Svelte/Vite frontend and copy dist into resources/static"
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
    inputs.dir("frontend/src")
    inputs.file("frontend/package.json")
    inputs.file("frontend/vite.config.js")
    outputs.dir("frontend/dist")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(buildFrontend)
    from("frontend/dist") { into("static") }
}


tasks.register<JavaExec>("loadSeason") {
    group = "f1analytics"
    description = "Load a season from Jolpica into the local DB. Pass year with -Pargs=\"2026\"."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.f1analytics.tools.SeasonLoaderKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
}
kotlin {
    jvmToolchain(17)
}