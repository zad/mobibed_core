# traffic5.tcl
# Scenario:
#       Source (SimpleTrace, trace from a file)
#          |
#          |
#          ---------------> TrafficMonitor ----> Plotter

# test root
cd [mkdir drcl.comp.Component /test]

# set up source
set src [mkdir drcl.net.traffic.SimpleTrace source]

$src load traffic5.txt
setflag loop true $src
$src setLoopPeriod 10.0

puts "Source: [$src info]"

set count [mkdir drcl.comp.tool.DataCounter counter]
connect -c $src/down@ -to $count/in@

# Traffic monitors and plotter
set monitor_ [mkdir drcl.comp.tool.CountMonitor .monitor]
set plot [mkdir drcl.comp.tool.Plotter .plot]
# connect monitors
#connect -c $src/.timer@ -to $monitor_/timer@src
connect -c $src/down@ -to $monitor_/down@src
# connect monitors to plotter
connect -c $monitor_/sizecount@ -to $plot/0@0

# set up simulator
set sim [attach_simulator .]

puts "Simulation running..."
run $src 
$sim stop 30
