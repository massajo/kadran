package io.korallis.kadran.platform.web

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

/**
 * Compte les octets réellement écrits dans le corps de la réponse, pour la ligne d'accès de
 * [AccessLogFilter] (spec §10.7.1).
 *
 * `Content-Length` ne suffit pas : Tomcat le calcule dans sa couche interne et ne le rend pas
 * toujours par `getHeader`, si bien que la taille manquerait sur la plupart des réponses. Ce
 * qui est compté ici est ce qui est passé sur le fil, pas une valeur annoncée.
 *
 * **Seul le flux d'octets est instrumenté**, pas `getWriter()`. Réencoder le corps caractère
 * par caractère derrière le conteneur, c'est prendre un risque de jeu de caractères et de
 * tampon sur chaque réponse, pour un champ de journal ; les rares chemins qui passent par un
 * `Writer` — pages d'erreur, gabarits — ne publient simplement pas de taille, et un champ
 * absent se lit mieux qu'un champ faux (CLAUDE.md §2.1).
 */
internal class CountingServletResponse(
    delegate: HttpServletResponse,
) : HttpServletResponseWrapper(delegate) {
    private var counting: CountingServletOutputStream? = null

    /** Octets écrits, ou `null` si la réponse n'a pas emprunté le flux d'octets. */
    val bytesWritten: Long? get() = counting?.written

    override fun getOutputStream(): ServletOutputStream =
        counting ?: CountingServletOutputStream(super.getOutputStream()).also { counting = it }
}

private class CountingServletOutputStream(
    private val delegate: ServletOutputStream,
) : ServletOutputStream() {
    var written: Long = 0
        private set

    override fun write(b: Int) {
        delegate.write(b)
        written += 1
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        delegate.write(b, off, len)
        written += len
    }

    override fun isReady(): Boolean = delegate.isReady

    override fun setWriteListener(writeListener: WriteListener?) = delegate.setWriteListener(writeListener)

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}
