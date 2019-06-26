# TeamCity Yandex Cloud Agents 

TeamCity integration with Yandex Compute Engine which allows using cloud instances to scale the pool of build agents.

## Compatibility

The plugin is compatible with TeamCity 10.0.x and greater.

## Installation

You can [download the plugin](https://plugins.jetbrains.com/plugin/12619-yandex-cloud-agents) and install it as an [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

## Configuration

The plugin supports Yandex Compute images to start new instances. 
You need to create a service account, assign the `Editor` or `Admin` [roles](https://cloud.yandex.ru/docs/iam/operations/sa/assign-role-for-sa)
and [create JSON private key](https://cloud.yandex.ru/docs/iam/operations/iam-token/create-for-sa#keys-create) 
for this service account 

### Image Creation

Before you can start using the integration, you need to create a new cloud image. 
To do that, create a new cloud instance, install the 
[TeamCity Build Agent](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent)
on it and set it to start [automatically](https://confluence.jetbrains.com/display/TCDL/Setting+up+and+Running+Additional+Build+Agents#SettingupandRunningAdditionalBuildAgents-AutomaticStart). 
You also need to manually point the agent to the existing TeamCity server with the Yandex Cloud plugin installed to let the build agent download the plugins.

Then install required build tools and [remove temporary files](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine).

Then you need to create a new image from the instance disk.
You can do this by two ways: 
1. [http-request](https://cloud.yandex.ru/docs/compute/api-ref/Image/create) 
2. [CLI](https://cloud.yandex.ru/docs/cli/)
```
yc compute disk list
yc compute image create --name teamcity-agent --source-disk-id=DISK_ID
```

Now you need to delete instance.

### How to use
To create a Yandex agent cloud profile, navigate to the project where you want to set up the profile and select 
the Cloud Profiles link. Then click the “Create new profile” button and select “Yandex Compute” as the cloud type.
Specify the profile name and the JSON private key value in the corresponding field.

To add a new image to the profile, press the “Add image” button, select the recently created cloud image, 
and fill in other properties. Next you need to save the image and then the profile settings. 

#### Preemtible instance

If you are using preemptible instances you have to specify shutdown script to gracefully reschedule build from preempted VM on another build agent like that.

For Linux instances:
```bash
#!/bin/bash
/opt/buildagent/bin/agent.sh stop force
```
save your script in /etc/rc6.d

Notes:
* Make it executable: sudo chmod +x K99_script
* The script in rc6.d must be with no .sh extension
* The name of your script must begin with K99 to run at the right time.
* The scripts in this directory are executed in alphabetical order.

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository issues.
