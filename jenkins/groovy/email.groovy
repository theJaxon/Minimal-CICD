#!groovy

import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

def jenkinsParameters = [
  email:  'jaxon@jenkins.com>',
  url:    'http://172.42.42.100:8080/'
]

// get Jenkins location configuration
def jenkinsLocationConfiguration = JenkinsLocationConfiguration.get()

// set Jenkins URL
jenkinsLocationConfiguration.setUrl(jenkinsParameters.url)

// set Jenkins admin email address
jenkinsLocationConfiguration.setAdminAddress(jenkinsParameters.email)

// save current Jenkins state to disk
jenkinsLocationConfiguration.save()