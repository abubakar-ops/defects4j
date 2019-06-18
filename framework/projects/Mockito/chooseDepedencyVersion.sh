#!/bin/bash
# Gregory Gay (greg@greggay.com)
# Ensures the correct version of the byte-buddy dependency is used at compile time
# $1 = workDir

HERE=$(cd `dirname $0` && pwd)

cd $1
./gradlew dependencies >> tmpDepend.txt
if grep -q buddy tmpDepend.txt; then
    version=`cat tmpDepend.txt | grep buddy | cut -d: -f 3 | head -1`
    cp $HERE/byte-buddy/byte-buddy-$version.jar $1/compileLib/
fi
rm tmpDepend.txt
