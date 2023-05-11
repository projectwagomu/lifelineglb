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
        -Dapgas.verbose.launcher=true \
        -Dapgas.places=4 \
        -Dapgas.threads=8 \
        -Dapgas.immediate.threads=8 \
        -Dapgas.elastic=malleable \
        -Dapgas.hostfile=$HOSTFILE \
        -Dapgas.consoleprinter=false \
        -Dglb.multiworker.workerperplace=4 \
        -Dglb.multiworker.n=127 \
        -Dmalleable_scheduler_ip=127.0.0.1 \
        -Dmalleable_scheduler_port=8081 \
        handist.glb.examples.bc.StartBC -n 15 &

sleep 10
echo "##### INITATIATING MALLEABLE GROWTH #####"
java -cp "../target/*" apgas.testing.MalleableOrder expand 2 $HOSTNAME $HOSTNAME

echo "##### INITATIATING MALLEABLE SHRINK #####"
sleep 10
java -cp "../target/*" apgas.testing.MalleableOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
