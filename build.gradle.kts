plugins {
    kotlin("jvm") version "1.9.21"
    id("com.google.devtools.ksp") version "1.9.21-1.0.15"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.hugo.thankmasbuildserver"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven(url = "https://jitpack.io")
    maven(url = "https://repo.papermc.io/repository/maven-public/")
}

val exposedVersion: String by project
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // compileOnly("net.luckperms:api:5.4")

    ksp("io.insert-koin:koin-ksp-compiler:1.3.1")

    // Work on a paper specific library!
    implementation(files("C:/Users/hugov/IdeaProjects/ThankmasPaper/build/libs/ThankmasPaper-1.0-SNAPSHOT.jar"))
}

tasks.shadowJar {
    minimize()
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<JavaCompile> { // Preserve parameter names in the bytecode
    options.compilerArgs.add("-parameters")
}

tasks.compileKotlin {
    kotlinOptions.javaParameters = true
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        javaParameters = true
    }
}