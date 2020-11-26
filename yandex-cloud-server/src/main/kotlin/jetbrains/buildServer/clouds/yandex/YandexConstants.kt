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

import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.clouds.CloudImageParameters

/**
 * Yandex cloud constants.
 */
class YandexConstants {
    val accessKey: String
        get() = ACCESS_KEY

    val sourceImage: String
        get() = SOURCE_IMAGE

    val zone: String
        get() = ZONE

    val maxInstancesCount: String
        get() = MAX_INSTANCES_COUNT

    val subnet: String
        get() = SUBNET_ID

    val machineCores: String
        get() = MACHINE_CORES

    val machineMemory: String
        get() = MACHINE_MEMORY

    val diskType: String
        get() = DISK_TYPE

    val vmNamePrefix: String
        get() = CloudImageParameters.SOURCE_ID_FIELD

    val imagesData: String
        get() = CloudImageParameters.SOURCE_IMAGES_JSON

    val agentPoolId: String
        get() = CloudImageParameters.AGENT_POOL_ID_FIELD

    val preemptible: String
        get() = PREEMPTIBLE

    val metadata: String
        get() = METADATA

    val growingId: String
        get() = GROWING_ID

    val serviceAccount: String
        get() = SERVICE_ACCOUNT

    val ipv6: String
        get() = IPV6

    val nat: String
        get() = NAT

    val instanceFolder: String
        get() = INSTANCE_FOLDER

    val computeID: String
        get() = COMPUTE_ID

    companion object {
        const val API_ENDPOINT_URL = "api.cloud.yandex.net:443"
        const val ACCESS_KEY = Constants.SECURE_PROPERTY_PREFIX + "accessKey"
        const val SOURCE_IMAGE = "sourceImage"
        const val ZONE = "zone"
        const val SUBNET_ID = "subnet"
        const val IPV6 = "ipv6"
        const val NAT = "nat"
        const val MAX_INSTANCES_COUNT = "maxInstances"
        const val MACHINE_CORES = "machineCores"
        const val MACHINE_MEMORY = "machineMemory"
        const val DISK_TYPE = "diskType"
        const val TAG_SERVER = "teamcity-server"
        const val TAG_DATA = "teamcity-data"
        const val TAG_PROFILE = "teamcity-profile"
        const val TAG_SOURCE = "teamcity-source"
        const val PREEMPTIBLE = "preemptible"
        const val METADATA = "metadata"
        const val GROWING_ID = "growingId"
        const val SERVICE_ACCOUNT = "serviceAccount"
        const val INSTANCE_FOLDER = "instanceFolder"
        const val COMPUTE_ID = "computeID"
    }
}
