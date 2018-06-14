package fenrir;

public interface ExperimentRunner {
    void executeGenetic(String trafficProfile, int numExperiments);
    void executeGenetic(String trafficProfile, String experiments);

    void executeGeneticRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardExperiments);

    void executeRandomSampling(String trafficProfile, String experiments, int sampleSize);
    void executeRandomSamplingRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardExperiments, int sampleSize);

    void executeLocalSearch(String trafficProfile, String experiments, int iterations);
    void executeLocalSearchRestart(String trafficProfile, String schedule, String newExperiments, int restartAt, String discardExperiments, int iterations);

    void executeSA(String trafficProfilePath, String experiments, int iterations);
    void executeSARestart(String trafficProfilePath, String schedule, String newExperiments, int restartAt, String discardExperiments, int iterations);
}
