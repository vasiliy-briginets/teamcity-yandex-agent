/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.yandex.web

import jetbrains.buildServer.clouds.yandex.YandexConstants
import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles sub networks request.
 */
internal class SubnetsHandler : YandexResourceHandler() {
    override suspend fun handle(connector: YandexApiConnector, parameters: Map<String, String>) = coroutineScope {
        val subnets = connector.getSubnets()
        val subnetsElement = Element("subnets")

        for ((id, props) in subnets) {
            subnetsElement.addContent(Element("subnet").apply {
                setAttribute("id", id)
                setAttribute("network", props[1])
                text = props[0]
            })
        }

        subnetsElement
    }
}
