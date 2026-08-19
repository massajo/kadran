#!/bin/sh
# Bascule le processus final vers un utilisateur dont l'uid/gid correspond à ceux de l'hôte
# (HOST_UID/HOST_GID, défaut 1000:1000 — l'uid du premier utilisateur sur la quasi-totalité des
# postes Linux) avant de lancer la vraie commande de développement.
#
# Sans cette bascule, les conteneurs `backend`/`web` de `docker/compose.yml` tournent en
# `root` (défaut Docker), et tout ce qu'ils écrivent dans l'arbre source bind-monté — cache
# Gradle et Kotlin du projet, `*/build/`, `node_modules`, `.next`, `next-env.d.ts` — devient
# root:root sur l'hôte. Le développeur ne peut alors plus ni reconstruire depuis sa machine
# (`Permission denied` sur les fichiers de verrou Gradle), ni nettoyer (les fichiers ne lui
# appartiennent plus) : l'inverse de l'objectif de KDN-5 (spec §10.6, une seule commande doit
# suffire, y compris pour que Claude Code puisse valider son travail après coup).
#
# Usage : run-as-host-user.sh <répertoires-cache séparés par des espaces, ou ""> \
#                              <répertoire de travail interne au conteneur à chowner sans
#                               récursion, ou ""> \
#                              <commande à exécuter en root avant la bascule, ou ""> \
#                              <commande finale, exécutée sous l'utilisateur hôte>
set -eu

TARGET_UID="${HOST_UID:-1000}"
TARGET_GID="${HOST_GID:-1000}"
CACHE_DIRS="$1"
WORKDIR="$2"
ROOT_SETUP="$3"
FINAL_CMD="$4"

# Réutilise le groupe/utilisateur déjà présent à cet uid/gid s'il existe (ex. `node` sur
# `node:22-alpine`, déjà 1000:1000) plutôt que d'en recréer un en doublon.
GROUP_NAME=$(getent group "$TARGET_GID" | cut -d: -f1)
if [ -z "$GROUP_NAME" ]; then
  addgroup -g "$TARGET_GID" hostuser
  GROUP_NAME=hostuser
fi

USER_NAME=$(getent passwd "$TARGET_UID" | cut -d: -f1)
if [ -z "$USER_NAME" ]; then
  adduser -D -u "$TARGET_UID" -G "$GROUP_NAME" hostuser
  USER_NAME=hostuser
fi

# Les volumes nommés (caches Gradle/pnpm) sont créés root:root par Docker à leur première
# utilisation, quel que soit l'utilisateur final du conteneur : il faut les chowner une fois,
# en root, avant de basculer. Ils vivent hors de tout bind-mount (chemins fixes `/cache/...`),
# donc ce chown ne touche jamais l'arbre source de l'hôte.
for dir in $CACHE_DIRS; do
  mkdir -p "$dir"
  chown -R "$TARGET_UID:$TARGET_GID" "$dir"
done

# Répertoire de travail auto-créé par Docker à l'intérieur du conteneur (ex. `/repo`, parent
# de plusieurs montages individuels — fichiers en lecture seule et sous-répertoire bind-monté
# en écriture) : Docker le crée root:root, sans rapport avec l'arbre de l'hôte puisque ce
# n'est pas lui-même un point de bind-mount. Sans intervention, l'outil de développement ne
# peut pas y créer de nouvelle entrée (ex. `node_modules` à la racine du workspace pnpm) une
# fois basculé sous l'utilisateur cible. Chown non récursif seulement : les montages
# individuels en `:ro` qu'il contient refuseraient un `chown -R`, et ceux en écriture
# appartiennent déjà à l'utilisateur cible (bind-mount de l'arbre de l'hôte).
if [ -n "$WORKDIR" ]; then
  chown "$TARGET_UID:$TARGET_GID" "$WORKDIR"
fi

# Étape qui a besoin de root (ex. `corepack enable`, qui écrit des liens symboliques sous
# `/usr/local/bin`) — jamais sur l'arbre source, donc sans conséquence pour l'hôte.
if [ -n "$ROOT_SETUP" ]; then
  sh -c "$ROOT_SETUP"
fi

exec su -s /bin/sh "$USER_NAME" -c "$FINAL_CMD"
