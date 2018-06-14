package fenrir;

import org.apache.commons.cli.*;

public class CLI {
    public CLI() {}

    public void parseArgs(String[] args, ExperimentRunner runner) {
        Options options = new Options();
        Option experimentOption = Option.builder("e")
                .hasArg()
                .argName("json file")
                .desc("experiments").build();

        Option restartOption = Option.builder("r")
                .desc("restart {schedule} at {restartAt} include optional {new_experiments}")
                .hasArgs()
                .numberOfArgs(3)
                .optionalArg(true)
                .argName("schedule restartAt [new_experiments]").build();

        Option discardOption = Option.builder("d").hasArgs().desc("restart: discard experiments").argName("experimentIds").numberOfArgs(Option.UNLIMITED_VALUES).build();

        Option trafficOption = Option.builder("t").hasArg().desc("traffic profile").argName("trafficProfile").build();

        Option sampleOption = Option.builder("n").hasArg().argName("number of experiments").desc("creates {number of experiments} random experiments").build();

        Option randomSampling = Option.builder("randomSampling").hasArg().desc("random sampling with {sampleSize} sample size").argName("sampleSize").build();

        Option localSearch = Option.builder("localSearch").hasArg().desc("local search with {numIterations} iterations").argName("numIterations").build();

        Option simulatedAnnealing = Option.builder("SA").hasArg().desc("simulated annealing with {numIterations} iterations").argName("numIterations").build();

        options.addOption(experimentOption);
        options.addOption(restartOption);
        options.addOption(discardOption);
        options.addOption(trafficOption);
        options.addOption(sampleOption);
        options.addOption(randomSampling);
        options.addOption(localSearch);
        options.addOption(simulatedAnnealing);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            String profilePath = null;
            if(cmd.hasOption("t"))
                profilePath = cmd.getOptionValue("t");
            else
                profilePath = Constants.DEFAULT_PROFILE;

            if(cmd.hasOption("e") && !cmd.hasOption("r") && !cmd.hasOption("n") && !cmd.hasOption("randomSampling") && !cmd.hasOption("localSearch") && !cmd.hasOption("SA")) {
                runner.executeGenetic(profilePath, cmd.getOptionValue("e"));
                return;
            }else if(cmd.hasOption("n") && !cmd.hasOption("r") && !cmd.hasOption("randomSampling") && !cmd.hasOption("localSearch") && !cmd.hasOption("SA")) {
                runner.executeGenetic(profilePath, Integer.parseInt(cmd.getOptionValue("n")));
                return;
            }else if(cmd.hasOption("r")) {
                String[] restartArgs = cmd.getOptionValues("r");
                String discard = null;

                if(cmd.hasOption("d")) {
                    discard = String.join(" ", cmd.getOptionValues("d"));
                }

                if(restartArgs.length >= 2) {
                    int restartAt = Integer.parseInt(restartArgs[1]);
                    if(!cmd.hasOption("randomSampling") && !cmd.hasOption("localSearch") && !cmd.hasOption("SA")) {
                        runner.executeGeneticRestart(profilePath, restartArgs[0], restartArgs.length > 2 ? restartArgs[2] : null, restartAt, discard);
                        return;
                    }else if(cmd.hasOption("randomSampling")) {
                        int sampleSize = Integer.parseInt(cmd.getOptionValue("randomSampling"));
                        runner.executeRandomSamplingRestart(profilePath, restartArgs[0], restartArgs.length > 2 ? restartArgs[2] : null, restartAt, discard, sampleSize);
                        return;
                    }else if(cmd.hasOption("localSearch")) {
                        int iterations = Integer.parseInt(cmd.getOptionValue("localSearch"));
                        runner.executeLocalSearchRestart(profilePath, restartArgs[0], restartArgs.length > 2 ? restartArgs[2] : null, restartAt, discard, iterations);
                        return;
                    }else if(cmd.hasOption("SA")) {
                        int iterations = Integer.parseInt(cmd.getOptionValue("SA"));
                        runner.executeSARestart(profilePath, restartArgs[0], restartArgs.length > 2 ? restartArgs[2] : null, restartAt, discard, iterations);
                        return;
                    }
                }
            }else if(cmd.hasOption("randomSampling") && cmd.hasOption("e") && !cmd.hasOption("localSearch") && !cmd.hasOption("SA")) {
                int sampleSize = Integer.parseInt(cmd.getOptionValue("randomSampling"));
                runner.executeRandomSampling(profilePath, cmd.getOptionValue("e"), sampleSize);
                return;
            }else if(cmd.hasOption("localSearch") && cmd.hasOption("e") && !cmd.hasOption("SA")) {
                int iterations = Integer.parseInt(cmd.getOptionValue("localSearch"));
                runner.executeLocalSearch(profilePath, cmd.getOptionValue("e"), iterations);
                return;
            }else if(cmd.hasOption("SA") && cmd.hasOption("e")) {
                int iterations = Integer.parseInt(cmd.getOptionValue("SA"));
                runner.executeSA(profilePath, cmd.getOptionValue("e"), iterations);
                return;
            }
            System.out.println("Wrong usage");
            formatter.printHelp("fenrir", options);

        } catch (ParseException | NumberFormatException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("fenrir", options);
        }
    }
}
