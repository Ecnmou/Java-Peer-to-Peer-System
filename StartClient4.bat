@echo off

cd src

java peer.ClientEntryPoint my-port 9096 shared-dir "..\peer_shared_files\shared_files_peer_4" 

pause