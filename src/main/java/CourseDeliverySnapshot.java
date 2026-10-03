import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Domain policy for the audit snapshot, independent of HTTP and scheduling. */
public final class CourseDeliverySnapshot {
    public record Enrollment(String courseId, String learnerId, LocalDate deadline,
                             boolean submitted, String educatorId) {}

    public record Report(LocalDate businessDate, int activeDeliveries, int overdueLearners,
                         List<String> educatorReviewQueue) {
        public byte[] toJsonBytes() {
            String queue = educatorReviewQueue.stream().map(CourseDeliverySnapshot::quote)
                    .reduce((a, b) -> a + "," + b).orElse("");
            String json = "{\"business_date\":\"" + businessDate
                    + "\",\"active_deliveries\":" + activeDeliveries
                    + ",\"overdue_learners\":" + overdueLearners
                    + ",\"educator_review_queue\":[" + queue + "]}";
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    public Report build(LocalDate businessDate, List<Enrollment> enrollments) {
        List<String> review = new ArrayList<>();
        int overdue = 0;
        for (Enrollment enrollment : enrollments) {
            if (!enrollment.submitted() && enrollment.deadline().isBefore(businessDate)) {
                overdue++;
                review.add(enrollment.educatorId() + ":" + enrollment.courseId()
                        + ":" + enrollment.learnerId());
            }
        }
        review.sort(Comparator.naturalOrder());
        return new Report(businessDate, enrollments.size(), overdue, List.copyOf(review));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
