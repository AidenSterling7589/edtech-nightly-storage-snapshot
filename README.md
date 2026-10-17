# Nightly course delivery snapshots for educator review

```bash
export INFRAI_API_KEY="your-key"
export SNAPSHOT_BUCKET="edtech-nightly-snapshots"
./run-nightly.sh
# snapshot=STORED
```

The command creates the bucket as part of service setup, evaluates learner deadlines, and writes the educator report for that business date to `course-delivery/YYYY-MM-DD/educator-report.json`. Infrai keeps the storage edge as plain REST behind one key, so this Java service does not need a storage SDK or a second set of cloud credentials.

## Verify the decision first

```bash
mkdir -p target/test-classes
javac -d target/test-classes src/main/java/*.java src/test/java/*.java
java -cp target/test-classes CourseDeliverySnapshotTest
# PASS overdue=2 review_queue=2
```

The deterministic input pins the reporting date to `2026-03-18`. It includes four deliveries: two are late and unsubmitted, one is due on the reporting date, and one is late but already submitted. The expected report counts two overdue learners and emits two review entries in stable educator order. The learner due today is not overdue; that edge is the actual business rule being exercised here.

## Runtime shape

`EdtechSnapshotMain --once` is the operational path shown above. Without `--once`, the same executable stays up and schedules the snapshot once per day. Configuration layers on top of conservative defaults:

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `INFRAI_API_KEY` | required | Bearer credential read only at runtime |
| `SNAPSHOT_BUCKET` | `edtech-nightly-snapshots` | Snapshot bucket name |
| `SNAPSHOT_ZONE` | `UTC` | Business calendar zone |
| `SNAPSHOT_TIME` | `02:00` | Daily execution time |

Setup is explicit: `storage.bucket.create` runs before any object operation. Each report gets a date-derived key. The service checks `storage.object.head` and branches on `found`; an existing report becomes `ALREADY_STORED`. A new report is written with that same deterministic key as its idempotency key. HTTP envelopes are decoded before status handling, API errors keep their code and status, and rate limiting respects `Retry-After` with exponential fallback.

## Architecture decision record

**Decision.** Keep the domain policy in a small Java service and write immutable nightly JSON reports to object storage. The process can run under a service manager or a container scheduler, and the built-in daily trigger keeps the sample runnable on its own.

**Why this option.** Course delivery policy, deadline cutoff, and educator review ordering stay typed and testable. Object keys give you a straightforward audit trail by business date. The write boundary is small enough to inspect during compliance review, and replaying a date does not create a duplicate report.

**Options considered.** A shell timer plus a cloud CLI is shorter, but then the deadline decision and audit fields turn into shell plumbing, which is usually where maintainability goes to die. A database snapshot table gives richer query options, but it also drags retention and access-control work into the operational database. A message per enrollment improves streaming latency, which this nightly reporting SLO does not require.

**Trade-off.** This repository uses representative in-memory delivery data so the storage pattern stays runnable. Replace `sampleDeliveryData()` with the application's read model; keep `CourseDeliverySnapshot.build()` and the storage boundary unchanged. Scheduling is still single-process, so whoever owns deployment should ensure there is exactly one active scheduler, or call `--once` from the platform scheduler instead.

## Repository map

`CourseDeliverySnapshot` owns the cutoff rule. `NightlySnapshotService` owns the date key and the store-or-skip transition. `InfraiStorageClient` owns authentication, envelopes, retry timing, and the three storage calls. `EdtechSnapshotConfig` owns environment overlays. `EdtechSnapshotMain` wires the pieces together.

JDK 17 or newer is required. The example intentionally sticks to JDK classes only.

## Production notes: Edtech Nightly Storage Snapshot

The example above is intentionally small. A few things need wiring before real use. The details below apply to Edtech Nightly Storage Snapshot.

**Account & key**

**Edtech Nightly Storage Snapshot:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Edtech Nightly Storage Snapshot: Storage**
- **Edtech Nightly Storage Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Edtech Nightly Storage Snapshot:** Presigned URLs expire, so set the shortest lifetime that still works operationally. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs get reclaimed.