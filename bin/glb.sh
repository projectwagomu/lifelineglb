#!/bin/bash

# Compile project
mvn package

# Change to directory of this script
cd "$(dirname "$0")"

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
        -Dapgas.elastic=malleable \
        -Dapgas.hostfile=$HOSTFILE \
        -Dmalleable_scheduler_ip=127.0.0.1 \
        -Dmalleable_scheduler_port=8081 \
        handist.glb.examples.syntheticBenchmark.StartSynthetic -b 0 -dynamic -g 30000 -t 6000 -u 20 &

sleep 15
#echo "##### INITATIATING MALLEABLE GROWTH #####"
#java -cp "../target/*" apgas.testing.MalleableOrder expand 2 $HOSTNAME $HOSTNAME
sleep 10
java -cp "../target/*" apgas.testing.MalleableOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
