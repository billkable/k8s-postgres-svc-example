# README

This project is a simple code example of consuming a database from a
spring boot app,
both running on kubernetes.

The postgres database is treated as a backing resource,
and as such is deployed and managed in a different namespace
from the `pal-tracker` application/deployment.

The postgres database `tracker-db` will not be exposed externally,
and will be exposed to `pal-tracker` spring boot app through a
kubernetes `Service` object.

The `pal-tracker` application will consume the database via its
Service exposed (kubernetes assigned) DNS name.

## Building `pal-tracker` and `db-migration`

If you prefer to avoid the Spring coding,
go straight to [K8s Setup](#k8s-setup) section.
The images needed to run the deployments are pre-built and
pushed to Docker Hub `bkable/pal-tracker:v10` and
`bkable/tracker-db-migration:v10` images.

### Building and publishing the `tracker-db-migration` image

1.  Build the docker image on your dev workstation,
    running the following from the project root directory:

    `./gradlew db-migration: bootBuildImage --imageName=<your registry/repo>/tracker-db-migration:v10`

1.  Login to your docker registry (Docker Hub assumed by default,
    see documentation for your Container Registry for login details
    if not Docker Hub):

    `docker login`

    Supply credentials as necessary.

1.  Push your image:

    `docker push <your registry/repo>/tracker-db-migration:v10`

### Building and publishing the `pal-tracker` image

1.  Build the docker image on your dev workstation,
    running the following from the project root directory:

    `./gradlew app:bootBuildImage --imageName=<your registry/repo>/pal-tracker:v10`

1.  Login to your docker registry (Docker Hub assumed by default,
    see documentation for your Container Registry for login details
    if not Docker Hub):

    `docker login`

    Supply credentials as necessary.

1.  Push your image:

    `docker push <your registry/repo>/pal-tracker:v10`

### Change the deployment images to point to your repo

Search and replace `bkable` in the following deployment
object definition files with the name of your registry/repo to
which you published your images:

- `k8s/app/deployment.yaml`
- `k8s/app/db-migration-job.yaml`

## K8s Setup

### Directories

1.  The `k8s/app` directory hosts the kubernetes objects necessary
    to deploy the `pal-tracker` application,
    and apply its database migration.

1.  The `k8s/db` directory hosts the kubernetes objects necessary
    to deploy the postgres database.

### Namespaces

Create the following namespaces (if not already existing):

- `kubectl create ns development`
- `kubectl create ns databases-development`

The `pal-tracker` application and associated database migration
job will be deployed to the `development` namespace.

The postgres database will be deployed to the `databases-development`
namespace.

## Deploy the database via Helm

### Install

```bash
helm3 install tracker-db bitnami/postgresql -n databases-development --set "global.postgresql.postgresqlDatabase=tracker_development,global.postgresql.postgresqlUsername=tracker"
```

### Accessing

Database is referenced by following k8s DNS:

`tracker-db-postgresql.databases-development.svc.cluster.local`

To get the password for "postgres" run:

```bash
export POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace databases-development tracker-db-postgresql -o jsonpath="{.data.postgresql-postgres-password}" | base64 --decode)
```

To get the password for "tracker" run:

```bash
export POSTGRES_PASSWORD=$(kubectl get secret --namespace databases-development tracker-db-postgresql -o jsonpath="{.data.postgresql-password}" | base64 --decode)
```

To connect to your database run the following command:

```bash
kubectl run tracker-db-postgresql-client --rm --tty -i --restart='Never' --namespace databases-development --image docker.io/bitnami/postgresql:11.10.0-debian-10-r9 --env="PGPASSWORD=$POSTGRES_PASSWORD" --command -- psql --host tracker-db-postgresql -U postgres -d postgres -p 5432
```

To connect to your database from outside the cluster execute the following commands:

```bash
kubectl port-forward --namespace databases-development svc/tracker-db-postgresql 5432:5432 &
PGPASSWORD="$POSTGRES_PASSWORD" psql --host 127.0.0.1 -U postgres -d $POSTGRES_ADMIN_PASSWORD -p 5432
```

### Secrets

You will need to set up secrets for the `POSTGRES_PASSWORD` and the
`SPRING_DATASOURCE_URL`:

```bash
kubectl create secret generic db-credentials --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql://tracker-db.databases-development:5432/tracker_development?user=tracker&password=$POSTGRES_PASSWORD' --namespace=development
```

The `db-credentials` are used by the `pal-tracker` and associated db migration
applications to connect to the postges database.

Notice the JDBC connection string contains the host name that is the
DNS name of the postgres database server:

`{pal-tracker database svc name}.{namespace}`

The actual host name:

`tracker-db.databases-development`

## Deploy `pal-tracker` application and migrate its database

1.  Apply the deployment:

    `kubectl apply -f k8s/app/`

    This will deploy the `pal-tracker` application,
    as well as apply the database migration against
    the tracker database (see `app/db-migration-job.yaml`).

1.  Watch the database migration job creation and execution,
    wait for the status to go to `1/1` completions.

    `watch kubectl get jobs -n development`

1.  Verify `pal-tracker` deployment:

    `kubectl get all -n development`

## Test it out

### Postman collection

You can import the postman collection and environments from the
`postman` folder.

Use the `local k8s` environment running microK8s / minikube,
although beware if you are running in context of a VM,
you may need to replace `localhost` with the VM/Node IP address.

### Curl

You can run requests manually with `curl`,
make sure to correlate and substitute the necessary `${}`
placeholders.

`${INGRESS}` will be the ingress route for your app.

1.  Get all time entries

    `curl -i ${INGRESS}/time-entries`

1.  Create a time entry

    `curl -i -XPOST -H"Content-Type: application/json" ${INGRESS}/time-entries -d"{\"projectId\": 1, \"userId\": 2, \"date\": \"2019-01-01\", \"hours\": 8}"`

1.  Get a time entry by ID

    `curl -i ${INGRESS}/time-entries/${TIME_ENTRY_ID}`

1.  Update a time entry by ID

    `curl -i -XPUT -H"Content-Type: application/json" ${INGRESS}/time-entries/${TIME_ENTRY_ID} -d"{\"projectId\": 88, \"userId\": 99, \"date\": \"2019-01-01\", \"hours\": 8}"`

1.  Delete a time entry by ID

    `curl -i -XDELETE -H"Content-Type: application/json" ${INGRESS}/time-entries/${TIME_ENTRY_ID}`

    # Clean up

    1.  `kubectl delete -f k8s/app/`
    1.  `kubectl delete -f k8s/db/`
    1.  `kubectl delete ns databases-development`
    1.  If you no longer need the development workspace you can delete it:
        `kubectl delete ns development`

## Application lifecycle orchestration

Notice this example demonstrates a simple web app consuming a database.

The database is a mandatory backing service for the `pal-tracker` application.
It relies not only on the database server,
but the proper schema version present for the `pal-tracker` application to work
properly.

All this means the database,
with the proper schema migrated,
must be functioning before the `pal-tracker` application can service requests.

The codebase presented in this project ties the state of the database
and schema as part of the `pal-tracker` readiness probe.

Try starting both the `app` and `db` deployments at the same time,
and watch the `tracker-db` migration,
as well as the `pal-tracker` pods spent in the pending state:

```bash
watch "kubectl get all --all-namespaces --selector 'app in (pal-tracker,tracker-db)'"
```

## Cleaning up

### Delete app and migration

```bash
k delete -f k8s/app
k delete secret/db-credentials -n development
```

### Cleaning up the database

```bash
helm uninstall tracker-db -n databases-development
k delete pvc/data-tracker-db-postgresql-0 -n databases-development
```

### Verify cleaned

Nothing should remain:

```bash
k get pvc -n databases-development
k get pv -n databases-development
k get all -n databases-development
k get all -n development
```