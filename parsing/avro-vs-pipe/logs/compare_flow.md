# Avro vs Pipe Flow (Run: 20260210_155939)

Ortak Kafka broker uzerinde Pipe ve Avro Consumer-1 karsilastirmasi.

~~~mermaid
%%{init: {'flowchart': {'useMaxWidth': false, 'nodeSpacing': 70, 'rankSpacing': 90}}}%%
flowchart LR
  subgraph P["Pipe Senaryosu (Consumer-1)"]
    direction LR
    P1["Map/Array Obj (shared sabit)"] -->|"time=3214ms cpu_time=62090ms cpu_avg=3.7499% cpu_max=16.3889% gpu=N/A"| P2["Producer: pipe encode (250)"]
    P2 -->|"time=151139ms cpu_time=62090ms cpu_avg=3.7499% cpu_max=16.3889% gpu=N/A"| P3["Kafka Topic: RAW_PIPE_20260210_155939 (same Kafka broker)"]
    P3 -->|"time=4221ms cpu_time=69500ms cpu_avg=4.2168% cpu_max=15.4206% gpu=N/A"| P4["Consumer-1: split 250"]
    P4 -->|"time=2410ms cpu_time=69500ms cpu_avg=4.2168% cpu_max=15.4206% gpu=N/A"| P5["Consumer-1: parse 250 + select 10"]
  end

  subgraph A["Avro Senaryosu (Consumer-1)"]
    direction LR
    A1["Map/Array Obj (shared sabit)"] -->|"time=8586ms cpu_time=67620ms cpu_avg=3.9345% cpu_max=13.7164% gpu=N/A"| A2["Producer: Avro encode (250)"]
    A2 -->|"time=150133ms cpu_time=67620ms cpu_avg=3.9345% cpu_max=13.7164% gpu=N/A"| A3["Kafka Topic: AVRO_FULL_20260210_155939 (same Kafka broker)"]
    A3 -->|"time=2947ms cpu_time=68440ms cpu_avg=4.0526% cpu_max=20.1500% gpu=N/A"| A4["Consumer-1: reader decode 10"]
    A4 -->|"time=276ms cpu_time=68440ms cpu_avg=4.0526% cpu_max=20.1500% gpu=N/A"| A5["Consumer-1: parse 10"]
  end

  P5 --> E["E2E Total\nPipe=155433ms\nAvro=159628ms\nDelta=4195ms (2.6989%)"]
  A5 --> E
~~~
