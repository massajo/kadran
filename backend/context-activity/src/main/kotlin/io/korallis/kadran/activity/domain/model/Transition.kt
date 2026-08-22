package io.korallis.kadran.activity.domain.model

/**
 * Le nouvel état d'un agrégat et l'événement qui le justifie, rendus ensemble (`CLAUDE.md`
 * §2.5, même patron que `Transition<IdentityEvent>` de KDN-27). Rendre l'un sans l'autre
 * laisserait à l'appelant le soin de ne pas les dissocier — ce qu'il finirait par faire,
 * silencieusement.
 *
 * Générique sur l'événement, pas seulement sur l'état : `Outing` (KDN-34) et `RevenueRecord`
 * (KDN-35), construits en parallèle, avaient chacun leur propre `Transition<T>` codée en dur
 * sur leur type d'événement — deux déclarations incompatibles sous le même nom, réconciliées
 * ici en un seul type partagé par tout le contexte `activity`.
 */
data class Transition<out T, out E>(
    val state: T,
    val event: E,
)
