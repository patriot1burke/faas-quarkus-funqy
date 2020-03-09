### Knative Eventing Function with Quarkus Funqy Framework
This example shows how Knative eventing can be used with the Quarkus Funqy framework

### Building Quarkus Funqy Framework

First we need to build Quarkus Funqy Framework as its not released yet.  You will need maven and JDK 8 installed.

```console
$ git clone git@github.com:patriot1burke/quarkus.git
$ git checkout funqy
$ mvn -DskipTests -Dmaven.test.skip=true clean install
```

### Installing Knative with minikube:
```console
$ curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 \
  && chmod +x minikube
$ ./minikube start -p example --memory=8192 --cpus=6 --kubernetes-version=v1.15.0 --vm-driver=kvm2 --disk-size=30g --extra-config=apiserver.enable-admission-plugins="LimitRanger,NamespaceExists,NamespaceLifecycle,ResourceQuota,ServiceAccount,DefaultStorageClass,MutatingAdmissionWebhook"
```
Notice that we are using a profile which is specified with the `-p` option. We
can later stop and start this profile by using `./minikube start -p example`.

We need to use the same version of `kubectl` that matches `kubernetes` which in
our case is `1.15.0`:
```console
$ curl -LO https://storage.googleapis.com/kubernetes-release/release/v1.15.0/bin/linux/amd64/kubectl
$ chmod +x ./kubectl
$ sudo mv ./kubectl /usr/local/bin/kubectl
```

Next, we need to install istio:
```console
$ export ISTIO_VERSION=1.3.6
$ curl -L https://git.io/getLatestIstio | sh -
$ cd istio-${ISTIO_VERSION}
$ for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
$ cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Namespace
metadata:
  name: istio-system
  labels:
    istio-injection: disabled
EOF
namespace/istio-system created
```

