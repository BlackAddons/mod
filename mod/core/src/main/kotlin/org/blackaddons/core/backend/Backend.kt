package org.blackaddons.core.backend


/** Backend endpoints. Traefik strips `/blackaddons`, `/v1` is the contract version. */
object Backend {
    const val BASE_URL = "https://api.blackaddons.com/blackaddons"
    const val API_V1 = "$BASE_URL/v1"

    /**
     * Single-use `serverId` for the Mojang handshake. The backend issues it, because any server operator could replay a
     * nonce the client picks.
     */
    const val CHALLENGE_URL = "$API_V1/auth/challenge"

    /** `wss`. The token goes in the first frame, never in the URL, because proxies log URLs. */
    val GATEWAY_URL = API_V1.replaceFirst("https://", "wss://") + "/gateway"

    /** Mojang session login, returns a player JWT. Needs a [CHALLENGE_URL] `serverId`. A `deviceToken` replaces 2FA. */
    const val SESSION_URL = "$API_V1/auth/session"

    /**
     * 2FA elevation (player JWT). POST `{ code, rememberDevice }` returns a new token, plus a 30-day `deviceToken` if
     * remembered. 401 means a wrong code, not a stale session.
     */
    const val ELEVATE_URL = "$API_V1/auth/session/elevate"

    /**
     * Sign-in with a [LINK_CODE_URL] code. Creates the account on first use. It's the panel, not the SSO page, because
     * the flow has to start at the client to come back to it.
     */
    const val SIGN_IN_URL = "https://dash.blackaddons.com"

    /** Links this Minecraft account to an existing account. Goes straight to SSO, because credentials change only there. */
    const val LINK_MINECRAFT_URL = "https://sso.blackaddons.com/account/minecraft"

    /** 2FA setup for staff. On SSO, because it changes credentials. */
    const val TWO_FACTOR_URL = "https://sso.blackaddons.com/account/2fa"

    /**
     * Short code to type into [SIGN_IN_URL] (player JWT). POST returns `{ code, expiresInSeconds }`. Single-use, valid
     * for 10 minutes. The only way in without an email.
     */
    const val LINK_CODE_URL = "$API_V1/auth/link-code"

    /** The linked account (player JWT): nickname, identities, permissions. 400 when not linked. */
    const val ME_URL = "$API_V1/me"

    /**
     * Update check (anonymous, ETag), with `?channel=` and `?mcVersion=`. 404 means nothing is published. Anonymous, so
     * expired, offline and banned clients still find out they're outdated.
     */
    const val VERSION_URL = "$API_V1/version"

    /** Game-data documents (anonymous, ETag). Append `/{key}`. The backend only reads `version` from them. */
    const val GAMEDATA_URL = "$API_V1/gamedata"

    /** Signed identity assertion for an addon (player JWT). POST `{ "audience", "nonce" }`. The host picks the audience. */
    const val IDP_ASSERTION_URL = "$API_V1/idp/assertion"

    /**
     * Chat over HTTP: `GET /{channel}/messages?after=&limit=` backfills (`{ messages, hasMore }`), `DELETE
     * /messages/{id}`, `/attachments/{sha256}`. Sending is `MESSAGE_SEND` on the gateway only.
     */
    const val CHAT_URL = "$API_V1/chat"

    /**
     * Staff tickets: `GET` lists with unread counts, `GET /{id}`, `POST /{id}/messages`, `POST /{id}/read`,
     * `/{id}/attachments` (private).
     */
    const val TICKETS_URL = "$API_V1/me/tickets"

    /** Anonymous. POST `{ "players": [uuid, ...] }`. */
    const val COSMETICS_RESOLVE_URL = "$API_V1/cosmetics/resolve"

    const val COSMETIC_OWNERS_URL = "$API_V1/cosmetics/owners"

    /** Content-addressed, cached forever. Append `/{sha256}`. */
    const val ASSETS_URL = "$API_V1/assets"

    /** Player JWT. */
    const val ME_COSMETIC_URL = "$API_V1/me/cosmetic"

    /** Player JWT. `GET` catalog with `unlocked`/`equipped`, `PUT /{id}` equips or restyles, `DELETE /{id}` removes. */
    const val ME_COSMETICS3D_URL = "$API_V1/me/cosmetics3d"

    /** Append `/{name}`. */
    const val MOJANG_UUID_URL = "$API_V1/mojang/uuid"

    /** Append `/{uuid}`. */
    const val MOJANG_PROFILE_URL = "$API_V1/mojang/profile"

    const val MOJANG_USERNAME_URL = "$API_V1/mojang/username"

    /** The face and hat layer cut from the skin. Append `/{uuid without dashes}/{pixels}`. */
    const val MOJANG_AVATAR_URL = "$API_V1/mojang/avatar"

    /** Goes through the backend key pool. Append path and query, e.g. `/player?uuid=<uuid>`. */
    const val HYPIXEL_URL = "$API_V1/hypixel"

    /** Append `/{uuid}`. Hypixel's `profiles` body plus networth from the backend. */
    const val SKYBLOCK_PROFILES_URL = "$API_V1/skyblock/profiles"
}
