import java.time.LocalDate;
import java.util.List;

/** Focused check for the deadline boundary and stable educator reporting order. */
public final class CourseDeliverySnapshotTest {
    public static void main(String[] args) {
        LocalDate close = LocalDate.of(2026, 3, 18);
        List<CourseDeliverySnapshot.Enrollment> input = List.of(
                new CourseDeliverySnapshot.Enrollment("course-b", "learner-2", close.minusDays(1), false, "teacher-z"),
                new CourseDeliverySnapshot.Enrollment("course-a", "learner-1", close.minusDays(3), false, "teacher-a"),
                new CourseDeliverySnapshot.Enrollment("course-c", "learner-3", close, false, "teacher-a"),
                new CourseDeliverySnapshot.Enrollment("course-d", "learner-4", close.minusDays(2), true, "teacher-b"));

        CourseDeliverySnapshot.Report report = new CourseDeliverySnapshot().build(close, input);
        require(report.activeDeliveries() == 4, "all course deliveries must be counted");
        require(report.overdueLearners() == 2, "only unsubmitted deadlines before the close are overdue");
        require(report.educatorReviewQueue().equals(List.of(
                "teacher-a:course-a:learner-1", "teacher-z:course-b:learner-2")),
                "review queue must be deterministic");
        System.out.println("PASS overdue=2 review_queue=2");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
