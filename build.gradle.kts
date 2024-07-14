plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("com.github.johnrengelman.shadow")
}

group = "me.hugo.thankmasbuildserver"
version = "1.0-SNAPSHOT"

val exposedVersion: String by project
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // compileOnly("net.luckperms:api:5.4")

    ksp("io.insert-koin:koin-ksp-compiler:1.3.1")

    // Work on a paper specific library!
    implementation(project(":common-paper"))
}

tasks.shadowJar {
    // minimize()
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