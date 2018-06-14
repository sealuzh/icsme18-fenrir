package fenrir.genetic;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import fenrir.App;
import fenrir.Experiment;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Individual {
    private final UUID id;

    public UUID getId() {
        return id;
    }

    private HashMap<Experiment, Schedule> scheduledExperiments;

    private transient BigInteger crossoverCount;
    private transient BigInteger mutationCount;

    public Individual(UUID id) {
        this(id, new HashMap<>(), BigInteger.ZERO, BigInteger.ZERO);
    }

    public Individual(UUID id, HashMap<Experiment, Schedule> scheduledExperiments, BigInteger crossoverCount, BigInteger mutationCount) {
        this.id = id;
        this.scheduledExperiments = scheduledExperiments;
        this.crossoverCount = crossoverCount;
        this.mutationCount = mutationCount;
    }

    public Individual(UUID id, HashMap<Experiment, Schedule> scheduledExperiments) {
        this(id, scheduledExperiments, BigInteger.ZERO, BigInteger.ZERO);
    }

    public Individual(UUID id, BigInteger crossoverCount, BigInteger mutationCount) {
        this(id, new HashMap<>(), crossoverCount, mutationCount);
    }

    public HashMap<Experiment, Schedule> getScheduledExperiments() {
        return scheduledExperiments;
    }

    public void setScheduledExperiments(HashMap<Experiment, Schedule> scheduledExperiments) {
        this.scheduledExperiments = scheduledExperiments;
    }

    public BigInteger getCrossoverCount() {
        return crossoverCount;
    }

    public void setCrossoverCount(BigInteger crossoverCount) {
        this.crossoverCount = crossoverCount;
    }

    public BigInteger getMutationCount() {
        return mutationCount;
    }

    public void setMutationCount(BigInteger mutationCount) {
        this.mutationCount = mutationCount;
    }

    public void increaseMutationCount() {
        this.mutationCount = this.mutationCount.add(BigInteger.ONE);
    }

    public Set<Experiment> getExperiments() {
        return this.getScheduledExperiments().keySet();
    }

    public boolean isValid(HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        boolean validBusiness = hasValidBusinessExperiments();
//        if(!validBusiness)
//            System.out.println("valid business experiments: " + validBusiness);

        boolean notMoreThan100 = consumeNotMoreThan100Percent();
//        if(!notMoreThan100)
//            System.out.println("experiments consume not > 100%: " + notMoreThan100);

        boolean consumesEnoughTraffic = experimentsConsumeEnoughTraffic(trafficProfile);
//        if(!consumesEnoughTraffic)
//            System.out.println("experiments consume enough: " + consumesEnoughTraffic);

        boolean nonInterrupted = nonInterruptedExperiments();
//        if(!nonInterrupted)
//            System.out.println("non interrupted: " + nonInterrupted);

        return
            validBusiness && notMoreThan100 &&
            consumesEnoughTraffic && nonInterrupted;
    }

    public void printStats(PopulationProperties props) {
        float fitness = getFitness(props).getValue();

        System.out.println("Ind{" +
                "id=" + id +
                ", fitness = " + fitness +
                '}');
    }

    public boolean nonInterruptedExperiments() {
        return this.getScheduledExperiments().values().stream()
                .map(schedule -> !schedule.isInterrupted())
                .allMatch(b -> b);
    }

    public boolean hasValidBusinessExperiments() {
        Map<Experiment, Schedule> experiments = getScheduledBusinessExperiments();

        return experiments.entrySet().stream()
                .map(entry -> isValidBusinessExperiment(entry.getKey(), entry.getValue()))
                .allMatch(b -> b);
    }

    /**
     * Checks whether an experiment uses the same user groups throughout the entire schedule
     * @param e Experiment
     * @param s Schedule
     * @return true, if the same user groups are used throughout the entire schedule
     */
    public boolean isValidBusinessExperiment(Experiment e, Schedule s) {
        boolean sameGroups = s.getAssignments().stream()
                .map(assignment -> {
                    return assignment.getTrafficAssignment().keySet();
                })
                .allMatch(set -> {
                    return s.getAssignments().get(0).getTrafficAssignment().keySet().equals(set);
                });

        return sameGroups;
    }

    public boolean experimentsConsumeEnoughTraffic(HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        return this.scheduledExperiments.entrySet().stream()
                .map(entry -> consumesEnoughTraffic(entry.getKey(), entry.getValue(), trafficProfile))
                .allMatch(b -> b);
    }

    /**
     * Checks whether an experiment consumes at every time slot the specified minimum amount of traffic
     * @param e Experiment
     * @param s Schedule
     * @param trafficProfile TrafficProfile
     * @return true, if an experiment is scheduled to consume enough traffic
     */
    private boolean consumesEnoughTraffic(Experiment e, Schedule s, HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
//        System.out.println(s.getDuration() >= e.getMinDuration());
        return s.getDuration() >= e.getMinDuration() &&
                s.getAssignments().stream()
                .map(assignment -> {
//                    System.out.println("min traffic required: " + e.getMinTrafficAt(assignment.getHour() - s.getStartSlot(), s.getDuration()));
                            long trafficConsumed = assignment.getTrafficAssignment().entrySet().stream()
                                    .map(entry -> getTrafficAt(trafficProfile, assignment.getHour(), entry.getKey(), entry.getValue()))
//                            .map(entry -> getTrafficAt(s.getStartSlot(), entry.getKey(), entry.getValue()))
                                    .map(t -> {
//                                System.out.println("traffic consumed: " + t);
                                        return t;
                                    })
                                    .reduce(Long::sum).orElse(0L);
                            long minTraffic = e.getMinTrafficAt(assignment.getHour() - s.getStartSlot(), s.getDuration());
                            if(trafficConsumed < minTraffic)
                                System.out.println(String.format("Experiment %d @ slot %d: not enough traffic (%d <= %d)", e.getId(), assignment.getHour(), trafficConsumed, minTraffic));

                            return trafficConsumed >= minTraffic;
                        }
                ).allMatch(b -> b);
    }

    private long getTrafficAt(HashMap<Integer, HashMap<String, Integer>> trafficProfile, int hour, String userGroup, float percentage) {
        return Math.round(trafficProfile.get(hour).get(userGroup) * percentage);
    }

    private Map<Experiment, Schedule> getScheduledBusinessExperiments() {
        return this.scheduledExperiments.entrySet().stream()
                .filter(entry -> entry.getKey().isBusinessExperiment())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Checks whether the scheduled experiments do not consume more than 100% traffic per user group per service
     * @return true, if not more than 100% traffic is consumend for each user group and for every service
     */
    public boolean consumeNotMoreThan100Percent() {

//        System.out.println(this.scheduledExperiments.values().stream()
//                .flatMap(schedule -> schedule.getAssignments().stream())
//                .map(assignment -> assignment.getHour())
//                .reduce(Integer::max).get());

        Set<String> targetServices = this.scheduledExperiments.keySet().stream()
                .map(experiment -> experiment.getTargetService())
                .collect(Collectors.toSet());

//        System.out.println("targetServices: " + String.join(", ", targetServices));

        for (String targetService : targetServices) {

            Map<Experiment, Schedule> experiments = this.scheduledExperiments.entrySet().stream()
                    .filter(entry -> entry.getKey().getTargetService().equals(targetService))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // get all UserGroups involved in scheduled experiments
            Set<String> userGroups = experiments.entrySet().stream()
                    .flatMap(entry -> entry.getValue().getAssignments().stream())
                    .flatMap(assignment -> assignment.getTrafficAssignment().keySet().stream())
                    .collect(Collectors.toSet());

//            System.out.println("userGroups: " + String.join(", ", userGroups));

            for (String userGroup : userGroups) {
                int lastTimeSlot = experiments.entrySet().stream()
                        .flatMap(entry -> entry.getValue().getAssignments().stream())
                        .filter(assignment -> assignment.getTrafficAssignment().containsKey(userGroup))
                        .map(assignment -> assignment.getHour())
                        .reduce(Integer::max).get();

//                System.out.println("targetService = " + targetService + ", userGroup = " + userGroup + ", lastTimeSlot = " + lastTimeSlot);

                for (int i = 0; i <= lastTimeSlot; i++) {
                    int hour = i;
                    float totalTraffic =
                            experiments.entrySet().stream()
                                    .flatMap(entry -> entry.getValue().getAssignments().stream())
                                    .filter(assignment -> assignment.getTrafficAssignment().containsKey(userGroup))
                                    .filter(assignment -> assignment.getHour() == hour)
                                    .map(assignment -> assignment.getTrafficAssignment().get(userGroup))
                                    .reduce(Float::sum).orElse(0.0F);
//
                    if (totalTraffic > 1.0F) {
                        System.out.println(String.format("More than 100%% traffic at time slot %d, user group '%s', target service '%s'", hour, userGroup, targetService));
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Individual that = (Individual) o;
        return getId() == that.getId() &&
                Objects.equals(getScheduledExperiments(), that.getScheduledExperiments());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getId(), getScheduledExperiments());
    }

    public static void saveIndividualAsJson(Individual individual, String path) {
        if(individual == null || path == null)
            return;

        try (Writer writer = new FileWriter(path)) {
            Gson gson = App.gsonBuilder.create();
            gson.toJson(individual, writer);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static Individual readIndividualFromJson(String path) {
        if(path == null)
            return null;

        File f = new File(path);
        if(!f.exists() || f.isDirectory())
            return null;

        Gson gson = App.gsonBuilder.create();
        try (JsonReader reader = new JsonReader(new FileReader(path))) {
            return gson.fromJson(reader, Individual.class);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public void exportTrafficConsumptionAsCsv(String path, String targetService, HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        if(path == null)
            return;

        try (PrintWriter pw = new PrintWriter(path)) {
            // get all UserGroups involved in scheduled experiments
            TreeSet<String> userGroups = this.scheduledExperiments.entrySet().stream()
                    .flatMap(entry -> entry.getValue().getAssignments().stream())
                    .flatMap(assignment -> assignment.getTrafficAssignment().keySet().stream())
                    .collect(Collectors.toCollection(TreeSet::new));

            pw.println("hour,totalConsumption," + String.join(",", userGroups));
            IntStream.rangeClosed(0, getLastTimeSlot(this.scheduledExperiments, targetService))
                    .forEach(timeSlot -> {
                        Map<String, Long> groupedConsumption = this.scheduledExperiments.entrySet().stream()
                            .filter(entry -> entry.getKey().getTargetService().equals(targetService))
                            .filter(entry -> entry.getValue().getStartSlot() <= timeSlot && timeSlot < entry.getValue().getStartSlot() + entry.getValue().getDuration())
                            .flatMap(entry -> entry.getValue().getAssignments().stream())
                                .filter(assignment -> assignment.getHour() == timeSlot)
                                .flatMap(assignment -> assignment.getTrafficAssignment().entrySet().stream())
                                    .collect(Collectors.groupingBy(item -> item.getKey(), Collectors.summingLong(item -> getTrafficAt(trafficProfile, timeSlot, item.getKey(), item.getValue()))));
                        long totalConsumption = groupedConsumption.values().stream().mapToLong(Long::longValue).sum();

                        pw.println(timeSlot + "," + totalConsumption + ", " +
                                String.join(",", userGroups.stream().map(ug -> groupedConsumption.containsKey(ug) ? groupedConsumption.get(ug).toString() : "0").collect(Collectors.toList())));
                    });
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    private int getLastTimeSlot(Map<Experiment, Schedule> experiments, String targetService) {
        return experiments.entrySet().stream()
                .filter(entry -> entry.getKey().getTargetService().equals(targetService))
                .flatMap(entry -> entry.getValue().getAssignments().stream())
                .map(assignment -> assignment.getHour())
                .reduce(Integer::max).orElse(0);
    }

    public void printASCIISchedule(String path) {

        if(path == null || this.getScheduledExperiments().size() == 0)
            return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            pw.println("Individual id = " + this.getId());
            int numExperiments = getExperiments().size();

            Set<String> targetServices = this.scheduledExperiments.keySet().stream()
                    .map(experiment -> experiment.getTargetService())
                    .collect(Collectors.toSet());

            for (String targetService : targetServices) {

                Map<Experiment, Schedule> experiments = this.scheduledExperiments.entrySet().stream()
                        .filter(entry -> entry.getKey().getTargetService().equals(targetService))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                int maxUserGroups = experiments.entrySet().stream()
                        .flatMap(entry -> entry.getValue().getAssignments().stream())
                        .map(assignment -> assignment.getTrafficAssignment().keySet().size())
                        .reduce(Integer::max).get();
                if(maxUserGroups < 2)
                    maxUserGroups = 2;

                List<Experiment> orderedExperiments = new ArrayList<>(experiments.keySet());
                Collections.sort(orderedExperiments, (e1, e2) -> e1.getId() - e2.getId());

                int lastTimeSlot = getLastTimeSlot(experiments, targetService);
//                        experiments.entrySet().stream()
//                        .flatMap(entry -> entry.getValue().getAssignments().stream())
//                        .map(assignment -> assignment.getHour())
//                        .reduce(Integer::max).get();


                pw.print("   |");

                for(Experiment exp : orderedExperiments) {
                    pw.print(String.format("%s%1s%2d|",(exp.isGradual() ? "G" : "E"), (exp.isBusinessExperiment() ? "B" : ""), exp.getId()));

//                            (exp.isGradual() ? "G" : "E") + String.format("%1$2s ", exp.getId()));
//                    pw.print((exp.isGradual() ? "G" : "E") + String.format("%1$2s ", exp.getId()));
                }
                pw.println();
                for (int i = 0; i <= lastTimeSlot; i++) {
                    pw.print(String.format("%03d|", i));
                    for (Experiment exp : orderedExperiments) {
                        Schedule s = experiments.get(exp);
                        int hour = i;

                        if (hour >= s.getStartSlot() && hour < s.getStartSlot() + s.getDuration()) {
                            pw.print(String.format("%4s|",
                                    String.join("", s.getAssignments().stream()
                                            .filter(assignment -> assignment.getHour() == hour)
                                            .flatMap(assignment -> assignment.getTrafficAssignment().keySet().stream())
                                            .map(ug -> ug.substring(ug.length() - 1)) // todo: dirty hack
                                            .collect(Collectors.toList()))));

                        } else
                            pw.print("    |");
                    }
                    pw.println("");
                }

                pw.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
                pw.print("   |");
                int pad = 5*maxUserGroups + maxUserGroups-1 - 4;
                for(Experiment exp : orderedExperiments) {
                    pw.print(String.format(" %" + (pad/2 + (pad % 2 == 1 ? 1 : 0)) + "s%s%1s%2d%" + (pad/2) + "s |", " ", (exp.isGradual() ? "G" : "E"), (exp.isBusinessExperiment() ? "B" : ""), exp.getId(), " "));
                }
                pw.println();
                for (int i = 0; i <= lastTimeSlot; i++) {
                    pw.print(String.format("%03d|", i));
                    for (Experiment exp : orderedExperiments) {
                        Schedule s = experiments.get(exp);
                        int hour = i;

                        if (hour >= s.getStartSlot() && hour < s.getStartSlot() + s.getDuration()) {
                            pw.print(String.format(" %" + (5*maxUserGroups + maxUserGroups-1) + "s |",
                                    String.join(" ", s.getAssignments().stream()
                                            .filter(assignment -> assignment.getHour() == hour)
                                            .flatMap(assignment -> assignment.getTrafficAssignment().entrySet().stream())
                                            .map(entry -> String.format("%s:%03d",entry.getKey().substring(entry.getKey().length() -1), Math.round(entry.getValue()*1000)))
                                            .collect(Collectors.toList()))));

                        } else
                            pw.print(String.format(" %" + (5*maxUserGroups + maxUserGroups-1) + "s |", " "));
                    }
                    pw.println("");
                }
            }
            pw.println("----------------------------------------------------");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public Fitness getFitness(PopulationProperties props) {

        float durationScore = getDurationScore(props.minDurations, props.prioritySum);
        float userGroupScore = props.maxUserGroupCoverage > 0 ? (getPreferredUserGroupCoverage(props.trafficProfile) / props.maxUserGroupCoverage) : 1.0F;
        float startScore = getStartScore(props.prioritySum);

        return new Fitness(durationScore,userGroupScore, startScore);
    }

    public float getDurationScore(Map<Integer, Integer> minDurations, int prioritySum) {
        return this.getScheduledExperiments().entrySet().stream()
                .map(entry -> entry.getKey().getPriority() * minDurations.get(entry.getKey().getId()) / (float) entry.getValue().getDuration())
                .reduce(Float::sum).orElse(0F) / prioritySum;
    }

    public float getStartScore(int prioritySum) {
        return this.getScheduledExperiments().entrySet().stream()
                .map(entry -> entry.getKey().getPriority() / (float) (1 + entry.getValue().getStartSlot()))
                .reduce(Float::sum).orElse(0F) / prioritySum;
    }

    public float getPreferredUserGroupCoverage(HashMap<Integer,HashMap<String,Integer>> trafficProfile) {
        return this.getScheduledExperiments().entrySet().stream()
                .filter(entry -> entry.getKey().getPreferredUserGroup() != null && entry.getKey().getPreferredUserGroup().size() > 0)
                .map(entry -> entry.getValue().preferredUserGroupCoverage(entry.getKey().getPreferredUserGroup(), trafficProfile) * entry.getKey().getPriority())
                .reduce(Float::sum).orElse(0.0F);
    }

    public Fitness getFitnessOfExperiment(PopulationProperties props, Experiment e, Schedule s) {
        float durationScore = props.minDurations.get(e.getId()) / (float) s.getDuration();

        float userGroupScore = (e.getPreferredUserGroup() != null && e.getPreferredUserGroup().size() > 0) ? s.preferredUserGroupCoverage(e.getPreferredUserGroup(), props.trafficProfile) : 1.0F;

        float startScore = 1 / (float) (1 + s.getStartSlot());

        return new Fitness(durationScore, userGroupScore, startScore);
    }

    private float getTrafficOverheadScore(HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        List<Long> totalConsumption = new ArrayList<>();
        List<Long> totalMinimum = new ArrayList<>();

        for(Map.Entry<Experiment, Schedule> entry : this.getScheduledExperiments().entrySet()) {
            entry.getValue().getAssignments().stream()
                    .forEach(assignment -> {
                                totalConsumption.add(assignment.getTrafficAssignment().entrySet().stream()
                                        .map(item -> getTrafficAt(trafficProfile, assignment.getHour(), item.getKey(), item.getValue()))
                                        .reduce(Long::sum).orElse(0L));
                                totalMinimum.add(entry.getKey().getMinTrafficAt(assignment.getHour() - entry.getValue().getStartSlot(), entry.getValue().getDuration()));
                            }
                    );
        }
        return totalMinimum.stream().mapToLong(Long::longValue).sum() / (float) totalConsumption.stream().mapToLong(Long::longValue).sum();
    }


    public int getDuration() {
        return this.getScheduledExperiments().values().stream()
                .flatMap(schedule -> schedule.getAssignments().stream())
                .map(assignment -> assignment.getHour())
                .reduce(Integer::max).get() + 1;
    }
}
