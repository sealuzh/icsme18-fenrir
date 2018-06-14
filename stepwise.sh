#!/bin/bash

let repetitions=5
let generations=90
let population=40
let iterations=3000
let stepsize=5

profile="gitlab_12_months.csv"
mode="high"
resultFolder="stepwise"

echo "======================================================================="
echo "| START EXPERIMENTS"
echo "======================================================================="

echo "create result folder"
mkdir -p ${resultFolder}

awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="POPULATION_SIZE" newval="$population" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 
awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="NUM_GENERATIONS" newval="$generations" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 

for step in `seq 2 10`;
do
   let experiments=step*stepsize
   echo "Start with $experiments experiments ($mode):"
   echo "------------------------------"

   for run in `seq 1 ${repetitions}`;
   do
      echo "-> random sampling run $run"
      fname="randomSampling_${mode}_exp${experiments}_sample${population}_${run}"

      java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}_${mode}.json -t traffic_profiles/${profile} -randomSampling ${population} > ${resultFolder}/log_${fname}.txt 2>&1 
   
      mv consumption.csv ${resultFolder}/consumption_${fname}.csv
      mv visual.txt ${resultFolder}/visual_${fname}.csv
      mv schedule.json ${resultFolder}/schedule_${fname}.json

      echo "-> GA run $run"
      fname="GA_${mode}_exp${experiments}_pop${population}_gen${generations}_${run}"
      java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}_${mode}.json -t traffic_profiles/${profile} > ${resultFolder}/log_${fname}.txt 2>&1 
   
      mv stats.csv ${resultFolder}/stats_${fname}.csv
      mv consumption.csv ${resultFolder}/consumption_${fname}.csv
      mv visual.txt ${resultFolder}/visual_${fname}.csv
      mv schedule.json ${resultFolder}/schedule_${fname}.json

      echo "-> localSearch run $run"
      fname="localSearch_${mode}_exp${experiments}_it${iterations}_$run"
      java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}_${mode}.json -t traffic_profiles/${profile} -localSearch ${iterations} > ${resultFolder}/log_${fname}.txt 2>&1 
   
      mv stats.csv ${resultFolder}/stats_${fname}.csv
      mv consumption.csv ${resultFolder}/consumption_${fname}.csv
      mv visual.txt ${resultFolder}/visual_${fname}.csv
      mv schedule.json ${resultFolder}/schedule_${fname}.json

      echo "-> simulated annealing run $run"
      fname="SA_${mode}_exp${experiments}_it${iterations}_$run"
      java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}_${mode}.json -t traffic_profiles/${profile} -SA ${iterations} > ${resultFolder}/log_${fname}.txt 2>&1 
   
      mv stats.csv ${resultFolder}/stats_${fname}.csv
      mv consumption.csv ${resultFolder}/consumption_${fname}.csv
      mv visual.txt ${resultFolder}/visual_${fname}.csv
      mv schedule.json ${resultFolder}/schedule_${fname}.json

      echo "end run and remove population.json"
      rm population.json
   done
done
