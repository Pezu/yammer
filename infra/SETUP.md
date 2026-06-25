# Yammer — GCP setup runbook (Cloud Run)

One-time provisioning for `yammer-order-app`. Architecture:

- **api** → Cloud Run (`yammer-api`), **pinned to 1 instance** (`--min/max-instances=1`)
  because the on-prem bridge's WebSocket session lives in memory. Cloud Run's 60-min
  request cap drops the ws roughly hourly; the **durable fiscal outbox + idempotent
  bridge** absorb that (no lost/duplicate prints). Connects to Cloud SQL via the Cloud
  SQL socket factory (IAM, no public IP).
- **web** → Cloud Run (`yammer-web`), static Angular SPA.
- **db** → Cloud SQL Postgres 16 (`yammer-db`, database `yammer`, schema `yammer`).
- **bridge** → built by CI as a jar artifact; runs on-prem at the venue (never deployed).
- CI/CD → GitHub Actions (`.github/workflows/deploy.yml`) via Workload Identity Federation.

Placeholders:

```
BILLING_ACCOUNT=011234-A0D030-EDB5B0
GH_REPO=Pezu/yammer
PROJECT=yammer-order-app
REGION=europe-west1
SQL_INSTANCE=$PROJECT:$REGION:yammer-db
```

## 1. Project, billing, APIs  ✅ done

```
gcloud config set project $PROJECT
gcloud services enable \
  run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  artifactregistry.googleapis.com storage.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com cloudresourcemanager.googleapis.com
```

## 2. Artifact Registry

```
gcloud artifacts repositories create yammer --repository-format=docker \
  --location=$REGION --description="yammer images" --project=$PROJECT
```

## 3. Cloud SQL  (Enterprise edition for the cheap shared-core tier)

```
gcloud sql instances create yammer-db --database-version=POSTGRES_16 \
  --edition=ENTERPRISE --tier=db-g1-small --region=$REGION --storage-size=10GB --project=$PROJECT
DBPASS=$(openssl rand -base64 24)
gcloud sql databases create yammer --instance=yammer-db --project=$PROJECT
gcloud sql users create yammer_app --instance=yammer-db --password="$DBPASS" --project=$PROJECT
printf '%s' "$DBPASS" | gcloud secrets create yammer-db-password --data-file=- --project=$PROJECT
```

Create the schema (as `postgres`, after `gcloud sql users set-password postgres ...`):
`CREATE SCHEMA IF NOT EXISTS yammer AUTHORIZATION yammer_app;`

## 4. GCS media bucket

```
gcloud storage buckets create gs://$PROJECT-uploads --location=$REGION --project=$PROJECT
```

## 5. Secrets

```
printf '%s' "$(openssl rand -base64 48)" | gcloud secrets create jwt-secret    --data-file=- --project=$PROJECT
printf '%s' "$(openssl rand -base64 32)" | gcloud secrets create bridge-api-key --data-file=- --project=$PROJECT
# yammer-db-password created in §3
```

## 6. Cloud Run runtime service account roles

Cloud Run runs as the default compute SA. Grant it Cloud SQL, Secret Manager, and GCS:

```
PNUM=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
RUNSA=$PNUM-compute@developer.gserviceaccount.com
for ROLE in roles/cloudsql.client roles/secretmanager.secretAccessor roles/storage.objectAdmin; do
  gcloud projects add-iam-policy-binding $PROJECT --member="serviceAccount:$RUNSA" --role="$ROLE"
done
```

## 7. Workload Identity Federation + deployer SA (GitHub → GCP)

```
gcloud iam service-accounts create gh-deployer --project=$PROJECT --display-name="GitHub Actions deployer"
GHSA=gh-deployer@$PROJECT.iam.gserviceaccount.com
for ROLE in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
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

gcloud iam service-accounts add-iam-policy-binding $GHSA --project=$PROJECT \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PNUM/locations/global/workloadIdentityPools/github/attribute.repository/$GH_REPO"
```

In **GitHub → repo → Settings → Secrets and variables → Actions**, add:

```
WIF_PROVIDER        = projects/$PNUM/locations/global/workloadIdentityPools/github/providers/github
WIF_SERVICE_ACCOUNT = gh-deployer@$PROJECT.iam.gserviceaccount.com
```

## 8. Trigger the pipeline

Push to `main` (or run **deploy** from the Actions tab). It deploys only the service whose
folder changed: `api/**` → api on Cloud Run (pinned to 1 instance, wired to Cloud SQL +
secrets), `web/**` → web on Cloud Run. A manual run deploys both. The **bridge** is not
built in CI — it runs on-prem; build it locally with `cd bridge && mvn -DskipTests package`.

## 9. Custom domain + clients (optional but recommended)

The prod domain is `rendezvous-app.ro`: the apex serves the web app, `api.` serves the API.

First **verify domain ownership** (one-time, required before an apex mapping) in
[Search Console](https://search.google.com/search-console) — add the TXT record it gives you
at the registrar. Then map both hosts (add the DNS records each command prints):

```
# apex → web app (A/AAAA records)
gcloud run domain-mappings create --service=yammer-web \
  --domain=rendezvous-app.ro --region=$REGION --project=$PROJECT

# api subdomain → API (CNAME → ghs.googlehosted.com)
gcloud run domain-mappings create --service=yammer-api \
  --domain=api.rendezvous-app.ro --region=$REGION --project=$PROJECT
```

The web app reaches the API through nginx (proxies `/api` → the `yammer-api` run.app URL),
so it does **not** depend on the custom API domain. The `api.` host exists for the on-prem
bridge, which connects with:

```
BRIDGE_SERVER_URL=wss://api.rendezvous-app.ro/ws/bridge
BRIDGE_API_KEY=<value of the bridge-api-key secret>
```

Once cutover is confirmed, drop the old mappings:

```
gcloud beta run domain-mappings delete --domain=servioapp.ro     --region=$REGION --project=$PROJECT
gcloud beta run domain-mappings delete --domain=api.servioapp.ro --region=$REGION --project=$PROJECT
```
