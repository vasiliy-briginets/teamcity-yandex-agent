/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

function YandexImagesViewModel($, ko, dialog, config) {
    var self = this;

    self.loadingResources = ko.observable(false);
    self.validatingKey = ko.observable(false);
    self.errorResources = ko.observable("");
    self.showAccessKey = ko.observable(false);
    self.showMetadata = ko.observable(false);
    self.showCloudConfig = ko.observable(false);
    self.showDNSRecords = ko.observable(false);
    self.showServiceAccount = ko.observable(false);
    self.showInstanceFolder = ko.observable(false);
    self.isDragOver = ko.observable(false);
    self.hasFileReader = ko.observable(typeof FileReader !== "undefined");

    // Credentials
    self.credentials = ko.validatedObservable({
        accessKey: ko.observable().extend({required: true}).extend({
            validation: {
                async: true,
                validator: function (accessKey, otherVal, callback) {
                    var url = getBasePath() + "resource=permissions";
                    self.validatingKey(true);

                    $.post(url, {
                        "prop:secure:accessKey": accessKey
                    }).then(function (response) {
                        var $response = $(response);
                        var errors = getErrors($response);
                        if (errors) {
                            callback({isValid: false, message: errors});
                        } else {
                            callback(true);
                        }
                    }, function (error) {
                        callback({isValid: false, message: error.message});
                    }).always(function () {
                        self.validatingKey(false);
                    });
                },
                message: 'Invalid key'
            }
        })
    });

    self.isValidCredentials = ko.pureComputed(function () {
        return self.credentials().accessKey.isValid();
    });

    self.isValidCredentials.subscribe(function (value) {
        if (value) self.loadInfo();
    });


    // Image details
    var maxLength = 60;
    self.image = ko.validatedObservable({
        sourceImage: ko.observable().extend({required: true}),
        zone: ko.observable().extend({required: true}),
        platformId: ko.observable(),
        subnet: ko.observable().extend({required: true}),
        ipv6: ko.observable(false),
        nat: ko.observable(false),
        securityGroups: ko.observable(),
        hostname: ko.observable(),
        customProps: ko.observable(),
        maxInstances: ko.observable(1).extend({required: true, min: 0}),
        preemptible: ko.observable(false),
        machineCores: ko.observable().extend({
            validation: {
                validator: function (value) {
                    var number = parseInt(value);
                    return number > 1 && (number % 2 === 0);
                },
                message: "even number of vCPUs can be created"
            }
        }),
        machineMemory: ko.observable().extend({
            validation: {
                validator: function (value) {
                    return value > 0;
                },
                message: "Memory must be a positive value"
            }
        }),
        diskType: ko.observable(),
        diskSize: ko.observable(),
        secondaryDiskType: ko.observable(),
        secondaryDiskSize: ko.observable(),
        secondaryDiskMountPath: ko.observable(),
        vmNamePrefix: ko.observable('').trimmed().extend({required: true, maxLength: maxLength}).extend({
            validation: {
                validator: function (value) {
                    return self.originalImage && self.originalImage['source-id'] === value ||
                        !ko.utils.arrayFirst(self.images(), function (image) {
                            return image['source-id'] === value;
                        });
                },
                message: 'Name prefix should be unique within subscription'
            }
        }).extend({
            pattern: {
                message: 'Name can contain alphanumeric characters, underscore and hyphen',
                params: /^[a-z][a-z0-9_-]*$/i
            }
        }),
        metadata: ko.observable().extend({
            validation: {
                validator: function (value) {
                    if (!value) return true;

                    var root;
                    try {
                        root = JSON.parse(value);
                    } catch (error) {
                        console.log("Unable to parse metadata: " + error);
                        return false;
                    }

                    for (var key in root) {
                        if (root.hasOwnProperty(key)) {
                            if ("object" === typeof(key)) {
                                console.log("Invalid key: " + key);
                                return false;
                            }

                            if ("object" === typeof(root[key])) {
                                console.log("Invalid value for key: " + key);
                                return false;
                            }

                            if (key === "user-data") {
                                console.log("'user-data' metadata key is not allowed. Use cloud-config option instead.");
                                return false;
                            }
                        }
                    }

                    return true;
                },
                message: "Invalid metadata value"
            }
        }),
        cloudConfig: ko.observable(),
        dnsRecords: ko.observable(),
        growingId: ko.observable(false),
        serviceAccount: ko.observable(),
        instanceFolder: ko.observable(),
        agentPoolId: ko.observable().extend({required: true}),
        profileId: ko.observable()
    });

    // Data from APIs
    self.sourceImages = ko.observableArray([]);
    self.zones = ko.observableArray([]);
    self.subnets = ko.observableArray([]);
    self.diskTypes = ko.observableArray([]);
    self.agentPools = ko.observableArray([]);

    // Hidden fields for serialized values
    self.images_data = ko.observable();

    // Deserialized values
    self.images = ko.observableArray();

    // Reload info on credentials change
    self.credentials().accessKey.isValidating.subscribe(function (isValidating) {
        if (isValidating || !self.credentials().accessKey.isValid()) {
            return;
        }

        self.showAccessKey(false);
    });

    self.images_data.subscribe(function (data) {
        var images = ko.utils.parseJson(data || "[]");
        images.forEach(function (image) {
            image.preemptible = getBoolean(image.preemptible);
            image.ipv6 = getBoolean(image.ipv6);
            image.nat = getBoolean(image.nat);
            image.growingId = getBoolean(image.growingId);
        });
        self.images(images);
    });

    // Dialogs
    self.originalImage = null;

    self.showDialog = function (data) {
        self.originalImage = data;

        var model = self.image();
        var image = data || {
            maxInstances: 1,
            preemptible: false,
            growingId: false,
            ipv6: false,
            nat: true
        };

        var sourceImage = image.sourceImage;
        if (sourceImage && !ko.utils.arrayFirst(self.sourceImages(), function (item) {
            return item.id === sourceImage;
        })) {
            self.sourceImages({id: sourceImage, text: sourceImage});
        }

        var diskType = image.diskType;
        if (diskType && !ko.utils.arrayFirst(self.diskTypes(), function (item) {
            return item.id === diskType;
        })) {
            self.diskTypes({id: diskType, text: diskType});
        }

        var secondaryDiskType = image.secondaryDiskType;
        if (secondaryDiskType && !ko.utils.arrayFirst(self.diskTypes(), function (item) {
            return item.id === secondaryDiskType;
        })) {
            self.diskTypes({id: secondaryDiskType, text: secondaryDiskType});
        }

        model.sourceImage(image.sourceImage);
        model.zone(image.zone);
        model.platformId(image.platformId);
        model.subnet(image.subnet);
        model.ipv6(image.ipv6);
        model.nat(image.nat);
        model.hostname(image.hostname);
        model.securityGroups(image.securityGroups);
        model.customProps(image.customProps);
        model.machineCores(image.machineCores);
        model.machineMemory(image.machineMemory);
        model.diskType(diskType);
        model.diskSize(image.diskSize);
        model.secondaryDiskType(secondaryDiskType);
        model.secondaryDiskSize(image.secondaryDiskSize);
        model.secondaryDiskMountPath(image.secondaryDiskMountPath);
        model.maxInstances(image.maxInstances);
        model.preemptible(image.preemptible);
        model.vmNamePrefix(image['source-id']);
        model.metadata(image.metadata);
        model.dnsRecords(image.dnsRecords);
        model.growingId(image.growingId);
        model.serviceAccount(image.serviceAccount);
        model.instanceFolder(image.instanceFolder);
        model.agentPoolId(image.agent_pool_id);
        model.profileId(image.profileId);

        self.showMetadata(false);
        self.showCloudConfig(false);
        self.showDNSRecords(false);
        self.showServiceAccount(!!image.serviceAccount);
        self.showInstanceFolder(!!image.instanceFolder);

        self.image.errors.showAllMessages(false);
        dialog.showDialog(!self.originalImage);

        return false;
    };

    self.closeDialog = function () {
        dialog.close();
        return false;
    };

    self.saveImage = function () {
        var model = self.image();
        var image = {
            sourceImage: model.sourceImage(),
            zone: model.zone(),
            platformId: model.platformId(),
            subnet: model.subnet(),
            ipv6: model.ipv6(),
            nat: model.nat(),
            securityGroups: model.securityGroups(),
            hostname: model.hostname(),
            customProps: model.customProps(),
            maxInstances: model.maxInstances(),
            preemptible: model.preemptible(),
            'source-id': model.vmNamePrefix(),
            machineCores: model.machineCores(),
            machineMemory: model.machineMemory(),
            diskType: model.diskType(),
            diskSize: model.diskSize(),
            secondaryDiskType: model.secondaryDiskType(),
            secondaryDiskSize: model.secondaryDiskSize(),
            secondaryDiskMountPath: model.secondaryDiskMountPath(),
            metadata: model.metadata(),
            dnsRecords: model.dnsRecords(),
            growingId: model.growingId(),
            serviceAccount: model.serviceAccount(),
            instanceFolder: model.instanceFolder(),
            agent_pool_id: model.agentPoolId(),
            profileId: model.profileId()
        };

        var originalImage = self.originalImage;
        if (originalImage) {
            self.images.replace(originalImage, image);
        } else {
            self.images.push(image);
        }
        saveImages();

        dialog.close();
        return false;
    };

    self.deleteImage = function (image) {
        var message = "Do you really want to delete agent image based on " + image.image + "?";
        var remove = confirm(message);
        if (!remove) {
            return false;
        }

        self.images.remove(image);
        saveImages();

        return false;
    };

    self.loadInfo = function () {
        if (!self.isValidCredentials()) {
            return
        }

        self.loadingResources(true);

        var url = getBasePath() +
            "resource=permissions" +
            "&resource=zones" +
            "&resource=images" +
            "&resource=subnets" +
            "&resource=diskTypes";

        $.post(url, getCredentials()).then(function (response) {
            var $response = $(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

            self.sourceImages(getSourceImages($response));
            self.zones(getZones($response));
            self.diskTypes(getDiskTypes($response));
            getSubnets($response);
        }, function (error) {
            self.errorResources("Failed to load data: " + error.message);
            console.log(error);
        }).always(function () {
            self.loadingResources(false);
        });
    };

    self.loadAccessKey = function (file) {
        if (!self.hasFileReader()) return;
        var reader = new FileReader();
        reader.onload = function (e) {
            self.credentials().accessKey(e.target.result);
        };
        reader.readAsText(file);
    };

    self.dragEnterHandler = function () {
        self.isDragOver(true);
    };

    self.dragLeaveHandler = function () {
        self.isDragOver(false);
    };

    self.dropHandler = function (e) {
        self.isDragOver(false);
        var dt = e.dataTransfer || e.originalEvent.dataTransfer;
        if (dt.items) {
            for (var i = 0; i < dt.items.length; i++) {
                if (dt.items[i].kind === "file") {
                    var file = dt.items[i].getAsFile();
                    self.loadAccessKey(file);
                    return
                }
            }
        } else {
            for (var i = 0; i < dt.files.length; i++) {
                self.loadAccessKey(dt.files[i]);
                return
            }
        }
    };

    function saveImages() {
        var images = self.images();
        self.images_data(JSON.stringify(images));
    }

    function getBasePath() {
        return config.baseUrl + "?";
    }

    function getCredentials() {
        return {"prop:secure:accessKey": self.credentials().accessKey()};
    }

    function getErrors($response) {
        var $errors = $response.find("errors:eq(0) error");
        if ($errors.length) {
            return $.map($errors, function (error) {
                return $(error).text();
            }).join(", ");
        }

        return "";
    }

    function getZones($response) {
        return $response.find("zones:eq(0) zone").map(function () {
            return {id: $(this).attr("id"), region: $(this).attr("region"), text: $(this).text()};
        }).get();
    }

    function getSubnets($response) {
        return $response.find("subnets:eq(0) subnet").map(function () {
            return {id: $(this).attr("id"), text: $(this).text(), network: $(this).attr("network")};
        }).get();
    }

    function getDiskTypes($response) {
        return $response.find("diskTypes:eq(0) diskType").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getSourceImages($response) {
        return $response.find("images:eq(0) image").map(function () {
            return {id: $(this).attr("id"), text: $(this).text()};
        }).get();
    }

    function getBoolean(variable) {
        if (typeof variable === 'string' || variable instanceof String) {
            return ko.utils.parseJson(variable);
        }

        return variable
    }

    (function loadAgentPools() {
        var url = config.baseUrl + "?resource=agentPools&projectId=" + encodeURIComponent(config.projectId);
        return $.post(url).then(function (response) {
            var $response = $(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

            var agentPools = $response.find("agentPools:eq(0) agentPool").map(function () {
                return {
                    id: $(this).attr("id"),
                    text: $(this).text()
                };
            }).get();

            self.agentPools(agentPools);
            self.image().agentPoolId.valueHasMutated();
        }, function (error) {
            self.errorResources("Failed to load data: " + error.message);
            console.log(error);
        });
    })();

    self.afterRender = function () {
        if (!self.credentials().accessKey()) {
            self.showAccessKey(true);
        }
    };
}

function FormatDiskSummary (image) {
    let summary = ''
    if (image.diskSize > 0) {
        summary += image.diskSize
    } else {
        summary += 'IMG'
    }
    if (image.secondaryDiskSize > 0) {
        summary += ` / ${image.secondaryDiskSize}`
    }
    return summary
}
