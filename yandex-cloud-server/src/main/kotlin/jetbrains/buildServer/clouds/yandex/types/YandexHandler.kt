package jetbrains.buildServer.clouds.yandex.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.yandex.YandexCloudImage
import jetbrains.buildServer.clouds.yandex.YandexCloudInstance

interface YandexHandler {
    suspend fun checkImage(image: YandexCloudImage): List<Throwable>
    suspend fun createInstance(instance: YandexCloudInstance, userData: CloudInstanceUserData)
}
