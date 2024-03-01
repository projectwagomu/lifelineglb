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
  -Dapgas.resilient=true \
  -Dapgas.backupcount=6 \
  -Dapgas.places=2 \
  -Dapgas.threads=8 \
  -Dapgas.immediate.threads=8 \
  -Dapgas.elastic=evolving \
  -Dapgas.evolving.mode=task \
  -Dapgas.lowload=10 \
  -Dapgas.highload=90 \
  -Dglb.synth=evotree \
  -Dglb.synth.branch=5000 \
  -Dapgas.hostfile=$HOSTFILE \
  -Dapgas.consoleprinter=false \
  -Dapgas.elastic.allatonce=false \
  -Dglb.multiworker.workerperplace=2 \
  -Delastic_scheduler_ip=127.0.0.1 \
  -Delastic_scheduler_port=8081 \
  handist.glb.examples.syntheticBenchmark.StartSynthetic -b 0 -dynamic -g 50000 -t 1000000 -u 20 &

wait $MAINPRGM

rm -f $HOSTFILE
