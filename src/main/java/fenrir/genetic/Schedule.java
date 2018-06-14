package fenrir.genetic;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Schedule {
    private int startSlot;
    private List<Assignment> assignments;

    public int getStartSlot() {
        return startSlot;
    }

    public void setStartSlot(int startSlot) {
        this.startSlot = startSlot;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        this.assignments = assignments;
    }

    public int getDuration() {
        return this.getAssignments().size();
    }

    public Schedule(int startSlot, List<Assignment> assignments) {

        this.startSlot = startSlot;
        this.assignments = assignments;
    }

    public Schedule() {
    }

    public boolean isInterrupted() {
        int current = getStartSlot();

        for(Assignment a : getAssignments()) {
            if(a.getHour() == current) {
                current++;

                if(a.getTrafficAssignment().values().stream().max(Float::compare).orElse(0.0F) <= 0.0)
                    return true;
            }
            else
                return true;
        }
        return false;
    }

    public void moveByHours(int hours, boolean back) {
        if(back && this.startSlot < hours) {
            hours = this.startSlot;
        }
        this.startSlot = this.startSlot + (back ? -hours : hours);

        for(Assignment a : this.assignments) {
            a.setHour(a.getHour() + (back ? -hours : hours));
        }
    }

    public void shortenByHours(int hours) {
        if(hours >= this.getDuration())
            return;

        this.assignments = this.assignments.subList(0, this.getDuration() - hours);
    }

    public void adjustDuration(int hours) {
        if(hours < 0)
            shortenByHours(Math.abs(hours));
        else {
            Assignment last = this.assignments.get(this.assignments.size()-1);
            IntStream.range(1, hours + 1)
                    .forEach(hour -> {
                        Assignment a = last.copyAssignment();
                        a.setHour(a.getHour() + hour);
                        this.assignments.add(a);
                    });
        }
    }

    public void addUserGroup(String newGroup, float defaultTraffic) {
        this.assignments.stream()
                .forEach(assignment -> {
                    if(!assignment.getTrafficAssignment().containsKey(newGroup) || assignment.getTrafficAssignment().get(newGroup) == 0F) {
                        assignment.getTrafficAssignment().put(newGroup, defaultTraffic);
                    }
                });
    }

    public void addUserGroupRange(String newGroup, float defaultTraffic, int fromSlot, int toSlot) {
        this.assignments.stream()
                .filter(assignment -> assignment.getHour() >= fromSlot && assignment.getHour() <= toSlot)
                .forEach(assignment -> {
                    if(!assignment.getTrafficAssignment().containsKey(newGroup) || assignment.getTrafficAssignment().get(newGroup) == 0F) {
                        assignment.getTrafficAssignment().put(newGroup, defaultTraffic);
                    }
                });
    }

    public void removeUserGroup(String group) {
        this.assignments.stream()
                .filter(assignment -> assignment.getTrafficAssignment().containsKey(group) && assignment.getTrafficAssignment().size() > 1)
                .forEach(assignment -> assignment.getTrafficAssignment().remove(group));
    }

    public void removeUserGroupRange(String group, int fromSlot, int toSlot) {
        this.assignments.stream()
                .filter(assignment -> assignment.getHour() >= fromSlot && assignment.getHour() <= toSlot)
                .filter(assignment -> assignment.getTrafficAssignment().containsKey(group) && assignment.getTrafficAssignment().size() > 1)
                .forEach(assignment -> assignment.getTrafficAssignment().remove(group));
    }

    public List<String> getUserGroups() {
        return this.assignments.stream()
                .flatMap(assignment -> assignment.getTrafficAssignment().keySet().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getUserGroupsInRange(int fromSlot, int toSlot) {
        return this.assignments.stream()
                .filter(assignment -> assignment.getHour() >= fromSlot && assignment.getHour() <= toSlot)
                .flatMap(assignment -> assignment.getTrafficAssignment().keySet().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public void flipUserGroup(String old, String newGroup) {
        if(old.equals(newGroup))
            return;

        this.assignments.stream()
                .forEach(assignment -> assignment.flipUserGroup(old, newGroup)

//                {
//                    if(assignment.getTrafficAssignment().containsKey(newGroup))
//                        assignment.getTrafficAssignment().put(newGroup, assignment.getTrafficAssignment().get(newGroup) + assignment.getTrafficAssignment().get(old));
//                    else
//                        assignment.getTrafficAssignment().put(newGroup, assignment.getTrafficAssignment().get(old));
//
//                    assignment.getTrafficAssignment().remove(old); }
                    );
    }

    public float preferredUserGroupCoverage(List<String> userGroups, HashMap<Integer,HashMap<String,Integer>> trafficProfile) {
        if(userGroups == null || userGroups.size() == 0)
            return 0.0F;

//        return getAssignments().stream()
//                .map(assignment -> {
//                    return userGroups.stream().
//                            anyMatch(userGroup -> assignment.getTrafficAssignment().containsKey(userGroup) &&
//                                    assignment.getTrafficAssignment().get(userGroup) > 0.0F)
//                            ? 1.0F/getDuration() : 0.0F;
//                }).reduce(Float::sum).orElse(0.0F);

        return getAssignments().stream()
                .map(assignment -> {
                    return userGroups.stream().
                            anyMatch(userGroup -> assignment.getTrafficAssignment().containsKey(userGroup) &&
                                    isPrimaryUserGroup(assignment, userGroup, trafficProfile))
                            ? 1.0F/getDuration() : 0.0F;
                }).reduce(Float::sum).orElse(0.0F);
    }

    private boolean isPrimaryUserGroup(Assignment assignment, String userGroup, HashMap<Integer,HashMap<String, Integer>> trafficProfile) {
        float consumedTraffic = getTrafficAt(trafficProfile, assignment.getHour(), userGroup, assignment.getTrafficAssignment().get(userGroup));

        return assignment.getTrafficAssignment().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(userGroup))
                .allMatch(entry -> consumedTraffic > getTrafficAt(trafficProfile, assignment.getHour(), entry.getKey(), entry.getValue()));
    }

    private long getTrafficAt(HashMap<Integer, HashMap<String, Integer>> trafficProfile, int hour, String userGroup, float percentage) {
        return Math.round(trafficProfile.get(hour).get(userGroup) * percentage);
    }

    public void adjustTrafficConsumption(String userGroup, float change) {
        this.assignments.stream()
                .filter(assignment -> assignment.getTrafficAssignment().containsKey(userGroup))
                .forEach(assignment -> {
                    float newPercent = assignment.getTrafficAssignment().get(userGroup) + change;
                    if(newPercent < 0F)
                        newPercent = 0F;
                    else if(newPercent >1F)
                        newPercent = 1F;

                    assignment.getTrafficAssignment().put(userGroup, newPercent);
                });
    }

    public void adjustTrafficConsumption(int start, int endInclusive, String userGroup, float change) {
        IntStream.rangeClosed(start, endInclusive)
                .mapToObj(index -> this.assignments.get(index))
                .filter(assignment -> assignment.getTrafficAssignment().containsKey(userGroup)) // TODO: check what's better
                .forEach(assignment -> {
                    float newPercent = assignment.getTrafficAssignment().get(userGroup) + change;
                    if(newPercent < 0F)
                        newPercent = 0F;
                    else if(newPercent >1F)
                        newPercent = 1F;

                    assignment.getTrafficAssignment().put(userGroup, newPercent);
                });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;
        return getStartSlot() == schedule.getStartSlot() &&
                Objects.equals(getAssignments(), schedule.getAssignments());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getStartSlot(), getAssignments());
    }


}
