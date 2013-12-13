mkdir drcl.inet.host.MobibedSocketEcho server
mkdir drcl.inet.host.MobibedSocketEcho client

! server setServer 9418
! client connectServer "localhost" 9418
# Attaches mobibed runtime
attach_mobibed .
run .
