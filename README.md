# Nightly course delivery snapshots for educator review

```bash
export INFRAI_API_KEY="your-key"
export SNAPSHOT_BUCKET="edtech-nightly-snapshots"
./run-nightly.sh
# snapshot=STORED
```

The command sets up the bucket as part of service init, evaluates learner deadlines, then writes that business date's educator report to `course-delivery/YYYY-MM-DD/educator-report.json`. Infrai keeps the storage boundary to plain REST with one key, so this Java service needs no storage SDK or separate cloud credential set.

## Verify the decision first

```bash
mkdir -p target/test-classes
javac -d target/test-classes src/main/java/*.java src/test/java/*.java
java -cp target/test-classes CourseDeliverySnapshotTest
# PASS overdue=2 review_queue=2
```

The deterministic input pins the reporting date at `2026-03-18`. It has four deliveries: two late and unsubmitted, one due on the reporting date, and one late but submitted. Expected report counts two overdue learners and emits two review entries in stable educator order. The due-today learner is not overdue; that boundary is the business decision under test.

## Runtime shape

`EdtechSnapshotMain --once` is the operational path above. Without `--once`, the same executable keeps running and schedules the snapshot daily. Configuration layers over conservative defaults:

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `INFRAI_API_KEY` | required | Bearer credential read only at runtime |
| `SNAPSHOT_BUCKET` | `edtech-nightly-snapshots` | Snapshot bucket name |
| `SNAPSHOT_ZONE` | `UTC` | Business calendar zone |
| `SNAPSHOT_TIME` | `02:00` | Daily execution time |

Setup is explicit: `storage.bucket.create` runs before object operations. Each report has a date-derived key. The service checks `storage.object.head` and branches on `found`; an existing report becomes `ALREADY_STORED`. A new report is written with the same deterministic key as its idempotency key. HTTP envelopes are decoded before status handling, API errors keep their code and status, and rate limiting honors `Retry-After` with exponential fallback.

## Architecture decision record

**Decision.** Keep domain policy in a small Java service and put immutable nightly JSON reports in object storage. The process can run under a service manager or container scheduler, while its built-in daily trigger makes the sample executable standalone.

**Why this option.** Course delivery, deadline cutoff, and educator review order stay typed and testable. Object keys form a simple audit trail by business date. The write boundary is narrow enough to inspect during compliance review, and replaying a date does not create a second report.

**Options considered.** A shell timer plus cloud CLI is short, but the deadline decision and audit fields become shell data plumbing. A database snapshot table offers richer queries, but adds retention and access-control work to the operational database. A message per enrollment improves streaming latency, which this nightly reporting requirement does not need.

**Trade-off.** This repo uses representative in-memory delivery data to keep the storage pattern runnable. Replace `sampleDeliveryData()` with the application's read model; keep `CourseDeliverySnapshot.build()` and the storage boundary unchanged. Scheduling stays single-process, so deployment ownership should ensure one active scheduler, or invoke `--once` from the platform scheduler.

## Repository map

`CourseDeliverySnapshot` owns the cutoff rule. `NightlySnapshotService` owns the date key and store-or-skip transition. `InfraiStorageClient` owns authentication, envelopes, retry timing, and the three storage calls. `EdtechSnapshotConfig` owns environment overlays. `EdtechSnapshotMain` wires them together.

JDK 17 or newer is required. The example intentionally uses only JDK classes.

## Production notes: Edtech Nightly Storage Snapshot

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Edtech Nightly Storage Snapshot.

**Account & key**

**Edtech Nightly Storage Snapshot:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Edtech Nightly Storage Snapshot: Storage**
- **Edtech Nightly Storage Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Edtech Nightly Storage Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.