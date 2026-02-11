# Benchmark Logları

## Dosya Yapısı

```
logs/
├── compare_summary.kv    # Ok bazlı metrikler (Pipe vs Avro)
├── compare_flow.md       # Mermaid diagram
├── run.sh                # log.html'i localhost'ta aç
└── runs/
    └── <run_id>/
        ├── log.html              # Görsel benchmark raporu
        ├── pipe_producer.kv      # Pipe producer metrikleri
        ├── pipe_consumer_1.kv    # Pipe consumer-1 metrikleri
        ├── avro_producer.kv      # Avro producer metrikleri
        └── avro_consumer_1.kv    # Avro consumer-1 metrikleri
```

## log.html Görüntüleme

```bash
# Default port 8080
./run.sh

# Farklı port
./run.sh 3000
```

Tarayıcıda açılacak URL terminalde gösterilir.
