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

package jetbrains.buildServer.clouds.yandex.connector

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.yandex.YandexCloudImage
import jetbrains.buildServer.clouds.yandex.YandexCloudInstance

/**
 * Yandex API connector.
 */
interface YandexApiConnector : CloudApiConnector<YandexCloudImage, YandexCloudInstance> {
    suspend fun createImageInstance(instance: YandexCloudInstance, userData: CloudInstanceUserData)

    suspend fun deleteVm(instance: YandexCloudInstance)

    suspend fun restartVm(instance: YandexCloudInstance)

    suspend fun startVm(instance: YandexCloudInstance)

    suspend fun stopVm(instance: YandexCloudInstance)

    suspend fun getImages(): Map<String, String>

    suspend fun getZones(): Map<String, List<String>>

    suspend fun getNetworks(): Map<String, String>

    suspend fun getSubnets(): Map<String, List<String>>

    suspend fun getDiskTypes(): Map<String, String>
}
