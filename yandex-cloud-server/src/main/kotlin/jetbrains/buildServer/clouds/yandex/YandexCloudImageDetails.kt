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

import com.google.gson.annotations.SerializedName
import jetbrains.buildServer.clouds.CloudImageParameters
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails
import jetbrains.buildServer.clouds.base.types.CloneBehaviour

/**
 * Yandex cloud image details.
 */
class YandexCloudImageDetails(
        @SerializedName(CloudImageParameters.SOURCE_ID_FIELD)
        private val sourceId: String,
        @SerializedName(YandexConstants.SOURCE_IMAGE)
        val sourceImage: String?,
        @SerializedName(YandexConstants.ZONE)
        val zone: String,
        @SerializedName(YandexConstants.NETWORK_ID)
        val network: String?,
        @SerializedName(YandexConstants.SUBNET_ID)
        val subnet: String,
        @SerializedName(YandexConstants.IPV6)
        val ipv6: Boolean = false,
        @SerializedName(YandexConstants.NAT)
        val nat: Boolean = true,
        @SerializedName(YandexConstants.MACHINE_CORES)
        val machineCores: Long,
        @SerializedName(YandexConstants.MACHINE_MEMORY)
        val machineMemory: Long,
        @SerializedName(YandexConstants.MAX_INSTANCES_COUNT)
        private val maxInstances: Int,
        @SerializedName(CloudImageParameters.AGENT_POOL_ID_FIELD)
        val agentPoolId: Int?,
        @SerializedName(YandexConstants.PREEMPTIBLE)
        val preemptible: Boolean = false,
        @SerializedName(YandexConstants.DISK_TYPE)
        val diskType: String?,
        @SerializedName(YandexConstants.METADATA)
        val metadata: String?,
        @SerializedName(YandexConstants.GROWING_ID)
        val growingId: Boolean = false,
        @SerializedName(YandexConstants.SERVICE_ACCOUNT)
        val serviceAccount: String?,
        @SerializedName(YandexConstants.INSTANCE_FOLDER)
        val instanceFolder: String?) : CloudImageDetails {

    override fun getSourceId(): String {
        return sourceId
    }

    override fun getMaxInstances(): Int {
        return maxInstances
    }

    override fun getBehaviour(): CloneBehaviour {
        return CloneBehaviour.FRESH_CLONE
    }
}
