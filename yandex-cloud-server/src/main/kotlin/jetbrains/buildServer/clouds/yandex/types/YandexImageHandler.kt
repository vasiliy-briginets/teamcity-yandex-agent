package jetbrains.buildServer.clouds.yandex.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.clouds.yandex.YandexCloudImage
import jetbrains.buildServer.clouds.yandex.YandexCloudInstance
import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnector
import kotlinx.coroutines.coroutineScope
import java.util.*

class YandexImageHandler(private val connector: YandexApiConnector) : YandexHandler {

    override suspend fun checkImage(image: YandexCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        if (details.sourceImage.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image should not be empty"))
        } else {
            if (!connector.getImages().containsKey(details.sourceImage)) {
                exceptions.add(CheckedCloudException("Image does not exist"))
            }
        }

        if (details.machineCores < 2) {
            exceptions.add(CheckedCloudException("Number of cores should be positive value equal or greater 2"))
        }
        if (details.machineMemory <= 0) {
            exceptions.add(CheckedCloudException("Machine memory should be positive value"))
        }

        exceptions
    }

    override suspend fun createInstance(instance: YandexCloudInstance, userData: CloudInstanceUserData) =
            connector.createImageInstance(instance, userData)
}