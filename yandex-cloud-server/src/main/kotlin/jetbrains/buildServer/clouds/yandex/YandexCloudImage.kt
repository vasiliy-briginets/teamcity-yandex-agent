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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.yandex.connector.YandexApiConnector
import jetbrains.buildServer.clouds.yandex.types.YandexImageHandler
import jetbrains.buildServer.clouds.yandex.utils.IdProvider
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Yandex cloud image.
 */
class YandexCloudImage constructor(private val myImageDetails: YandexCloudImageDetails,
                                   private val myApiConnector: YandexApiConnector,
                                   private val myIdProvider: IdProvider)
    : AbstractCloudImage<YandexCloudInstance, YandexCloudImageDetails>(myImageDetails.sourceId, myImageDetails.sourceId),
DisposableHandle, CoroutineScope {

    private val job = Job()

    override fun dispose() {
        job.cancel()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    override fun getImageDetails(): YandexCloudImageDetails {
        return myImageDetails
    }

    override fun createInstanceFromReal(realInstance: AbstractInstance): YandexCloudInstance {
        val zone = realInstance.properties[YandexConstants.ZONE]!!
        val instanceId = realInstance.properties[YandexConstants.COMPUTE_ID]!!
        LOG.debug("Setting computeID from instance properties: $instanceId")
        return YandexCloudInstance(this, realInstance.name, zone).apply {
            properties = realInstance.properties
            computeId = instanceId
            folderId = realInstance.properties[YandexConstants.FOLDER_ID] ?: ""
        }
    }

    override fun canStartNewInstance(): Boolean {
        return activeInstances.size < myImageDetails.maxInstances
    }

    override fun startNewInstance(userData: CloudInstanceUserData): YandexCloudInstance = runBlocking {
        if (!canStartNewInstance()) {
            throw QuotaException("Unable to start more instances. Limit has reached")
        }

        createInstance(userData)
    }

    /**
     * Creates a new virtual machine.
     *
     * @param userData info about server.
     * @return created instance.
     */
    private fun createInstance(userData: CloudInstanceUserData): YandexCloudInstance {
        val name = getInstanceName()
        val instance = YandexCloudInstance(this, name, imageDetails.zone)
        instance.status = InstanceStatus.SCHEDULED_TO_START
        val data = YandexUtils.setVmNameForTag(userData, name)

        launch {
            try {
                LOG.info("Creating new virtual machine ${instance.name}")
                handler.createInstance(instance, data)
                instance.status = InstanceStatus.STARTING
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                LOG.info("Removing allocated resources for virtual machine ${instance.name}")
                try {
                    myApiConnector.deleteVm(instance)
                    LOG.info("Allocated resources for virtual machine ${instance.name} have been removed")
                    removeInstance(instance.instanceId)
                } catch (e: Throwable) {
                    val message = "Failed to delete allocated resources for virtual machine ${instance.name}: ${e.message}"
                    LOG.warnAndDebugDetails(message, e)
                }
            }
        }.invokeOnCompletion { if(instance.status==InstanceStatus.STARTING) {
            instance.status = InstanceStatus.RUNNING
        } }

        addInstance(instance)

        return instance
    }

    override fun restartInstance(instance: YandexCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        launch {
            try {
                LOG.info("Restarting virtual machine ${instance.name}")
                myApiConnector.restartVm(instance)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun terminateInstance(instance: YandexCloudInstance) {
        instance.status = InstanceStatus.SCHEDULED_TO_STOP

        launch {
            try {
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image settings")
                    myApiConnector.deleteVm(instance)
                } else {
                    LOG.info("Stopping virtual machine ${instance.name}")
                    myApiConnector.stopVm(instance)
                }
                instance.status = InstanceStatus.STOPPED
                LOG.info("Virtual machine ${instance.name} has been successfully terminated")
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun getAgentPoolId(): Int? {
        return myImageDetails.agentPoolId
    }

    val handler = YandexImageHandler(myApiConnector)

    private fun getInstanceName(): String {
        val sourceName = myImageDetails.sourceId.toLowerCase()

        val id = if (imageDetails.growingId) {
            myIdProvider.nextId
        } else {
            val keys = instances.map { it.instanceId.toLowerCase() }
            var i = 1
            while (keys.contains(sourceName + i)) i++
            i
        }

        return sourceName + id
    }

    /**
     * Returns active instances.
     *
     * @return instances.
     */
    private val activeInstances: List<YandexCloudInstance>
        get() = instances.filter { instance -> instance.status.isStartingOrStarted }

    companion object {
        private val LOG = Logger.getInstance(YandexCloudImage::class.java.name)
    }
}
