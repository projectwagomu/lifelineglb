#!/bin/bash

thisDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Compile apgas
cd "${thisDir}/../../apgas-for-java"
#I am using branch 1-jonas-bugfixing, commit f3985633b6f0b1b00f582ce1b0359b9aa1cc72bc
mvn install -DskipTests

# Compile project
cd "${thisDir}/.."
mvn package

# Change to directory of this script
cd "${thisDir}/.."

# Prepare hostfile
HOSTNAME=`hostname`
HOSTFILE="hostfile"
echo $HOSTNAME > $HOSTFILE

echo "Contents of the hostfile is:"
echo "<<<<"
cat $HOSTFILE
echo ">>>>"

# Launch a GLB program
MAINPRGM= java -cp "target/*" \
     --add-modules java.se \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED  \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.management/sun.management=ALL-UNNAMED \
     --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
     -Dapgas.verbose.launcher=true \
     -Dapgas.consoleprinter=true \
     -Dapgas.places=1 \
     -Dapgas.threads=4 \
     -Dapgas.immediate.threads=4 \
     -Dapgas.elastic=malleable \
     -Dapgas.hostfile=$HOSTFILE \
     -Dglb.multiworker.workerperplace=2 \
     -Dmalleable_scheduler_ip=127.0.0.1 \
     -Dmalleable_scheduler_port=8081 \
     handist.glb.examples.uts.StartMultiworkerUTS -d 14 &

sleep 10
echo "##### INITIATING MALLEABLE GROWTH #####"
java -cp "target/*" apgas.impl.elastic.MalleableOrder grow 1 $HOSTNAME

wait $MAINPRGM

rm $HOSTFILE
