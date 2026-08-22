package io.korallis.kadran.identity.infrastructure.spi.persistence

import org.jooq.Field
import org.jooq.Record
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Conversion d'un [Instant] du domaine vers le type que jOOQ lie à une colonne `TIMESTAMPTZ`.
 *
 * L'offset est **UTC et non `Europe/Paris`**. La colonne est un point sur la ligne du temps ;
 * y écrire un offset local ne changerait pas l'instant stocké mais laisserait croire que la
 * base porte un fuseau, et c'est ainsi qu'on finit par comparer des heures locales. Le fuseau
 * d'exploitation (spec §4.3) s'applique à la lecture métier — `BusinessDayPolicy`, KDN-33 —
 * jamais au stockage.
 */
internal fun Instant.toColumnValue(): OffsetDateTime = atOffset(ZoneOffset.UTC)

/**
 * Lit une colonne **en convertissant**, plutôt qu'en pariant sur le type que le pilote JDBC
 * a choisi.
 *
 * `Record.get(Field<T>)` ne convertit pas : il localise la colonne par son nom puis effectue
 * une conversion de type non vérifiée. Tant que la génération de code jOOQ n'est pas en place,
 * les colonnes d'un `SELECT *` sont typées d'après les métadonnées du `ResultSet` — et une
 * colonne `DATE` y arrive en `java.sql.Date`, pas en `LocalDate`. Le programme compile, puis
 * lève un `ClassCastException` à la première lecture. Passer le type attendu force la
 * conversion documentée de jOOQ et supprime la classe entière de ce défaut.
 */
internal fun <T : Any> Record.readOrNull(field: Field<T>): T? = get(field, field.type)

/**
 * Variante des colonnes `NOT NULL`.
 *
 * @throws IllegalStateException si la colonne est nulle — ce serait un schéma qui ne
 *   correspond plus au changeset, et il vaut mieux le dire que rendre un agrégat amputé.
 */
internal fun <T : Any> Record.read(field: Field<T>): T =
    checkNotNull(readOrNull(field)) { "colonne ${field.name} nulle alors que le schema l'interdit" }
