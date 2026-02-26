CLIENT (Mobile App)                 CAMARA GATEWAY
Flutter / Dart                     (Gateway)
(Node 1)                            (Node 2)
 |                                      |
 | step 1 : auth code request           |
 |------------------------------------->|
 |                                      |
 | step 2 : auth code response          |
 |<-------------------------------------|
 |                                      |
 | step 3 : token request               |
 |------------------------------------->|
 |                                      |
 | step 4 : access token                |
 |<-------------------------------------|
 |                                      |
 | step 5 : verification w/num-token    |
 |------------------------------------->|
 |                                      |
 | step 6 : result                      |
 |<-------------------------------------|
 |                                      |
s
