package fenrir.genetic;

import fenrir.Constants;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

public class Assignment {
    private int hour;

    private HashMap<String, Float> trafficAssignment;

    private static final SecureRandom rand = new SecureRandom();

    public Assignment(int hour, HashMap<String, Float> trafficAssignment) {
        this.hour = hour;
        this.trafficAssignment = trafficAssignment;
    }

    public Assignment() {}

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public HashMap<String, Float> getTrafficAssignment() {
        return trafficAssignment;
    }

    public void setTrafficAssignment(HashMap<String, Float> trafficAssignment) {
        this.trafficAssignment = trafficAssignment;
    }

    public Assignment copyAssignment() {
        Assignment n = new Assignment();

        n.setHour(this.getHour());
        n.setTrafficAssignment(new HashMap<>());

        this.getTrafficAssignment().entrySet().stream()
                .forEach(entry -> n.getTrafficAssignment().put(entry.getKey(), new Float(entry.getValue().floatValue())));

        return n;
    }

    public void flipUserGroup(String old, String newGroup) {
        if(old.equals(newGroup))
            return;

        if(getTrafficAssignment().containsKey(newGroup))
            getTrafficAssignment().put(newGroup, getTrafficAssignment().get(newGroup).floatValue() + (getTrafficAssignment().containsKey(old) ? getTrafficAssignment().get(old).floatValue() : Constants.MIN_TRAFFIC_ADJUSTMENT));
        else
            getTrafficAssignment().put(newGroup, getTrafficAssignment().containsKey(old) ? getTrafficAssignment().get(old).floatValue() : Constants.MIN_TRAFFIC_ADJUSTMENT);

        getTrafficAssignment().remove(old);
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "hour=" + hour +
                ", trafficAssignment= [" + String.join(",", trafficAssignment.entrySet().stream().map(entry -> entry.getKey() + " -> " + entry.getValue()).collect(Collectors.toSet())) +
                "]}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return getHour() == that.getHour() &&
                Objects.equals(getTrafficAssignment(), that.getTrafficAssignment());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getHour(), getTrafficAssignment());
    }

}
