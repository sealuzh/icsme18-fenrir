package fenrir.misc;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class ProfileGenerator {
    public ProfileGenerator() {}

    public static void main(String[] args) {
        ProfileGenerator p = new ProfileGenerator();

        List<Integer> reqPerHour = p.readProfile(args[0]);

        List<String> userGroups = Arrays.asList("group1", "group2", "group3", "group4", "group5");
        List<Float> userGroupDist = Arrays.asList(0.4F, 0.1F, 0.2F, 0.15F, 0.15F);
//        List<String> userGroups = Arrays.asList("group1", "group2");
//        List<Float> userGroupDist = Arrays.asList(0.8F, 0.2F);
        float controlGroupSize = 0.0F;

        HashMap<Integer, HashMap<String, Integer>> trafficProfile = p.createTrafficProfile(reqPerHour, userGroups.size(), userGroups, userGroupDist, controlGroupSize);

        p.saveAsCsv(trafficProfile, "traffic_profiles/constant_12_months.csv");
    }

    protected List<Integer> readProfile(String path) {
        ArrayList<Integer> profile = new ArrayList<>();

        try (Scanner scanner = new Scanner(new File(path))) {
            // skip header
            if(scanner.hasNextLine()) {
                scanner.nextLine();
            }

            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] arr = line.split(",");
                profile.add(Math.round(Float.parseFloat(arr[2])));
            }
        }catch(Exception e) {
            System.out.println(e.getStackTrace());
        }

        return profile;
    }

    protected void saveAsCsv(HashMap<Integer, HashMap<String, Integer>> trafficProfile, String path) {
        if(trafficProfile == null || trafficProfile.size() == 0)
            return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            List<String> userGroups = new ArrayList<String>(trafficProfile.get(0).keySet());
            Collections.sort(userGroups);

            // write header, take user groups at hour 0
            pw.println("hour," + String.join(",", userGroups));

            for(Map.Entry<Integer, HashMap<String, Integer>> entry : trafficProfile.entrySet()) {
                pw.println(entry.getKey() + "," +
                        String.join(",",
                                userGroups.stream().map(userGroup -> entry.getValue().get(userGroup).toString()).collect(Collectors.toList())));
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static HashMap<Integer, HashMap<String, Integer>> readFromCsv(String path) {
        if(path == null)
            return null;

        File f = new File(path);
        if(!f.exists() || f.isDirectory())
            return null;

        HashMap<Integer, HashMap<String, Integer>> trafficProfile = new HashMap<>();
        try (Scanner scanner = new Scanner(f)) {

            // read header
            if(!scanner.hasNextLine()) {
                return null;
            }
            String line = scanner.nextLine();
            String[] header = line.split(",");
            assert header[0].equals("hour") : "non-hour input, currently no other formats supported";

            // read content
            while(scanner.hasNextLine()) {
                HashMap<String, Integer> traffic = new HashMap<>();
                line = scanner.nextLine();
                String[] input = line.split(",");
                for(int i = 1; i < input.length; i++) {
                    traffic.put(header[i], Integer.parseInt(input[i]));
                }
                trafficProfile.put(Integer.parseInt(input[0]), traffic);
            }
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        return trafficProfile;
    }

    protected HashMap<Integer, HashMap<String, Integer>> createTrafficProfile(List<Integer> reqPerHour,
                                                                            int numUserGroups,
                                                                            List<String> userGroups,
                                                                            List<Float> userGroupDist, float controlGroupSize) {
        // todo: think about introducing some variation (in % per user group)

        // <Hour, Map<UserGroup, numRequests>>
        HashMap<Integer, HashMap<String, Integer>> trafficProfile = new HashMap<>();
        int hour = 0;
        for(Integer totalRequests : reqPerHour) {
            int requests = totalRequests - Math.round(totalRequests * controlGroupSize);

            HashMap<String, Integer> hourDist = new HashMap<>();
            for(int i = 0; i < userGroups.size(); i++) {
                String userGroup = userGroups.get(i);
                Float groupDist = userGroupDist.get(i);

                hourDist.put(userGroup, Math.round(requests * groupDist));
            }
            trafficProfile.put(hour++, hourDist);
        }

        return trafficProfile;
    }
}
