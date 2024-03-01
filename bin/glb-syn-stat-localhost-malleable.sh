#!/bin/bash

CWD="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

# Compile project
cd "${CWD}/.."
mvn package

# Change to directory of this script
cd "${CWD}"

# Prepare hostfile
HOSTNAME=$(hostname)
HOSTFILE="hostfile"
echo $HOSTNAME >$HOSTFILE
echo $HOSTNAME >>$HOSTFILE
echo $HOSTNAME >>$HOSTFILE
echo $HOSTNAME >>$HOSTFILE

echo "Contents of the hostfile is:"
echo "<<<<"
cat $HOSTFILE
echo ">>>>"

# Launch a GLB program
MAINPRGM= java -cp "../target/*" \
  --add-modules java.se \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.management/sun.management=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  -Dapgas.verbose.launcher=true \
  -Dapgas.places=4 \
  -Dapgas.threads=8 \
  -Dapgas.immediate.threads=8 \
  -Dapgas.elastic=malleable \
  -Dapgas.hostfile=$HOSTFILE \
  -Dapgas.consoleprinter=false \
  -Dglb.multiworker.workerperplace=4 \
  -Dglb.multiworker.n=127 \
  -Delastic_scheduler_ip=127.0.0.1 \
  -Delastic_scheduler_port=8081 \
  handist.glb.examples.syntheticBenchmark.StartSynthetic -b 0 -static -g 50000 -t 6000 -u 20 &

sleep 15
echo "##### INITIATING MALLEABLE GROWTH #####"
java -cp "../target/*" apgas.impl.elastic.ElasticOrder grow 2 $HOSTNAME $HOSTNAME

sleep 10
echo "##### INITIATING MALLEABLE SHRINK #####"
java -cp "../target/*" apgas.impl.elastic.ElasticOrder shrink 1

wait $MAINPRGM

rm $HOSTFILE
