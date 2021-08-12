package jetbrains.buildServer.clouds.yandex.web

import com.fasterxml.jackson.databind.ObjectMapper
import jetbrains.buildServer.clouds.yandex.YandexCloudImage
import jetbrains.buildServer.web.openapi.PageExtension
import jetbrains.buildServer.web.openapi.PagePlaces
import jetbrains.buildServer.web.openapi.PlaceId
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest

class CloudImageDetailsPageExtension(pagePlaces: PagePlaces, private val descriptor: PluginDescriptor): PageExtension {
    private val mapper = ObjectMapper()

    init {
        pagePlaces.getPlaceById(PlaceId.CLOUD_IMAGE_DETAILS).addExtension(this)
    }

    override fun getPluginName(): String = "YandexCloudImageDetailsPageExtension"

    override fun getIncludeUrl(): String = descriptor.getPluginResourcesPath("CloudImageDetailsExtension.jsp")

    override fun getCssPaths() = mutableListOf<String>()

    override fun getJsPaths() = mutableListOf<String>()

    override fun isAvailable(request: HttpServletRequest): Boolean = true

    override fun fillModel(model: MutableMap<String, Any>, request: HttpServletRequest) {
        model["resPath"] = descriptor.pluginResourcesPath
        model["imageURL"] = descriptor.getPluginResourcesPath("cloud.png")
        val image = model["image"]
        if (image is YandexCloudImage) {
            model["agents"] = image.instances.stream()
                .map { i -> Agent(i.name, i.folderId, i.computeId) }
                .collect(Collectors.toList())
                .map { l -> mapper.writeValueAsString(l) }
        }
    }
}

private data class Agent(
    val name: String,
    val folder: String,
    val instanceID: String,
)
