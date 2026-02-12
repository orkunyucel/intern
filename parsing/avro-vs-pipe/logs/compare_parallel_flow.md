# Avro vs Pipe Parallel Flow (Run: 20260212_142843)

~~~mermaid
%%{init: {'flowchart': {'useMaxWidth': false, 'nodeSpacing': 70, 'rankSpacing': 80}}}%%
flowchart LR
  subgraph P["Pipe Senaryosu (Parallel C1 + C2)"]
    direction LR
    P1["Map/Array Obj (shared sabit)"] -->|"time=9803ms cpu_time=73460ms cpu_avg=3.9178% cpu_max=15.9741% gpu=N/A"| P2["Producer: pipe encode (250)"]
    P2 -->|"time=167227ms cpu_time=73460ms cpu_avg=3.9178% cpu_max=15.9741% gpu=N/A"| P3["Kafka Topic: RAW_PIPE_20260212_142843 (same broker)"]
    P3 -->|"time=4254ms cpu_time=69310ms cpu_avg=3.7086% cpu_max=14.4068% gpu=N/A"| P4["Consumer-1: split250"]
    P4 -->|"time=2557ms cpu_time=69310ms cpu_avg=3.7086% cpu_max=14.4068% gpu=N/A"| P5["Consumer-1: parse250 + select10"]
    P3 -->|"time=4269ms cpu_time=69970ms cpu_avg=3.7485% cpu_max=13.5545% gpu=N/A"| P6["Consumer-2: split250"]
    P6 -->|"time=2291ms cpu_time=69970ms cpu_avg=3.7485% cpu_max=13.5545% gpu=N/A"| P7["Consumer-2: parse250 (full)"]
  end

  subgraph A["Avro Senaryosu (Parallel C1 + C2)"]
    direction LR
    A1["Map/Array Obj (shared sabit)"] -->|"time=3981ms cpu_time=68760ms cpu_avg=3.5239% cpu_max=15.4275% gpu=N/A"| A2["Producer: avro encode (250)"]
    A2 -->|"time=179164ms cpu_time=68760ms cpu_avg=3.5239% cpu_max=15.4275% gpu=N/A"| A3["Kafka Topic: AVRO_FULL_20260212_142843 (same broker)"]
    A3 -->|"time=3155ms cpu_time=69710ms cpu_avg=3.6152% cpu_max=17.7778% gpu=N/A"| A4["Consumer-1: reader decode10"]
    A4 -->|"time=282ms cpu_time=69710ms cpu_avg=3.6152% cpu_max=17.7778% gpu=N/A"| A5["Consumer-1: parse10"]
    A3 -->|"time=7465ms cpu_time=82200ms cpu_avg=4.2628% cpu_max=16.6350% gpu=N/A"| A6["Consumer-2: full decode250"]
    A6 -->|"time=4636ms cpu_time=82200ms cpu_avg=4.2628% cpu_max=16.6350% gpu=N/A"| A7["Consumer-2: parse250 (full)"]
  end

  P5 --> E["E2E Parallel\nPipe=178120ms\nAvro=184125ms\nDelta=6005ms (3.3713%)"]
  P7 --> E
  A5 --> E
  A7 --> E
~~~
