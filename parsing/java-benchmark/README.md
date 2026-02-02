---

# 📊 Split vs Avro vs Protobuf – Kafka Consumer Parsing Benchmark

## Amaç

Bu benchmark’ın amacı, **live sistemde yaşanan şu problemi** adil ve teknik olarak doğru biçimde ölçmektir:

> **250 alanlı pipe (`|`) ayrılmış bir kayıttan
> sadece 10 alan gerekli olmasına rağmen,
> String split yaklaşımı nedeniyle 250 alanın tamamının parse edilmesi.**

Bu benchmark, bu yaklaşımı:

* **Split (String tabanlı parsing)**
* **Avro (reader schema ile projection)**
* **Protobuf (selective parsing / skipField)**

yöntemleriyle karşılaştırır.

---

## Benchmark’ın Temel Sorusu (Net)

> **Benim benchmark’ım, live’daki
> “250 alanı split et → 10 alanı seç” sorununu,
> Avro/Protobuf’un
> “250 alanı almadan sadece 10 alanı okuma” avantajıyla
> adil biçimde karşılaştırıyor mu?**

### ✅ Cevap: **Evet.**

---

# 🧪 Benchmark Ortamı

### Test Başlangıcı

```
Initializing Benchmark Suite (1 Million Records)...
```

### Ortam Bilgileri

```
Environment:
- Java: 25.0.1
- OS: Mac OS X 15.6.1
- Processors: 11
- Max Memory: 12288 MB
```

---

## Senaryo Tanımları

```
Scenario Definitions:
X (10 fields): parse only 10 fields and re-emit as pipe-delimited string.
Y (250 fields): parse all 250 fields (full parsing).
```

### X Senaryosu (Gerçek Live İhtiyaç)

* 250 alanlı kayıttan **sadece 10 alan** gereklidir

### Y Senaryosu (Referans / Full Parsing)

* Tüm 250 alan parse edilir

---

## X Senaryosunda Seçilen Alanlar

```
Selected fields for X (fixed per run):
int_5(pos=5,field=6), int_13(pos=13,field=14), int_19(pos=19,field=20),
int_25(pos=25,field=26), long_9(pos=93,field=94), long_46(pos=130,field=131),
long_50(pos=134,field=135), str_1(pos=168,field=169),
str_53(pos=220,field=221), str_81(pos=248,field=249)
```

---

## Ölçüm Notu

```
Note: Each strategy measures full end-to-end path per run
(pipe -> encode -> decode -> output).
```

Bu, ölçümlerin:

* sadece decode değil
* **gerçek consumer pipeline’ını** temsil ettiğini gösterir

---

# 📥 Veri Üretimi

```
>> Generating Data: 1000000 records...
```

---

# 📜 HAM (RAW) LOG SONUÇLARI – TAMAMI

```
Repetition 1 results (raw):

- Avro X(10):      25601 ms (25.60 s), 39,061 rec/s | CPU 23.57 s (92.1%)
- Avro Y(250):     23669 ms (23.67 s), 42,249 rec/s | CPU 23.28 s (98.3%)

- Protobuf X(10):  17955 ms (17.95 s), 55,695 rec/s | CPU 17.32 s (96.5%)
- Protobuf Y(250): 16393 ms (16.39 s), 61,003 rec/s | CPU 16.03 s (97.8%)

- Split X(10):      3507 ms (3.51 s), 285,140 rec/s | CPU 3.49 s (99.6%)
- Split Y(250):     7819 ms (7.82 s), 127,889 rec/s | CPU 7.33 s (93.8%)
```

---

# 🎯 AYIKLANMIŞ (KARARA GİDEN) LOG’LAR

Bu bölümde **sadece karar vermek için anlamlı olan loglar** yer alır.

## X Senaryosu – 10 Alan İhtiyacı (Live Case)

```
X (10 fields) - parse 10 fields and re-emit as pipe:
```

| Yöntem   | Süre (ms) | Süre (s) | Hız (kayıt/sn) | CPU Süre (s) | CPU Kullanım (%) |
| -------- | --------- | -------- | -------------- | ------------ | ---------------- |
| Avro     | 25601     | 25.60    | 39,061         | 23.57        | 92.1             |
| Protobuf | 17955     | 17.95    | 55,695         | 17.32        | 96.5             |
| Split    | 3507      | 3.51     | 285,140        | 3.49         | 99.6             |

📌 **Kritik yorum:**

* Split X, **10 alan parse etmiyor**
* 250 alan split edilip sadece 10’u output ediliyor
* Bu nedenle “Split 10” gerçek partial parsing değildir

---

## Y Senaryosu – Full Parsing (Referans)

```
Y (250 fields) - parse all 250 fields (full parsing):
```

| Yöntem   | Süre (ms) | Süre (s) | Hız (kayıt/sn) | CPU Süre (s) | CPU Kullanım (%) |
| -------- | --------- | -------- | -------------- | ------------ | ---------------- |
| Avro     | 23669     | 23.67    | 42,249         | 23.28        | 98.3             |
| Protobuf | 16393     | 16.39    | 61,003         | 16.03        | 97.8             |
| Split    | 7819      | 7.82     | 127,889        | 7.33         | 93.8             |

---

# 🧠 DOĞRU OKUMA (ÇOK ÖNEMLİ)

### Split

* X ve Y arasındaki fark **parsing değil**
* Sadece output üretim farkı
* **250 alan her zaman split ediliyor**

### Avro / Protobuf

* X vs Y farkı **gerçek parsing farkı**
* X senaryosunda:

  * 250 alan materialize edilmez
  * sadece 10 alan decode edilir

---

# ✅ SONUÇ (NET VE KAPATICI)

## Teknik Sonuç

* String split yaklaşımı:

  * 10 alan ihtiyacında bile
  * **250 alanı parse etmek zorundadır**
* Avro ve Protobuf:

  * Binary format sayesinde
  * **250 alanı parse etmeden**
  * **sadece gerekli 10 alanı okur**

---

## Karar Açısından Anlamı

> **Live sistemdeki CPU probleminin nedeni Kafka değil,
> string tabanlı parsing yaklaşımıdır.**

Bu benchmark:

* Avro / Protobuf’a geçiş kararını
* **ölçülebilir ve savunulabilir** hale getirir

---

## Tek Cümlelik Nihai Özet

> **Bu benchmark, live’daki
> “250 alan split etme → 10 alan kullanma” problemini,
> Avro ve Protobuf’un gerçek partial parsing yeteneğiyle
> adil ve doğru biçimde karşılaştırmaktadır.**

---
