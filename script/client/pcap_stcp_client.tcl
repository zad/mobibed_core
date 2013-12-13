# tcp_test.tcl

# Create nodes, link and connect
mkdir drcl.inet.application.BulkSource app
#mkdir drcl.inet.transport.STCP tcp
mkdir drcl.inet.transport.TCP tcp
#mkdir drcl.inet.mobibed.AndrSocket js 
mkdir drcl.inet.host.JavaSocket js
mkdir drcl.inet.tool.PCapTrace pcap
! tcp setPeer "mojo.cs.jhu.edu" 9418
#! tcp setPeer "mojo.cs.jhu.edu" 5001
# connect components
connect -c app/down@ -and tcp/up@
connect -c tcp/down@ -and js/up@

# set debug
setflag debug true -at sample tcp
#setflag debug true node0/app js pcap

# Attaches pcap trace
attach pcap/in@ -to js/pcap@

# Attaches simulator runtime
set sim [attach_simulator .]
run .
