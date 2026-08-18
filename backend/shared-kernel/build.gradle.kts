// Value objects partagés (Money, Ratio, Distance, WorkPeriod…), spec §7.2.
// **Aucune dépendance Spring** : c'est une propriété structurelle, pas une convention.
// Le module n'applique que `kadran.kotlin-conventions`, qui n'apporte pas Spring.
plugins { id("kadran.kotlin-conventions") }
