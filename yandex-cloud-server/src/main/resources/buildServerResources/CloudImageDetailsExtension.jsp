<html>
<script language="JavaScript">
    // Remove empty image details row which this html is inserted in.
    for (let instance of document.getElementsByClassName("image_details")) {
        instance.remove()
    }
    // Append compute link to each instance element.
    let agents = ${agents};
    let instances = document.getElementById("cloudRefreshableInner").getElementsByClassName("instance")
    for (let instance of instances) {
        for (const agent of agents) {
            if (!agent.folder || !agent.instanceID) {
                continue
            }
            if (instance.innerHTML.includes(agent.name)) {
                let link = "https://console.cloud.yandex.ru/folders/"+ agent.folder + "/compute/instance/" + agent.instanceID;
                instance.firstElementChild.insertAdjacentHTML('afterbegin', `
                    <a style="vertical-align:middle; text-align:center;"
                       href="`+ link +`"
                       title="Compute instance link">
                        <img style="width:16px; height:auto;" src="${imageURL}">
                    </a>`);
            }
        }
    }
</script>
</html>