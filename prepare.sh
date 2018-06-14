#!/bin/bash

echo "======================================================================="
echo "| PREPARE EXPERIMENTATION PACKAGE"
echo "======================================================================="

echo "remove old bundle"
rm package.zip

echo "create package folder"
mkdir -p package

echo "remove previous content"
rm -rf package/*

echo "build application"
./gradlew jar

echo "prepare experimentation bundle..."
cp build/libs/fenrir-1.0-SNAPSHOT.jar package/.

cp fenrir.properties package/.

cp -r traffic_profiles package/.
cp -r experiments package/.
cp runner.sh package/.
cp stepwise.sh package/.
cp restart.sh package/.
cp -r restart package/.
cp bruteforce_15.sh package/.

echo "zipping bundle (package.zip)"
zip -r package.zip package

echo "done"
