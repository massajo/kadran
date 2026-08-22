package io.korallis.kadran.platform.security.web

import io.korallis.kadran.platform.security.AuthenticationResult
import io.korallis.kadran.platform.security.AuthenticationService
import io.korallis.kadran.platform.security.token.RefreshTokenSecret
import io.korallis.kadran.platform.security.token.authenticatedSubjectOf
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Les trois points d'entrée de l'authentification (spec §10.3).
 *
 * Le contrôleur ne décide rien : il traduit du HTTP en appels à [AuthenticationService] et
 * inversement. Toute la politique — refus uniforme, rotation, révocation, audit — vit dans le
 * service, où elle se teste sans conteneur web.
 *
 * `/api/auth/login` et `/api/auth/refresh` sont **publics** : ce sont les deux seules portes
 * par lesquelles on peut entrer sans jeton. `/api/auth/logout` exige d'être authentifié —
 * pouvoir déconnecter quelqu'un d'autre sans preuve d'identité serait un déni de service
 * gratuit.
 */
@RestController
@RequestMapping("/api/auth")
class AuthenticationController(
    private val authentication: AuthenticationService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody body: LoginRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = respond(authentication.login(body.login, body.password, request.authenticationContext()))

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody body: RefreshRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        respond(
            authentication.refresh(
                RefreshTokenSecret.of(body.refreshToken),
                request.authenticationContext(),
            ),
        )

    /**
     * Répond `204` quel que soit l'état antérieur des jetons : une déconnexion est idempotente,
     * et signaler « il n'y avait rien à révoquer » n'apprendrait rien d'utile au client.
     */
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal token: Jwt,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val subject = authenticatedSubjectOf(token)
        return if (subject == null) {
            // Inatteignable en pratique : le décodeur refuse déjà un jeton incomplet
            // (KadranClaimsValidator). Le cas est traité plutôt que forcé pour ne pas
            // transformer un futur assouplissement de la validation en NullPointerException.
            unauthorized()
        } else {
            authentication.logout(subject.accountId, subject.tenantId, request.authenticationContext())
            ResponseEntity.noContent().build()
        }
    }

    private fun respond(result: AuthenticationResult): ResponseEntity<Any> =
        when (result) {
            is AuthenticationResult.Granted -> ResponseEntity.ok(result.toResponse())
            AuthenticationResult.Refused -> unauthorized()
        }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED))
}
