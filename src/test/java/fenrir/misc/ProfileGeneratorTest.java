package fenrir.misc;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ProfileGeneratorTest {

    @Test
    public void readFromCsv() {
        ProfileGenerator p = new ProfileGenerator();

        List<Integer> reqPerHour = p.readProfile("traffic_profiles/profile_gitlab_7_months.csv");

        List<String> userGroups = Arrays.asList("group1", "group2", "group3", "group4", "group5");
        List<Float> userGroupDist = Arrays.asList(0.4F, 0.1F, 0.2F, 0.15F, 0.15F);
        float controlGroupSize = 0.1F;

        HashMap<Integer, HashMap<String, Integer>> trafficProfile = p.createTrafficProfile(reqPerHour, userGroups.size(), userGroups, userGroupDist, controlGroupSize);

        p.saveAsCsv(trafficProfile, "gitlab_test.csv");
        HashMap<Integer, HashMap<String, Integer>> trafficProfile2 = ProfileGenerator.readFromCsv("gitlab_test.csv");
        assertEquals(trafficProfile.size(), trafficProfile2.size());

        for(Map.Entry<Integer, HashMap<String, Integer>> entry : trafficProfile.entrySet()) {
            assertEquals(trafficProfile2.containsKey(entry.getKey()), true);

            for(Map.Entry<String, Integer> traffic : entry.getValue().entrySet()) {
                assertEquals(trafficProfile2.get(entry.getKey()).containsKey(traffic.getKey()), true);
                assertEquals(trafficProfile2.get(entry.getKey()).get(traffic.getKey()), traffic.getValue());
            }
        }

    }

    @After
    public void tearDown() throws Exception {
        File f = new File("gitlab_test.csv");
        f.delete();

    }
}