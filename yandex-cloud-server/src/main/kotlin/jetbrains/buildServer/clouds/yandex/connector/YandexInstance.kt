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

import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.yandex.YandexConstants
import yandex.cloud.compute.v1.InstanceOuterClass
import java.text.SimpleDateFormat
import java.util.*

/**
 * Yandex cloud instance.
 */
class YandexInstance internal constructor(private val instance: InstanceOuterClass.Instance, zone: String) : AbstractInstance() {

    private val properties: Map<String, String>

    init {
        properties = instance.metadataMap.toMutableMap()
        properties[YandexConstants.ZONE] = zone
        properties[YandexConstants.COMPUTE_ID] = instance.id
    }

    override fun getName(): String {
        return instance.name
    }

    override fun getStartDate(): Date? {
        return Date(instance.createdAt.seconds * 1000L + instance.createdAt.nanos / 1000_000L)
    }

    override fun getIpAddress(): String? {
        val interfaces = instance.networkInterfacesList
        if (interfaces.isEmpty()) return null
        if (interfaces[0].primaryV6Address.address.isNotEmpty()) return interfaces[0].primaryV6Address.address
        if (interfaces[0].primaryV4Address.hasOneToOneNat()) return interfaces[0].primaryV4Address.oneToOneNat.address
        return interfaces[0].primaryV4Address.address
    }

    override fun getInstanceStatus(): InstanceStatus {
        STATES[instance.status.toString()]?.let {
            return it
        }

        return InstanceStatus.UNKNOWN
    }

    override fun getProperties() = properties

    companion object {
        private var STATES = TreeMap<String, InstanceStatus>(String.CASE_INSENSITIVE_ORDER)
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        init {
            STATES["PROVISIONING"] = InstanceStatus.SCHEDULED_TO_START
            STATES["RUNNING"] = InstanceStatus.RUNNING
            STATES["STOPPING"] = InstanceStatus.STOPPING
            STATES["STOPPED"] = InstanceStatus.STOPPED
            STATES["RESTARTING"] = InstanceStatus.RESTARTING
        }
    }
}
