## Search-Based Scheduling of Experiments in Continuous Deployment - Online Appendix

This is the online appendix for our paper accepted at [ICSME'18](https://icsme2018.github.io/). It contains the source code of the implementations of a genetic algorithm, random sampling, local search, and simulated annealing. Further, it provides a pre-built replication package to replicate all our evaluation runs (on any machine running at least Java 8), and detailled information on how to use the tooling.

### Table of Contents
1. **[Source Code](#source-code)**<br>
2. **[Build Information](#build-information)**<br>
3. **[Run Information](#run-information)**<br>
4. **[Pre-Built Replication Package](#pre-built-replication-package)**<br>

### Source Code
The source code of our implementations can be found in the `src` subfolder. The only external dependency is the `GSON` library used for serializing/deserializing _JSON_ objects.

### Build Information
We rely on _Gradle_ as our build system. We ship the bundle with a _Gradle Wrapper_, i.e., the project can be built without having _Gradle_ installed locally. The wrapper automatically downloads the required _Gradle_ version and builds the project. <br>
Simply run `./gradlew build` to build the project, or run `./gradlew jar` to create a self-contained, executable _jar_ file.

### Run Information
The application reads parameters specified in `fenrir.properties` (e.g., population size, number of generations, crossover probability, starting temperature of SA) and parameters that are supplied on the command line. The following command line parameters exist:

Parameter | Optional | Description
------------ | -------------|---------------
-e | required | Experiments to be scheduled (.json file)
-t | optional | Traffic profile to be used as basis (.csv file)
-randomSampling #size | optional | Executes scheduling using random sampling with `#size` individuals instead of the GA
-localSearch #it | optional | Executes scheduling using local search with `#it` iterations instead of the GA
-SA #it | optional | Executes scheduling using simulated annealing with `#it` iterations instead of the GA

#### Output
Running the tool creates multiple resources:
1. The resulting schedule
1. Overview of the evolution of the fitness score
1. Scheduled traffic consumption throughout the experiment execution
1. ASCII representation of the resulting schedule

Once the chosen algorithm is finished, the resulting schedule is saved by default in a file `schedule.json`. Moreover, the individual scores for each generation or iteration are saved by default in a file `stats.csv`. An overview of how much traffic (sample data) the scheduled experiments consume throughout the schedule's execution (i.e., on an hourly basis) is provided by default in `consumption.csv`. Finally, the resulting schedule is visualized in ASCII format (e.g., see `restart/visual_GA_mid_exp30_pop40_gen90_4.txt` for a schedule of 30 experiments with medium _RESS_). The ASCII representation provides an overview of when a certain experiment starts and on which user groups. In a second step, it also lists how much traffic is consumed per user group per hour. <br>
An example for each of those files can be found in the `restart` subfolder.

For evaluation purposes (if the flag `evaluation_run` is set to `true` in the `fenrir.properties` file), the resulting population of the random sampling run is saved in a file `population.json`, which is then read by the other algorithms. If `evaluation_run` is set to `false`, then every algorithm execution creates its own (initial) population.

#### Reevaluation
Besides scheduling experiments from scratch, our approach also supports the reevaluation of existing schedules (i.e., taking into account experiments that get canceled, finished within the executed period, or experiments that need to be added to the schedule). <br>
Reevaluation is conducted by specifying the following command line parameters:

`-r {schedule} {restartAt} {newExperiments}` <br>
Then the provided `schedule` (i.e., the outcome of a previous run) is reevaluated at timestamp `restartAt` and optionally `newExperiments` specified in a `.json` file are added to the schedule. <br>
Further, the argument `-d [int]` allows discarding experiments. For example, `-d 2 3 6` in the context of a _reevaluation_ discards experiments with Ids 2, 3, and 6.

Every reevaluation run create the exact same output files as a standard run.

### Pre-Built Replication Package
To foster replication, we provide an already pre-built replication package containg all scripts that are required to re-execute our evaluation and a compiled version of our project (i.e., `fenrir-1.0-SNAPSHOT.jar`).

This involves the following scripts:
* `./bruteforce_15.sh`, first aspect of our evaluation: identifying the maximum fitness
* `./stepwise.sh`, second aspect: increase the number of experiments to schedule in a stepwise manner
* `./restart.sh`, third aspect: reevaluate a given schedule

The parameters in those scripts control, amongst others, for which variants the runs should be executed (e.g., low/medium/high RESS), how many repetitions should be conducted, on which traffic profile, and on how many experiments.

All results (e.g., the fitness scores, execution times) are collected in a CSV file `results.csv` containing elements in the following order: <br>
`NumberExperiments, TypeAlgorithm, NumGenerations/Iterations, PopulationSize/0, FitnessScore, ExecutionTime (in milliseconds), NumCrossoverOperations, NumMutationOperations, DurationScore, UserGroupScore, StartScore`

A single execution adds exactly one line to `results.csv`.

Example experiments that were used throughout our evaluation are provided in the `experiments` subfolder, traffic profiles can be found in the `traffic_profiles` subfolder. Furthermore, the script `runner.sh` was used for calibration purposes, it can be used to find optimal population sizes, number of generations, and number of iterations (for local search and simulated annealing). Calibration for other parameters such as crossover probability can be done in a similar manner, just reuse the existing snippets to modify the parameters as required.

Finally, the script `./prepare.sh` is used to create a new version of the replication package. It builds the project and includes the specified files & scripts into the resulting `zip` archive.
