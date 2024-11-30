plugins {
    alias(libs.plugins.paperweight)
}

group = "me.hugo.thankmasbuildserver"
version = "1.0-SNAPSHOT"

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)

    ksp(libs.koin.ksp.compiler)

    compileOnly(libs.aswm)
    compileOnly(libs.aswm.loaders)

    compileOnly(files("C:/Users/hugov/IdeaProjects/thankmas/common-paper/libs/PaperPolar.jar"))

    // Work on a paper specific library!
    implementation(project(":common-paper"))
}