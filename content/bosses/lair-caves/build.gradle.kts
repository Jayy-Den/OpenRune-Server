plugins {
    id("base-conventions")

}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
}
