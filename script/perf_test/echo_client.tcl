mkdir drcl.inet.host.SyncMobibedSocketEcho client

! client connectServer "localhost" 9418


# Attaches mobibed runtime
attach_mobibed .
run .
