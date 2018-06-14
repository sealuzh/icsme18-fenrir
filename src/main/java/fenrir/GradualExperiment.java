package fenrir;

import java.util.List;
import java.util.Objects;

public class GradualExperiment extends Experiment {

    // workaround as GSON does not support automatic subclass serialization/deserialization
    private float startTraffic;

    public float getStartTraffic() {
        return startTraffic;
    }

    public GradualExperiment(int id, ExperimentType type, String targetService, int duration, long minTraffic, int priority, float startTraffic, List<String> preferredUserGroup) {
        this(id, type, targetService, duration, minTraffic, priority, startTraffic, preferredUserGroup, false);
    }

    public GradualExperiment(int id, ExperimentType type, String targetService, int duration, long minTraffic, int priority, float startTraffic, List<String> preferredUserGroup, boolean restarted) {
        super(id, type, targetService, duration, minTraffic, priority, preferredUserGroup, restarted);
        this.startTraffic = startTraffic;
    }

    @Override
    public long getMinTrafficAt(int hour, int duration) {
//        if(duration < getMinDuration())
//            duration = getMinDuration();

        if(hour < 0 || hour > duration)
            return 0;

        /* assumption: linear function for gradual increase
            f(x) = kx + b
                where
                b ... startTraffic

            i.e., integral ( kx + b) dx from 0 to duration should be totalTraffic
                ---> k = 2 * ( totalTraffic - duration * startTraffic) / duration^2

            i.e., traffic at hour X:
            2 * ( totalTraffic - duration * startTraffic) * X / duration^2 + startTraffic
        */

//        double factor = 2 * (1 - this.startTraffic) * hour / Math.pow(duration, 2) + this.startTraffic / duration;
//        return Math.round(this.getRequiredTotalTraffic() * factor);

        return Math.round(2 * (this.getRequiredTotalTraffic() - this.startTraffic * duration) * hour / Math.pow(duration, 2) + this.startTraffic);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GradualExperiment that = (GradualExperiment) o;
        return Float.compare(that.getStartTraffic(), getStartTraffic()) == 0;
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), getStartTraffic());
    }
}
