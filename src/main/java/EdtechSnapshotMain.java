import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Executable composition root. Use --once for an operator-triggered snapshot. */
public final class EdtechSnapshotMain {
    public static void main(String[] args) throws Exception {
        EdtechSnapshotConfig config = EdtechSnapshotConfig.fromEnvironment();
        InfraiStorageClient storage = new InfraiStorageClient(config.apiKey());
        storage.createBucket(config.bucket());
        NightlySnapshotService service = new NightlySnapshotService(
                storage, new CourseDeliverySnapshot(), config, Clock.system(config.zone()));

        if (args.length == 1 && args[0].equals("--once")) {
            System.out.println("snapshot=" + service.run(sampleDeliveryData()));
            return;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long initialDelay = millisUntilNextRun(config);
        scheduler.scheduleAtFixedRate(() -> runScheduled(service), initialDelay,
                Duration.ofDays(1).toMillis(), TimeUnit.MILLISECONDS);
        System.out.println("nightly_snapshot_scheduled_at=" + config.nightlyAt() + " zone=" + config.zone());
    }

    private static long millisUntilNextRun(EdtechSnapshotConfig config) {
        ZonedDateTime now = ZonedDateTime.now(config.zone());
        ZonedDateTime next = now.toLocalDate().atTime(config.nightlyAt()).atZone(config.zone());
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }

    private static void runScheduled(NightlySnapshotService service) {
        try {
            System.out.println("snapshot=" + service.run(sampleDeliveryData()));
        } catch (Exception exception) {
            System.err.println("snapshot_run_failed=" + exception.getMessage());
        }
    }

    private static List<CourseDeliverySnapshot.Enrollment> sampleDeliveryData() {
        return List.of(
                new CourseDeliverySnapshot.Enrollment("risk-101", "learner-7",
                        java.time.LocalDate.now().minusDays(1), false, "educator-2"),
                new CourseDeliverySnapshot.Enrollment("ledger-201", "learner-9",
                        java.time.LocalDate.now().plusDays(2), false, "educator-1"));
    }
}
