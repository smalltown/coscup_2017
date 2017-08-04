# COSCUP 2017 - Infrastructure As Code
This repository is for demonstrate below items:

- Jenkins: Pipeline As Code
- <del>Terraform: Infrastructure As Code</del> (Wait For DevOpsDays Taipei 2017)
- CM: Provision As Code
- Packer: Image As Code
- Docker: Server As Code
- Kubernetes: Orchestration As Code
- Hubot: Chat As Code


# There are three modules need to be setup...

## Prerequisite

- Linux Based Machine
- Slack Account
- Docker
- DockerHub Account


## Kubernetes (power by minikube)
- Follow [**official document**](https://github.com/kubernetes/minikube#installation) to install **minikube**

- Follow [**official document**](https://kubernetes.io/docs/tasks/tools/install-kubectl/#install-kubectl-binary-via-curl) to install **kubectl**

- Within this repository to start minikube with below command

	```
	# Specific VM driver by --vm-driver flag if needed, VM driver is one of: [virtualbox xhyve vmwarefusion] (default "virtualbox")
	
	~$ minikube start --extra-config=apiserver.BasicAuthFile=static-password.k8s.csv
	
	...
	Starting local Kubernetes v1.6.4 cluster...
	Starting VM...
	Moving files into cluster...
	Setting up certs...
	Starting cluster components...
	Connecting to cluster...
	Setting up kubeconfig...
	Kubectl is now configured to use the cluster.
	```

- There should be a file named **kubeconfig** is generated, kubectl use this file to communicate with minikube

## Jenkins (power by container)
- Within this repository, get minikube master endpoint by kubectl

	```
	~$ kubectl cluster-info
	
	Kubernetes master is running at https://192.168.64.2:8443
	```

- Export minikube master endpoint

	```
	export KUBERNETES_ENDPOINT="https://192.168.64.2:8443"
	```

- Export dockerhub username and password

	```
	export DOCKERHUB_USERNAME="XXXXXX"
	export DOCKERHUB_PASSWORD="XXXXXX"
	```

- Execute below command to launch Jenkins

	```
	~$ ./jenkins.container
	
	Pull the image: jenkins/jenkins:lts
	lts: Pulling from jenkins/jenkins
	Digest: sha256:00d924e51f89ee01cf4b11ebedb92148976c3ac03f5cdc717ccf49852e1d7893
	Status: Image is up to date for jenkins/jenkins:lts
	
	Starting Docker container: jenkins/jenkins:lts
	Container ID: jenkins-1501424671
	1638d5ffc3c25f0a62e737e5b1c8090a2028c2d3dd9cd613a24873022dc93327
	```

- Visit http://127.0.0.1:8080 from the browser after several minutes

- Login Jenkins by below account and password<br/>
	- Admin Account  : coscup<br/>
	- Admin Password : coscup


## Hubot (power by container)
- Within this repository, export Slack legacy token

	```
	~$ export 
	```

- Execute below command to launch hubbot container

	```
	~$ ./hubot.container
	```