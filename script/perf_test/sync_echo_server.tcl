mkdir drcl.inet.transport.SyncEchoCP tcp
mkdir drcl.inet.host.SimpleMobibedSocket server

! server setServer 9418

connect -c tcp/down@ -and server/up@
# Attaches mobibed runtime
attach_mobibed .
run .
