# Sender Address Validity Checker

Bu proje, gönderici adreslerinin (Sender Address) geçerliliğini belirli kurallara göre kontrol eden bir Java uygulaması ve bu mantığın SQL karşılığını içerir.

## Dosya İçerikleri

* **check.java**: Adres doğrulama mantığını içeren ana Java uygulaması.
* **check_logic.sql**: Aynı doğrulama mantığının SQL (T-SQL) karşılığı.
* **test_inputs.txt**: Test edilmek istenen örnek gönderici adresleri.
* **output.txt**: Java uygulamasının `test_inputs.txt` dosyasını işledikten sonra ürettiği sonuç raporu.

## Nasıl Çalıştırılır?

1. Java dosyasını derleyin:

    ```bash
    javac check.java
    ```

2. Uygulamayı çalıştırın (input dosyasını argüman olarak verin):

    ```bash
    java check test_inputs.txt
    ```

3. Sonuçlar `output.txt` dosyasına yazılacaktır.

## Doğrulama Mantığı ve Karşılaştırma (Java vs SQL)

Kodlar temel olarak aşağıdaki kuralları kontrol eder:

1. **Uzunluk:** 3-11 karakter arası.
2. **Boşluklar:** Başta/sonda boşluk olmamalı, çift boşluk içermemeli.
3. **Sayısal Adresler:** Sadece rakamlardan oluşuyorsa; uzunluk tam 3 olmalı, '1' ile başlamalı ve ikinci rakam '0' olmamalıdır.
4. **Karakter Seti:** Alfanümerik karakterler (harf ve rakam) ve boşluk kabul edilir.
5. **Yasaklı Karakterler:** `OoIlEASsbTBqg0123456789` listesindeki karakterlerden *SADECE* oluşan adresler yasaklanmıştır (Suspicious Characters).

### Önemli Fark: Alfanümerik Kontrolü

Java ve SQL kodları mantıksal olarak birebir aynı akışa sahiptir, ancak **alfanümerik** kontrolünde küçük bir kapsam farkı bulunmaktadır:

* **Java (`Character.isLetterOrDigit`):** Unicode standardını kullanır. Dünyadaki tüm dillerin harflerini (Çince, Arapça, Rusça vb.) ve tüm rakam sistemlerini geçerli kabul eder.
* **SQL (`LIKE` pattern):** Sadece belirlenen aralıktaki (Latin alfabesi, Türkçe karakterler ve 0-9 rakamları) karakterleri kabul eder.

**Sonuç:** Eğer girdi sadece İngilizce/Türkçe karakterlerden oluşuyorsa iki kod **%100 aynı** sonucu verir. Farklı dillerden karakterler girilirse Java daha kapsayıcı davranacaktır.
