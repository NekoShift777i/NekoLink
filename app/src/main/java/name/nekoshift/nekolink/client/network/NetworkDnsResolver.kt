package name.nekoshift.nekolink.client.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address

object NetworkDnsResolver {

    suspend fun resolveIpv4(
        context: Context,
        hostname: String
    ): String = withContext(Dispatchers.IO) {
        val connectivityManager =
            context.getSystemService(ConnectivityManager::class.java)

        val network = connectivityManager.activeNetwork
            ?: throw IllegalStateException(
                "Активное сетевое подключение отсутствует"
            )

        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw IllegalStateException(
                "Не удалось получить параметры сети"
            )

        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            throw IllegalStateException(
                "Активное сетевое подключение является VPN"
            )
        }

        network.getAllByName(hostname)
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
            ?: throw IllegalStateException(
                "Не удалось определить IPv4-адрес для $hostname"
            )
    }
}