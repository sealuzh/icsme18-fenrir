package fenrir;

import java.util.List;
import java.util.Objects;

public class Experiment {
    private final int id;

    private final ExperimentType type;

    private final String targetService;

    private final int minDuration; // number of slots needed

    private final long requiredTotalTraffic; // total number of requests/users needed to allow for statistical reasoning

    private final int priority;

    private final List<String> preferredUserGroup;

    private transient final boolean restarted;

    public Experiment(int id, ExperimentType type, String targetService, int minDuration, long requiredTotalTraffic, int priority, List<String> preferredUserGroup, boolean restarted) {
        this.id = id;
        this.type = type;
        this.targetService = targetService;
        this.minDuration = minDuration;
        this.requiredTotalTraffic = requiredTotalTraffic;
        this.priority = priority;
        this.preferredUserGroup = preferredUserGroup;
        this.restarted = restarted;
    }

    public Experiment(int id, ExperimentType type, String targetService, int minDuration, long requiredTotalTraffic, int priority, List<String> preferredUserGroup) {
        this(id, type, targetService, minDuration, requiredTotalTraffic, priority, preferredUserGroup, false);
    }

    public int getPriority() {
        return priority;
    }

    public int getId() {
        return id;
    }

    public ExperimentType getType() {
        return type;
    }

    public String getTargetService() {
        return targetService;
    }

    public int getMinDuration() {
        return minDuration;
    }

    public long getRequiredTotalTraffic() {
        return requiredTotalTraffic;
    }

    public boolean isGradual() {
        return this instanceof GradualExperiment;
    }

    public List<String> getPreferredUserGroup() {
        return preferredUserGroup;
    }

    public boolean isRestarted() {
        return restarted;
    }

//    public String getGradual() {
//        return gradual;
//    }

    /**
     * Returns the lower bound of traffic which is needed at the given time slot when experiments takes duration hours.
     * Non-gradual experiment, i.e., traffic consumed throughout experiment is constant.
     * @param hour Timeslot
     * @param duration Duration of the Experiment
     * @return Lower bound of traffic needed for experiment.
     */
    public long getMinTrafficAt(int hour, int duration) {
//        if(duration < getMinDuration())
//            duration = getMinDuration();

        if(hour >= 0 && hour < duration) {
            return Math.round(this.requiredTotalTraffic / (float) duration);
        }
        return 0;
    }

    public boolean isBusinessExperiment() {
        return this.getType() == ExperimentType.BUSINESS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Experiment that = (Experiment) o;
        return getId() == that.getId() &&
                getMinDuration() == that.getMinDuration() &&
                getRequiredTotalTraffic() == that.getRequiredTotalTraffic() &&
                getPriority() == that.getPriority() &&
                getType() == that.getType() &&
                Objects.equals(getTargetService(), that.getTargetService());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getType(), getTargetService(), getMinDuration(), getRequiredTotalTraffic(), getPriority());
    }

    @Override
    public String toString() {
        return "Experiment{" +
                "id=" + id +
                ", type=" + type +
                ", targetService='" + targetService + '\'' +
                ", minDuration=" + minDuration +
                ", requiredTotalTraffic=" + requiredTotalTraffic +
                ", priority=" + priority +
                '}';
    }
}
