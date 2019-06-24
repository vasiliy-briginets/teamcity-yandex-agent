package jetbrains.buildServer.clouds.yandex.web

import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles disk types request.
 */
internal class DiskTypesHandler : YandexResourceHandler() {
    override suspend fun handle(connector: YandexApiConnector, parameters: Map<String, String>) = coroutineScope {
        val diskTypes = connector.getDiskTypes()
        val diskTypesElement = Element("diskTypes")

        for ((id, displayName) in diskTypes) {
            diskTypesElement.addContent(Element("diskType").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        diskTypesElement
    }
}