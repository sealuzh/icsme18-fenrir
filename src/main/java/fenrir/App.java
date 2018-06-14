package fenrir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import fenrir.genetic.*;
import fenrir.misc.ConfigLoader;
import fenrir.misc.ProfileGenerator;
import fenrir.misc.SampleGenerator;

import java.io.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App implements ExperimentRunner{
    private static final SecureRandom rand = new SecureRandom();

    private static final RuntimeTypeAdapterFactory<Experiment> typeFactory = RuntimeTypeAdapterFactory
            .of(Experiment.class, "baseType")
            .registerSubtype(Experiment.class)
            .registerSubtype(GradualExperiment.class);

    public static final GsonBuilder gsonBuilder = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapterFactory(typeFactory)
            .enableComplexMapKeySerialization();

    // Hour : [UserGroup : NumRequests]
    private HashMap<Integer, HashMap<String, Integer>> trafficProfile;

    private List<String> userGroups;

    private PopulationProperties props = null;

    private PrintWriter statsWriter = null;

    private AtomicLong mutationCount = new AtomicLong(0L);
    private AtomicLong crossoverCount = new AtomicLong(0L);

    public static void main(String[] args) {
        CLI cli = new CLI();

        cli.parseArgs(args, new App());
    }

    private App() {
        ConfigLoader.load(Constants.class, "fenrir.properties");
    }


    private void startGenetic(List<Individual> population, long startTime) {
        System.out.println("population size: " + population.size());

        if(population == null || population.size() < Constants.POPULATION_SIZE)
            return;

        Set<Experiment> experiments = population.get(0).getExperiments();
        App.saveExperimentsAsJson(experiments, Constants.EXPERIMENT_OUTPUT_PATH);

        System.out.println("Start Population Size: " + population.size());

        props = new PopulationProperties(trafficProfile, getMinDurations(experiments), getMaxUserCoverageScore(experiments), getPrioritySum(experiments));

        try {
            statsWriter = new PrintWriter(Constants.STATS_OUTPUT_PATH);
            statsWriter.println("Generation,DurationScore,UserGroupScore,StartScore,Total");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        Individual bestStart = getBest(population);
        statsWriter.println(bestStart.getFitness(props).toCSV(0));

        List<Individual> result = evolve(new ArrayList<>(experiments), population, Constants.NUM_GENERATIONS, Constants.TARGET_FITNESS, Constants.POPULATION_SIZE);

        Individual best = getBest(result);
        long endTime = System.currentTimeMillis();

        System.out.println("Best at start: " + bestStart.getFitness(props).toString());
        saveStats(best, endTime - startTime, "genetic", Constants.NUM_GENERATIONS);

        App.savePopulationAsJson(population, Constants.POPULATION_OUTPUT_PATH);
        statsWriter.close();
    }

    private void shiftTrafficProfile(int newStart) {
        this.trafficProfile = new HashMap<>(this.trafficProfile.entrySet().stream()
                .filter(entry -> entry.getKey() >= newStart)
                .collect(Collectors.toMap((Map.Entry<Integer, HashMap<String, Integer>> e) -> e.getKey() - newStart, e -> e.getValue())));
    }

    private List<Individual> evolve(List<Experiment> experiments, List<Individual> population, int numGenerations, double targetFitness, int targetPopulationSize) {
        int generation = 0;

        System.out.println("Start Genetic Algorithm");
        final List<Individual> temp = new ArrayList<>(population);
        Set<Individual> newPopulation = null;

        while(generation < numGenerations && getPopulationStats(getFitnessList(temp)).getMax() < targetFitness) {

            List<Individual> childPopulation = Collections.synchronizedList(new ArrayList<>());

            IntStream.range(0, targetPopulationSize - Constants.ELITISM_SIZE).parallel()
                .forEach(item -> {
                    AtomicInteger ordinal = new AtomicInteger(0);
                    do {

                        Individual parent1 = fitnessProportionateSelection(temp);
                        Individual parent2;

                        do {
                            parent2 = fitnessProportionateSelection(temp);
                        } while (parent1.getId() == parent2.getId());

                        Set<Individual> children = crossover(parent1, parent2);
                        this.crossoverCount.addAndGet(children.size());

                        children.stream()
                                .map(individual -> mutate(individual, experiments))
                                .filter(individual -> individual.isValid(trafficProfile))
                                .forEach(individual -> {
                                    childPopulation.add(individual);
                                    ordinal.getAndIncrement();
                                });
                    }while(ordinal.get() < 1);
                });

            newPopulation = selectElite(childPopulation, targetPopulationSize - Constants.ELITISM_SIZE);
            newPopulation.addAll(selectElite(temp, targetPopulationSize - newPopulation.size()));

            temp.clear();
            temp.addAll(newPopulation);

            generation++;
            System.out.println("Generation " + generation);
            printPopulationStats(temp, generation);
            System.out.println("----------------------");
        }

        return temp;
    }

    private Individual getBest(List<Individual> population) {
        return population.stream()
                .sorted(Comparator.comparingDouble((Individual individual) -> individual.getFitness(props).getValue()).reversed())
                .findFirst().get();
    }

    private Fitness getBestFitness(List<Fitness> result) {
        return result.stream()
                .max(Comparator.comparingDouble(Fitness::getValue)).get();
//                .sorted(Comparator.comparingDouble(Fitness::getValue).reversed()).findFirst().get();

    }

    private void printPopulationStats(List<Individual> population, int generation) {
        List<Fitness> fitness = getFitnessList(population);
        DoubleSummaryStatistics stats = getPopulationStats(fitness);

        System.out.println("best = " + stats.getMax() + ", average = " + stats.getAverage());
        Fitness best = getBestFitness(fitness);
        System.out.println(best.toString());
        statsWriter.println(best.toCSV(generation));
    }

    private DoubleSummaryStatistics getPopulationStats(List<Fitness> population) {
        return population.stream()
                .map(Fitness::getValue)
                .mapToDouble(Float::doubleValue)
                .summaryStatistics();
    }

    private List<Fitness> getFitnessList(List<Individual> population) {
        return population.stream()
                .map(individual -> individual.getFitness(props))
                .collect(Collectors.toList());
    }

    private Individual mutate(Individual individual, List<Experiment> experiments) {
        if(rand.nextFloat() > Constants.MUTATION_PROBABILITY)
            return individual;

        int numMutations = Math.round(experiments.size() * Constants.MUTATION_SCOPE);
        this.mutationCount.addAndGet(numMutations);
        int count = 0;

        while(count < numMutations) {

            // get experiment to mutate
            Experiment e = experiments.get(rand.nextInt(experiments.size()));
            Schedule schedule = individual.getScheduledExperiments().get(e);

            MutationType type = MutationType.randomMutationType();

            int hours = 0, start = 0, end = 0;
            switch (type) {
                case MoveSchedule:
                    if (e.isRestarted()) // do not allow moving restarted/running experiments
                        break;

                    // move schedule by X hours
                    int shift = 1 + rand.nextInt(Constants.MUTATION_MOVE_BY_HOURS);
                    boolean backShift = rand.nextBoolean(); // move back or forward
                    schedule.moveByHours(shift, backShift);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case ShortenSchedule:
                    /* shorten schedule by X hours */
                    hours = 1 + rand.nextInt(Constants.MUTATION_SHORTEN_BY_HOURS);
                    schedule.adjustDuration(-hours);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case ExtendSchedule:
                    /* extend schedule by X hours */
                    hours = 1 + rand.nextInt(Constants.MUTATION_EXTEND_BY_HOURS);
                    schedule.adjustDuration(hours);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case FlipUserGroup:

                    // do not allow changing user groups of restarted business experiments
                    if (e.isRestarted() && e.isBusinessExperiment())
                        break;

                    /* flip user group of entire experiment */
                    String old = getRandomUserGroupFromAssignment(schedule.getAssignments()
                            .get(rand.nextInt(schedule.getAssignments().size())));
                    String newGroup = getRandomUserGroup(old);
                    schedule.flipUserGroup(old, newGroup);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case FlipUserGroupRange:
                    if (!e.isBusinessExperiment()) {
                        /* flip user group for some time slots */
                        start = rand.nextInt(schedule.getDuration());
                        end = start + rand.nextInt(schedule.getDuration() - start);

                        // get first time slot
                        Assignment a = schedule.getAssignments().get(start);

                        String oldGroup = getRandomUserGroupFromAssignment(a);
                        String newGroupName = getRandomUserGroup(oldGroup);

                        IntStream.rangeClosed(start, end)
                                .forEach(p -> schedule.getAssignments().get(p).flipUserGroup(oldGroup, newGroupName));
                        individual.increaseMutationCount();
                        count++;
                    }
                    break;

                case AddUserGroup:
                    // do not allow changing user groups of restarted business experiments
                    if (e.isRestarted() && e.isBusinessExperiment())
                        break;

                    /* add user group to entire schedule */
                    String randomGroup = userGroups.get(rand.nextInt(userGroups.size()));
                    schedule.addUserGroup(randomGroup, Constants.MIN_TRAFFIC_ADJUSTMENT);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case AddUserGroupRange:
                    // do not allow changing user groups of restarted business experiments
                    if (e.isRestarted() && e.isBusinessExperiment())
                        break;

                    /* add user group for some time slots */
                    start = rand.nextInt(schedule.getDuration());
                    end = start + rand.nextInt(schedule.getDuration() - start);

                    start += schedule.getStartSlot();
                    end += schedule.getStartSlot();

                    String group = userGroups.get(rand.nextInt(userGroups.size()));
                    schedule.addUserGroupRange(group, Constants.MIN_TRAFFIC_ADJUSTMENT, start, end);
                    individual.increaseMutationCount();
                    count++;
                    break;

                case RemoveUserGroup:
                    // do not allow changing user groups of restarted business experiments
                    if (e.isRestarted() && e.isBusinessExperiment())
                        break;

                    List<String> usedGroups = schedule.getUserGroups();
                    schedule.removeUserGroup(usedGroups.get(rand.nextInt(usedGroups.size())));
                    individual.increaseMutationCount();
                    count++;
                    break;

                case RemoveUserGroupRange:
                    // do not allow changing user groups of restarted experiments
                    if (e.isRestarted() && e.isBusinessExperiment())
                        break;

                    /* remove user group for some time slots */
                    start = rand.nextInt(schedule.getDuration());
                    end = start + rand.nextInt(schedule.getDuration() - start);

                    start += schedule.getStartSlot();
                    end += schedule.getStartSlot();

                    List<String> groups = schedule.getUserGroupsInRange(start, end);
                    schedule.removeUserGroupRange(groups.get(rand.nextInt(groups.size())), start, end);
                    individual.increaseMutationCount();
                    count++;
                    break;

            }
            smarterTrafficAdjustment(e, schedule);
        }

        return individual;
    }

    private Map<Integer, Map<String,Float>> createSampleRatio(Schedule s) {

        Map<Integer, Map<String, Float>> ratio = new HashMap<>();

        Set<String> previous = null;
        for(int timeSlot = s.getStartSlot(); timeSlot < s.getStartSlot()+s.getDuration(); timeSlot++) {
            Assignment a = s.getAssignments().get(timeSlot - s.getStartSlot());
            Set<String> userGroups = a.getTrafficAssignment().keySet();

            if(previous == null || previous.size() != userGroups.size() || !previous.containsAll(userGroups)) {
                ratio.put(timeSlot, SampleGenerator.createSampleRatio(new ArrayList<>(userGroups)));
                previous = userGroups;
            }else if(previous.size() == userGroups.size() && previous.containsAll(userGroups)) {
                ratio.put(timeSlot, ratio.get(timeSlot - 1));
            }
        }
        return ratio;
    }


    private void smarterTrafficAdjustment(Experiment e, Schedule s) {
        Map<Integer, Map<String, Float>> ratio = createSampleRatio(s);

        s.getAssignments()
                .forEach(assignment -> {
                    long minTraffic = e.getMinTrafficAt(assignment.getHour() - s.getStartSlot(), s.getDuration());

                    Map<String,Float> required = ratio.get(assignment.getHour()).entrySet().stream()
                            .collect(Collectors.toMap((Map.Entry<String,Float> entry) -> entry.getKey(), (Map.Entry<String,Float> entry) -> entry.getValue() * minTraffic / trafficProfile.get(assignment.getHour()).get(entry.getKey())));

                    required.entrySet().stream()
                            .forEach(entry -> assignment.getTrafficAssignment().put(entry.getKey(), (float) Math.ceil(entry.getValue() * 1000) / 1000.0F));

                });
    }

    private long getTrafficAt(HashMap<Integer, HashMap<String, Integer>> trafficProfile, int hour, String userGroup, float percentage) {
        return Math.round(trafficProfile.get(hour).get(userGroup) * percentage);
    }

    public String getRandomUserGroupFromAssignment(Assignment assignment) {
        // get random user group
        int item = rand.nextInt(assignment.getTrafficAssignment().keySet().size());
        int i = 0;

        String entry = null;
        for(String group : assignment.getTrafficAssignment().keySet()) {
            if(i == item)
                entry = group;
            i++;
        }

        return entry;
    }

    private String getRandomUserGroup(String previous) {
        List<String> tmp = new ArrayList<>(userGroups);
        tmp.remove(previous);
        return tmp.get(rand.nextInt(tmp.size()));
    }

    private Set<Individual> crossover(Individual parent1, Individual parent2) {

        Set<Individual> children = new HashSet<>();

        Set<Experiment> experiments = parent1.getExperiments();
        if(rand.nextFloat() <= Constants.CROSSOVER_PROBABILITY) {
            Set<Experiment> coveredExperiments = new HashSet<>();

            LinkedList<Experiment> experimentQueue = new LinkedList<>(parent1.getExperiments());
            Collections.shuffle(experimentQueue);

            Individual child = new Individual(UUID.randomUUID());

            child.setCrossoverCount(parent1.getCrossoverCount().add(parent2.getCrossoverCount()).add(BigInteger.ONE));
            child.setMutationCount(parent1.getMutationCount().add(parent2.getMutationCount()));

            do {
                Experiment next = experimentQueue.getFirst();
                experimentQueue.remove();

                if(!coveredExperiments.contains(next)) {
                    float fitnessP1 = parent1.getFitnessOfExperiment(this.props, next, parent1.getScheduledExperiments().get(next)).getValue();
                    float fitnessP2 = parent2.getFitnessOfExperiment(this.props, next, parent2.getScheduledExperiments().get(next)).getValue();
                    Schedule s;

                    if(fitnessP1 > fitnessP2) {
                        s = cloneSchedule(parent1.getScheduledExperiments().get(next));
                    }else {
                        s = cloneSchedule(parent2.getScheduledExperiments().get(next));
                    }

                    child.getScheduledExperiments().put(next, s);
                    coveredExperiments.add(next);
                }

            }while(coveredExperiments.size() != experiments.size());
            children.add(child);
        }else {
            Individual child1 = new Individual(UUID.randomUUID(), parent1.getCrossoverCount(), parent1.getMutationCount());
            Individual child2 = new Individual(UUID.randomUUID(), parent2.getCrossoverCount(), parent2.getMutationCount());

            experiments.forEach(experiment -> {
                child1.getScheduledExperiments().put(experiment, cloneSchedule(parent1.getScheduledExperiments().get(experiment)));
                child2.getScheduledExperiments().put(experiment, cloneSchedule(parent2.getScheduledExperiments().get(experiment)));
            });

            children.add(child1);
            children.add(child2);
        }

        return children;
    }

    private Individual cloneIndividual(Individual individual) {
        Individual clone = new Individual(individual.getId(), individual.getCrossoverCount(), individual.getMutationCount());

        individual.getScheduledExperiments().entrySet().stream()
                .forEach(entry -> clone.getScheduledExperiments().put(entry.getKey(), cloneSchedule(entry.getValue())));

        return clone;
    }

    private Schedule cloneSchedule(Schedule s) {
        Schedule copy = new Schedule();
        copy.setStartSlot(s.getStartSlot());
        copy.setAssignments(new ArrayList<>());

        s.getAssignments().stream()
                .forEach(assignment -> {
                    copy.getAssignments().add(assignment.copyAssignment());
                });

        return copy;
    }

    private Set<Individual> selectElite(List<Individual> population, int size) {
        List<Individual> tmp = new ArrayList<>(population);

        return tmp.stream()
                .sorted(Comparator.comparingDouble((Individual individual) -> individual.getFitness(props).getValue()).reversed())
                .limit(size)
                .collect(Collectors.toSet());
    }

    private Individual tournamentSelection(List<Individual> population) {
        Individual best = null;

        for(int i = 0; i < Constants.TOURNAMENT_SIZE; i++) {
            Individual pick = population.get(rand.nextInt(population.size()));
            if(best == null || pick.getFitness(props).getValue() > best.getFitness(props).getValue()) {
                best = pick;
            }
        }

        return best;
    }

    // Returns the selected index based on the weights(probabilities)
    private Individual fitnessProportionateSelection(List<Individual> population) {
        // calculate the total fitness
        List<Double> fitnessList = population.stream()
                .map(individual -> individual.getFitness(props).getValue())
                .mapToDouble(Float::doubleValue).boxed().collect(Collectors.toList());

        double fitness_sum = fitnessList.stream().mapToDouble(Double::doubleValue).sum();

        // get a random value
        double value = rand.nextDouble() * fitness_sum;

        // locate the random value based on the fitness sums
        for(int i=0; i < fitnessList.size(); i++) {
            value -= fitnessList.get(i);
            if(value < 0) return population.get(i);
        }
        // when rounding errors occur, we return the last item's index
        return population.get(population.size() - 1);
    }

    private int getMaxUserCoverageScore(Set<Experiment> experiments) {
        return experiments.stream()
                .filter(experiment -> experiment.getPreferredUserGroup() != null && experiment.getPreferredUserGroup().size() > 0)
                .map(experiment -> experiment.getPriority())
                .reduce(Integer::sum).orElse(0);
    }

    public int getPrioritySum(Set<Experiment> experiments) {
        return experiments.stream()
                .mapToInt(Experiment::getPriority)
                .sum();
    }

    private Map<Integer,Integer> getMinDurations(Set<Experiment> experiments) {
        return experiments.stream()
                .collect(Collectors.toMap((Experiment e) -> e.getId(), (Experiment e) -> e.getMinDuration()));
    }

    private List<String> getUserGroups(HashMap<Integer, HashMap<String, Integer>> trafficProfile) {
        return trafficProfile.values().stream()
                .flatMap(entry -> entry.keySet().stream())
                .collect(Collectors.toList());
    }

    public static void savePopulationAsJson(List<Individual> population, String path) {
        if(population == null || path == null)
            return;

        try (Writer writer = new FileWriter(path)) {
            Gson gson = gsonBuilder.create();
            gson.toJson(population, writer);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private Individual prepareRestart(String individualPath, String newExperimentsPath, String discardedExperiments, int timeSlot) {
        if (individualPath == null)
            return null;

        Individual old = Individual.readIndividualFromJson(individualPath);

        if (old == null) {
            System.out.println("Could not read old schedule");
            return null;
        }

        Set<Integer> discardedIds;
        if (discardedExperiments != null && !discardedExperiments.equals("")) {
            discardedIds = Arrays.asList(discardedExperiments.split(" ")).stream().map(item -> Integer.parseInt(item)).collect(Collectors.toSet());
        } else
            discardedIds = new HashSet<>();

        Individual individual = new Individual(UUID.randomUUID());

        if (newExperimentsPath != null) {
            // add new experiments to individual without schedule
            App.readExperimentsFromJson(newExperimentsPath).stream()
                    .forEach(experiment -> individual.getScheduledExperiments().put(experiment, null));
        }

        // update experiments based on already consumed traffic
        old.getScheduledExperiments().entrySet().stream()
                .filter(entry -> !discardedIds.contains(entry.getKey().getId()) && entry.getValue().getStartSlot() + entry.getValue().getDuration() > timeSlot)
                .forEach(entry -> {
                    long consumedTraffic = entry.getValue().getAssignments().stream()
                            .filter(assignment -> assignment.getHour() < timeSlot)
                            .map(assignment -> assignment.getTrafficAssignment().entrySet().stream()
                                    .map(item -> getTrafficAt(trafficProfile, assignment.getHour(), item.getKey(), item.getValue()))
                                    .reduce(Long::sum).orElse(0L))
                            .reduce(Long::sum).orElse(0L);

                    int consumedHours = entry.getValue().getAssignments().stream()
                            .filter(assignment -> assignment.getHour() < timeSlot)
                            .mapToInt(a -> 1).sum();

                    System.out.println("before: " + entry.getKey().toString());
                    System.out.println(entry.getKey().getId() + ": " + consumedTraffic + " traffic --- " + consumedHours + " hours, totalTraffic: " + entry.getKey().getRequiredTotalTraffic());

                    boolean running = false;
                    Experiment o = entry.getKey();

                    if(consumedTraffic < o.getRequiredTotalTraffic() || consumedHours < o.getMinDuration()) {

                        Schedule schedule = old.getScheduledExperiments().get(o);
                        if (schedule.getStartSlot() < timeSlot)
                            running = true;

                        Experiment e = null;
                        int minDuration = consumedHours > o.getMinDuration() ? 0 : o.getMinDuration() - consumedHours;
                        long requiredTraffic = consumedTraffic >= o.getRequiredTotalTraffic() ? 0 : o.getRequiredTotalTraffic() - consumedTraffic;

                        if (!o.isGradual())
                            e = new Experiment(o.getId(), o.getType(), o.getTargetService(), minDuration, requiredTraffic, o.getPriority(), o.getPreferredUserGroup(), running);
                        else {
                            GradualExperiment g = (GradualExperiment) o;
                            float startTraffic = running ? o.getMinTrafficAt(timeSlot - schedule.getStartSlot(), schedule.getDuration()) : g.getStartTraffic();
                            e = new GradualExperiment(g.getId(), g.getType(), g.getTargetService(), minDuration, requiredTraffic, g.getPriority(), startTraffic, g.getPreferredUserGroup(), running);
                        }
                        System.out.println("after: " + e.toString());
                        individual.getScheduledExperiments().put(e, schedule);
                    }
                });

        return individual;
    }

    public static List<Individual> readPopulationFromJson(String path) {
        if(path == null)
            return null;

        File f = new File(path);
        if(!f.exists() || f.isDirectory())
            return null;

        Gson gson = gsonBuilder.create();
        try (JsonReader reader = new JsonReader(new FileReader(path))) {
            return gson.fromJson(reader, new TypeToken<List<Individual>>(){}.getType());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public static void saveExperimentsAsJson(Set<Experiment> experiments, String path) {
        if(experiments == null || path == null)
            return;

        try (Writer writer = new FileWriter(path)) {
            Gson gson = gsonBuilder.create();
            gson.toJson(experiments, new TypeToken<Set<Experiment>>(){}.getType(), writer);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static Set<Experiment> readExperimentsFromJson(String path) {
        if(path == null)
            return null;

        File f = new File(path);
        if(!f.exists() || f.isDirectory())
            return null;

        Gson gson = gsonBuilder.create();
        try (JsonReader reader = new JsonReader(new FileReader(path))) {
            return gson.fromJson(reader, new TypeToken<Set<Experiment>>(){}.getType());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private void saveStats(Individual best, long duration, String type, int value) {
        long second = (duration / 1000) % 60;
        long minute = (duration / (1000 * 60)) % 60;
        long hour = (duration / (1000 * 60 * 60)) % 24;
        long millis = duration % 1000;

        String time = String.format("%02d:%02d:%02d:%d", hour, minute, second, millis);

        Fitness bestFitness = best.getFitness(props);
        System.out.println("Best (" + type + "): " + bestFitness.toString());
        System.out.println("Total Runtime: " + time + " (" + duration / 1000 + " sec)");
        best.printASCIISchedule(Constants.ASCII_OUTPUT_PATH);
        best.exportTrafficConsumptionAsCsv(Constants.CONSUMPTION_OUTPUT_PATH, "service1", this.trafficProfile);

        Individual.saveIndividualAsJson(best, Constants.SCHEDULE_OUTPUT_PATH);

        try (FileWriter fw = new FileWriter(Constants.RESULT_LOG, true)) {
            if(type.startsWith("genetic")) {
                fw.write(best.getExperiments().size() + "," + type + "," + Constants.NUM_GENERATIONS + "," + Constants.POPULATION_SIZE + "," + bestFitness.getValue() + "," +
                        duration + "," + this.crossoverCount.get() + "," + this.mutationCount.get() + "," + bestFitness.getIndividualScoresCommaSeparated() + System.lineSeparator());
            }else {
                fw.write(best.getExperiments().size() + "," + type + "," + value + "," + 0 + "," + bestFitness.getValue() + "," +
                        duration + "," + this.crossoverCount.get() + "," + this.mutationCount.get() + "," + bestFitness.getIndividualScoresCommaSeparated() + System.lineSeparator());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void executeGenetic(String trafficProfilePath, String experimentPath, int numExperiments) {
        initialize(trafficProfilePath);

        if(experimentPath == null && numExperiments <= 0) {
            System.out.println("Invalid experiments or non-positive number of experiments specified");
            return;
        }

        Set<Experiment> experiments;
        if(experimentPath == null)
            experiments = SampleGenerator.createSampleExperiments(numExperiments, userGroups, Arrays.asList("service1"));
        else
            experiments = App.readExperimentsFromJson(experimentPath);

        if(experiments == null)
            return;

        long startTime = System.currentTimeMillis();

        long timePopulationStart = System.currentTimeMillis();

        List<Individual> entirePopulation;
        if(Constants.EVALUATION_RUN) {
            entirePopulation = App.readPopulationFromJson(Constants.POPULATION_OUTPUT_PATH);
            initializeCounters(entirePopulation);
            System.out.println("Note: Start with existing population");
        }else {
            entirePopulation = SampleGenerator.createPopulation(Constants.POPULATION_SIZE, experiments, userGroups, trafficProfile);
        }

        long timePopulationEnd = System.currentTimeMillis();
        System.out.println((timePopulationEnd - timePopulationStart) / 1000 + " seconds for sampling");

        startGenetic(entirePopulation, startTime);
    }

    @Override
    public void executeGenetic(String trafficProfile, int numExperiments) {
        executeGenetic(trafficProfile, null, numExperiments);
    }

    @Override
    public void executeGenetic(String trafficProfile, String experiments) {
        executeGenetic(trafficProfile, experiments, -1);
    }

    @Override
    public void executeGeneticRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardExperiments) {
        initialize(trafficProfile);

        if(restartAt <= 0) {
            System.out.println("Restart time slot must be positive");
            return;
        }

        long startTime = System.currentTimeMillis();

        Individual individual = prepareRestart(schedule, newExperiments, discardExperiments, restartAt);

        if(individual == null) {
            System.out.println("Restart of genetic algorithm failed.");
            return;
        }

        shiftTrafficProfile(restartAt);

        // create new population
        List<Individual> population;
        if(Constants.EVALUATION_RUN) {
            population = App.readPopulationFromJson(Constants.POPULATION_OUTPUT_PATH);
            initializeCounters(population);
            System.out.println("Note: Start with existing population");
        }else {
            population = SampleGenerator.createPopulationForRestart(Constants.POPULATION_SIZE, individual, userGroups, restartAt, this.trafficProfile);
        }

        startGenetic(population, startTime);
    }

    @Override
    public void executeRandomSampling(String trafficProfile, String experimentPath, int sampleSize) {
        initialize(trafficProfile);

        if(experimentPath == null) {
            System.out.println("Experiments not specified");
            return;
        }

        Set<Experiment> experiments = App.readExperimentsFromJson(experimentPath);

        if(experiments == null)
            return;

        long startTime = System.currentTimeMillis();

        List<Individual> entirePopulation = SampleGenerator.createPopulation(Constants.POPULATION_SIZE, experiments, userGroups, this.trafficProfile);

        props = new PopulationProperties(this.trafficProfile, getMinDurations(experiments), getMaxUserCoverageScore(experiments), getPrioritySum(experiments));

        Individual best = getBest(entirePopulation);

        long endTime = System.currentTimeMillis();

        saveStats(best, endTime - startTime, "random sampling", sampleSize );

        if(Constants.EVALUATION_RUN)
            App.savePopulationAsJson(entirePopulation, Constants.POPULATION_OUTPUT_PATH);
    }

    @Override
    public void executeRandomSamplingRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardedExperiments, int sampleSize) {
        initialize(trafficProfile);

        if(restartAt <= 0) {
            System.out.println("Restart time slot must be positive");
            return;
        }

        Individual individual = prepareRestart(schedule, newExperiments, discardedExperiments, restartAt);

        if(individual == null) {
            System.out.println("Restart of random sampling algorithm failed.");
            System.exit(1);
        }

        shiftTrafficProfile(restartAt);

        // create new population
        long startTime = System.currentTimeMillis();

        List<Individual> entirePopulation = SampleGenerator.createPopulationForRestart(Constants.POPULATION_SIZE, individual, userGroups, restartAt, this.trafficProfile);

        Set<Experiment> experiments = entirePopulation.get(0).getExperiments();

        props = new PopulationProperties(this.trafficProfile, getMinDurations(experiments), getMaxUserCoverageScore(experiments), getPrioritySum(experiments));

        Individual best = getBest(entirePopulation);

        long endTime = System.currentTimeMillis();

        saveStats(best, endTime - startTime, "random sampling restart", sampleSize);

        if(Constants.EVALUATION_RUN)
            App.savePopulationAsJson(entirePopulation, Constants.POPULATION_OUTPUT_PATH);
    }

    private Individual performLocalSearchOrSA(Individual individual, int iterations, boolean simulated_annealing) {

        try {
            statsWriter = new PrintWriter("stats.csv");
            statsWriter.println("Iteration,DurationScore,UserGroupScore,StartScore,Total");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }

        Individual best = cloneIndividual(individual);
        Fitness bestFitness = best.getFitness(props);
        List<Experiment> experiments = new ArrayList<>(individual.getExperiments());

        double temperature = Constants.SA_STARTING_TEMP;
        double decrease = Constants.SA_TEMP_DECREASE;

        Set<Double> increases = new HashSet<>();

        int counter = 0;
        while(counter < iterations) {
            Individual neighbor;
            do {
                neighbor = mutate(cloneIndividual(best), experiments);
            }while(!neighbor.isValid(this.trafficProfile));

            if(!simulated_annealing) {
                // perform local search
                Fitness neighborFitness = neighbor.getFitness(props);

                if (bestFitness.getValue() < neighborFitness.getValue()) {
                    increases.add((double) neighborFitness.getValue() - bestFitness.getValue());
                    best = neighbor;
                    bestFitness = neighborFitness;
                }
            }else {
                // perform simulated annealing
                double acceptProbability = Math.exp(-Math.abs(best.getFitness(props).getValue() - neighbor.getFitness(props).getValue()) / temperature);
                Fitness neighborFitness = neighbor.getFitness(props);

                if(bestFitness.getValue() < neighborFitness.getValue() || rand.nextDouble() < acceptProbability) {
                    best = neighbor;
                    bestFitness = neighborFitness;
                }
                temperature *= decrease;
            }

            statsWriter.println(bestFitness.toCSV(counter));
            counter++;
        }

        if(!simulated_annealing) {
            double avgIncrease = increases.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
            System.out.println("start temp: " + avgIncrease / -Math.log(0.8));
        }

        statsWriter.close();
        return best;
    }

    private void executeLocalSearchOrSA(String trafficProfile, String experimentPath, int iterations, boolean simulated_annealing) {
        initialize(trafficProfile);

        Set<Experiment> experiments = App.readExperimentsFromJson(experimentPath);

        if(experiments == null)
            return;

        long startTime = System.currentTimeMillis();

        List<Individual> entirePopulation;

        if(Constants.EVALUATION_RUN) {
            entirePopulation = App.readPopulationFromJson(Constants.POPULATION_OUTPUT_PATH);
            initializeCounters(entirePopulation);
            System.out.println("Note: Start with existing population");
        }else {
            entirePopulation = SampleGenerator.createPopulation(Constants.POPULATION_SIZE, experiments, userGroups, this.trafficProfile);
        }

        props = new PopulationProperties(this.trafficProfile, getMinDurations(experiments), getMaxUserCoverageScore(experiments), getPrioritySum(experiments));

        Individual bestStart = getBest(entirePopulation);

        System.out.println("Start " + (simulated_annealing ? "SA" : "local search"));
        Individual best = performLocalSearchOrSA(getBest(entirePopulation), iterations, false);

        long endTime = System.currentTimeMillis();

        System.out.println("Best at start: " + bestStart.getFitness(props).toString());
        saveStats(best, endTime - startTime, (simulated_annealing ? "SA" : "local search"), iterations);
    }

    private void executeLocalSearchOrSARestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardedExperiments, int iterations, boolean simulated_annealing) {
        initialize(trafficProfile);

        if(restartAt <= 0) {
            System.out.println("Restart time slot must be positive");
            return;
        }

        Individual individual = prepareRestart(schedule, newExperiments, discardedExperiments, restartAt);

        if(individual == null) {
            System.out.println("Restart of " + (simulated_annealing ? "SA" : "local search")  + "algorithm failed.");
            System.exit(1);
        }

        shiftTrafficProfile(restartAt);

        long startTime = System.currentTimeMillis();

        // create new population
        List<Individual> entirePopulation;
        if(Constants.EVALUATION_RUN) {
            entirePopulation = App.readPopulationFromJson(Constants.POPULATION_OUTPUT_PATH);
            initializeCounters(entirePopulation);
            System.out.println("Note: Start with existing population");
        }else {
            entirePopulation = SampleGenerator.createPopulationForRestart(Constants.POPULATION_SIZE, individual, userGroups, restartAt, this.trafficProfile);
        }

        Set<Experiment> experiments = entirePopulation.get(0).getExperiments();

        props = new PopulationProperties(this.trafficProfile, getMinDurations(experiments), getMaxUserCoverageScore(experiments), getPrioritySum(experiments));

        Individual best = performLocalSearchOrSA(getBest(entirePopulation), iterations, simulated_annealing);

        long endTime = System.currentTimeMillis();

        saveStats(best, endTime - startTime, (simulated_annealing ? "SA" : "local search") + " restart", iterations);
    }

    @Override
    public void executeLocalSearch(String trafficProfile, String experimentPath, int iterations) {
        executeLocalSearchOrSA(trafficProfile, experimentPath, iterations, false);
    }

    @Override
    public void executeLocalSearchRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardedExperiments, int iterations) {
        executeLocalSearchOrSARestart(trafficProfile, schedule, newExperiments, restartAt, discardedExperiments, iterations, false);
    }

    @Override
    public void executeSA(String trafficProfilePath, String experiments, int iterations) {
        executeLocalSearchOrSA(trafficProfilePath, experiments, iterations, true);
    }

    @Override
    public void executeSARestart(String trafficProfilePath, String schedule, String newExperiments, int restartAt, String discardExperiments, int iterations) {
        executeLocalSearchOrSARestart(trafficProfilePath, schedule, newExperiments, restartAt, discardExperiments, iterations, true);
    }

    private void initialize(String trafficProfilePath) {
        this.trafficProfile = ProfileGenerator.readFromCsv(trafficProfilePath);

        if(this.trafficProfile == null) {
            System.out.println("Could not read traffic profile");
            System.exit(1);
        }

        this.userGroups = getUserGroups(this.trafficProfile);

        // clear "console"
        clearVisualRepresentation();
    }

    private void clearVisualRepresentation() {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter("visual.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            pw.close();
        }
    }

    private void initializeCounters(List<Individual> population) {
        population.forEach(individual -> {
            individual.setCrossoverCount(BigInteger.ZERO);
            individual.setMutationCount(BigInteger.ZERO);
        });
    }
}
