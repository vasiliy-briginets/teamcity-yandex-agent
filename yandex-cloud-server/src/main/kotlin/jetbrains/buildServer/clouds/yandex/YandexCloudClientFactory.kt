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

package jetbrains.buildServer.clouds.yandex

import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnectorImpl
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.ServerPaths
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.io.File
import java.util.*

/**
 * Constructs Yandex cloud clients.
 */
class YandexCloudClientFactory(cloudRegistrar: CloudRegistrar,
                               serverPaths: ServerPaths,
                               private val myPluginDescriptor: PluginDescriptor,
                               private val mySettings: ServerSettings,
                               private val myImagesHolder: YandexCloudImagesHolder)
    : AbstractCloudClientFactory<YandexCloudImageDetails, YandexCloudClient>(cloudRegistrar) {

    private val myIdxStorage: File = File(serverPaths.pluginDataDirectory, "yandexIdx")

    init {
        if (!myIdxStorage.exists()) {
            myIdxStorage.mkdirs()
        }
    }

    override fun createNewClient(state: CloudState,
                                 images: Collection<YandexCloudImageDetails>,
                                 params: CloudClientParameters): YandexCloudClient {
        return createNewClient(state, params, emptyArray())
    }

    override fun createNewClient(state: CloudState,
                                 params: CloudClientParameters,
                                 errors: Array<TypedCloudErrorInfo>): YandexCloudClient {
        val accessKey = getParameter(params, YandexConstants.ACCESS_KEY)
        val apiConnector = YandexApiConnectorImpl(accessKey)
        apiConnector.loadSaFolderId()
        apiConnector.setServerId(mySettings.serverUUID)
        apiConnector.setProfileId(state.profileId)

        val cloudClient = YandexCloudClient(params, apiConnector, myImagesHolder, myIdxStorage)
        cloudClient.updateErrors(*errors)

        return cloudClient
    }

    private fun getParameter(params: CloudClientParameters, parameter: String): String {
        return params.getParameter(parameter) ?: throw RuntimeException("$parameter must not be empty")
    }

    override fun parseImageData(params: CloudClientParameters): Collection<YandexCloudImageDetails> {
        if (!params.getParameter(CloudImageParameters.SOURCE_IMAGES_JSON).isNullOrEmpty()) {
            return YandexUtils.parseImageData(YandexCloudImageDetails::class.java, params)
        }

        return params.cloudImages.map {
            YandexCloudImageDetails(
                    it.id!!,
                    it.getParameter(YandexConstants.SOURCE_IMAGE),
                    it.getParameter(YandexConstants.ZONE)!!,
                    it.getParameter(YandexConstants.SUBNET_ID)!!,
                    (it.getParameter(YandexConstants.IPV6)
                            ?: "").toBoolean(),
                    (it.getParameter(YandexConstants.NAT)
                            ?: "").toBoolean(),
                    it.getParameter(YandexConstants.SECURITY_GROUPS),
                    it.getParameter(YandexConstants.MACHINE_CORES)!!.toLong(),
                    it.getParameter(YandexConstants.MACHINE_MEMORY)!!.toLong()*1024*1024*1024,
                    (it.getParameter(YandexConstants.MAX_INSTANCES_COUNT)
                            ?: "1").toInt(),
                    it.agentPoolId,
                    (it.getParameter(YandexConstants.PREEMPTIBLE)
                            ?: "").toBoolean(),
                    it.getParameter(YandexConstants.DISK_TYPE),
                    it.getParameter(YandexConstants.DISK_SIZE).let { if (it.isNullOrEmpty()) 0 else it.toLong()*1024*1024*1024 },
                    it.getParameter(YandexConstants.SECONDARY_DISK_TYPE),
                    it.getParameter(YandexConstants.SECONDARY_DISK_SIZE).let { if (it.isNullOrEmpty()) 0 else it.toLong()*1024*1024*1024 },
                    it.getParameter(YandexConstants.SECONDARY_DISK_MOUNT_PATH),
                    it.getParameter(YandexConstants.METADATA),
                    it.getParameter(YandexConstants.CLOUD_CONFIG),
                    it.getParameter(YandexConstants.DNS_RECORDS),
                    it.getParameter(YandexConstants.HOSTNAME),
                    (it.getParameter(YandexConstants.GROWING_ID)
                            ?: "").toBoolean(),
                    it.getParameter(YandexConstants.SERVICE_ACCOUNT),
                    it.getParameter(YandexConstants.INSTANCE_FOLDER),
                    it.getParameter(YandexConstants.PLATFOTM_ID),
                    it.getParameter(YandexConstants.CUSTOM_PROPS)
            )
        }
    }

    override fun checkClientParams(params: CloudClientParameters): Array<TypedCloudErrorInfo>? {
        return emptyArray()
    }

    override fun getCloudCode(): String {
        return "yandex"
    }

    override fun getDisplayName(): String {
        return "Yandex Compute Engine"
    }

    override fun getEditProfileUrl(): String? {
        return myPluginDescriptor.getPluginResourcesPath("settings.html")
    }

    override fun getInitialParameterValues(): Map<String, String> {
        return emptyMap()
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return PropertiesProcessor { properties ->
            properties.keys
                    .filter { SKIP_PARAMETERS.contains(it) }
                    .forEach { properties.remove(it) }

            emptyList()
        }
    }

    override fun canBeAgentOfType(description: AgentDescription): Boolean {
        return description.configurationParameters.containsKey(YandexAgentProperties.INSTANCE_NAME)
    }

    companion object {
        private val SKIP_PARAMETERS = Arrays.asList(CloudImageParameters.SOURCE_ID_FIELD)
    }
}
