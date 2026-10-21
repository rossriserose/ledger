subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all,-serial")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
