package com.s3arch.app.data.vpn

import android.content.Context
import android.content.Intent
import com.s3arch.app.util.AppLog
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Tunnel WireGuard embarque dans l'application.
 *
 * La Freebox fournit la configuration du client (`vpn/download_config`), et cette
 * bibliotheque officielle (celle de l'application WireGuard) monte le tunnel via
 * `VpnService`. L'utilisateur doit autoriser une fois le VPN au niveau d'Android :
 * c'est ce que renvoie [consentIntent], declenche par l'ecran.
 *
 * Toutes les methodes sont bloquantes : elles s'appellent depuis `Dispatchers.IO`.
 */
object VpnTunnel {

    /** Nom du tunnel (visible dans les statistiques WireGuard). */
    private const val NAME = "s3arch"

    private val tunnel = object : Tunnel {
        override fun getName(): String = NAME

        override fun onStateChange(newState: Tunnel.State) {
            AppLog.info("VPN", "Tunnel ${newState.name.lowercase()}")
        }
    }

    @Volatile
    private var backend: GoBackend? = null

    private fun backend(context: Context): GoBackend =
        backend ?: GoBackend(context.applicationContext).also { backend = it }

    /** Intent d'autorisation Android, ou `null` si l'autorisation est deja accordee. */
    fun consentIntent(context: Context): Intent? =
        runCatching { GoBackend.VpnService.prepare(context) }.getOrNull()

    fun isUp(context: Context): Boolean = runCatching {
        backend(context).getState(tunnel) == Tunnel.State.UP
    }.getOrDefault(false)

    /**
     * Monte le tunnel a partir de la configuration wg-quick renvoyee par la Freebox.
     *
     * @return le message d'erreur en cas d'echec, `null` si le tunnel est monte.
     */
    fun up(context: Context, wgQuickConfig: String): String? {
        if (wgQuickConfig.isBlank()) return "Aucune configuration VPN enregistrée"
        return try {
            val reader = BufferedReader(StringReader(wgQuickConfig))
            val config = Config.parse(reader)
            backend(context).setState(tunnel, Tunnel.State.UP, config)
            AppLog.info("VPN", "Tunnel monté")
            null
        } catch (e: Exception) {
            AppLog.throwable("VPN", "Montage du tunnel impossible", e)
            e.message ?: e.javaClass.simpleName
        }
    }

    fun down(context: Context) {
        runCatching {
            if (isUp(context)) backend(context).setState(tunnel, Tunnel.State.DOWN, null)
        }.onFailure { AppLog.warn("VPN", "Arrêt du tunnel impossible : ${it.message}") }
    }
}
