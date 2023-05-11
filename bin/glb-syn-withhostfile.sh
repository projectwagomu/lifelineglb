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

HOST_FOR_EXPAND1=`tail -n 2 hostfile | head -n 1`
HOST_FOR_EXPAND2=`tail -n 1 hostfile`

# Launch a GLB program
MAINPRGM= java -cp "../target/*" \
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
echo "##### INITATIATING MALLEABLE GROWTH #####"
java -cp "../target/*" apgas.testing.MalleableOrder expand 2 $HOST_FOR_EXPAND1 $HOST_FOR_EXPAND2

echo "##### INITATIATING MALLEABLE SHRINK #####"
sleep 10
java -cp "../target/*" apgas.testing.MalleableOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