Install [helm](https://helm.sh/docs/intro/install/) which is like a package manager for kubernetes:
```console
$ curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
$ chmod 700 get_helm.sh
$ ./get_helm.sh
```
Use help to create a the istio resources configurations:
```console
$ helm template --namespace=istio-system \
  --set prometheus.enabled=false \
  --set mixer.enabled=false \
  --set mixer.policy.enabled=false \
  --set mixer.telemetry.enabled=false \
  `# Pilot doesn't need a sidecar.` \
  --set pilot.sidecar=false \
  --set pilot.resources.requests.memory=128Mi \
  `# Disable galley (and things requiring galley).` \
  --set galley.enabled=false \
  --set global.useMCP=false \
  `# Disable security / policy.` \
  --set security.enabled=false \
  --set global.disablePolicyChecks=true \
  `# Disable sidecar injection.` \
  --set sidecarInjectorWebhook.enabled=false \
  --set global.proxy.autoInject=disabled \
  --set global.omitSidecarInjectorConfigMap=true \
  --set gateways.istio-ingressgateway.autoscaleMin=1 \
  --set gateways.istio-ingressgateway.autoscaleMax=2 \
  `# Set pilot trace sampling to 100%` \
  --set pilot.traceSampling=100 \
  --set global.mtls.auto=false \
  install/kubernetes/helm/istio \
  > ./istio-lean.yaml
```
And now apply these resources to kubernetes:
```console
$ kubectl apply -f istio-lean.yaml
```
Verify that istio is installed:
```console
$ kubectl get pods --namespace istio-system -w
NAME                                   READY   STATUS    RESTARTS   AGE
istio-ingressgateway-5d9bc67ff-cgfcp   0/1     Running   0          29s
istio-pilot-54c8644bc5-8jh47           0/1     Running   0          29s
istio-pilot-54c8644bc5-8jh47           1/1     Running   0          61s
```

Next, we install Knative itself:
```console
$ kubectl apply --selector knative.dev/crd-install=true --filename https://github.com/knative/serving/releases/download/v0.12.0/serving.yaml --filename https://github.com/knative/eventing/releases/download/v0.12.0/eventing.yaml --filename https://github.com/knative/serving/releases/download/v0.12.0/monitoring.yaml

$ kubectl apply --filename https://github.com/knative/serving/releases/download/v0.12.0/serving.yaml --filename https://github.com/knative/eventing/releases/download/v0.12.0/eventing.yaml --filename https://github.com/knative/serving/releases/download/v0.12.0/monitoring.yaml
```

Verify that Knative has been installed correctly:
```console
$ kubectl get pods --namespace knative-serving -w
NAME                               READY   STATUS    RESTARTS   AGE
activator-6b49796b46-lww55         1/1     Running   0          12m
autoscaler-7b46fcb475-lclgc        1/1     Running   0          12m
autoscaler-hpa-797c8c8647-zmrkc    1/1     Running   0          12m
controller-65f4f4bcb4-8gq7r        1/1     Running   0          12m
networking-istio-87d7c6686-tzvsk   1/1     Running   0          12m
webhook-59585cb6-vrmx8             1/1     Running   0          12m
```

### Build and deploy the example

We need to build and push the container image of our Quarkus Funqy service.  You need
to be in the directory that this README.md file is in.  `username` is your docker username.
```console
$ mvn clean install
$ docker build -f src/main/docker/Dockerfile.jvm -t {username}/faas-quarkus-funqy .
```
After this we have to push the image to our user account on docker hub:
```console
$ docker login -u {username} -p {password} docker.io
$ docker push {username}/faas-quarkus-funqy
```

Now, we deploy a namespace for our demo and with knative-eventing-injection
enabled:
```console
$ kubectl apply -f src/main/k8s/namespace.yaml
$ kubectl get ns quarkus-funqy-service --show-labels
NAME                 STATUS   AGE     LABELS
quarkus-funqy-service   Active   4d19h   knative-eventing-injection=enabled
```
Set the current context to our example namespace:
```console
$ kubectl config set-context --current --namespace=quarkus-funqy-service
```

Create a secret that we can use to pull images our user on docker.io:
```console
$ kubectl --namespace quarkus-funqy-service create secret docker-registry registry-secret --docker-server=https://index.docker.io/v1/ --docker-username={username} --docker-password={password} --docker-email={email}
```

Next, we have to edit our deployment file `src/main/k8s/deployment.yaml`.  Change `patriot1burke` to your docker
username.

Next, we create a deployment for our application:
```console
$ kubectl apply -f src/main/k8s/deployment.yaml
$ kubectl get deployments quarkus-funqy-service
NAME                 READY   UP-TO-DATE   AVAILABLE   AGE
quarkus-funqy-service   1/1     1            1           3d22h
```

Next we will create a service for our application, the deployment above:
```console
$ kubectl apply -f srsc/main/k8s/service.yaml
$ kubectl get svc quarkus-funqy-service
NAME                 TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)   AGE
quarkus-funqy-service   ClusterIP   10.103.182.147   <none>        80/TCP    3d22h
```

Next we create the trigger which is used by the Broker to filter events and
send them to our service:
```console
$ kubectl apply -f src/main/k8s/trigger.yaml
$ kubectl get trigger quarkus-funqy-service
NAME                    READY   REASON   BROKER    SUBSCRIBER_URI                                                    AGE
quarkus-funqy-service   True             default   http://quarkus-funqy-service.quarkus-funqy-service.svc.cluster.local/   58m
```
We can find the url of the `Broker` which we can use to POST events to:
```console
$ kubectl get broker
NAME      READY   REASON   URL                                                             AGE
default   True             http://default-broker.quarkus-funqy-service.svc.cluster.local   62m
```
Next, we are going to POST a event using curl:
```console
$ kubectl run curl --image=radial/busyboxplus:curl -it
curl -v "default-broker.quarkus-funqy-service.svc.cluster.local" -X POST -H "Ce-Id: 536808d3-88be-4077-9d7a-a3f162705f79" -H "Ce-specversion: 0.3" -H "Ce-Type: dev.nodeshift.samples.quarkus-funqy" -H "Ce-Source: dev.nodeshift.samples/quarkus-funqy-source" -H "Content-Type: application/json" -d '{"name": "Bill"}'
```

Now we can check the logs of our pod to see that it has received the event:
```console
$ kubectl get pod -l='app=quarkus-funqy-service'
NAME                                    READY   STATUS    RESTARTS   AGE
quarkus-funqy-service-554584999-tzsnx   1/1     Running   0          33m
$ kubectl logs quarkus-funqy-service-554584999-tzsnx
exec java -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:+ExitOnOutOfMemoryError -cp . -jar /deployments/app.jar
2020-03-09 20:37:31,823 INFO  [io.quarkus] (main) funq-demo 1.0-SNAPSHOT (running on Quarkus 999-SNAPSHOT) started in 2.546s. Listening on: http://0.0.0.0:8080
2020-03-09 20:37:31,825 INFO  [io.quarkus] (main) Profile prod activated.
2020-03-09 20:37:31,825 INFO  [io.quarkus] (main) Installed features: [cdi, funq]
2020-03-09 20:45:45,116 INFO  [fun.greeting] (executor-thread-1) *** In greeting service ***
2020-03-09 20:45:45,120 INFO  [fun.greeting] (executor-thread-1) Sending back: Hello Bill!
```
