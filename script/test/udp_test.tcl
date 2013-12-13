# udp_test.tcl
# send udp packet from node0 to node1


# Example root
mkdir drcl.comp.Component /example1
cd /example1

# Create nodes, link and connect
mkdir drcl.inet.Node node0 
mkdir drcl.inet.application.SUDPApplication node0/app
mkdir drcl.inet.transport.UDP node0/udp
mkdir drcl.inet.host.JSocket node0/js 


mkdir drcl.inet.Node node1 
mkdir drcl.inet.transport.UDP node1/udp
mkdir drcl.inet.host.JSocket node1/js 
! node1/js setPortOn 9418

# connect components
connect -c node0/app/down@ -to node0/udp/0@up
connect -c node0/udp/down@ -to node0/js/up@

connect -c node1/js/up@ -to node1/udp/down@


# Attaches simulator runtime
set sim [attach_simulator .]
run .
#send "test"
! node0/app send "test" "127.0.0.1" 9418
