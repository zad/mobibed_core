# inet_ex1.tcl
#
# Topology:
# node0 --- link0 --- node1

# Example root
mkdir drcl.comp.Component /example1
cd /example1

# Create nodes, link and connect
mkdir drcl.inet.Node node0 node1
mkdir drcl.inet.Link link0
connect -c node0/0@ -and link0/0@
connect -c node1/0@ -and link0/1@
! link0 setPropDelay .01; # 10ms

# Construct nodes
mkdir drcl.inet.core.PktDispatcher node0/pd node1/pd
mkdir drcl.inet.core.Identity node0/id node1/id
mkdir drcl.inet.core.queue.DropTail node0/q node1/q
mkdir drcl.inet.core.ni.PointopointNI node0/ni node1/ni
cd node0
connect -c pd/0@down -to q/up@
connect -c q/output@ -and ni/pull@
connect -c ni/down@ -to ./0@
connect -c ./0@ -to pd/0@down
! pd bind [! id]
cd ../node1
connect -c pd/0@down -to q/up@
connect -c q/output@ -and ni/pull@
connect -c ni/down@ -to ./0@
connect -c ./0@ -to pd/0@down 
! pd bind [! id]
cd ..
! .../ni setBandwidth 1.0e6; # 1Mbps

# Assign node addresses
! node0/id add 0
! node1/id add 1

# Open up raw IP ports for injecting/receiving packets
mkdir node0/pd/100@up node1/pd/100@up
mkdir drcl.comp.io.Stdout node1/stdout
connect -c node1/pd/100@up -to node1/stdout/in@

# Simple procedure to inject data at node0/pd/100@up
proc send data_ {
	set source_ 0
	set destination_ 1
	set routerAlert_ false
	set TTL_ 1
	set ToS_ 0
	set size_ 100
	set packet_ [java::call drcl.inet.contract.PktSending getForwardPack $data_ $size_ $source_ $destination_ $routerAlert_ $TTL_ $ToS_]
	inject $packet_ node0/pd/100@up
}

# Attaches simulator runtime
set sim [attach_simulator .]
puts Done!

