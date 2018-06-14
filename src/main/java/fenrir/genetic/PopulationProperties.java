package fenrir.genetic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PopulationProperties {
    public HashMap<Integer, HashMap<String, Integer>> trafficProfile;

    public int maxUserGroupCoverage;

    public Map<Integer, Integer> minDurations;

    public int prioritySum;

    public PopulationProperties(HashMap<Integer, HashMap<String, Integer>> trafficProfile, Map<Integer, Integer> minDurations, int maxUserGroupCoverage, int prioritySum) {
        this.trafficProfile = trafficProfile;
        this.minDurations = minDurations;
        this.maxUserGroupCoverage = maxUserGroupCoverage;
        this.prioritySum = prioritySum;
    }
}
