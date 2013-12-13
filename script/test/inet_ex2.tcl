# inet_ex2.tcl
#
# Test inet in following topology with TCP source on
# node h0 and sink on node h2
#
# Topology:
# 
# h0 ----- n1 ----- h2

cd [mkdir -q drcl.comp.Component /example2]

# create the topology
puts "create topology..."
set link_ [java::new drcl.inet.Link]
$link_ setPropDelay 0.3; # 300ms
set adjMatrix_ [java::new {int[][]} 3 {{1} {0 2} {1}}]
java::call drcl.inet.InetUtil createTopology [! .] $adjMatrix_ $link_

puts "create builders..."
# NodeBuilder:
set rb [mkdir drcl.inet.NodeBuilder .routerBuilder]
$rb setBandwidth 1.0e7; #10Mbps
set hb1 [cp $rb .hostBuilder1]
set hb2 [cp $rb .hostBuilder2]
# create TCP's at host builders
[mkdir drcl.inet.transport.TCP $hb1/tcp] setMSS 512; # bytes
mkdir drcl.inet.transport.TCPSink $hb2/tcpsink

puts "build..."
$rb build [! n?]
$hb1 build [! h0]
$hb2 build [! h2]

# Configure the bottleneck bandwidth and buffer size
! n1 setBandwidth 1 1.0e4; # 10Kbps at interface 1
! n1 setBufferSize 1 6000; # ~10 packets at interface 1

puts "Configure TCP's, source and sink..."
! h0/tcp setPeer 2

set src_ [mkdir drcl.inet.application.BulkSource h0/source]
$src_ setDataUnit 512
connect -c $src_/down@ -and h0/tcp/up@

set sink_ [mkdir drcl.inet.application.BulkSink h2/sink]
connect -c $sink_/down@ -and h2/tcpsink/up@

puts "setup static routes..."
java::call drcl.inet.InetUtil setupRoutes [! h0] [! h2] "bidirection"

puts "Set up TrafficMonitor & Plotter..."
set plot_ [mkdir drcl.comp.tool.Plotter .plot]
set tm_ [mkdir drcl.net.tool.TrafficMonitor .tm]
connect -c h2/csl/6@up -to $tm_/in@
connect -c $tm_/bytecount@ -to $plot_/0@0
connect -c h0/tcp/cwnd@ -to $plot_/0@1
connect -c h2/tcpsink/seqno@ -to $plot_/0@2
connect -c h0/tcp/srtt@ -to $plot_/0@3

# flags
setflag garbagedisplay true .../ni*

puts "simulation begins..."	
set sim [attach_simulator .]
run .
$sim stopAt 100
