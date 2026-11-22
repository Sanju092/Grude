package com.grude.app.auth

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.grude.app.model.AccountProfile
import com.grude.app.model.AuthMethod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.spec.NamedParameterSpec
import java.util.UUID

/**
 * Hardware-backed encrypted credential vault supporting all 5 GitHub authentication modes:
 * 1. Fine-grained / Classic PAT
 * 2. Device Flow (Serverless OAuth or GitHub App)
 * 3. GitHub App (expiring user access tokens + refresh tokens)
 * 4. OAuth App (Web Flow via optional Relay Server)
 * 5. SSH Key Pair (Git transport over SSH)
 */
class AuthVault(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "grude_auth_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun listAccounts(): List<AccountProfile> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AccountProfile>>(raw) }.getOrDefault(emptyList())
    }

    fun getActiveAccount(): AccountProfile? {
        val activeId = prefs.getString(KEY_ACTIVE_ID, null) ?: return listAccounts().firstOrNull()
        return listAccounts().find { it.id == activeId } ?: listAccounts().firstOrNull()
    }

    fun saveAccount(profile: AccountProfile, makeActive: Boolean = true) {
        val updated = listAccounts().filterNot { it.id == profile.id } + profile
        prefs.edit().apply {
            putString(KEY_ACCOUNTS, json.encodeToString(updated))
            if (makeActive) putString(KEY_ACTIVE_ID, profile.id)
        }.apply()
    }

    fun setActiveAccount(accountId: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, accountId).apply()
    }

    fun removeAccount(accountId: String) {
        val remaining = listAccounts().filterNot { it.id == accountId }
        prefs.edit().apply {
            putString(KEY_ACCOUNTS, json.encodeToString(remaining))
            if (prefs.getString(KEY_ACTIVE_ID, null) == accountId) {
                putString(KEY_ACTIVE_ID, remaining.firstOrNull()?.id)
            }
        }.apply()
    }

    /**
     * Generates an on-device RSA-3072 or Ed25519 SSH key pair and stores it inside the vault.
     * The returned public key can be copied directly into GitHub Settings -> SSH and GPG keys.
     */
    fun generateSshKeyProfile(label: String): AccountProfile {
        val keyPair = runCatching {
            KeyPairGenerator.getInstance("Ed25519").apply {
                initialize(NamedParameterSpec.ED25519)
            }.generateKeyPair()
        }.getOrElse {
            KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        }

        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val privB64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val algoPrefix = if (keyPair.public.algorithm.equals("Ed25519", ignoreCase = true)) "ssh-ed25519" else "ssh-rsa"

        val publicKeyLine = "$algoPrefix $pubB64 grude-$label"
        val privateKeyPem = "-----BEGIN PRIVATE KEY-----\n$privB64\n-----END PRIVATE KEY-----"

        val profile = AccountProfile(
            id = UUID.randomUUID().toString(),
            username = label.ifBlank { "ssh-user" },
            displayName = "SSH Key ($label)",
            authMethod = AuthMethod.SSH_KEY,
            sshPublicKey = publicKeyLine,
            sshPrivateKeyPem = privateKeyPem
        )
        saveAccount(profile, makeActive = false)
        return profile
    }

    /**
     * Builds the GitHub OAuth / GitHub App browser authorization URL for Web Flow.
     * Redirects back to grude://callback where the optional Relay Server exchanges the code.
     */
    fun buildOAuthAuthorizeUrl(
        clientId: String,
        scopes: String = "repo workflow read:user",
        redirectUri: String = "grude://callback"
    ): String {
        val state = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_OAUTH_STATE, state).apply()
        return "https://github.com/login/oauth/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&scope=${scopes.replace(" ", "%20")}" +
            "&state=$state"
    }

    fun getLastOAuthState(): String? = prefs.getString(KEY_OAUTH_STATE, null)

    companion object {
        private const val KEY_ACCOUNTS = "accounts_json"
        private const val KEY_ACTIVE_ID = "active_account_id"
        private const val KEY_OAUTH_STATE = "oauth_state"
    }
}
