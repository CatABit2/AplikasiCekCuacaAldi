# AplikasiCekCuacaAldi
Tugas 6 - M. Aldi Ripandi (2210010610)

# Aplikasi Cek Cuaca – OpenWeatherMap (JDK 8, Swing)

Aplikasi desktop sederhana untuk menampilkan cuaca kota berdasarkan API dari [OpenWeatherMap](https://openweathermap.org). Dibangun menggunakan **Java Swing**, **tanpa dependensi eksternal**, kompatibel **JDK 8**.
Mendukung favorit kota, cache ikon lokal, ekspor/impor CSV, dan kartu informasi cuaca yang rapi.

---

## Fitur Utama

* Ambil cuaca kota (metric, bahasa Indonesia)
* Kartu info: nama kota, negara, suhu, terasa, kondisi, kelembaban, tekanan, angin + arah (kompas), visibilitas, sunrise/sunset
* Tabel ringkasan multi-kolom
* Favorit kota (tersimpan ke `favorites.txt`, otomatis muncul di combobox)
* Cache ikon cuaca:

  * Prioritas ikon lokal (`/icons/*.png`) bila tersedia
  * Fallback ikon OWM (disimpan di `~/.owm-cache`)
  * Fallback terakhir emoji
* Ekspor CSV & impor CSV
* Menu klik kanan tabel: **Salin baris** (CSV ke clipboard) dan **Hapus baris**
* UX:

  * Enter di combobox = aksi “Cek”
  * Status bar berwarna (sukses/galat)
  * Non-blocking (pakai `SwingWorker`)

---

## Prasyarat

* **JDK 8** (atau lebih baru, namun kode menargetkan kompatibilitas 8)
* Koneksi internet
* API key OpenWeatherMap (free): [https://home.openweathermap.org/api_keys](https://home.openweathermap.org/api_keys)

---

## Struktur Proyek (sederhana)

```
project-root/
├─ src/
│  ├─ CuacaOWMApp.java
│  └─ icons/               # opsional (ikon lokal)
│      ├─ clear.png
│      ├─ clouds.png
│      ├─ rain.png
│      ├─ thunder.png
│      ├─ mist.png
│      └─ snow.png
├─ favorites.txt           # dibuat otomatis saat simpan favorit
└─ cuaca_owm.csv           # contoh file ekspor (opsional)
```

> Ikon lokal opsional. Jika tidak ada, aplikasi otomatis mengunduh dari OWM dan menyimpannya di folder cache:
> Windows: `C:\Users\<user>\.owm-cache`
> Linux/macOS: `/home/<user>/.owm-cache` atau `/Users/<user>/.owm-cache`

---

## Konfigurasi API Key

Di `CuacaOWMApp.java` cari baris berikut dan ganti dengan key milik Anda:

```java
private static final String API_KEY = "MASUKKAN_API_KEY_ANDA_DI_SINI";
```

Contoh (dari Anda):

```java
private static final String API_KEY = "aba534b094285303c6ce9f163f023a8f";
```

> Jaga kerahasiaan API key jika proyek dipublikasikan.

---

## Cara Menjalankan (NetBeans/IDE lain)

### NetBeans (direkomendasikan untuk JDK 8)

1. Buat proyek Java Application.
2. Tambahkan file `CuacaOWMApp.java` ke `src/`.
3. (Opsional) Buat folder `src/icons/` dan letakkan ikon PNG Anda.
4. Set `CuacaOWMApp` sebagai Main Class.
5. **Run**.

### Command Line (alternatif)

```bash
javac -encoding UTF-8 src/CuacaOWMApp.java
java -cp src CuacaOWMApp
```

> Pastikan `JAVA_HOME` menunjuk ke JDK 8 dan `javac/java` ada di PATH.

---

## Penggunaan

1. Ketik nama kota pada combobox (mis. “Banjarmasin”, “Jakarta”, dll).
   Tekan **Enter** atau klik **Cek**.
2. Klik **★** untuk menyimpan kota ke favorit. Tersimpan ke `favorites.txt` dan otomatis muncul di combobox.
3. **Simpan CSV** untuk mengekspor seluruh tabel.
4. **Muat CSV** untuk memuat data dari file CSV (format header akan dideteksi).
5. Klik dua kali baris di tabel untuk memunculkan ringkasan ke kartu info di kanan.
6. Klik kanan pada baris tabel:

   * **Salin baris (CSV)** ke clipboard
   * **Hapus baris** dari tabel

---

## Kolom Tabel

| Kolom         | Keterangan                    |
| ------------- | ----------------------------- |
| Kota          | Nama kota (capFirst)          |
| Negara        | Kode negara (ISO 2 huruf)     |
| Kondisi       | Clear, Clouds, Rain, dll (ID) |
| Suhu(°C)      | Suhu saat ini (metric)        |
| Terasa(°C)    | Feels like                    |
| Kelembaban(%) | Humidity                      |
| Tekanan(hPa)  | Pressure                      |
| Angin(m/s)    | Kecepatan angin               |
| Arah          | Arah angin (kompas)           |
| Vis(km)       | Visibilitas dalam kilometer   |
| Epoch         | Timestamp epoch (`dt`)        |

---

## Format CSV

* Ekspor header:

```
Kota,Negara,Kondisi,Suhu(°C),Terasa(°C),Kelembaban(%),Tekanan(hPa),Angin(m/s),Arah,Vis(km),Epoch
```

* Impor CSV:

  * Baris pertama boleh header (akan dilewati jika cocok)
  * Minimal 11 kolom sesuai header di atas

---

## Ikon Cuaca

Urutan pemilihan ikon:

1. Ikon lokal di classpath `src/icons/*.png`
   `clear.png`, `clouds.png`, `rain.png`, `thunder.png`, `mist.png`, `snow.png`
2. Fallback unduh dari OWM (`https://openweathermap.org/img/wn/{icon}@2x.png`)
3. Fallback emoji: ☀️ ☁️ 🌧️ ⛈️ 🌫️ ❄️

Jika tidak ingin internet untuk ikon, sediakan seluruh ikon di `src/icons/` agar selalu lokal.

---

## Troubleshooting

* **HTTP 401 – API key ditolak/limit**

  * Cek API key, apakah salah ketik atau nonaktif.
  * Pastikan paket API Anda mengizinkan endpoint `Current weather data`.
* **HTTP 404 – Kota tidak ditemukan**

  * Gunakan nama kota yang lebih spesifik (mis. “Bekasi,ID”).
  * Coba tanpa spasi ganda, cek ejaan.
* **Tidak muncul ikon**

  * Pastikan koneksi internet atau sediakan ikon lokal di `src/icons/`.
  * Periksa izin tulis ke folder cache `~/.owm-cache`.
* **Karakter Indonesia/UTF-8 kacau**

  * Pastikan opsi kompilasi pakai `-encoding UTF-8`.
* **Timeout / Lambat**

  * Jaringan bermasalah; aplikasi sudah non-blocking dengan `SwingWorker`, jadi UI tidak macet. Coba ulang.

---

## Kesesuaian Penilaian (Checklist)

* [x] **Pengambilan data API** OpenWeatherMap dengan parameter yang benar (units=metric, lang=id)
* [x] **UI informatif**: card detail + tabel ringkasan multi-kolom
* [x] **Interaksi pengguna**: Enter untuk aksi, favorit, menu klik kanan, notifikasi status
* [x] **Pengelolaan data**: ekspor CSV, impor CSV, clipboard
* [x] **Asset handling**: ikon lokal + cache ikon OWM
* [x] **Asinkron**: `SwingWorker`, UI tidak hang
* [x] **Kode rapi**: util parsing sederhana sesuai batasan JDK 8, tanpa lib pihak ketiga

---

## Catatan Lisensi & Batasan

* Ikon dari OWM tunduk pada ketentuan OpenWeatherMap.
* Data cuaca berasal dari OWM; hormati rate limit paket akun Anda.
* Proyek contoh ini bebas dipakai untuk keperluan praktikum/tugas.

---

## Kredit

* Data & ikon cuaca: OpenWeatherMap
* UI: Java Swing (JDK 8), tanpa dependensi eksternal

---

Selamat mencoba. Kalau ada error, cantumkan **stacktrace** dan langkah yang dilakukan saat error muncul, supaya perbaikan bisa tepat sasaran.
