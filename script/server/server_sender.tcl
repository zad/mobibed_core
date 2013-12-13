# tcp_test.tcl
mkdir drcl.inet.application.MBulkSource app

mkdir drcl.inet.transport.TCPb tcp
mkdir drcl.inet.host.SyncMobibedSocket sock 
#! sock setGlobalAddr 192.168.77.100

! app setDataSize 5024000
! tcp setLocalPort 9418
! tcp setImplementation "CUBIC"
! tcp setMSS 1400
! tcp setWindowScale 7
! tcp setSackEnabled true
! tcp setMaxSendBufferSize 10240000
#mkdir drcl.comp.io.FileWriter plot
#mkdir drcl.comp.io.FileWriter plot1
#mkdir drcl.comp.io.FileWriter plot2
# mkdir drcl.inet.tool.PCapTrace pcap


# connect components
connect -c sock/up@ -and tcp/down@
connect -c tcp/up@ -and app/down@

#connect -c tcp/cwnd@ -to plot/0@1
#connect -c tcp/sst@ -to plot1/0@1
#connect -c tcp/cwnd_cnt@ -to plot2/0@1

# setting for debug
#setflag debug true -at "cwnd cubic timeout sample send" tcp
setflag debug true app 
#setflag debug true sock
# Attaches pcap trace
#attach pcap/in@ -to sock/pcap@

# Attaches simulator runtime
attach_mobibed .
run .
