import java.time.LocalTime;
import java.time.ZoneId;

/** Layered configuration: stable defaults, then environment overrides. */
public record EdtechSnapshotConfig(
        String apiKey, String bucket, ZoneId zone, LocalTime nightlyAt) {

    public static EdtechSnapshotConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String bucket = getenv("SNAPSHOT_BUCKET", "edtech-nightly-snapshots");
        ZoneId zone = ZoneId.of(getenv("SNAPSHOT_ZONE", "UTC"));
        LocalTime nightlyAt = LocalTime.parse(getenv("SNAPSHOT_TIME", "02:00"));
        return new EdtechSnapshotConfig(key, bucket, zone, nightlyAt);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String getenv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
