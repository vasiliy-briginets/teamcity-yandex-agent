package jetbrains.buildServer.clouds.yandex.web

import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles permissions request.
 */
internal class PermissionsHandler : YandexResourceHandler() {
    override suspend fun handle(connector: YandexApiConnector, parameters: Map<String, String>) = coroutineScope {
        val permissions = Element("permissions")
        try {
            connector.test()
        } catch (e: CloudException) {
            throw e
        }
        permissions
    }
}