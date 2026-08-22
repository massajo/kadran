# syntax=docker/dockerfile:1
#
# Image de production du frontend Kadran (Next.js, sortie `standalone`).
# Symétrique de `backend/Dockerfile` (KDN-6) côté front — voir spec §10.6.
#
# Contexte de build : la RACINE du monorepo, pas `web/` — le workspace pnpm
# (`pnpm-workspace.yaml`) exige le lockfile et les manifests à la racine.
#
#   docker build -f web/Dockerfile \
#     --build-arg GIT_SHA="$(git rev-parse HEAD)" \
#     --build-arg GIT_SOURCE="https://github.com/massajo/kadran" \
#     --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
#     -t kadran-web:local .

# AUCUN `--platform=$BUILDPLATFORM` ici, contrairement à `backend/Dockerfile`
# (KDN-130) — et ce n'est pas un oubli.
#
# Côté back, l'étage de build est épinglé sur l'architecture de la machine parce
# qu'un JAR est le même octet quelle que soit la cible. Le raisonnement ne se
# transpose PAS ici : la sortie `standalone` de Next.js embarque des binaires
# natifs. Constaté sur les images produites, pas supposé — les deux
# architectures ne contiennent pas le même fichier :
#
#   amd64  @img/sharp-linuxmusl-x64/lib/sharp-linuxmusl-x64-0.35.3.node
#          @img/sharp-libvips-linuxmusl-x64/lib/libvips-cpp.so.8.18.3
#   arm64  @img/sharp-linuxmusl-arm64/lib/sharp-linuxmusl-arm64-0.35.3.node
#          @img/sharp-libvips-linuxmusl-arm64/lib/libvips-cpp.so.8.18.3
#
# L'en-tête ELF du premier porte `e_machine = 0x3e` (x86-64). `sharp` est une
# dépendance optionnelle de `next` (l'optimiseur d'images de production) ; pnpm
# n'installe que la variante correspondant à la plateforme courante, et le
# traçage de fichiers de Next.js la recopie dans `standalone`.
#
# Épingler cet étage sur la machine de build livrerait donc un `.node` x86-64
# dans l'image arm64. Le conteneur démarrerait quand même — `sharp` n'est requis
# qu'à la première requête `/_next/image` — et casserait plus tard, à
# l'exécution, sur une erreur de classe ELF. Une panne différée et silencieuse
# est pire qu'un build lent : les étages `deps` et `builder` restent construits
# pour l'architecture cible, émulation comprise.
FROM node:22-alpine AS base
# corepack embarqué dans node:22-alpine échoue sur une clé de signature pnpm
# périmée ; voir CLAUDE.md / issue KDN-7.
ENV COREPACK_INTEGRITY_KEYS=0
RUN corepack enable

# --- Dépendances : couche mise en cache tant que les manifests ne changent pas ---
FROM base AS deps
WORKDIR /repo
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./
COPY web/package.json ./web/package.json
RUN pnpm install --frozen-lockfile

# --- Build : source du seul workspace nécessaire, sortie standalone ---
FROM base AS builder
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/web/node_modules ./web/node_modules
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./
COPY web ./web

# Les variables NEXT_PUBLIC_* sont inlinées par Next.js au build : le jour où
# le front en consomme une, la déclarer ici en ARG puis ENV avant `pnpm build`.
# Aucune à ce jour. Les variables privées ne sont jamais lues au build — seul
# le conteneur runtime les lit, via son propre environnement (aucun secret
# n'entre donc dans cette image).
ENV NEXT_TELEMETRY_DISABLED=1
# `output: standalone` n'élague pas `public/` s'il n'existe pas encore : le
# créer vide rend la COPY du stage runtime déterministe qu'il existe ou non.
RUN mkdir -p web/public && pnpm --filter web build

# --- Runtime : uniquement ce que `standalone` a élagué, en non-root ---
FROM node:22-alpine AS runner
# npm et corepack sont embarqués dans l'image de base `node:22-alpine`, mais
# le runtime `standalone` lance directement `node web/server.js` et n'invoque
# jamais npm ni corepack. Ce sont eux qui portaient les 7 HIGH / 1 CRITICAL
# du scan Trivy (KDN-128) : on supprime le code vulnérable plutôt que de le
# masquer au scanner.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
# `adduser --system` seul (BusyBox) rattache l'utilisateur à `nogroup`, pas au
# groupe créé : `-G` est nécessaire pour que le groupe primaire soit `nodejs`.
RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 -G nodejs nextjs
WORKDIR /app

ARG GIT_SHA
ARG GIT_SOURCE
ARG BUILD_DATE
LABEL org.opencontainers.image.title="kadran-web" \
      org.opencontainers.image.description="Frontend Kadran (Next.js, sortie standalone)" \
      org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.source="$GIT_SOURCE" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.licenses="UNLICENSED"

# Racine du standalone : server.js + node_modules élagués aux dépendances de
# production réellement importées (pas le node_modules complet du monorepo).
COPY --from=builder --chown=nextjs:nodejs /repo/web/.next/standalone ./
# `standalone` n'embarque ni les assets statiques ni `public/` : Next.js
# documente cette étape manuelle, à reproduire ici.
COPY --from=builder --chown=nextjs:nodejs /repo/web/.next/static ./web/.next/static
COPY --from=builder --chown=nextjs:nodejs /repo/web/public ./web/public

# Le traçage de fichiers de Next.js (Turbopack) élague `@swc/helpers` de façon
# incomplète sous pnpm : seul `cjs/` survit, `esm/` manque à l'exécution
# (`Cannot find module '.../esm/_interop_require_default.js'`, constaté au
# runtime). On complète depuis l'installation non élaguée du stage `deps`,
# où le paquet est entier. Version alignée sur `pnpm-lock.yaml` — à ajuster
# si `@swc/helpers` change de version.
COPY --from=deps --chown=nextjs:nodejs \
     /repo/node_modules/.pnpm/@swc+helpers@0.5.23/node_modules/@swc/helpers \
     ./node_modules/.pnpm/@swc+helpers@0.5.23/node_modules/@swc/helpers

ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1

EXPOSE 3000
USER nextjs

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "require('http').get({host:'127.0.0.1',port:process.env.PORT||3000,path:'/overview'},r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"

CMD ["node", "web/server.js"]
