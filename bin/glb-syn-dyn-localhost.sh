#!/bin/bash

thisDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Compile project
cd "${thisDir}/.."
mvn package

# Change to directory of this script
cd "${thisDir}"

# Prepare hostfile
HOSTNAME=`hostname`
HOSTFILE="hostfile"
echo $HOSTNAME > $HOSTFILE
echo $HOSTNAME >> $HOSTFILE
echo $HOSTNAME >> $HOSTFILE
echo $HOSTNAME >> $HOSTFILE

echo "Contents of the hostfile is:"
echo "<<<<"
cat $HOSTFILE
echo ">>>>"

# Launch a GLB program
MAINPRGM= java -cp "../target/*" \
        --add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED\
        -Dapgas.verbose.launcher=true \
        -Dapgas.places=4 \
        -Dapgas.threads=8 \
        -Dapgas.immediate.threads=8 \
        -Dapgas.elastic=malleable \
        -Dapgas.hostfile=$HOSTFILE \
        -Dapgas.consoleprinter=false \
        -Dglb.multiworker.workerperplace=4 \
        -Dmalleable_scheduler_ip=127.0.0.1 \
        -Dmalleable_scheduler_port=8081 \
        handist.glb.examples.syntheticBenchmark.StartSynthetic -b 0 -dynamic -g 30000 -t 6000 -u 20 &

sleep 15
echo "##### INITIATING MALLEABLE GROWTH #####"
java -cp "../target/*" apgas.impl.elastic.MalleableOrder grow 2 $HOSTNAME $HOSTNAME

echo "##### INITIATING MALLEABLE SHRINK #####"
sleep 10
java -cp "../target/*" apgas.impl.elastic.MalleableOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
