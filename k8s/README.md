# OrderFlow on Kubernetes

Plain manifests, applied with Kustomize (built into `kubectl`). Tested target: Docker Desktop's built-in cluster.

```
k8s/
├── namespace.yaml          orderflow namespace
├── config.yaml             ConfigMap (shared settings) + Secret (DB password, JWT secret — dev values)
├── infra/                  mysql (StatefulSet + PVC), kafka (KRaft StatefulSet + PVC), mailpit
├── apps/                   one file per service: Service + Deployment (+ HPA for gateway/order/notification)
├── ingress.yaml            optional, needs ingress-nginx
└── kustomization.yaml
```

## Run it

```powershell
# 1. Build the images (same ones Docker Compose uses: orderflow-<service>:latest)
docker compose build

# 2. Stop Compose so ports 8080/3306/9092 are free
docker compose down

# 3. Deploy
kubectl apply -k k8s
kubectl get pods -n orderflow -w        # Ctrl+C once everything is 1/1 Running (2-3 min)

# 4. Use it — the gateway Service is type LoadBalancer, which Docker Desktop publishes on localhost
curl http://localhost:8080/actuator/health
start http://localhost:8080/swagger-ui.html
```

Mailpit and the other internal services are **not** exposed. To peek at one:
`kubectl port-forward -n orderflow svc/mailpit 8025:8025` then open http://localhost:8025.

Tear down: `kubectl delete -k k8s` (add `kubectl delete pvc -n orderflow --all` to wipe data).

## What's worth explaining in an interview

- **Only the gateway is reachable from outside.** Every other Service is `ClusterIP`, so the `X-User-Id` headers the gateway sets can't be forged by calling a service directly. This closes the gap that Docker Compose leaves open.
- **Three probes per service.** The startup probe gives the JVM up to 3 minutes to boot. The liveness probe restarts a wedged JVM. The readiness probe keeps a pod out of the load balancer until it's ready, so rolling updates (`maxUnavailable: 0`) never drop requests.
- **Init containers** wait for MySQL and Kafka, so pods don't crash-loop on a cold start.
- **StatefulSets with volume claims** for MySQL and Kafka keep their data across pod restarts. Stateless services are Deployments.
- **Config and secrets** are injected as environment variables from a ConfigMap and a Secret, so the same image runs in every environment. In production the Secret would come from Vault, External Secrets or Sealed Secrets instead of a committed file.
- **HPA** scales the gateway, order and notification services between 2 and 4 pods at 70% CPU. Two replicas from the start shows that Kafka consumer groups split partitions, and that the saga timeout job is safe to run on several pods at once.
- **Resource requests and limits** let the scheduler place pods sensibly. The JVM sizes its heap from the container limit (`MaxRAMPercentage=75`).
- **`enableServiceLinks: false`.** Otherwise Kubernetes injects `KAFKA_PORT=tcp://…` for the `kafka` Service, and the Kafka image reads every `KAFKA_*` variable as broker config, so the broker fails to start.

## Autoscaling needs metrics-server

Docker Desktop doesn't ship it:

```powershell
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl get hpa -n orderflow             # TARGETS shows a % once metrics arrive (~1 min)
```

## Optional: Ingress at http://orderflow.localhost

```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/cloud/deploy.yaml
```

The ingress controller also wants port 80, and it routes `orderflow.localhost` to the gateway.
