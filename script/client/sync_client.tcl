# mobile_receiver.tcl

# Create nodes, link and connect
mkdir drcl.inet.application.BulkSink app
#mkdir drcl.inet.transport.STCP tcp
mkdir drcl.inet.transport.TCPb tcp
mkdir drcl.inet.host.MobibedSocket sock
#mkdir drcl.inet.tool.PCapTrace pcap

# mkdir drcl.comp.tool.Plotter plot

! tcp setPeer "localhost" 9418
#! tcp setPeer "mojo.cs.jhu.edu" 9418
! tcp setMSS 1400
! tcp setSackEnabled true
! tcp setMaxReceiveBufferSize 110208
! tcp setWindowScale 7
#! tcp setDRWA true
# connect components
connect -c app/down@ -and tcp/up@
connect -c tcp/down@ -and sock/up@

# connect -c tcp/cwnd@ -to plot/0@1

# set debug
#setflag debug true -at "timeout sack rcv send out-of-order sample" tcp
setflag debug true app
#setflag debug true sock

# Attaches pcap trace
#attach pcap/in@ -to sock/pcap@

# Attaches mobibed runtime
attach_mobibed .
run .
