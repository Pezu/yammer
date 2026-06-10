# Yammer — GCP setup runbook

One-time provisioning for the `yammer-order-app` project. Architecture:

- **api** → Compute Engine VM (`yammer-api`) running `caddy` + `api` + `cloud-sql-proxy`
  via `deploy/docker-compose.yml`. Caddy gives it trusted TLS and streams the bridge's
  `wss://` connection with **no timeout** (the reason it's not on Cloud Run).
- **web** → Cloud Run (`yammer-web`), static Angular SPA.
- **db** → Cloud SQL Postgres 16 (`yammer-db`, database `yammer`).
- **bridge** → built by CI as a jar artifact; runs on-prem at the venue (never deployed).
- CI/CD → GitHub Actions (`.github/workflows/deploy.yml`) auth'd via Workload Identity
  Federation. **The api Cloud Run note does not apply — api runs on the VM.**

Fill in the two placeholders before you start:

```
BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX      # gcloud billing accounts list
API_DOMAIN=api.example.com                # the subdomain you'll point at the VM
GH_REPO=Pezu/yammer
PROJECT=yammer-order-app
REGION=europe-west1
ZONE=europe-west1-b
```

## 1. Project, billing, APIs

```
gcloud projects create $PROJECT --name="Yammer"
gcloud billing projects link $PROJECT --billing-account=$BILLING_ACCOUNT
gcloud config set project $PROJECT
gcloud services enable \
  run.googleapis.com sqladmin.googleapis.com compute.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com storage.googleapis.com \
  iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  iap.googleapis.com cloudresourcemanager.googleapis.com
```

## 2. Artifact Registry (docker)

```
gcloud artifacts repositories create yammer --repository-format=docker \
  --location=$REGION --description="yammer images" --project=$PROJECT
```

## 3. Cloud SQL Postgres

```
gcloud sql instances create yammer-db --database-version=POSTGRES_16 \
  --tier=db-g1-small --region=$REGION --storage-size=10GB --project=$PROJECT
gcloud sql databases create yammer --instance=yammer-db --project=$PROJECT
# app user (password from the secret below)
gcloud sql users create yammer_app --instance=yammer-db \
  --password="$(openssl rand -base64 24)" --project=$PROJECT   # note this value for the secret
```

Create the `yammer` schema (the app uses `currentSchema=yammer`): connect once and run
`CREATE SCHEMA IF NOT EXISTS yammer AUTHORIZATION yammer_app;` (psql via the proxy, or the
Console). Flyway then owns the tables.

## 4. GCS media bucket

```
gcloud storage buckets create gs://$PROJECT-uploads --location=$REGION --project=$PROJECT
```

## 5. Secrets

```
printf '%s' "$(openssl rand -base64 48)" | gcloud secrets create jwt-secret      --data-file=- --project=$PROJECT
printf '%s' "$(openssl rand -base64 32)" | gcloud secrets create bridge-api-key   --data-file=- --project=$PROJECT
# use the SAME password you set for yammer_app above:
printf '%s' "<yammer_app password>"      | gcloud secrets create yammer-db-password --data-file=- --project=$PROJECT
```

## 6. api VM + firewall + static IP

```
gcloud compute addresses create yammer-api-ip --region=$REGION --project=$PROJECT
IP=$(gcloud compute addresses describe yammer-api-ip --region=$REGION --project=$PROJECT --format='value(address)')

gcloud compute instances create yammer-api \
  --project=$PROJECT --zone=$ZONE --machine-type=e2-small \
  --image-family=ubuntu-2204-lts --image-project=ubuntu-os-cloud \
  --address=yammer-api-ip --tags=yammer-api \
  --scopes=cloud-platform \
  --metadata-from-file=startup-script=deploy/vm-startup.sh

# inbound 80/443 from anywhere; SSH only via IAP (35.235.240.0/20)
gcloud compute firewall-rules create yammer-api-web --project=$PROJECT \
  --direction=INGRESS --action=ALLOW --rules=tcp:80,tcp:443 --target-tags=yammer-api
gcloud compute firewall-rules create yammer-api-iap-ssh --project=$PROJECT \
  --direction=INGRESS --action=ALLOW --rules=tcp:22 \
  --source-ranges=35.235.240.0/20 --target-tags=yammer-api
```

Grant the **VM's default compute service account** what the containers need (Cloud SQL,
secrets, GCS, pulling the image):

