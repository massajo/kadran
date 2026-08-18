// Contexte borné `fiscal` — structure interne : domain/{model,api,spi} · application ·
// infrastructure/{api,spi} (ADR-005). Les dépendances vers shared-kernel et platform
// sont apportées par la convention.
plugins { id("kadran.context-conventions") }
