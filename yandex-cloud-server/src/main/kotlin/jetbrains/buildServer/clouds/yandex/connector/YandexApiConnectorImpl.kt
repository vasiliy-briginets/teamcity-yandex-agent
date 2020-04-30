package jetbrains.buildServer.clouds.yandex.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import io.grpc.CallCredentials
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.StatusRuntimeException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.yandex.YandexCloudImage
import jetbrains.buildServer.clouds.yandex.YandexCloudInstance
import jetbrains.buildServer.clouds.yandex.YandexConstants
import jetbrains.buildServer.clouds.yandex.utils.AlphaNumericStringComparator
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.io.pem.PemReader
import yandex.cloud.access.Access
import yandex.cloud.compute.v1.*
import yandex.cloud.compute.v1.ImageServiceOuterClass.*
import yandex.cloud.compute.v1.InstanceOuterClass.*
import yandex.cloud.compute.v1.InstanceServiceOuterClass.*
import yandex.cloud.endpoint.ApiEndpointServiceGrpc
import yandex.cloud.endpoint.ApiEndpointServiceOuterClass
import yandex.cloud.iam.v1.IamTokenServiceGrpc
import yandex.cloud.iam.v1.IamTokenServiceOuterClass.CreateIamTokenRequest
import yandex.cloud.iam.v1.ServiceAccountServiceGrpc
import yandex.cloud.iam.v1.ServiceAccountServiceOuterClass.GetServiceAccountRequest
import yandex.cloud.operation.OperationServiceGrpc
import yandex.cloud.operation.OperationServiceOuterClass
import yandex.cloud.resourcemanager.v1.FolderServiceGrpc
import yandex.cloud.vpc.v1.NetworkServiceGrpc
import yandex.cloud.vpc.v1.NetworkServiceOuterClass
import yandex.cloud.vpc.v1.NetworkServiceOuterClass.*
import yandex.cloud.vpc.v1.SubnetServiceGrpc
import yandex.cloud.vpc.v1.SubnetServiceOuterClass
import yandex.cloud.vpc.v1.SubnetServiceOuterClass.*
import java.io.StringReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor

class YandexApiConnectorImpl(accessKey: String) : YandexApiConnector {

    private val comparator = AlphaNumericStringComparator()
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private var myServiceAccountId: String? = null
    private var myKeyId: String? = null
    private var privateKey: PrivateKey? = null
    private var myFolderId: String? = null
    private val mapper = ObjectMapper()

