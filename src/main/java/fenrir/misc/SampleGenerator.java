package fenrir.misc;

import fenrir.Experiment;
import fenrir.ExperimentType;
import fenrir.GradualExperiment;
import fenrir.genetic.Assignment;
import fenrir.genetic.Individual;
import fenrir.genetic.Schedule;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SampleGenerator {
    private static final SecureRandom rand = new SecureRandom();

    public static Set<Experiment> createSampleExperiments(int numExperiments, List<String> userGroups, List<String> targetServices) {

        Set<Experiment> experiments = new HashSet<>();

        IntStream.rangeClosed(1, numExperiments)
                .forEach(i -> experiments.add(
                        // between 5 and 24 days
                        SampleGenerator.createSampleExperiment(i, 120 + rand.nextInt(240), userGroups, targetServices, 10, 20000000, 30000000)));
//        SampleGenerator.createSampleExperiment(i, 48, userGroups, targetServices, 10, 50000, 100000)));


        return experiments;
    }

    public static Experiment createSampleExperiment(int id, int minDuration, List<String> userGroups, List<String> targetServices, int maxPriority, int minTotalTraffic, int maxTotalTraffic) {
        int totalTraffic = minTotalTraffic + rand.nextInt(maxTotalTraffic - minTotalTraffic);

        if((rand.nextInt(3) + 1) % 3 == 0)
            return new GradualExperiment(
                    id,
                    ExperimentType.randomExperimentType(),
                    targetServices.get(rand.nextInt(targetServices.size())),
                    minDuration,
                    totalTraffic,
                    rand.nextInt(maxPriority + 1),
                    0.0001F * totalTraffic,
                    getPreferredUserGroups(userGroups)
            );
        else
            return new Experiment(
                    id,
                    ExperimentType.randomExperimentType(),
                    targetServices.get(rand.nextInt(targetServices.size())),
                    minDuration,
                    totalTraffic,
                    rand.nextInt(maxPriority + 1),
                    getPreferredUserGroups(userGroups));
    }

    public static List<String> getPreferredUserGroups(List<String> userGroups) {
        List<String> groups = new ArrayList<>(userGroups);
        Collections.shuffle(groups);

        if(rand.nextBoolean())
            return null;

        int num = 1 + rand.nextInt(2);

        return groups.subList(0, num);
    }

    public static List<String> getSubset(List<String> userGroups) {
        List<String> groups = new ArrayList<>(userGroups);
        Collections.shuffle(groups);

        int num;
//        if(rand.nextFloat() < 0.8)
        num = 1 + rand.nextInt(2); // rand.nextInt(userGroups.size());
//        else
//            num = 2 + rand.nextInt(1);

        return groups.subList(0, num);
    }

    public static Map<String,Float> createSampleRatio(List<String> userGroups) {
        HashMap<String,Float> ratio = new HashMap<>();

        int remaining = 100;
        int i;
        for(i = 0; i<userGroups.size() - 1;i++) {
            int v = rand.nextInt(remaining + 1);
            ratio.put(userGroups.get(i), v / 100.0F);
            remaining -= v;
        }
        ratio.put(userGroups.get(i), remaining / 100.0F);

        return ratio;
    }

    public static Schedule createSmartSampleSchedule(List<String> userGroups, Experiment e, int startTimeSlot, HashMap<Integer, HashMap<String, Integer>> trafficProfile, int numExperiments) {
        List<Assignment> assignments = new ArrayList<>();

        List<String> ug = getSubset(userGroups);

        int startHour = startTimeSlot + rand.nextInt(24*numExperiments*2); // experiments start within numExperiment days

        int duration = e.getMinDuration() + rand.nextInt(24 * numExperiments/2);

        Map<String, Float> ratio = createSampleRatio(ug);

        IntStream.range(startHour, startHour + duration)
                .forEach(timeSlot -> {
                    Assignment a = new Assignment();
                    a.setHour(timeSlot);
                    a.setTrafficAssignment(new HashMap<>());

                    long minTraffic = e.getMinTrafficAt(timeSlot - startHour, duration);

                    Map<String,Float> required = ratio.entrySet().stream()
                            .collect(Collectors.toMap((Map.Entry<String,Float> entry) -> entry.getKey(), (Map.Entry<String,Float> entry) -> entry.getValue() * minTraffic / trafficProfile.get(timeSlot).get(entry.getKey())));

                    required.entrySet().stream()
                            .forEach(entry -> a.getTrafficAssignment().put(entry.getKey(), (float) Math.ceil(entry.getValue() * 1000) / 1000.0F));

                    assignments.add(a);

//                    long available = trafficProfile.get(timeSlot).get(userGroup);
//                    HashMap<String, Float> traffic = new HashMap<>();
//                    traffic.put(userGroup, (float) Math.ceil(minTraffic * 1000 / (float) available) / 1000.F);
//                    a.setTrafficAssignment(traffic);

                });
        return new Schedule(startHour, assignments);
    }

    public static <E> E choice(Collection<? extends E> coll, SecureRandom random) {
        if (coll.size() == 0) {
            return null; // or throw IAE, if you prefer
        }

        int index = random.nextInt(coll.size());
        if (coll instanceof List) { // optimization
            return ((List<? extends E>) coll).get(index);
        } else {
            Iterator<? extends E> iter = coll.iterator();
            for (int i = 0; i < index; i++) {
                iter.next();
            }
            return iter.next();
        }
    }

    public static List<Individual> createPopulation(int size, Set<Experiment> experiments, List<String> userGroups, HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        if(experiments == null)
            return null;

        List<Individual> population = new ArrayList<>();
        int numExperiments = experiments.size();

        IntStream.range(0, size).parallel()
                .forEach(item -> {
                    Individual individual = new Individual(UUID.randomUUID());

                    LinkedList<Experiment> remaining = new LinkedList<>(experiments);
                    Collections.shuffle(remaining);

                    while(remaining.size() > 0) {
                        Experiment next = remaining.removeFirst();

                        Schedule s;
                        int counter=0;
                        int startTime = 0;
                        do {
                            s = createSmartSampleSchedule(userGroups, next, startTime, trafficProfile, numExperiments);
                            individual.getScheduledExperiments().put(next, s);

                            counter++;
                            if(counter % 10 == 0)
                                startTime += 24;
                        } while (!individual.isValid(trafficProfile));
                    }
                    population.add(individual);
                    System.out.println("Created individual " + (item + 1));
                });

        return population;
    }

    public static List<Individual> createPopulationForRestart(int size, Individual individual, List<String> userGroups, int timeSlot, HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        if(individual == null)
            return null;

        Set<Experiment> experiments = individual.getExperiments();
        List<Individual> population = new ArrayList<>();
        int numExperiments = experiments.size();

        IntStream.range(0, size).parallel()
                .forEach(item -> {
                    Individual i = new Individual(UUID.randomUUID());

                    LinkedList<Experiment> remaining = new LinkedList<>(experiments);
                    Collections.shuffle(remaining);

                    while(remaining.size() > 0) {
                        Experiment next = remaining.removeFirst();

                        Schedule s;
                        do {
                            s = createSampleScheduleFromExisting(next, individual.getScheduledExperiments().get(next), timeSlot, userGroups, item == 0, trafficProfile, numExperiments);
                            i.getScheduledExperiments().put(next, s);
                        } while (!i.isValid(trafficProfile));
                    }
                    population.add(i);
                    System.out.println("Created individual " + (item + 1));
                });

        return population;
    }

    private static Schedule createSampleScheduleFromExisting(Experiment experiment, Schedule schedule, int timeSlot, List<String> userGroups, boolean first, HashMap<Integer, HashMap<String, Integer>> trafficProfile, int numExperiments) {

        // new experiment
        if(schedule == null) {
            return createSmartSampleSchedule(userGroups, experiment, timeSlot, trafficProfile, numExperiments);
        }

        // running business experiment, take existing schedule and adapt to new time slots
        if((experiment.isRestarted() && experiment.isBusinessExperiment()) || first) {

//            int cut = timeSlot > schedule.getStartSlot() ? (timeSlot - schedule.getStartSlot()) : timeSlot;

            List<Assignment> assignments = schedule.getAssignments().stream()
                    .filter(assignment -> assignment.getHour() >= timeSlot)
                    .map(assignment -> {
                        Assignment n = new Assignment();

                        n.setHour(assignment.getHour() - timeSlot);
                        n.setTrafficAssignment(new HashMap<>());

                        assignment.getTrafficAssignment().entrySet().stream()
                                .forEach(entry -> n.getTrafficAssignment().put(entry.getKey(), entry.getValue() + 0.0001F)); // buffer value

                        return n;
                    })
                    .collect(Collectors.toList());

            return new Schedule(experiment.isRestarted() ? 0 : schedule.getStartSlot() - timeSlot, assignments);
        }

        return createSmartSampleSchedule(userGroups, experiment, timeSlot, trafficProfile, numExperiments);
    }
}
