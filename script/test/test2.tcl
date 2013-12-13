# inet_ex1.tcl
#
# Topology:
# node0 --- link0 --- node1

# Example root
mkdir drcl.comp.Component /example1
cd /example1

# Create nodes, link and connect
mkdir drcl.inet.Node node0 node1
mkdir drcl.inet.host.JavaHostUnit node0/jhu node1/jhu
mkdir drcl.inet.core.Identity node0/id node1/id

cd node0
! jhu bind [! id]

cd ../node1
! jhu bind [! id]

cd ..
# Assign node addresses
! node0/id add 0
! node1/id add 1

# Open up raw IP ports for injecting/receiving packets
mkdir node0/jhu/10000@up node1/jhu/10000@up
mkdir drcl.inet.transport.TCP node0/tcp
! node0/tcp setMSS 512
mkdir drcl.comp.io.Stdout node1/stdout
connect -c node0/tcp -to node0/jhu/10000@up
connect -c node1/jhu/10000@up -to node1/stdout/in@

# Simple procedure to inject data at node0/jhu/10000@up
proc send data_ {
	set source_ 0
	set destination_ 1
	set routerAlert_ false
	set TTL_ 1
	set ToS_ 0
	set size_ 100
	set packet_ [java::call drcl.inet.contract.PktSending getForwardPack $data_ $size_ $source_ $destination_ $routerAlert_ $TTL_ $ToS_]
	inject $packet_ node0/jhu/10000@up
}

# Attaches simulator runtime
set sim [attach_simulator .]
puts Done!
run .
send "test"
