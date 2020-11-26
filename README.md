# Minimal-CICD

![Alpine](https://img.shields.io/badge/-Alpine-0D597F?style=for-the-badge&logo=alpine%20linux&logoColor=white)
![Gitea](https://img.shields.io/badge/-gitea-609926?style=for-the-badge&logo=gitea&logoColor=white)
![Jenkins](https://img.shields.io/badge/-JENKINS-D24939?style=for-the-badge&logo=JENKINS&logoColor=white)
![Docker](https://img.shields.io/badge/-docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/-kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)


Create CICD pipeline using Jenkins, Nexus, Gitea deployments on Kubernetes.

![CICD](https://github.com/theJaxon/Minimal-CICD/blob/master/etc/CICD/Overview-transparent-bg.png)

### :zap: [Gitea](https://hub.docker.com/r/gitea/gitea) self-hosted git service:
- Gitea docker uses `sqlite3` as a DB by default.
- A configMap is created to hold the environment variables used in the gitea pod.

<details>
<summary>gitea.env content</summary>
<p>

```
DOMAIN=172.42.42.100
SSH_DOMAIN=172.42.42.100
DB_NAME=admin
DB_USER=admin
DISABLE_GIT_HOOKS=false
```

</p>
</details>


```bash
# Create a ConfigMap to hold the env vars 
k create cm gitea --from-env-file=gitea.env -o yaml --dry-run=client > gitea-cm.yml
k apply -f gitea-cm.yml

# Create a secret to hold db password 
k create secret generic gitea --from-literal=DB_PASSWD=admin -o yaml --dry-run=client > gitea-secret.yml
k apply -f gitea-secret.yml

# Generate gitea deployment spec
k create deploy gitea --image=gitea/gitea:1.12.6 -o yaml --dry-run=client > gitea.yml
k apply -f gitea.yml --record

# Use the generated cm and secret 
k set env deploy/gitea --from=cm/gitea
k annotate deploy gitea kubernetes.io/change-cause="Use gitea ConfigMap"

k set env deploy/gitea --from=secret/gitea
k annotate deploy gitea kubernetes.io/change-cause="Use gitea secret"

# Display revisions 
k rollout history deploy/gitea

# Create a service to access gitea 
k create svc nodeport gitea  --tcp=222:22 --tcp=3000:3000 -o yaml --dry-run=client > gitea-svc.yml
k apply -f gitea-svc.yml

k port-forward deploy/gitea 3000:3000 --address 0.0.0.0 &
```

![annotate](https://github.com/theJaxon/Minimal-CICD/blob/master/etc/gitea/gitea-rollout.jpg)

#### :black_circle: Pushing the sample task to the repo:
```
git init
git add .
git commit -m "add Dockerfile"
git remote add origin http://172.42.42.100:3000/jaxon/sample-task.git
git push -u origin master
```

---

### :zap: Jenkins CI - Build server:
- A custom built image is used where all the needed tools are installed.
- `/edge/testing` and `/edge/community` repos are enabled to allow the installation for `docker-cli`, `Podman` and `buildah`.
- Jenkins image was built using the following commmand:

```
docker build -t jenkins:ci .
```

- jenkins war file is located at [`/usr/share/webapps/jenkins/jenkins.war`](https://pkgs.alpinelinux.org/contents?branch=edge&name=jenkins&arch=x86&repo=community)
- The ENTRYPOINT in the docker file uses this war file with an additional **-Djenkins.install.runSetupWizard=false** to skip initial setup screen.
- `JENKINS_HOME` ENV variable is set to `/data`, inside it exists `init.groovy.d` **[init hook](https://www.jenkins.io/doc/book/managing/groovy-hook-scripts/#post-initialization-script-init-hook)**, any groovy script found here gets executed by jenkins after it starts up.
- Jenkins runs the `Jenkinsfile` which does the following steps:

  1. Audit the tools: Just simply printing the versions of the current tools available in this container.
  2. Builds the image using the given Dockerfile found on gitea
  3. Pushes the image after building into sonatype Nexus
  4. Deletes the sample-task pod so that a new one gets created with the latest image version.

#### :black_circle: Using docker from inside the pod:
- `/var/run/docker.sock` from the host is mounted into the container using `hostPath` volume type to allow access to docker daemon running on the host.

#### :black_circle: Using kubectl from inside the pod:
- a `ServiceAccount` object is created and the pod is configured to use this SA.

```bash
# Generate serviceAccount yaml file
k create sa jaxon -o yaml --dry-run=client > serviceAccount.yml
k apply -f serviceAccount.yml

# Update the serviceAccount file to include the label
k label sa jaxon app=jenkins  -o yaml --dry-run=client > serviceAccount.yml
k apply -f serviceAccount.yml

# Create a cluster role 
k create clusterrole jenkins --resource=po,po/exec,deploy,svc,cm,secrets --verb=create,delete,get,list,update,watch -o yaml --dry-run=client > clusterRole.yml
k apply -f clusterRole.yml

k label clusterrole jenkins app=jenkins -o yaml --dry-run=client > clusterRole.yml
k apply -f clusterRole.yml

# Create a cluster role binding 
k create clusterrolebinding jenkins --clusterrole=jenkins  --serviceaccount=default:jaxon -o yaml --dry-run=client > clusterRoleBinding.yml
k apply -f clusterRoleBinding.yml

k label clusterrolebinding jenkins app=jenkins -o yaml --dry-run=client > clusterRoleBinding.yml
k apply -f clusterRoleBinding.yml

# Confirm that the permissions were granted to the service account 
# https://stackoverflow.com/a/54889459 
k auth can-i get po --as=system:serviceaccount:default:jaxon # returns yes so we're good to go

# Set serviceAccount to jenkins deployment 
k set serviceaccount deploy/jenkins jaxon
```
#### :black_circle: List of tools installed on top of openjdk:8-jre-alpine image:

| Tools List 	|
|:-:	|
| buildah 	|
| docker-cli 	|
| git 	|
| jenkins 	|
| kubectl 	|
| podman 	|


#### :black_circle: Using podman from the container:
```bash
# Add dockerhub as a registry to be able to pull the images 
vi /etc/containers/registries.conf

[registries.search]
registries = ['docker.io']
```

#### :black_circle: Adding the repo to Jenkins pipeline project:
Since gitea is running in a different pod, jenkins pod is able to resolve the service name using kubernetes DNS so all we need is to add the address as follow:
```bash
http://gitea:3000/jaxon/sample-task.git
```

![Jenkins](https://github.com/theJaxon/Minimal-CICD/blob/master/etc/Pipeline-repo.jpg)

---

### :zap: Sonatype Nexus - Private container registry:

```bash
# Create Nexus deployment 
k create deployment nexus --image=sonatype/nexus3 -o yaml --dry-run=client > nexus.yml
k apply -f nexus.yml

# Create Nexus SVC 
k expose deploy/nexus --type=NodePort --port=8081 --target-port=8081 -o yaml --dry-run=client > nexus-svc.yml
k apply -f nexus-svc.yml

# Port forward to access from outside vagrant
k port-forward deploy/nexus 8081:8081 --address 0.0.0.0 &
k port-forward deploy/nexus 8123:8123 --address 0.0.0.0 &
```
#### :black_circle: Add [docker repository in Nexus](https://www.ivankrizsan.se/2016/06/09/create-a-private-docker-registry/):
From `http://172.42.42.100:8081/#admin/repository/repositories` click on **Create repository** and select **docker (hosted)** as the option

![nexus1](https://github.com/theJaxon/Minimal-CICD/blob/master/etc/Nexus/Nexus3-repo.jpg)

#### :black_circle: Setup docker to work with Nexus over HTTP:
Add nexus as an insecure registry in `/etc/docker/daeomn.json`

```json
{
  "insecure-registries" : ["http://172.42.42.100:8123"]
}
```

#### :black_circle: Enable Docker bearer token realm:

From http://172.42.42.100:8081/#admin/security/realms activate **Docker bearer token realm**

#### [Pushing the image into Nexus](https://help.sonatype.com/repomanager3/formats/docker-registry/pushing-images):
1. Tag the image 
```bash
# Tag the image 
docker tag jenkins:ci 172.42.42.100:8123/jenkins:ci

# Push the tagged image 
docker push 172.42.42.100:8123/jenkins:ci
```

Final result:
![final-result](https://github.com/theJaxon/Minimal-CICD/blob/master/etc/Nexus/Nexus-image.jpg)
---

### Deploying the sample app using k8s:
1. Create a [secret](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-by-providing-credentials-on-the-command-line) containing credentials for Neuxs private registry.

```bash
# Not secure but that's not a production env 
k create secret docker-registry regcred --docker-server=172.42.42.100:8123 --docker-username=admin --docker-password=admin
```

2. Refer to this secret using **imagePullSecrets** to gain access to Nexus private registry and fetch the image.

```bash
k create deploy sample-task --image=172.42.42.100:8123/sample-task -o yaml --dry-run=client > sample-task.yml
```
