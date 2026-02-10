# Avro vs Pipe Benchmark

## PIPE Senaryosu - Adım Adım

### Producer

1. `StaticMappedObject.DATA` → Hardcoded Map (250 entry), program başında hazır
2. `toPipeRecord(sequence)` → Map'in tüm value'larını `|` ile birleştirip String yapar
   - `"100000001|100000002|...|STR_082"` (250 alan, pipe separated)
3. **⏱ Ok1 ölçümü başlar**
4. Bu String payload oluşturulur
5. **⏱ Ok1 ölçümü biter**
6. **⏱ Ok2 ölçümü başlar**
7. `producer.send(record).get()` → Kafka'ya synchronous gönderir
8. **⏱ Ok2 ölçümü biter**

### Consumer-1

1. Kafka'dan String mesajı okur
2. **⏱ Ok3 ölçümü başlar**
3. `payload.split("\\|", -1)` → String'i 250 parçaya böler → `String[250]`
4. **⏱ Ok3 ölçümü biter**
5. **⏱ Ok4 ölçümü başlar**
6. `int[84]` oluştur, 84 int alanı `Integer.parseInt()` ile parse et
7. `long[83]` oluştur, 83 long alanı `Long.parseLong()` ile parse et
8. `int[83]` oluştur, 83 string alanının `.length()` al
9. `selectedPositions` = {5, 13, 19, 25, 93, 130, 134, 168, 220, 248} → bu 10 indexten değerleri topla
10. **⏱ Ok4 ölçümü biter**

---

## AVRO Senaryosu - Adım Adım

### Producer

1. `StaticMappedObject.getData()` → Hardcoded Map (250 entry), program başında hazır
2. `buildFullRecordTemplate(fullSchema)` → Map'ten GenericRecord oluşturur, **1 KERE** çalışır
   - Schema'daki her field için `data.get(fieldName)` ile Map'ten alır, `record.put()` yapar
3. **Her record için loop başlar:**
4. **⏱ Ok1 ölçümü başlar**
5. `reusableRecord.put("long_0", sequence)` → Sadece sequence güncellenir (TEK PUT)
6. `writer.write(reusableRecord, encoder)` → GenericRecord → binary bytes
7. `out.toByteArray()` → byte[] payload
8. **⏱ Ok1 ölçümü biter**
9. **⏱ Ok2 ölçümü başlar**
10. `producer.send(kafkaRecord).get()` → Kafka'ya synchronous gönderir
11. **⏱ Ok2 ölçümü biter**

### Consumer-1

1. Kafka'dan `byte[]` mesajı okur
2. **⏱ Ok3 ölçümü başlar**
3. `DecoderFactory.get().binaryDecoder(payload, decoder)` → decoder hazırla
4. `reader.read(null, decoder)` → binary bytes → GenericRecord
    - Reader: `GenericDatumReader(fullSchema, reader10Schema)`
    - **Sadece 10 alanı deserialize eder**, 240 alanı skip eder
5. **⏱ Ok3 ölçümü biter**
6. **⏱ Ok4 ölçümü başlar**
7. `sinkReader10Record(decoded, reader10Schema)` → 10 alanı oku:
    - int → `.intValue()`, long → `.longValue()`, string → `.toString().length()`
    - Hepsini topla
8. **⏱ Ok4 ölçümü biter**

---

## FARK

```
PIPE:
  Ok3: split 250 parça
  Ok4: parse 250 alan + select 10

AVRO:
  Ok3: decode sadece 10 alan (240 skip)
  Ok4: oku 10 alan
```

## Çalıştırma

```bash
# 1M kayıt (default)
./scripts/run_compare.sh

# 10K kayıt (hızlı test)
RECORD_COUNT=10000 ./scripts/run_compare.sh
```

## Çıktılar

- `logs/compare_summary.kv` → Ok bazlı metrikler
- `logs/compare_flow.md` → Mermaid diagram
- `logs/runs/<run_id>/` → Her role'un ayrı .kv dosyası