    init {
        val json = mapper.readValue(accessKey, Map::class.java)

        (json["service_account_id"] as String?)?.let { myServiceAccountId = it }
        (json["id"] as String?)?.let { myKeyId = it }
        var pem = ""
        (json["private_key"] as String?)?.let { pem = it }
        val privateKeyPem = PemReader(StringReader(pem.replace("\\n", "\n"))).readPemObject()
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyPem.content))
    }

    fun loadFolderId() {
        val serviceAccount = serviceAccountService.get(GetServiceAccountRequest.newBuilder()
                .setServiceAccountId(myServiceAccountId)
                .build())
        myFolderId = serviceAccount.folderId
    }

    override fun test() {
        try {
            if (folderService.listAccessBindings(Access.ListAccessBindingsRequest.newBuilder()
                            .setResourceId(myFolderId)
                            .build()).accessBindingsList.none() {
                        it.subject.id == myServiceAccountId && (it.roleId == "editor" || it.roleId == "admin")
                    }) {
                throw CloudException("Missing required role editor or admin ")
            }
        } catch (ex: StatusRuntimeException) {
            throw CloudException("Missing required role editor or admin ")
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun createImageInstance(instance: YandexCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val details = instance.image.imageDetails

        val metadata = mutableMapOf(
                YandexConstants.TAG_SERVER to myServerId,
                YandexConstants.TAG_DATA to userData.serialize(),
                YandexConstants.TAG_PROFILE to myProfileId,
                YandexConstants.TAG_SOURCE to details.sourceId
        ).apply {
            details.metadata?.let {
                if (it.isBlank()) {
                    return@let
                }
                val json = try {
                    mapper.readValue(it, Map::class.java)
                } catch (e: Exception) {
                    LOG.warn("Invalid JSON metadata $it", e)
                    return@let
                }
                for (entry in json) {
                    val key = entry.key as String
                    if (entry.value is String) {
                        this[key] = entry.value as String
                    } else {
                        LOG.warn("Invalid value for metadata key $key")
                    }
                }
            }
        }

        val image = imageService.get(GetImageRequest.newBuilder()
                .setImageId(details.sourceImage)
                .build())
                .get()

        val request = CreateInstanceRequest.newBuilder()
                .setFolderId(myFolderId)
                .setName(instance.name)
                .setZoneId(instance.zone)
                .setPlatformId("standard-v1")
                .setResourcesSpec(ResourcesSpec.newBuilder()
                        .setCores(details.machineCores)
                        .setMemory(details.machineMemory))
                .putAllMetadata(metadata)
                .setBootDiskSpec(AttachedDiskSpec.newBuilder()
                        .setAutoDelete(true)
                        .setMode(AttachedDiskSpec.Mode.READ_WRITE)
                        .setDiskSpec(AttachedDiskSpec.DiskSpec.newBuilder()
                                .setImageId(image.id)
                                .setSize(image.minDiskSize)
                                .apply {
                                    if (!details.diskType.isNullOrBlank()) {
                                        typeId = details.diskType
                                    }
                                }
                                .build())
                )
                .addNetworkInterfaceSpecs(NetworkInterfaceSpec.newBuilder()
                        .setSubnetId(details.subnet)
                        .setPrimaryV4AddressSpec(PrimaryAddressSpec.newBuilder()
                                .setOneToOneNatSpec(OneToOneNatSpec.newBuilder()
                                        .setIpVersion(IpVersion.IPV4)))
                        .apply {
                            if (details.ipv6) {
                                setPrimaryV6AddressSpec(PrimaryAddressSpec.newBuilder())
                            }
                        }
                        .build())
                .setSchedulingPolicy(SchedulingPolicy.newBuilder().setPreemptible(instance.image.imageDetails.preemptible))
                .apply {
                    if (!details.serviceAccount.isNullOrBlank()) {
                        serviceAccountId = details.serviceAccount
                    }
                }
                .build()

        instanceService.create(request)
                .await()
        Unit
    }

    override suspend fun startVm(instance: YandexCloudInstance) = coroutineScope {
        instanceService.start(StartInstanceRequest.newBuilder()
                .setInstanceId(getInstanceId(instance))
                .build())
                .await()
        Unit
    }

    override suspend fun restartVm(instance: YandexCloudInstance) = coroutineScope {
        instanceService.restart(RestartInstanceRequest.newBuilder()
                .setInstanceId(getInstanceId(instance))
                .build())
                .await()
        Unit
    }

    override suspend fun stopVm(instance: YandexCloudInstance) = coroutineScope {
        instanceService.stop(StopInstanceRequest.newBuilder()
                .setInstanceId(getInstanceId(instance))
                .build())
                .await()
        Unit
    }

    override suspend fun deleteVm(instance: YandexCloudInstance) = coroutineScope {
        instanceService.delete(DeleteInstanceRequest.newBuilder()
                .setInstanceId(getInstanceId(instance))
                .build())
                .await()
        Unit
    }

    private fun getInstanceId(instance: YandexCloudInstance): String {
        val response = instanceService.list(ListInstancesRequest.newBuilder()
                .setFolderId(myFolderId)
                .build())
                .get()
        return response.instancesList
                .first { it.name == instance.id }
                .id
    }

    override fun checkImage(image: YandexCloudImage) = runBlocking {
        val errors = image.handler.checkImage(image)
        errors.map { TypedCloudErrorInfo.fromException(it) }.toTypedArray()
    }

    override fun checkInstance(instance: YandexCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    override suspend fun getImages() = coroutineScope {
        val images = imageService.list(ListImagesRequest.newBuilder()
                .setFolderId(myFolderId)
                .build())
                .await()
        images.imagesList
                .map { it.id to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getZones() = coroutineScope {
        val zones = zoneService.list(ZoneServiceOuterClass.ListZonesRequest.getDefaultInstance())
                .await()
        zones.zonesList
                .map { it.id to listOf(it.id, it.regionId) }
                .sortedWith(compareBy(comparator) { it.second.first() })
                .associate { it.first to it.second }
    }

    override suspend fun getNetworks() = coroutineScope {
        val networks = networkService.list(ListNetworksRequest.newBuilder()
                .setFolderId(myFolderId)
                .build())
                .await()

        networks.networksList
                .map { it.id to it.name }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getSubnets() = coroutineScope {
        val subnets = subnetService.list(ListSubnetsRequest.newBuilder()
                .setFolderId(myFolderId)
                .build())
                .await()

        subnets.subnetsList
                .map { it.id to listOf(it.name, it.networkId) }
                .sortedWith(compareBy(comparator) { it.second.first() })
                .associate { it.first to it.second }
    }

    override suspend fun getDiskTypes() = coroutineScope {
        val diskTypes = diskTypeService.list(DiskTypeServiceOuterClass.ListDiskTypesRequest.getDefaultInstance())
                .await()

        diskTypes.diskTypesList
                .map { it.id to it.description }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override fun <R : AbstractInstance?> fetchInstances(image: YandexCloudImage): MutableMap<String, R> {
        val instances = fetchInstances<R>(arrayListOf(image))
        return instances[image] as MutableMap<String, R>
    }

    override fun <R : AbstractInstance?> fetchInstances(images: MutableCollection<YandexCloudImage>)
            : MutableMap<YandexCloudImage, MutableMap<String, R>> {
        val map = mutableMapOf<String, MutableList<Instance>>()
        instanceService.list(ListInstancesRequest.newBuilder().setFolderId(myFolderId).build()).get().instancesList
                .forEach {
                    val instance = instanceService.get(GetInstanceRequest.newBuilder()
                            .setInstanceId(it.id)
                            .setView(InstanceView.FULL)
                            .build())
                            .get()
                    val metadata = instance.metadataMap
                    if (metadata[YandexConstants.TAG_SERVER] != myServerId) return@forEach
                    if (metadata[YandexConstants.TAG_PROFILE] != myProfileId) return@forEach
                    if (metadata[YandexConstants.TAG_DATA].isNullOrEmpty()) return@forEach
                    metadata[YandexConstants.TAG_SOURCE]?.let { sourceId ->
                        val list = map.getOrPut(sourceId) { mutableListOf() }
                        list.add(instance)
                    }
                }

        val result = hashMapOf<YandexCloudImage, MutableMap<String, R>>()

        for (image in images) {
            val instances = hashMapOf<String, R>()
            map[image.imageDetails.sourceId]?.let { foundInstances ->
                foundInstances.forEach {
                    val name = it.name
                    val zone = it.zoneId
                    @Suppress("UNCHECKED_CAST")
                    instances[name] = YandexInstance(it, zone) as R

                    if (Instance.Status.STOPPED == it.status) {
                        GlobalScope.launch(image.coroutineContext) {
                            try {
                                LOG.info("Removing stopped instance $name")
                                deleteVm(YandexCloudInstance(image, it.name, zone))
                            } catch (e: Exception) {
                                LOG.infoAndDebugDetails("Failed to remove instance $name", e)
                            }
                        }
                    }
                }
            }
            result[image] = instances
        }

        return result
    }

    fun setServerId(serverId: String?) {
        myServerId = serverId
    }

    fun setProfileId(profileId: String) {
        myProfileId = profileId
    }

    private val operationService: OperationServiceGrpc.OperationServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["operation"]).build()
        OperationServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val instanceService: InstanceServiceGrpc.InstanceServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["compute"]).build()
        InstanceServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val imageService: ImageServiceGrpc.ImageServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["compute"]).build()
        ImageServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val networkService: NetworkServiceGrpc.NetworkServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["vpc"]).build()
        NetworkServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val subnetService: SubnetServiceGrpc.SubnetServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["vpc"]).build()
        SubnetServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val diskTypeService: DiskTypeServiceGrpc.DiskTypeServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["compute"]).build()
        DiskTypeServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val zoneService: ZoneServiceGrpc.ZoneServiceFutureStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["compute"]).build()
        ZoneServiceGrpc.newFutureStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val serviceAccountService: ServiceAccountServiceGrpc.ServiceAccountServiceBlockingStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["iam"]).build()
        ServiceAccountServiceGrpc.newBlockingStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val folderService: FolderServiceGrpc.FolderServiceBlockingStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["resourcemanager"]).build()
        FolderServiceGrpc.newBlockingStub(channel).withCallCredentials(YandexCallCredentials())
    }

    private val iamTokenService: IamTokenServiceGrpc.IamTokenServiceBlockingStub by lazy {
        val channel = ManagedChannelBuilder.forTarget(endpoints["iam"]).build()
        IamTokenServiceGrpc.newBlockingStub(channel)
    }

    private val endpoints: Map<String, String> by lazy {
        val channel = ManagedChannelBuilder
                .forTarget(YandexConstants.API_ENDPOINT_URL)
                .build()
        val apiEndpointService = ApiEndpointServiceGrpc.newBlockingStub(channel)
        apiEndpointService.list(ApiEndpointServiceOuterClass.ListApiEndpointsRequest.getDefaultInstance()).endpointsList
                .associate { it.id to it.address }
    }

    inner class YandexCallCredentials : CallCredentials() {
        private var token: String = ""

        private var tokenExpiration: Instant = Instant.MIN
        override fun applyRequestMetadata(requestInfo: RequestInfo?, appExecutor: Executor?, applier: MetadataApplier?) {
            val metadata = Metadata()
            val issueToken = issueToken()

            metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + issueToken)
            applier?.apply(metadata)
        }

        override fun thisUsesUnstableApi() {
        }

        private fun issueToken(): String {
            if (tokenExpiration.isAfter(Instant.now())) {
                return token
            }
            synchronized(this) {
                val now = Instant.now()
                if (tokenExpiration.isAfter(now)) {
                    return token
                }
                val expiration = now.plusSeconds(360)
                val jwtToken = Jwts.builder()
                        .setHeaderParam("kid", myKeyId)
                        .setIssuer(myServiceAccountId)
                        .setAudience("https://iam.api.cloud.yandex.net/iam/v1/tokens")
                        .setIssuedAt(Date.from(now))
                        .setExpiration(Date.from(expiration))
                        .signWith(privateKey, SignatureAlgorithm.PS256)
                        .compact()
                token = iamTokenService.create(CreateIamTokenRequest.newBuilder()
                        .setJwt(jwtToken)
                        .build())
                        .iamToken
                tokenExpiration = expiration.minusSeconds(60)
            }
            return token
        }

    }

    companion object {
        private val LOG = Logger.getInstance(YandexApiConnectorImpl::class.java.name)

        private fun nonEmpty(string: String?, defaultValue: String): String {
            string?.let {
                if (it.isNotBlank()) return it
            }
            return defaultValue
        }
    }
}