#!/bin/bash

thisDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Compile project
cd "${thisDir}/.."
mvn package

# Change to directory of this script
cd "${thisDir}"

# Check for the presence of hostfile
HOSTFILE="tmp_hostfile"
if [ ! -f hostfile ]
then
	echo "You need to prepare a file called hostfile containing 6 hosts to be use to test this program"
	exit -1
fi


echo "Contents of the temporary hostfile to launch the program are:"
echo "<<<<"
head -n 4 hostfile > $HOSTFILE
cat $HOSTFILE
echo ">>>>"

HOST_FOR_GROW1=`tail -n 2 hostfile | head -n 1`
HOST_FOR_GROW2=`tail -n 1 hostfile`

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
java -cp "../target/*" apgas.impl.elastic.MalleableOrder grow 2 $HOST_FOR_GROW1 $HOST_FOR_GROW2

echo "##### INITIATING MALLEABLE SHRINK #####"
sleep 10
java -cp "../target/*" apgas.impl.elastic.MalleableOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
