#!groovy

import jenkins.model.Jenkins;

plugin_manager = Jenkins.instance.pluginManager
update_center = Jenkins.instance.updateCenter

plugin_manager.doCheckUpdatesServer()

["git", "blueocean", "workflow-aggregator"].each {
    if (! plugin_manager.getPlugin(it)) {
    deployment = update_center.getPlugin(it).deploy(true)
    deployment.get()
    }
}