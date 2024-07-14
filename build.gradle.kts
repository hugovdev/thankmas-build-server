plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("com.github.johnrengelman.shadow")
    id("io.papermc.paperweight.userdev")
}

group = "me.hugo.thankmasbuildserver"
version = "1.0-SNAPSHOT"

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)

    ksp("io.insert-koin:koin-ksp-compiler:1.3.1")

    // Work on a paper specific library!
    implementation(project(":common-paper"))
}

tasks.compileKotlin {
    compilerOptions {
        javaParameters = true
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}