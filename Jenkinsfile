pipeline {
    agent any
    stages {
        stage('Audit tools') {                        
            steps {
                sh '''
                  git version
                  buildah --version
                  podman -v
                  kubectl version --short
                '''
            }
        }

        stage('Build the image'){
            steps {
                sh '''
                  docker build  -t sample-task:latest -t sample-task:${BUILD_NUMBER} .
                  docker image ls
                '''
            }
        }

        stage('Push the image to Nexus3'){
            steps {
                sh '''
                docker tag sample-task:${BUILD_NUMBER} 172.42.42.100:8123/sample-task:${BUILD_NUMBER}
                docker tag sample-task:${BUILD_NUMBER} 172.42.42.100:8123/sample-task:latest
		        docker login -u admin -p admin 172.42.42.100:8123 
                docker push 172.42.42.100:8123/sample-task:latest
                '''
            }
        }

        stage('Update k8s deployment image'){
            steps {
                sh '''
                kubectl delete po $(kubectl get po -l app=sample-task -o jsonpath='{.items[0].metadata.name}')
                '''
            }
        }

    }
}
