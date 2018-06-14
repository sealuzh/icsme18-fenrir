#!/bin/bash

let repetitions=5
let generations=90
let population=40
let iterations=3000
let experiments=30
let restartAt=72

profile="gitlab_12_months.csv"
mode="mid"
discard="4 21 28"
schedule="restart/schedule_GA_mid_exp30_pop40_gen90_4.json"
newExperiments="restart/new_experiments.json"
resultFolder="results"

echo "======================================================================="
echo "| START EXPERIMENTS"
echo "======================================================================="

echo "create result folder"
mkdir -p ${resultFolder}

awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="POPULATION_SIZE" newval="$population" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 
awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="NUM_GENERATIONS" newval="$generations" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 

echo "Start with $experiments experiments ($mode):"
echo "------------------------------"

for run in `seq 1 ${repetitions}`;
do
   echo "-> random sampling run $run"
   fname="randomSampling_restart_${mode}_exp${experiments}_sample${population}_${run}"

   java -jar fenrir-1.0-SNAPSHOT.jar -r ${schedule} ${restartAt} ${newExperiments} -t traffic_profiles/${profile} -d ${discard} -randomSampling ${population} > ${resultFolder}/log_${fname}.txt 2>&1 

   mv consumption.csv ${resultFolder}/consumption_${fname}.csv
   mv visual.txt ${resultFolder}/visual_${fname}.txt
   mv schedule.json ${resultFolder}/schedule_${fname}.json

   echo "-> GA run $run"
   fname="GA_${mode}_restart_exp${experiments}_pop${population}_gen${generations}_${run}"
   java -jar fenrir-1.0-SNAPSHOT.jar -r ${schedule} ${restartAt} ${newExperiments} -t traffic_profiles/${profile} -d ${discard} > ${resultFolder}/log_${fname}.txt 2>&1 

   mv stats.csv ${resultFolder}/stats_${fname}.csv
   mv consumption.csv ${resultFolder}/consumption_${fname}.csv
   mv visual.txt ${resultFolder}/visual_${fname}.txt
   mv schedule.json ${resultFolder}/schedule_${fname}.json

   echo "-> localSearch run $run"
   fname="localSearch_restart_${mode}_exp${experiments}_it${iterations}_$run"
   java -jar fenrir-1.0-SNAPSHOT.jar -r ${schedule} ${restartAt} ${newExperiments} -t traffic_profiles/${profile} -d ${discard} -localSearch ${iterations} > ${resultFolder}/log_${fname}.txt 2>&1 


   mv stats.csv ${resultFolder}/stats_${fname}.csv
   mv consumption.csv ${resultFolder}/consumption_${fname}.csv
   mv visual.txt ${resultFolder}/visual_${fname}.txt
   mv schedule.json ${resultFolder}/schedule_${fname}.json

   echo "-> simulated annealing run $run"
   fname="SA_restart_${mode}_exp${experiments}_it${iterations}_$run"
   java -jar fenrir-1.0-SNAPSHOT.jar -r ${schedule} ${restartAt} ${newExperiments} -t traffic_profiles/${profile} -d ${discard} -SA ${iterations} > ${resultFolder}/log_${fname}.txt 2>&1 

   mv stats.csv ${resultFolder}/stats_${fname}.csv
   mv consumption.csv ${resultFolder}/consumption_${fname}.csv
   mv visual.txt ${resultFolder}/visual_${fname}.txt
   mv schedule.json ${resultFolder}/schedule_${fname}.json

   echo "end run and remove population.json"
   rm population.json
done
