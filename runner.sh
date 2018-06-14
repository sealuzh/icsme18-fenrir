#!/bin/bash

let repetitions=6
let start_generation=30
let experiments=25

echo "======================================================================="
echo "| START EXPERIMENTS"
echo "======================================================================="

echo "create archive folder"
mkdir -p archive

for p in `seq 3 10`;
do
   let population=p*10
   echo "- POPULATION_SIZE=$population"
   awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="POPULATION_SIZE" newval="$population" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 

   for i in `seq 0 7`;
   do
      let generations=start_generation+i*10 
      echo " |-> NUM_GENERATIONS=$generations"

      awk 'BEGIN{ FS="=";OFS="=" } {if($1==pname) $2=newval; print $0;}' pname="NUM_GENERATIONS" newval="$generations" fenrir.properties > tmp.properties && mv tmp.properties fenrir.properties 

      for run in `seq 1 ${repetitions}`;
      do
         echo "     |-> run $run of $repetitions"
         echo "         processing archive/log_exp${experiments}_pop${population}_gen${generations}_${run}.txt"
         java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}.json -t traffic_profiles/gitlab_7_months.csv > archive/log_exp${experiments}_pop${population}_gen${generations}_${run}.txt 2>&1 

   #      java -jar build/libs/fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}.json -t traffic_profiles/gitlab_7_months.csv 2>&1 | tee archive/log_exp${experiments}_gen${i}_${run}.txt
         mv stats.csv archive/stats_exp${experiments}_pop${population}_gen${generations}_${run}.csv
         mv consumption.csv archive/consumption_exp${experiments}_pop${population}_gen${generations}_${run}.csv
         mv visual.txt archive/visual_exp${experiments}_pop${population}_gen${generations}_${run}.txt
      done
   done
done

echo "======================================================================="
echo "| EXECUTE LOCAL SEARCH"
echo "======================================================================="

for a in `seq 1 20`;
do
   let iterations=a*100
   echo " iteration: $iterations"
   for run in `seq 1 ${repetitions}`;
   do
      echo "  |-> processing archive/log_localSearch_it${iterations}_${run}.txt"
      java -jar fenrir-1.0-SNAPSHOT.jar -e experiments/experiments_${experiments}.json -t traffic_profiles/gitlab_7_months.csv -localSearch ${iterations} > archive/log_localSearch_it${iterations}_${run}.txt 2>&1

      mv stats.csv archive/stats_localSearch_it${iterations}_${run}.csv
      mv consumption.csv archive/consumption_localSearch_it${iterations}_${run}.csv
      mv visual.txt archive/visual_localSearch_it${iterations}_${run}.txt
   done
   
done
