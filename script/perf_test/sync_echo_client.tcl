mkdir drcl.inet.transport.SyncEchoCP tcp
mkdir drcl.inet.host.SimpleMobibedSocket client

! client connectServer "mojo.cs.jhu.edu" 9418

connect -c tcp/down@ -and client/up@ 
# Attaches mobibed runtime
attach_mobibed .
run .
