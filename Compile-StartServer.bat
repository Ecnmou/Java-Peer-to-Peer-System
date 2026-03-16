@echo off

cd src

javac tracker\*.java peer\*.java model\*.java messaging\*.java

java tracker.ServerEntryPoint

pause
