// Racine volontairement vide : la configuration partagée vit dans les plugins de
// convention de `buildSrc`, appliqués explicitement par chaque module.
// Aucun bloc `allprojects` ni `subprojects` — ils masquent l'origine de la configuration.

tasks.register("checkAll") {
    group = "verification"
    description = "Lance `check` sur tous les modules"
    dependsOn(subprojects.map { "${it.path}:check" })
}
