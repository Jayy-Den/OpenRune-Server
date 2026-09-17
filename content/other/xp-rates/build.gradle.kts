plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.kotlin.inline.logger)
    runtimeOnly(libs.logback.classic)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.realm)
}
