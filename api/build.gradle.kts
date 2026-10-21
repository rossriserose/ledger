plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("io.javalin:javalin:6.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "ledger.api.Main"
}
