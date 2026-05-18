plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "edu.bmu.attendance"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Maitri's data endpoints return JSON, so we don't need an HTML parser.
    // org.json is tiny (~70KB) and ships in the JDK in some flavours; we add
    // it explicitly so we don't depend on Android-only stdlib classes.
    implementation("org.json:json:20240303")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

application {
    // Matches @file:JvmName("MaitriProbe") in MaitriProbe.kt
    mainClass.set("MaitriProbe")
    // Quiet OkHttp/SLF4J unless the user opts in.
    applicationDefaultJvmArgs = listOf(
        "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn"
    )
}

kotlin {
    // Matches the JBR shipped with Android Studio so the same JAVA_HOME
    // that powers Studio also runs the probe.
    jvmToolchain(21)
}
