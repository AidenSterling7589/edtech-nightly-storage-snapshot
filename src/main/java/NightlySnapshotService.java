import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Application service: decide the report, then persist one immutable key per business date. */
public final class NightlySnapshotService {
    public enum Outcome { STORED, ALREADY_STORED }

    private final InfraiStorageClient storage;
    private final CourseDeliverySnapshot policy;
    private final EdtechSnapshotConfig config;
    private final Clock clock;

    public NightlySnapshotService(InfraiStorageClient storage, CourseDeliverySnapshot policy,
                                  EdtechSnapshotConfig config, Clock clock) {
        this.storage = storage;
        this.policy = policy;
        this.config = config;
        this.clock = clock;
    }

    public Outcome run(List<CourseDeliverySnapshot.Enrollment> enrollments)
            throws IOException, InterruptedException {
        LocalDate date = LocalDate.now(clock.withZone(config.zone()));
        String key = "course-delivery/" + date + "/educator-report.json";
        if (storage.objectExists(config.bucket(), key)) return Outcome.ALREADY_STORED;
        CourseDeliverySnapshot.Report report = policy.build(date, enrollments);
        storage.putJson(config.bucket(), key, report.toJsonBytes());
        return Outcome.STORED;
    }
}