```
PNUM=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
VMSA=$PNUM-compute@developer.gserviceaccount.com
for ROLE in roles/cloudsql.client roles/secretmanager.secretAccessor \
            roles/storage.objectAdmin roles/artifactregistry.reader; do
  gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$VMSA" --role="$ROLE"
done
```

## 7. DNS

Create an **A record** for `$API_DOMAIN` → `$IP` at your DNS provider. Caddy can't issue
the cert until this resolves.

## 8. First boot of the api VM

```
# copy the deploy files up (IAP SSH)
gcloud compute scp --project=$PROJECT --zone=$ZONE --tunnel-through-iap \
  deploy/docker-compose.yml deploy/Caddyfile deploy/render-env.sh yammer-api:/tmp/
gcloud compute ssh yammer-api --project=$PROJECT --zone=$ZONE --tunnel-through-iap --command "\
  sudo mkdir -p /opt/yammer && sudo mv /tmp/docker-compose.yml /tmp/Caddyfile /tmp/render-env.sh /opt/yammer/ && \
  sudo chmod +x /opt/yammer/render-env.sh && \
  sudo API_DOMAIN=$API_DOMAIN /opt/yammer/render-env.sh && \
  cd /opt/yammer && sudo docker compose up -d"
```

The api image won't exist until the first CI build (step 10); the `caddy`/`proxy`
containers come up now and the `api` one starts once the image is pushed.

## 9. Workload Identity Federation + deployer SA (GitHub → GCP)

```
gcloud iam service-accounts create gh-deployer --project=$PROJECT \
  --display-name="GitHub Actions deployer"
GHSA=gh-deployer@$PROJECT.iam.gserviceaccount.com
for ROLE in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser \
            roles/compute.osAdminLogin roles/iap.tunnelResourceAccessor roles/compute.viewer; do
  gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$GHSA" --role="$ROLE"
done

gcloud iam workload-identity-pools create github --location=global \
  --display-name="GitHub" --project=$PROJECT
gcloud iam workload-identity-pools providers create-oidc github \
  --location=global --workload-identity-pool=github --project=$PROJECT \
  --display-name="GitHub OIDC" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='$GH_REPO'" \
  --issuer-uri="https://token.actions.githubusercontent.com"

# let this repo impersonate the deployer SA
gcloud iam service-accounts add-iam-policy-binding $GHSA --project=$PROJECT \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PNUM/locations/global/workloadIdentityPools/github/attribute.repository/$GH_REPO"
```

In **GitHub → repo → Settings → Secrets and variables → Actions**, add two secrets:

```
WIF_PROVIDER = projects/$PNUM/locations/global/workloadIdentityPools/github/providers/github
WIF_SERVICE_ACCOUNT = gh-deployer@$PROJECT.iam.gserviceaccount.com
```

## 10. Trigger the pipeline

Push to `main` (or run the **deploy** workflow manually from the Actions tab). It builds
the api + web images, deploys web to Cloud Run, rolls the api container on the VM, and
attaches the bridge jar as an artifact.

After the first deploy, point the web app's API base URL at `https://$API_DOMAIN` and run
the bridge on-prem with `BRIDGE_SERVER_URL=wss://$API_DOMAIN/ws/bridge` and the
`bridge-api-key` value.

> Note: the api intentionally runs as a **single** VM container (the bridge WebSocket
> session is in-memory). If you ever move the api to Cloud Run instead, pin it to
> `--min-instances=1 --max-instances=1` for the same reason.
