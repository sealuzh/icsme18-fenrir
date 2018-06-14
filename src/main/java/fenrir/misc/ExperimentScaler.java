package fenrir.misc;

import fenrir.App;
import fenrir.Experiment;
import fenrir.GradualExperiment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.*;

public class ExperimentScaler {
    private static final String DEFAULT_OUT_FOLDER = "experiments";
    private static final SecureRandom rand = new SecureRandom();


    public static void main(String[] args) {
        if(args.length == 4) {
            new ExperimentScaler().scale(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3]);
        }
    }

    private ExperimentScaler() {

    }

    private void scale(String path, int stepSize, int stopSize, String suffix) {
        List<Experiment> input = new ArrayList<>(App.readExperimentsFromJson(path));

        if(input == null) {
            System.out.println("could not read experiments.");
            return;
        }

        input.sort(Comparator.comparingInt(Experiment::getId));

        int numExperiments = input.size();

        List<Experiment> experiments = new ArrayList<>(input);

        while(numExperiments < stopSize) {
            int newId = numExperiments + 1;

            Experiment e = input.get(numExperiments % input.size());
            if(e.isGradual()) {
                GradualExperiment g = (GradualExperiment)e;
                experiments.add(new GradualExperiment(newId, g.getType(), g.getTargetService(), g.getMinDuration(), g.getRequiredTotalTraffic(), 1 + rand.nextInt(10), g.getStartTraffic(), g.getPreferredUserGroup()));
            }else {
                experiments.add(new Experiment(newId, e.getType(), e.getTargetService(), e.getMinDuration(), e.getRequiredTotalTraffic(), 1 + rand.nextInt(10), e.getPreferredUserGroup()));
            }
            numExperiments++;
        }

        for(int i=input.size() + stepSize; i <= stopSize; i += stepSize) {
            App.saveExperimentsAsJson(new HashSet<>(experiments.subList(0, i)), DEFAULT_OUT_FOLDER + "/experiments_" + i + (suffix.equals("") ? "" : "_" + suffix) + ".json");
        }

    }


}
