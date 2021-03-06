# tcp_test.tcl
mkdir drcl.inet.application.BulkSink app
mkdir drcl.inet.transport.MTCPSink tcp
mkdir drcl.inet.host.JavaSocket js 
! tcp setLocalPort 9418

# connect components
connect -c js/up@ -and tcp/down@
connect -c tcp/up@ -and app/down@

# setting for debug
! app setDebugEnabled true
setflag debug true at "sample" tcp
! js setDebugEnabled true

# Attaches simulator runtime
set sim [attach_simulator .]
run js
