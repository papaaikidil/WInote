# WInote

(Sebelumnya bernama Hana Note.)

Aplikasi catatan tulis tangan untuk Android, dibuat khusus untuk tablet dan HP.

## Memasang di tablet

1. Buka halaman **Releases** repository ini dari browser tablet.
2. Unduh file `HanaNote-…apk` versi terbaru.
3. Buka file tersebut. Kalau Android meminta izin, izinkan browser untuk memasang aplikasi.
4. Versi berikutnya dipasang dengan cara yang sama dan menimpa versi lama. Catatan tetap aman.

## Tahap 1 (sekarang)

- Rak buku: catatan dikelompokkan dalam buku dengan sampul berwarna
- Satu catatan bisa berisi banyak halaman
- Ukuran kertas A4, A5, B5, F4, Letter, Persegi; tegak atau mendatar
- Pola kertas polos, bergaris, kotak, titik; lima warna kertas
- Alat tulis: pena, pensil, kuas, spidol, stabilo, penghapus; mengikuti tekanan pena
- Tombol pada pena aktif berfungsi sebagai penghapus sementara
- Telapak tangan diabaikan saat menulis dengan pena
- Tersimpan otomatis di perangkat, berjalan penuh tanpa internet
- Tampilan tablet dan HP dibedakan
- **Ubah jadi teks:** tulisan tangan bahasa Indonesia dibaca per baris di perangkat. Hasilnya bisa
  dikoreksi, disalin, dibagikan, atau menggantikan coretan di halaman (pilih baris mana saja).
  Kamusnya diunduh sekali saat ada internet, setelah itu bekerja offline.

## Tahap 2

- **Sisipkan:** gambar dari galeri, link website, stiker catatan (bisa ditulisi), dan tabel
- **Pilih:** ketuk benda untuk memilih, geser untuk memindahkan, tarik sudut untuk mengubah ukuran;
  tulisan di atas stiker, tabel, atau gambar ikut pindah bersamanya
- **Bentuk:** garis, panah, kotak, lingkaran, segitiga
- **Penggaris:** geser dengan satu jari, putar dengan dua jari; goresan di dekat tepinya jadi lurus
- **Tampilan:** enam warna utama, mode terang/gelap, dan gaya huruf judul

## Tambahan 0.1.8 ke atas

- **Teks otomatis:** aktifkan "Otomatis" (tablet) atau menu ⋮ → Teks otomatis (HP). Setelah jeda
  sekitar satu detik, tulisan tangan langsung berubah jadi teks ketik; tulisan di baris yang sama
  disambung ke teks sebelumnya.
- **Stiker:** setelah ditempel langsung bisa ditulisi pena; pilih stiker lalu "Isi teks" untuk teks ketik.
- **Teks dan font:** Sisipkan → Teks, dengan pilihan font, ukuran, dan warna. Font sendiri (.ttf/.otf)
  bisa ditambahkan dari dialog teks atau dari Tampilan di rak buku.

## Tambahan 0.1.13 ke atas

- **Teks otomatis lebih tepat:** baris ditentukan dari garis kertas dan "badan" huruf, jadi ekor huruf
  g, j, y tidak lagi terlepas. Pengenal diberi tinggi satu baris (bukan setinggi tulisan), sehingga
  huruf kecil tidak terbaca huruf besar. Teks sebelumnya dikirim sebagai konteks, spasi antarkata
  mengikuti tebakan pengenal dan jarak tulisan, dan teks hasil ubah duduk tepat di garis tulisan.
  Teks berubah setelah pena diangkat menjauh; selama pena masih melayang, aplikasi menunggu sebentar.
- **Gambar:** pilih gambar lalu "Putar" (90°), "Balik" (kiri-kanan atau atas-bawah), atau "Potong".
  Seret lingkaran di atas gambar untuk memutar bebas (menempel tiap 15°).
- **Stiker:** bisa diputar dengan lingkaran yang sama atau tombol "Putar"; tulisan di atasnya ikut berputar.

## Tambahan 0.1.14 ke atas

- **Ekspor:** menu ⋮ → Ekspor. PNG atau JPG (halaman ini atau semua halaman), dan Word (.docx) berisi
  teks yang bisa diedit beserta gambar tiap halaman. Bisa disimpan ke folder pilihan atau dibagikan.
- **Stiker gambar:** Sisipkan → Stiker → Tambah, pilih gambar dari galeri sekali, lalu tinggal ketuk
  untuk menempelkannya. Gambar di halaman juga bisa disimpan lewat "Jadikan stiker".
- **Kuas cat air:** sapuan transparan berlapis dengan tepi lebih pekat dan butiran kertas; berada di
  bawah tulisan, jadi bisa dipakai mewarnai latar.
- **Tanggal & kalender:** Sisipkan → Tanggal & kalender. Pilih tanggal (atau "Hari ini") dengan beberapa
  gaya tulisan, atau tempel kalender satu bulan yang tiap kotaknya bisa ditulisi.
- **Cari:** ikon kaca pembesar di rak buku mencari judul, teks ketik, isi stiker, dan tulisan tangan.
- **Bahasa tulisan:** menu ⋮ → Bahasa tulisan. Otomatis membaca dengan kamus Indonesia dan Inggris lalu
  memilih yang paling cocok (kamus Inggris diunduh sekali di latar).

## Tahap berikutnya

4. Sinkron Google Drive dan Google Calendar

## Merakit sendiri

Setiap perubahan di cabang `main` dirakit otomatis oleh GitHub Actions (lihat
`.github/workflows/rakit-apk.yml`). Untuk merakit di komputer: buka folder ini di
Android Studio, lalu jalankan `gradle :app:assembleRelease`.

Repository ini **publik**, jadi kunci tanda tangan tidak disimpan terbuka. Kunci (dan kata sandinya) ada di
`app/kunci.enc`, terenkripsi AES-256; alur rakit membukanya dengan rahasia GitHub Actions **KUNCI_RAKIT**.
Kunci yang sama dipakai di setiap versi supaya pembaruan bisa dipasang menimpa versi lama. Tanpa rahasia itu,
rakitan lokal memakai kunci debug (tidak bisa menimpa APK rilis).

## Tambahan 0.1.15 ke atas

- **Perataan teks:** pilih teks (atau stiker berisi teks), lalu ketuk salah satu dari empat tombol:
  rata kiri, tengah, kanan, atau kanan-kiri. Pil kecil di sisi kiri dan kanan kotak teks mengubah
  lebarnya tanpa mengubah ukuran huruf.
- **Hapus latar gambar:** pilih gambar → "Hapus latar". Cara otomatis mengenali objek utama
  (komponennya diunduh sekali lewat Layanan Google Play); cara "Latar polos" bekerja tanpa internet
  untuk latar satu warna, dengan pengatur kepekaan. Bisa diurungkan.
- **Mind map:** Sisipkan → Mind map. Ketuk ide untuk memilih, ketuk dua kali untuk mengisi teks,
  "+ Cabang" dan "+ Sejajar" untuk menambah, seret untuk memindahkan, "Rapikan" untuk menyusun ulang.
  Mind map di halaman bisa digeser, diperbesar, ditulisi, dan dibuka lagi lewat "Ubah mind map".

## Tambahan 0.1.16 ke atas

- **Bilah fitur di atas:** semua fitur dalam satu baris berlabel yang bisa digeser: Sisipkan, Suara,
  Tulisan, Alat, dan Halaman. Ketuk judul kelompok untuk menyembunyikan atau menampilkan tombolnya.
- **Suara jadi teks:** "Dikte" mengubah ucapan langsung jadi teks (Indonesia atau Inggris, memakai
  layanan pengenal suara Google di perangkat). "Rekam" menyimpan rekaman yang bisa diputar di halaman;
  pilih rekaman lalu "Ubah jadi teks" (Android 13 ke atas, bila layanan pengenal suaranya mendukung).
- **Bayangan gambar:** pilih gambar → "Bayangan": Timbul, Melayang, atau Tegas, dengan pengatur tinggi,
  kelembutan, dan kepekatan. Gambar tembus pandang berbayang sesuai bentuknya.
- **Huruf besar otomatis:** awal kalimat dari tulisan tangan, dikte, dan keyboard otomatis berhuruf besar.
  Bisa dimatikan lewat tombol "Huruf besar".

## Tambahan 0.1.18 ke atas

- **Ekspor PDF:** menu Ekspor → PDF. Satu berkas berisi halaman ini atau semua halaman, seukuran kertasnya;
  tulisan tetap tajam saat diperbesar atau dicetak.
- **Daftar halaman bergambar:** daftar halaman di kiri (tablet) dan lembar Halaman (HP) menampilkan isi
  tiap halaman secara samar, jadi halaman yang dicari mudah dikenali.

## Tambahan 0.1.19 ke atas

- **Coretan tidak lagi menempel sendiri ke stiker:** tulisan hanya ikut pindah bersama stiker, gambar, atau
  tabel bila ditulis di atasnya *setelah* benda itu ada. Coretan lama yang kebetulan tertutup stiker baru
  tetap bebas. Catatan lama tetap seperti sebelumnya.
- **Poin dan penomoran:** pilih teks (atau stiker berisi teks) lalu ketuk tombol poin (•) atau nomor (1. 2. 3.);
  ketuk lagi untuk melepas. Di kolom ketik, Enter langsung membuat butir berikutnya, dan Enter di butir kosong
  mengakhiri daftar. Butir yang panjang terlipat rapi sejajar isinya.
- **Ganti latar halaman:** Kertas → Latar. Pilih polos, tekstur kertas (daur ulang, kraft, linen, cat air,
  perkamen, kanvas, washi, karton hitam), atau gambar dari perangkat (memenuhi halaman). Bisa untuk halaman
  ini atau semua halaman, dan ikut terekspor.
- **Urutan objek seperti Canva:** pilih benda atau teks → "Urutan": Paling depan, Maju, Mundur, Paling
  belakang. Tulisan di atas stiker ikut lapisan stikernya.
- **Pilih banyak dan kelompok:** di mode Pilih, seret di tempat kosong untuk memilih banyak objek sekaligus
  (termasuk tulisan tangan). "Kelompokkan" menjadikannya satu kelompok yang selalu terpilih dan bergeser
  bersama; "Pisahkan" melepasnya lagi. Pilihan banyak bisa digeser, diubah ukurannya, diduplikat, dan dihapus.

## Tambahan 0.1.22 ke atas

- **Coretan tidak pernah menempel otomatis:** tulisan yang ditulis di atas gambar, stiker, atau tabel tetap berdiri
  sendiri. Supaya bergerak bersama, pilih keduanya lalu "Kelompokkan".
- **Urutan lapisan untuk semua objek:** gambar, stiker, teks ketik, dan coretan berbagi satu urutan. Pilih objek
  (atau seret kotak untuk memilih tulisan tangan) → "Urutan": Paling depan, Maju, Mundur, Paling belakang. Maju dan
  Mundur melewati satu objek yang bertumpuk; satu tulisan tangan yang ditulis berurutan dilewati sekaligus.
- **Stabilo dan cat air** hanya menggelapkan kertas, jadi tulisan yang tertimpa tetap pekat di lapisan mana pun.
- Fitur Google Drive dilepas dulu.

## Tambahan 0.1.23 ke atas

- **Atur tabel:** pilih tabel → "Atur tabel". Ubah jumlah baris dan kolom, seret garis di gambar tabel untuk mengatur
  lebar kolom dan tinggi baris, "Samakan kolom/baris", dan nyalakan atau matikan baris judul berwarna. Bisa diurungkan.
- **Emoji:** Sisipkan → Emoji. Daftar emoji lengkap per kategori seperti di WhatsApp (smiley & orang, hewan, makanan,
  aktivitas, perjalanan, benda, simbol, bendera), "Terakhir dipakai", pencarian (bahasa Indonesia atau Inggris), dan
  warna kulit dengan tekan lama. Emoji masuk sebagai teks besar yang bisa digeser, diperbesar, dan diurutkan.
  Data emoji dari [unicode-emoji-json](https://github.com/muan/unicode-emoji-json) (MIT).
- **Cadangan:** ikon cadangan di rak buku. Pilih folder di tablet; cadangan otomatis dibuat saat aplikasi ditinggalkan
  bila ada perubahan (harian atau mingguan), dan hanya beberapa cadangan terakhir yang disimpan. "Simpan cadangan ke…"
  untuk menyimpan ke Google Drive atau tempat lain, dan "Pulihkan dari cadangan…" untuk mengembalikan catatan (tidak ada
  yang dihapus; yang lebih baru di cadangan menggantikan yang lama).

## Tambahan 0.1.24 ke atas

- **Penghapus sebagian:** pilih penghapus → "Sebagian" (bawaan) hanya menghapus bagian goresan yang tersapu;
  "Goresan utuh" seperti sebelumnya. Satu sapuan = satu langkah urung.
- **Laso:** di mode Pilih, lingkari tulisan atau objek untuk memilihnya (mulai dari tempat kosong atau dari atas
  tulisan). Ketukan saja di tulisan memilih goresan itu. Pilihan bisa diganti warnanya lewat "Warna".
- **Salin, potong, tempel:** pilih lalu "Salin" atau "Potong"; buka halaman atau catatan lain lalu ketuk "Tempel"
  (di bilah Sisipkan, atau tombol Tempel di mode Pilih). Gambar dan rekaman ikut tersalin.
- **PDF:** Catatan baru → "Buka PDF untuk ditulisi…" menjadikan tiap halaman PDF satu halaman yang bisa ditulisi;
  di editor, Halaman → "Impor PDF" menambahkan halaman PDF setelah halaman ini. Ikut terekspor dan tercadangkan.
- **Daftar centang:** Sisipkan → "Centang", atau tombol daftar centang di teks. Ketuk kotaknya dengan pena atau jari
  untuk mencentang; yang selesai dicoret. Enter membuat butir baru.
- **Templat halaman:** di pilihan pola kertas: Daftar tugas, Agenda harian, Mingguan, Rapat, dan Cornell.

## Tambahan 0.1.27 ke atas

- **Kuas penghapus halus:** pilih penghapus → "Kuas halus", lalu kekuatan Tipis, Sedang, atau Kuat. Sapuannya
  memudarkan apa pun di bawahnya (tulisan, cat air, stabilo, gambar, teks) dengan tepi bergradasi; makin ditekan
  atau makin sering disapu, makin bersih. Kertas dan latarnya tidak ikut terhapus. Bekas sapuan ikut urutan lapisan
  (objek yang ditaruh di atasnya tidak terhapus), ikut terpilih dengan laso, dan bisa diurungkan.

## Tambahan 0.1.29 ke atas

- **Penghapus untuk gambar:** pilihan "Termasuk gambar" (menyala bawaan) di penghapus Sebagian dan Goresan utuh.
  "Sebagian" mengosongkan bagian gambar yang tersapu (gambar disimpan ulang sebagai PNG tembus pandang, bentuknya
  ikut putaran, balikan, dan potongan gambar); "Goresan utuh" menghapus gambar yang tersentuh. Bisa diurungkan.
  Matikan pilihan ini bila hanya ingin menghapus tulisan di atas gambar. Kuas halus sejak awal ikut memudarkan gambar.
- **Penghapus lebih ringan (0.1.31):** daerah yang digambar ulang hanya sekitar jejak penghapus (bukan seluruh
  goresan yang terpotong), sekali per bingkai layar; cat air digambar dengan satu kali pengaburan per sapuan.
- **Penghapus lebih ringan (0.1.30):** saat menghapus, hanya bagian halaman yang tersapu yang digambar ulang (bukan
  seluruh halaman), titik sentuh yang terlalu rapat digabung, goresan yang jauh dilewati tanpa hitungan berat, dan
  penyimpanan dijadwalkan sekali saat pena diangkat.

## Tambahan 0.1.32 ke atas

- **Layar penuh untuk menulis:** tombol ⛶ di bilah atas menyembunyikan semua bilah (judul, fitur, alat, daftar halaman,
  bilah status Android). Tinggal satu bilah alat kecil yang melayang: keluar layar penuh, urungkan/ulangi, semua alat
  tulis, bentuk, dan pilih. Ketuk alat yang sedang aktif untuk warna dan ketebalan; tombol ⇕ mengecilkan bilah jadi
  satu tombol. Tombol kembali juga keluar dari layar penuh.

## Tambahan 0.1.33 ke atas

- **Geser untuk ganti halaman:** sapu halaman ke kiri untuk halaman berikutnya, ke kanan untuk halaman sebelumnya
  (dengan jari; bila jari dipakai menulis, sapu dengan dua jari). Saat halaman diperbesar, geser dulu sampai tepi lalu
  sapu lagi. Pena tetap menulis seperti biasa.
- **Animasi membalik halaman:** saat pindah halaman (sapuan, tombol ◀ ▶, atau tambah halaman), halaman lama terangkat
  dan terbalik ke kiri seperti lembaran buku, dengan bayangan; mundur ke halaman sebelumnya membaliknya kembali.

## Tambahan 0.1.34 ke atas

- **Laporan galat:** bila aplikasi tertutup sendiri, penyebabnya beserta langkah-langkah terakhir sebelum galat disimpan
  di tablet. Saat aplikasi dibuka lagi muncul tawaran **Bagikan laporan** atau **Salin teks laporan**, tinggal tempel
  ke obrolan dengan Claude. Semua laporan juga bisa dilihat di **Tampilan → Laporan galat…**. Tidak ada yang dikirim
  otomatis. Galat yang tidak menutup aplikasi (misalnya ekspor atau cadangan gagal) ikut dicatat.
- **Urutkan halaman:** seret pegangan ⋮⋮ di pojok gambar kecil halaman (daftar halaman di samping pada tablet, atau
  lembar Halaman pada HP) ke posisi baru; daftarnya bergulir sendiri bila diseret ke tepi. Tahan gambar kecil halaman
  untuk **Pindah ke sebelumnya/sesudahnya** dan **Jadikan halaman pertama/terakhir**.
- **Perbaikan:** urungkan/ulangi tepat setelah menghapus sebagian gambar kini menunggu penyimpanan gambar selesai
  supaya urutannya benar, dan langkah dari halaman lama tidak lagi masuk ke riwayat halaman baru bila halaman
  diganti saat gambar masih disimpan.

## Tambahan 0.1.35 ke atas

- **Jadi teks lewat pilihan:** ketuk **Jadi teks**, lalu lingkari tulisan tangan yang ingin diubah dengan pena;
  begitu dilingkari, tulisan langsung dibaca dan hasilnya bisa dikoreksi sebelum dipakai. Ada juga pilihan
  **Semua di halaman** dan **Batal** di bilah petunjuknya. Bila tulisan sudah dipilih (laso atau alat pilih),
  tombol **Jadi teks** muncul di bilah pilihan. Setelah selesai, alat kembali ke pena.
- **Membalik halaman seperti kertas:** saat pindah halaman, pojok kanan bawah terangkat lebih dulu lalu
  lembarannya menggulung melengkung ke kiri, dengan bayangan di halaman bawahnya, kilau di lengkungan, dan
  punggung kertas yang memperlihatkan isi samar terbalik. Mundur ke halaman sebelumnya memutar gerakan ini.
  (Android 10 ke atas; Android 8–9 tetap memakai animasi lama.)

## Tambahan 0.1.36 ke atas

- **Urutkan buku dan catatan dengan diseret:** tahan buku (di rak) atau catatan sampai sedikit terangkat, lalu geser
  ke posisi baru; tempat tujuannya ditandai garis warna dan daftarnya bergulir sendiri bila diseret ke tepi.
  Tahan lalu lepas tanpa digeser tetap membuka menu seperti biasa. Halaman di daftar halaman editor juga bisa
  ditahan lalu digeser (pegangan ⋮⋮ di pojoknya tetap ada).
- **Urutan catatan:** semula catatan diurutkan menurut yang terakhir diubah. Begitu catatan di sebuah buku diseret,
  buku itu memakai urutan sendiri; kembalikan lewat **Urutkan: terakhir diubah** (tablet: di samping "Ubah buku";
  HP: menu ⋮ buku).
- **Sematkan (pin):** tahan buku atau catatan → **Sematkan di depan/di atas**. Yang disematkan selalu paling depan
  (bertanda pin), dan hanya bisa diurutkan di antara yang disematkan. **Lepas sematan** untuk mengembalikannya.

## Tambahan 0.1.37 ke atas

- **Tampilan rak baru:** layar depan berupa kisi sampul buku (3 kolom di tablet tegak, 2 di HP, lebih banyak saat
  tablet mendatar) dengan judul di bawah sampul, tombol bulat **+** untuk buku baru, dan cari/cadangan/tampilan di
  bilah atas. Ketuk buku untuk membuka kisi catatannya dengan susunan yang sama (gambar halaman pertama, judul dan
  tanggal di bawahnya). Tombol kembali membawa ke rak. Tablet dan HP kini memakai susunan yang sama.
- **Gambar sampul:** **Ubah nama & sampul** (tahan buku, atau menu ⋮ di dalam buku) → **Pilih dari galeri…** untuk
  memakai foto atau gambar apa pun sebagai sampul; **Pakai warna saja** untuk kembali ke sampul warna. Gambar
  sampul ikut tersimpan di cadangan.

## Tambahan 0.1.38 ke atas

- **Keyboard muncul tanpa tersendat:** saat menyunting teks (bilah "Perbaiki teks"), halaman tidak lagi digambar
  ulang penuh di setiap bingkai animasi keyboard. Selama keyboard naik/turun gambar halaman yang sudah ada digeser
  saja, lalu digambar ulang sekali setelah keyboard berhenti, sehingga keyboard dan bilah teks naik mulus.

## Tambahan 0.1.39 ke atas

- **Bagian yang dihapus tetap hilang:** bekas kuas penghapus halus kini menempel pada coretan, teks, atau gambar
  yang disapunya, bukan tertinggal di halaman. Jadi bila objeknya digeser, diperbesar, diputar, dibalik, dipotong,
  disalin, atau diduplikat, bagian yang sudah dihapus ikut terbawa dan tidak muncul lagi. Bekas penghapus di
  catatan lama otomatis dipindahkan ke objek yang dihapusnya saat halaman dibuka. Urungkan tetap bisa
  mengembalikan bagian yang terhapus.

## Tambahan 0.1.40 ke atas

- **Salin–tempel lewat tahan tekan:** tahan objek (coretan, teks, gambar, atau pilihan laso) sampai bergetar,
  lalu muncul pilihan **Salin · Potong · Tempel · Duplikat · Hapus** di atas jari. Tahan tempat kosong untuk
  **Tempel**: isinya ditaruh tepat di titik yang ditahan (bisa di halaman atau catatan lain). Saat menulis dengan
  pena, tahan dengan jari; di mode pilih, bisa dengan pena atau jari. Tombol Salin/Potong/Tempel yang selalu tampil
  di bilah sudah dihapus supaya bilah lebih ringkas.

## Tambahan 0.1.41 ke atas

- **Bilah alat tidak berkedip:** bilah atas, bilah alat, dan daftar halaman tidak lagi hilang sesaat saat halaman
  digeser dengan jari atau saat tata letak berubah (kanvas tidak lagi menggambar keluar dari kotaknya).
- **Bilah fitur bertinggi tetap:** membuka atau menutup kelompok (Sisipkan, Suara, Tulisan, Alat, Halaman) hanya
  melebar/menyempit ke samping dengan animasi halus; kanvas di bawahnya tidak ikut bergeser.
- **Membalik halaman mengikuti jari:** geser halaman ke samping dengan jari; pojok lembaran terangkat dan mengikuti
  jari (naik-turunnya jari juga memiringkan lipatan). Lepas setelah cukup jauh atau sentakkan untuk menyelesaikan
  membalik; lepas lebih awal untuk mengembalikannya. Halaman sebelum dan sesudahnya dimuat di latar supaya siap
  dibalik.

## Tambahan 0.1.42 ke atas

- **Daftar halaman tidak berkedip:** memilih halaman di daftar kiri tidak lagi membuat semua gambar kecil hilang lalu
  muncul memudar. Daftarnya hanya digulir bila halaman yang dipilih tidak tampil utuh, dan secukupnya saja. Hal yang
  sama berlaku untuk kisi buku dan catatan di rak.

## Tambahan 0.1.43 ke atas

- **Tombol tidak tampak aktif sendiri:** tombol di bilah alat (misalnya Kuas halus / Sebagian / Goresan utuh, alat
  tulis, warna, dan kelompok fitur) tidak lagi menyimpan sorotan "pena melayang" atau fokus. Sebelumnya, bila pena
  melayang di atas tombol lalu pindah ke kanvas, tombolnya bisa tetap tersorot abu-abu seperti sedang aktif.
  Sekarang tombol hanya tampak aktif bila memang dipilih.

## Tambahan 0.1.44 ke atas

- **Ketuk dua jari = urungkan, tiga jari = ulangi:** ketuk kanvas sebentar dengan dua (atau tiga) jari sekaligus,
  tanpa digeser. Muncul tulisan kecil "Urungkan"/"Ulangi" di atas kanvas sebagai tanda. Menggeser, memperbesar,
  menulis dengan pena, atau telapak tangan yang menempel tidak dihitung sebagai ketukan.

## Tambahan 0.1.45 ke atas

- **Ketuk tiga jari (ulangi) di tablet Xiaomi/HyperOS:** sistem tablet seperti ini memakai sentuhan tiga jari untuk
  tangkapan layar dan merebut sentuhannya, sehingga aplikasi hanya menerima "batal". Kini keadaan itu juga dikenali
  sebagai ketukan tiga jari. Batas waktu ketukan juga dilonggarkan (jari boleh mendarat dan diangkat lebih santai).

## Tambahan 0.1.46 ke atas (penyetelan untuk Xiaomi Pad 7)

- **Layar 144 Hz:** aplikasi meminta laju layar tertinggi yang didukung tablet. Tanpa ini HyperOS sering menjalankan
  aplikasi pada 60 Hz, sehingga tulisan terasa tertinggal di belakang pena.
- **Ujung tulisan menempel pada pena:** saat menulis dengan pena atau kuas memakai stylus, aplikasi memperkirakan ke
  mana pena bergerak dan menggambar ekor pendek di depannya. Jeda antara pena dan tinta jadi jauh lebih kecil. Saat
  pena berhenti, ekor itu langsung hilang.
- **Foto dan PDF tidak lagi menahan layar:** foto dan halaman PDF dibaca di latar belakang. Selama belum siap,
  dipakai versi yang sudah ada (atau kertas polos sesaat), lalu bagian itu saja yang digambar ulang begitu siap.
  Gambar yang siap saat sedang menulis ditunda sampai goresannya selesai, jadi menulis tidak pernah tersendat.
- **Halaman sebelah disiapkan lebih dulu:** foto dan halaman PDF di halaman sebelum/sesudahnya ikut disiapkan, jadi
  membalik halaman langsung tampil.
- **Lebih sedikit menggambar ulang:** urungkan/ulangi, menyunting teks, mengangkat pilihan, dan bekas kuas penghapus
  kini hanya menggambar ulang bagian yang berubah, bukan seluruh halaman.
- **Memori tetap lega di catatan tebal:** hanya isi sekitar 14 halaman yang terakhir dibuka yang dipegang di memori
  (halaman yang sedang dibuka dan sebelahnya selalu tetap). Ini mengurangi jeda akibat pembersihan memori.

## Tambahan 0.1.48 ke atas

- **Kertas tidak lagi terpotong saat digeser atau diperbesar:** sebelumnya yang bergerak hanya gambar layar terakhir,
  jadi bagian halaman yang baru masuk layar kosong abu-abu sampai jari diangkat. Kini seluruh halaman disiapkan di
  latar sebagai alas, sehingga bagian yang masuk layar langsung tampil berisi. Pada ukuran pas-lebar alas ini
  setajam layar; saat sangat diperbesar, bagian yang baru masuk sedikit kurang tajam sesaat.
- **Tidak tersendat saat jari diangkat:** gambar tajam untuk posisi baru dibuat di latar sementara gambar lama tetap
  tampil mengikuti posisi baru, lalu ditukar begitu siap. Menulis atau menghapus sebelum siap langsung memakai cara
  lama (digambar seketika), jadi coretan tidak pernah hilang sesaat.

## Tambahan 0.1.52 ke atas: menyunting langsung di halaman

- **Teks diketik langsung di kertas** (seperti di Canva): tidak ada lagi kolom ketik atau jendela tersendiri. Ketuk
  teks, kursor muncul tepat di huruf yang diketuk dan keyboard terbuka. Ketuk di dalam teks untuk memindahkan
  kursor, ketuk dua kali untuk memilih satu kata, seret untuk memilih beberapa huruf.
- **Bilah alat kecil melayang di dekat teks:** huruf, ukuran (− / +), warna, perataan, daftar, Salin, Potong, Tempel,
  Pilih semua, dan Selesai. Bacaan lain hasil ubah tulisan tangan ("Mungkin:") juga di sini.
- **Sisipkan teks dan daftar centang** langsung membuat kotak teks di halaman yang siap diketik. Bila tidak diisi,
  kotaknya hilang sendiri.
- **Isi stiker** juga diketik langsung di atas stikernya.
- **Mind map disunting di halaman**, tidak lagi di layar terpisah. Sisipkan mind map → langsung muncul di halaman dan
  ide utamanya siap diketik. Ketuk ide untuk memilih, ketuk lagi untuk mengetik di dalamnya, seret ide untuk
  memindahkan (cabangnya ikut; seret ide utama untuk memindahkan seluruh mind map). Enter = ide berikutnya.
  Bilah alat di dekat ide: Ketik, + Cabang, + Sejajar, Warna, Hapus, Rapikan, Selesai.
- Selesai dengan mengetuk di luar, tombol Selesai, tombol kembali, atau menurunkan keyboard. Tiap langkah bisa
  diurungkan.

## Tambahan 0.1.53 ke atas

- **Membalik halaman tidak berkedip lagi:** gelap-terang gulungan kertas dulu digambar sebagai lapisan terpisah yang
  di tablet ini kadang ikut tampil kadang tidak, sehingga punggung kertas tampak berkedip. Kini gelap-terangnya
  menyatu dengan gambar kertasnya (satu kali gambar), jadi stabil dan sedikit lebih ringan.
- **Foto dan PDF tidak muncul belakangan saat halaman dibalik atau baru dibuka:** gambar yang belum siap dibuka saat
  itu juga, jadi halaman langsung tampil lengkap tanpa kedipan.

## Tambahan 0.1.54 ke atas

- **Membalik halaman langsung mengikuti jari:** puncak gulungan kertas kini tepat di bawah jari, jadi lembaran
  bergerak bersama jari sejak awal (dulu bergerak setengah kecepatan jari dan mulai dari tepi kertas, sehingga
  terasa tertinggal). Saat mulai, lembaran menyusul jari dengan cepat dan mulus.
- **Tanpa jeda saat mulai membalik:** halaman sebelah dan halaman ini sudah digambar lebih dulu di latar, jadi saat
  jari mulai membalik tidak ada lagi yang perlu digambar atau disalin (dulu bisa tertahan sepersekian detik).
  Bila belum siap (misalnya baru saja menulis lalu langsung membalik), dipakai cara lama.

## Tambahan 0.1.55 ke atas

- **Simpan ke Google Keep:** di jendela Ekspor ada tombol "Simpan ke Google Keep". Catatan baru langsung dibuka di Keep:
  judulnya nama catatan, isinya teks ketik dan tulisan tangan yang terbaca jadi teks, ditambah gambar halaman (halaman
  ini atau semua halaman, sesuai pilihan). Google tidak menyediakan sambungan langsung ke Keep untuk akun pribadi, jadi
  ini lewat fitur berbagi Android: setiap kali dikirim jadi catatan baru di Keep, dan perubahan di Keep tidak kembali
  ke Hana Note.
- Bila Keep belum terpasang, muncul pilihan aplikasi lain untuk menyimpan gambarnya.

## Tambahan 0.1.56 ke atas

- **Putar halaman dengan dua jari:** letakkan dua jari lalu putar seperti memutar kertas di meja. Supaya mencubit
  biasa (perbesar/perkecil) tidak ikut memiringkan, halaman baru berputar setelah jari jelas diputar. Saat jari
  dilepas dekat posisi tegak (atau kelipatan 90°), halaman otomatis lurus.
- **Geser kertas ke kanan, kiri, atas, atau bawah dengan dua jari:** kertas boleh dibawa jauh dari tengah layar,
  jadi pinggir kanan, kiri, atau bawah kertas bisa ditaruh di tempat yang nyaman untuk ditulisi. Geser satu jari
  ke atas-bawah juga boleh melewati ujung kertas sedikit.
- **Tombol "Luruskan"** muncul di pojok kiri atas kanvas saat halaman miring atau digeser jauh: ketuk untuk
  mengembalikannya tegak di tengah (ukuran perbesaran tetap).
- Sapuan satu jari ke samping tetap membalik halaman. Saat halaman sedang miring, sapuan tidak membalik halaman;
  luruskan dulu, atau pakai tombol panah halaman. Ganti halaman selalu dimulai dalam posisi tegak.

## Tambahan 0.1.57 ke atas

- **Jadi teks langsung tersambung:** tulisan tangan yang dipilih langsung berubah jadi SATU teks ketik yang
  bersambung di tempat tulisannya, tanpa jendela daftar baris dan centang. Baris-baris disambung jadi kalimat
  (kata yang terpotong tanda hubung di ujung baris disatukan lagi); hanya jarak kosong yang lebar antar-paragraf
  dan awal butir daftar yang jadi baris baru. Selama dibaca, muncul tanda kecil "Membaca tulisan…" dengan tombol
  Batal. Hasilnya bisa langsung diketuk untuk diperbaiki, dan urungkan mengembalikan tulisan tangannya.

## Tambahan 0.1.58 ke atas

- **Ukuran huruf bisa diketik angkanya:**
  - Saat mengetik teks di halaman: ketuk angka ukuran di bilah alat melayang, keyboard berganti ke angka, ketik
    ukurannya (misalnya 18 atau 12,5) lalu Selesai/OK. Ukurannya langsung terlihat sambil diketik.
  - Saat teks atau stiker dipilih (alat Pilih): bilah aksi di atas punya kolom ukuran (pt) yang bisa diketuk dan
    diketik, plus tombol − dan + (naik-turun 1 pt).
  - Ukuran 6–120 pt. Mengubah ukuran dengan menarik pegangan pilihan tetap bisa.

## Tambahan 0.1.59 ke atas: widget layar utama

- **Widget "Catatan terbaru"** (4×3, bisa diubah ukurannya): empat catatan yang terakhir diubah beserta gambar
  halaman pertamanya; ketuk untuk langsung membuka catatannya. Tombol **+** membuat catatan baru (kertas seperti
  pilihan terakhir, di buku yang terakhir dipakai) dan langsung membukanya.
- **Widget "Satu catatan"** (2×3): saat dipasang, pilih catatannya; widget menampilkan halaman pertama dan judulnya,
  ketuk untuk membuka. Di Android 12 ke atas bisa diatur ulang (tahan widget → ubah).
- Widget ikut diperbarui saat catatan dibuat, diganti nama, dihapus, atau selesai ditulis (gambar kecilnya dibuat
  saat keluar dari catatan).
- Cara memasang di HyperOS: tahan bagian kosong layar utama → Widget → cari "Hana Note".

## Tambahan 0.1.60 ke atas: mode Teks ala Word, checklist, widget bergulir

- **Mode Pensil dan mode Teks:** saklar *Pensil | Teks* di kiri baris alat (tablet), atau tombol keyboard di baki
  alat (ponsel dan layar penuh).
  - **Teks:** pena dan jari tidak menulis. Ketuk di mana saja di halaman lalu langsung ketik, seperti di Word:
    baris mengalir sampai tepi kanan halaman, Enter membuat paragraf baru. Ketuk tepat di bawah teks yang sudah
    ada untuk melanjutkannya (baris kosong ditambahkan sampai ke tempat yang diketuk; bila tidak jadi diisi,
    dibuang lagi). Ketuk teks untuk menaruh kursor di huruf itu, ketuk stiker untuk mengetik isinya. Geser
    dengan jari atau pena untuk menggulir, dua jari untuk memperbesar.
  - Pita format di atas (tablet): huruf, ukuran (bisa diketik), **B** tebal, *I* miring, U garis bawah,
    S coret, warna, perataan, daftar berpoin/bernomor/centang (hanya untuk paragraf di kursor), salin, potong,
    tempel, pilih semua, Selesai. Belum mengetik? Pita mengatur gaya untuk teks berikutnya.
  - Tebal/miring/garis bawah/coret berlaku per huruf: pilih kata lalu tekan tombolnya, atau tekan tombolnya
    dulu lalu ketik. Tersimpan bersama catatan (format berkas versi 12).
  - **Pensil:** kembali menulis dengan alat terakhir.
- **Checklist dicentang manual:** ketuk kotaknya untuk mencentang, dan kalimatnya langsung tercoret. Bisa dengan
  jari atau pena, di mode Pensil, Teks, maupun Pilih, juga saat teks itu sedang diketik dan di dalam stiker.
  Bidang ketuknya lebih lebar, dan ketukan pena yang cepat tidak lagi jadi titik. Bisa diurungkan.
- **Widget lebih jelas dan bisa digulir:**
  - *Catatan terbaru:* daftar sampai 40 catatan yang bisa digulir, dengan judul besar, waktu ubah, dan cuplikan
    isinya (teks ketik dan tulisan tangan yang sudah terbaca).
  - *Satu catatan:* isi catatan bisa digulir ke atas dan bawah. Di atas ada *Daftar centang* (semua butir
    centang di catatan itu): ketuk butirnya untuk mencentang langsung dari layar utama, tanpa membuka aplikasi,
    dan kalimatnya tercoret. Di bawahnya halaman-halaman catatan selebar widget (bagian kosong dipangkas),
    ketuk untuk membuka catatan tepat di halaman itu. Perbesar widgetnya supaya tulisan makin besar.

## Tambahan 0.1.61 ke atas

- **Mode Teks tanpa kotak:** saat mengetik di mode Teks, tidak ada lagi kotak/bingkai di sekeliling teks; hanya
  kursor dan huruf, seperti mengetik di Word.
- **Mode Pensil: teks bisa dipilih dan dipindah.** Ketuk teks sekali (jari atau pena): teks terpilih dan bisa
  digeser, diubah ukurannya, atau dihapus lewat bilah pilihan. **Ketuk dua kali** untuk langsung mengetik di
  teks itu (kursor di huruf yang diketuk). Sentuh tempat lain untuk melepas pilihan; pena langsung menulis lagi.
  Di mode Teks, ketuk teks tetap langsung mengetik.

## Tambahan 0.1.62 ke atas

- **Rekam suara dan Dikte tidak langsung mulai:** mengetuk ikonnya hanya membuka jendelanya. Pilih bahasa dulu
  (untuk dikte) dan bersiap, lalu ketuk tombol bulat besar atau **Mulai rekam / Mulai dikte**. Batal menutup
  jendela tanpa merekam apa pun. Setelah mulai, Jeda/Lanjut dan Selesai/Sisipkan tetap seperti sebelumnya.

## Tambahan 0.1.63 ke atas: Sampah

- **Sampah:** menghapus catatan atau buku tidak lagi langsung hilang; isinya dipindah ke Sampah (ikon tempat
  sampah di bilah atas rak, dengan angka jumlah isinya).
- Di Sampah: **Pulihkan** mengembalikan catatan ke bukunya (bila bukunya sudah tidak ada, ke buku pertama di
  rak); memulihkan buku mengembalikan buku beserta semua catatan yang ikut terhapus bersamanya. Ikon merah
  **Hapus permanen** dan tombol **Kosongkan sampah** menghapus selamanya (dengan konfirmasi).
- Isi Sampah yang sudah lebih dari 30 hari terhapus permanen dengan sendirinya (diperiksa saat aplikasi dibuka).
- Isi Sampah tidak ikut dicadangkan dan tidak muncul di pencarian atau widget. Memulihkan cadangan yang berisi
  catatan yang sedang di Sampah mengembalikannya ke rak.

## Tambahan 0.1.64 ke atas: teks duduk di garis kertas

- **Kertas bergaris:** teks ketik baru (mode Teks, atau tombol Teks) langsung duduk tepat di atas garis kertas:
  baris pertamanya di garis terdekat di bawah titik yang diketuk, dan jarak antarbarisnya sama dengan jarak garis
  (tiap Enter turun tepat satu garis; huruf besar memakai dua garis). Garis dasarnya tidak bergeser walau
  teksnya panjang.
- Mulai mengetik dari kiri halaman kini dimulai di kanan garis margin merah, bukan menimpanya.
- Kursor hanya setinggi huruf dan berhenti di garis, tidak menembus ke bawah garis.
- Mengubah ukuran atau jenis huruf teks bergaris tetap menjaga baris pertamanya di garis yang sama; menggeser teks
  bergaris (tanpa mengubah ukurannya) membuatnya kembali duduk di garis terdekat.
- Format berkas versi 13 (menyimpan jarak baris teks).

## Tambahan 0.1.65 ke atas: nama baru WInote

- Aplikasi berganti nama menjadi **WInote**, dengan logo baru: huruf W tulisan tangan dan pensil sebagai huruf I,
  di atas garis kertas. Di Android 13 ke atas, logo ikut warna tema bila ikon bertema dinyalakan.
- Ini pembaruan biasa: pasang menimpa versi lama, semua catatan, buku, dan pengaturan tetap ada (identitas
  aplikasi di sistem tidak berubah).
- Berkas cadangan baru bernama "WInote-cadangan-….zip". Cadangan lama "HanaNote-cadangan-….zip" tetap bisa
  dipulihkan dan ikut dirapikan saat cadangan lama dihapus otomatis.
- Warna aksen "Hana" berganti nama jadi "Anggur" (warnanya sama).
- File APK sekarang bernama WInote-0.1.N.apk.

## Tambahan 0.1.66 ke atas: Mode Baca

- **Mode baca:** ikon buku terbuka di bilah atas penyunting. Catatan terkunci, hanya untuk dibaca:
  - pena dan jari hanya menggulir; dua jari memperbesar; sapu ke samping membalik halaman;
  - rekaman suara tetap bisa diputar dan link tetap bisa dibuka;
  - tidak bisa menulis, menghapus, memilih, mengetik, mencentang, mengurungkan, mengganti nama, atau
    menambah/memindah/menghapus halaman. Bilah alat dan fitur disembunyikan supaya halaman lebih luas.
- Tanda "Mode baca" tampil di atas halaman, dengan tombol **Sunting** untuk kembali menyunting. Di mode baca,
  tombol Layar penuh memberi tampilan baca penuh layar; tombol bagikan/ekspor tetap ada.
- Mode baca diingat per catatan: catatan yang ditutup dalam mode baca dibuka lagi dalam mode baca.

## Tambahan 0.1.69 ke atas: mengetik di dalam tabel

- **Ketuk dua kali tabel** (jari atau pena, di mode Pensil; atau ketuk dua kali tabel yang sedang dipilih di
  mode Pilih) untuk langsung mengetik di sel yang diketuk; keyboard muncul. Di mode Teks cukup ketuk sekali.
  Dengan pena, titik dari ketukan pertama dibuang otomatis.
- Teks sel menjadi bagian dari tabel: mengalir mengikuti lebar kolom, di tengah tinggi baris, dan baris yang
  teksnya tidak muat otomatis dipertinggi (seperti tabel di Word). Baris judul ditebalkan.
- Tabel digeser, diperbesar, atau diubah susunannya: teksnya ikut dan tetap di selnya (mengalir ulang bila
  lebarnya berubah). Menambah/mengurangi baris atau kolom tidak mengacak isi sel.
- Saat mengetik di sel: ketuk sel lain di tabel yang sama untuk langsung pindah; tombol ‹ › untuk sel
  sebelumnya/berikutnya (setelah sel terakhir, baris baru ditambahkan); tombol "+ Baris" menambah baris di
  bawahnya. Huruf, ukuran, dan perataan berlaku untuk seluruh tabel. Tab di keyboard fisik pindah ke sel berikutnya.
- Isi tabel ikut dicari dan tampil di cuplikan widget. Format berkas versi 14.

## Tambahan 0.1.71 ke atas: garis dalam tabel bisa digeser

- Pilih tabel (alat Pilih), lalu **geser garis di dalamnya**: garis kolom mengubah lebar dua kolom di kiri-kanannya,
  garis baris mengubah tinggi dua baris di atas-bawahnya. Ukuran luar tabel tetap.
- Tanda pil kecil di atas tiap garis kolom dan di kiri tiap garis baris menunjukkan garis yang bisa digeser (dan
  bisa dipegang juga); garis yang sedang digeser disorot.
- Teks di sel ikut mengalir ulang; baris yang teksnya jadi tidak muat otomatis dipertinggi, dan baris tidak bisa
  dibuat lebih pendek dari teksnya.
- Saat sedang mengetik di sel, garis dalam tabel itu juga bisa langsung digeser.
- Bisa diurungkan.

## Tambahan 0.1.72 ke atas: Ruang Gambar

- **Tombol + di beranda** (rak dan isi buku) sekarang punya pilihan **Gambar baru**, selain Buku/Catatan baru.
  Ini membuka ruang gambar khusus (mesin dari aplikasi Goresan): kanvas OpenGL berlapis, layar penuh, gelap
  seperti Procreate. Pilih ukuran kanvas, lalu mulai menggambar.
- Gambar terbaru tampil di deretan **Gambar** di atas rak; ketuk untuk melanjutkan, **Lihat semua** untuk galeri
  (ganti nama, hapus, kanvas baru). Selesai menggambar (tombol Galeri/kembali) langsung kembali ke beranda.
- Fitur di ruang gambar:
  - **Masukan jari atau pena:** tombol "Pena saja / Jari + pena" di bilah atas (juga di Aksi).
  - **Layer:** tambah, duplikat, gabung, urutan, opasitas, 26 mode campur, **Alpha Lock**, Clipping Mask, kunci.
  - **ColorDrop:** seret lingkaran warna (kanan atas) ke objek untuk mewarnai area itu; atur ambangnya.
  - **Kuas:** 22 kuas bawaan + Studio Kuas; **Impor kuas** (di panel kuas): berkas **.brush/.brushset**
    Procreate (bentuk ujung dan tekstur, nama, jarak, sebaran) atau **gambar PNG/JPG** sebagai cap kuas.
    Kuas impor masuk kategori sendiri dan bisa diubah/dihapus.
  - **Ekspor:** PNG (biasa/transparan) dan JPG ke Galeri › Pictures/WInote, **PDF** dan PSD berlapis ke
    Download/WInote, bagikan ke aplikasi lain.
  - Urungkan/ulangi, transformasi, penyesuaian warna, blur, pipet, sisipkan foto.
- Butuh Android 10 ke atas. Gambar disimpan terpisah dari catatan dan belum ikut cadangan WInote.

## Perbaikan 0.1.78: Ruang Gambar

- **Ganti kuas tidak lagi membuat kanvas macet.** Membuka pustaka kuas membuat pratinjau goresan tiap kuas, dan
  salah satu hitungannya menghasilkan NaN sehingga mesin gambar berputar tanpa akhir sampai memori habis; setelah
  itu goresan apa pun tidak tampil. Hitungan diperbaiki dan pemroses goresan kini kebal terhadap nilai tidak wajar
  (serta dibatasi jumlah cap per goresan).
- **ColorDrop tidak lagi mengunci alat.** Seret lingkaran warna ke dalam bentuk tertutup: area langsung terisi dan
  bilah ambang muncul. Isian tersimpan otomatis begitu mulai menggores lagi, ganti kuas, membuka panel, menjatuhkan
  warna lagi, atau menutup gambar; "Batal"/tombol kembali membatalkannya.

## Tambahan 0.1.79: Bentuk otomatis di Ruang Gambar

- **Tahan pena di ujung goresan** (sekitar setengah detik) dan coretan tangan langsung dirapikan menjadi bentuk:
  garis lurus, lingkaran, elips, kotak/persegi panjang (juga yang miring), segitiga, segi banyak, garis patah
  (V/L), atau busur. Coretan yang bukan bentuk (tulisan, arsiran, spiral) dibiarkan apa adanya.
- Selama pena masih ditahan, geser pena untuk **mengubah ukuran dan memutar** bentuknya (garis lurus: ujungnya
  ikut pena).
- Ketebalan dan warna mengikuti kuas dan tekanan pena saat menggambar.
- **Urungkan sekali** mengembalikan coretan tangan aslinya; urungkan lagi menghapusnya.
- Bisa dimatikan di Aksi › Preferensi › **Bentuk otomatis**.
- Stabilisasi garis sudah ada per kuas: ketuk kuas yang dipilih › Studio Kuas › Goresan › **StreamLine** dan
  **Stabilisasi**.

## Perbaikan 0.1.80: ColorDrop dan penghapus

- **ColorDrop tanpa pita pucat:** isian kini menembus sisi lembut garis (bagian tepi yang setengah transparan),
  jadi tidak ada garis putih tipis di antara garis dan isian.
- **Garis dan isian sewarna menyatu:** bila warna garis sama dengan warna isian, isian juga mengisi di belakang
  badan garis. Garis dari kuas yang tidak 100% pekat (aliran/flow di bawah 100%) jadi tampak sama persis dengan
  isiannya; tepi luar garis tetap halus. Garis yang warnanya beda (mis. hitam) tetap seperti aslinya.
- **Penghapus tidak berhenti di tengah jalan:**
  - Bentuk otomatis hanya berlaku untuk kuas, tidak untuk penghapus/baur (berhenti sejenak saat menghapus aman).
  - Telapak tangan yang menyentuh layar dalam 1,5 detik setelah pena menyentuh/melayang diabaikan: tidak memicu
    pipet, tidak dianggap ketukan dua/tiga jari (urungkan/ulangi).
  - Bila telapak tangan sempat memicu pipet, pena yang menyentuh langsung membatalkannya dan menggambar.
  - Bila sistem merebut sentuhan di tengah goresan pena (mis. gestur tiga jari HyperOS), goresan yang sudah
    dibuat disimpan, bukan dibuang.

## Tambahan 0.1.81: Grup layer, dan goresan tidak putus

- **Goresan pena tidak putus lagi di tengah jalan.** Penyebabnya simpan otomatis (4 detik setelah goresan
  sebelumnya) yang menutup paksa goresan yang sedang berjalan. Sekarang simpan otomatis menunggu sampai pena
  diangkat. Bentuk otomatis juga dibuat lebih ketat: pena harus benar-benar diam 0,7 detik.
- **Grup layer** (seperti folder di Procreate):
  - Panel Layer › **Pilih** › ketuk beberapa layer › **Kelompokkan**. Atau ketuk layer aktif › *Buat grup dari
    layer ini*.
  - Kepala grup: lipat/buka (panah), sembunyikan/tampilkan seluruh isi grup (mata), dan menu: ganti nama,
    pisahkan (bubarkan), **ratakan jadi satu layer**, duplikat, naikkan/turunkan grup, hapus grup & isinya.
  - Naikkan/Turunkan layer memperhatikan grup: di ujung grup layer keluar dari grup; layer lepas yang bertemu grup
    terbuka masuk ke grup itu, grup terlipat dilompati. Menu layer juga punya *Keluarkan dari grup*.
  - Layer baru ikut masuk grup layer yang sedang aktif.
  - Semua perubahan grup bisa diurungkan, tersimpan di karya, dan ikut ke **PSD sebagai folder grup** Photoshop.

## Tambahan 0.1.82: Tahan & seret layer

- Di panel Layer, **tahan sebuah layer (atau kepala grup) sebentar** sampai bergetar, lalu seret:
  - **Di celah antar-layer**: layer dipindah ke situ. Garis biru menunjukkan tempatnya; garis menjorok berarti
    layer akan masuk ke grup. Di celah tepat di bawah anggota terakhir grup, lepas di **sisi kanan** untuk masuk ke
    grup atau **sisi kiri** untuk tetap di luar (di bawah grup).
  - **Di atas layer lain** (bingkai biru): kedua layer langsung jadi grup baru. Bila layer itu sudah di dalam grup,
    layer yang diseret ikut masuk ke grup tersebut.
  - **Di atas kepala grup**: masuk ke grup itu (paling atas).
  - **Seret kepala grup** untuk memindah seluruh grup (grup tidak bisa dimasukkan ke grup lain).
- Daftar bergulir sendiri bila diseret ke tepi atas/bawah. Semua pemindahan bisa diurungkan.

## Tambahan 0.1.83: Mode campur ala Procreate, gambar di dalam buku

- **Pengaturan layer seperti Procreate:** ketuk huruf mode (mis. "N") di baris layer. Muncul opasitas dengan
  persen, lalu daftar semua mode campur berkelompok (Multiply…Darker Color · Normal · Lighten…Lighter Color ·
  Overlay…Hard Mix · Difference…Divide · Hue…Luminosity) lengkap dengan ikon; yang terpilih disorot biru dan
  langsung diterapkan (bisa diurungkan).
- **Gambar ada di dalam buku**, bukan lagi di deretan "Gambar" di atas rak:
  - Di dalam buku, tombol + › **Gambar baru**. Gambarnya tampil di antara catatan (keterangan "Gambar") dan dibuka
    di ruang gambar saat diketuk.
  - Bisa disematkan, diganti nama (nama karya ikut berubah), dipindah ke buku lain, diurutkan, dan dihapus ke
    Sampah seperti catatan; hapus permanen ikut menghapus berkas gambarnya.
  - Gambar lama yang belum punya buku dipindah otomatis ke buku **"Gambar"**.
  - Gambar kini ikut **cadangan WInote** (dan dipulihkan bersamanya).

## Tambahan 0.1.84: Pindah beberapa layer atau satu grup bersama

- **Di kanvas:** alat Transformasi (panah) kini menggeser, memutar, mengubah ukuran, dan membalik **semua layer yang
  dipilih** sekaligus. Caranya: panel Layer › **Pilih** › ketuk layer-layer (ketuk kepala grup = pilih seluruh isinya)
  › **Pindah bersama** (atau ketuk ikon panah). Untuk satu grup: ketuk kepala grup › **Pindah & ubah ukuran grup**.
  Hasilnya satu langkah urungkan untuk semua layer. Pratinjau diambil langsung dari tiap layer, jadi tidak butuh
  memori tambahan walau layernya banyak.
- **Di panel:** saat mode Pilih, tahan & seret salah satu layer terpilih untuk memindah **semua yang dipilih**
  bersama (urutannya tetap) ke celah lain, ke dalam grup, atau lepas di atas layer lepas untuk menjadikannya grup
  baru. Grup yang seluruh isinya ikut dipilih tetap utuh.

## Tambahan 0.1.85: Semua penyesuaian ala Procreate

Menu **Penyesuaian** (ikon tongkat ajaib) kini berisi seluruh daftar Procreate, dikelompokkan Warna · Blur · Efek ·
Alat. Semuanya bekerja pada layer aktif dengan pratinjau langsung, **Terapkan/Batal**, dan satu langkah urungkan.

- **Warna:** Rona/Saturasi/Kecerahan, **Keseimbangan Warna** (Bayangan, Nada tengah, Sorotan: Sian↔Merah,
  Magenta↔Hijau, Kuning↔Biru), **Kurva** (Gamma, Merah, Hijau, Biru; ketuk untuk menambah titik, seret untuk
  mengubah), **Peta Gradien** (8 preset + "Warna kuas", jumlah 0–100%).
- **Blur:** Gaussian, **Blur Gerak** (jumlah + arah), **Blur Perspektif** (Posisional/Searah; ketuk kanvas untuk
  memindah titik pusat).
- **Efek:** **Noise** (Awan, Gelombang, Punggung; skala, oktaf, turbulensi, acak lagi), **Pertajam**, **Bloom**
  (jumlah, transisi, ukuran, bakar), **Glitch** (Artefak, Gelombang, Sinyal, Pecah), **Halftone** (Warna penuh,
  Sablon, Koran), **Aberasi Kromatik** (Perspektif dari titik pusat, atau Geser satu arah).
- **Liquify:** Dorong, Putar kanan, Putar kiri, Cubit, Kembangkan, Kristal, Tepi, Pulihkan; atur Ukuran, Tekanan,
  Distorsi, dan **Sesuaikan** (mengecilkan seluruh efek), plus **Atur ulang**. Tekanan pena ikut dihitung; mode
  putar/cubit/kembangkan terus bekerja selama pena ditahan. Dua jari tetap untuk geser/perbesar kanvas.
- **Klon:** geser lingkaran sumber dengan jari (atau **Atur sumber** lalu ketuk kanvas), lalu gambar dengan kuas
  apa pun untuk menyalin dari sumber. Sumber ikut bergerak bersama pena dengan jarak tetap, seperti di Procreate.
- Saat penyesuaian terbuka, jari tetap bisa menggeser/memperbesar kanvas untuk melihat hasilnya.

## Tambahan 0.1.86: Nomor layer baru urut

- Layer baru kini diberi nama **Layer N** dengan N = jumlah layer + 1 (sama dengan hitungan di panel Layer), jadi
  menambah layer berturut-turut menghasilkan Layer 2, Layer 3, Layer 4, dst. Bila nama itu sudah dipakai, nomornya
  dinaikkan supaya tidak kembar. Grup baru memakai aturan yang sama (Grup 1, Grup 2, …).
- Nama layer lama di karya yang sudah ada tidak diubah.

## Tambahan 0.1.87: Bentuk otomatis untuk penghapus

- Tahan pena di ujung goresan **penghapus** kini juga merapikan hapusan jadi garis, lingkaran, elips, kotak,
  segitiga, atau busur (sama seperti kuas). Sekali urungkan mengembalikan hapusan tangan aslinya.
- Bisa dimatikan sendiri di Pengaturan › **Bentuk otomatis untuk penghapus**, bila penghapusan terasa berhenti saat
  pena diam sebentar. Alat baur tetap tidak pernah diubah jadi bentuk.

## Tambahan 0.1.88: Bentuk presisi & transformasi ala Procreate

**Bentuk presisi (QuickShape + satu jari).** Setelah bentuk otomatis muncul dan pena masih ditahan, sentuhkan satu
jari di layar: bentuknya jadi presisi.
- Elips → **lingkaran sempurna**, kotak → **persegi**, segitiga → **segitiga sama sisi**, segi banyak → segi-n
  beraturan.
- Garis lurus dan garis patah → sudut kelipatan 15°; busur → sapuan kelipatan 45° (setengah lingkaran, seperempat, …).
- Selama jari ditahan, memutar bentuk dengan pena ikut dibulatkan per 15°. Berlaku juga untuk penghapus.

**Transformasi** (ikon panah) kini seperti Procreate, dengan bilah di bawah:
- **Bebas**: seret sudut/tepi untuk mengubah ukuran tanpa menjaga proporsi.
- **Seragam**: seret sudut/tepi, proporsi tetap.
- **Distorsi**: seret sudut untuk perspektif, seret tepi untuk memiringkan.
- **Warp**: kisi 4×4; seret titik atau bagian dalam kotak untuk melengkungkan (satu layer).
- Bulatan hijau di atas kotak untuk **memutar**; dua jari tetap untuk geser/perbesar/putar.
- **Snapping**: kotak menempel ke tengah dan tepi kanvas (garis bantu oranye), putaran per 15°.
- **Balik H**, **Balik V**, **Putar 45°**, **Pas kanvas**, **Atur ulang**.
- **Interpolasi**: Terdekat (piksel tajam) / Bilinear / Bikubik — ketuk untuk berganti; tersimpan.

## Tambahan 0.1.89: Bentuk presisi tanpa jari kedua

- Di Android, sentuhan jari biasanya ditahan sistem selama pena menempel layar (penolakan telapak tangan), jadi jari
  kedua sering tidak sampai ke aplikasi. Sekarang bentuk presisi bisa didapat dengan:
  - **Tahan lebih lama**: sesudah bentuk rapi muncul, tetap tahan pena diam sekitar 1 detik lagi → lingkaran
    sempurna, persegi, segitiga sama sisi, segi-n beraturan, garis per 15°. Lepas lebih cepat bila ingin bentuk
    biasa (elips, kotak panjang). Bisa dimatikan di Pengaturan › **Tahan lebih lama = bentuk presisi**.
  - **Tombol di pena** ditekan saat bentuk ditahan.
  - **Jari kedua** tetap berfungsi bila perangkat mengirim sentuhannya (termasuk sebagai aliran sentuhan terpisah
    di Android 15). Bila sistem membatalkan goresan pena karena jari menyentuh saat bentuk ditahan, bentuknya juga
    dijadikan presisi.
- Sentuhan telapak tangan yang datang sebagai aliran terpisah tidak lagi mengganggu goresan pena yang sedang berjalan.

## Tambahan 0.1.90: Pilihan (Selection) ala Procreate

Ikon **Pilihan** (kotak bergaris putus-putus) di bilah atas, di antara Penyesuaian dan Transformasi.
- **Cara memilih:** **Otomatis** (ketuk area berwarna mirip di layer aktif; tahan lalu geser kiri/kanan untuk
  mengubah ambang), **Bebas** (seret untuk melingkari, atau ketuk titik-titik sudut lalu ketuk titik pertama untuk
  menutup), **Persegi**, **Elips** (seret).
- **Tambah / Kurangi**: pilihan baru ditambahkan ke atau dikurangkan dari pilihan yang ada.
- **Balik** (yang tidak terpilih jadi terpilih), **Salin & Tempel** (isi pilihan disalin ke layer baru),
  **Perhalus** (tepi lembut 0–100 px), **Simpan & Muat** (simpan pilihan untuk dipakai lagi selama karya terbuka),
  **Isi warna** (isi pilihan dengan warna aktif), **Hapus** (buang pilihan).
- Bagian di luar pilihan diberi arsir miring; tepinya garis putus-putus.
- Selama ada pilihan: **kuas, penghapus, baur, ColorDrop, Klon, dan semua Penyesuaian** hanya mengubah bagian di
  dalam pilihan; **Transformasi** hanya memindah isi pilihan (bagian lain tetap di tempat) dan pilihannya ikut pindah.

## Tambahan 0.1.91: ColorDrop tanpa garis putih tipis

- Di kanvas besar (sisi ≥ 2400 px), isian ColorDrop di dalam garis yang miring/melengkung (mis. lingkaran di arah
  serong) menyisakan garis putih tipis 1 piksel di tepi dalam garis, dan menggeser ambang tidak menolong. Penyebabnya:
  celah garis ditutup dengan penebalan berbentuk kotak, tetapi isian dikembalikan hanya ke 4 arah. Sekarang
  pengembaliannya juga ke arah serong (tanpa menembus garis serong yang tipis), jadi isian menempel rapat ke garis.

## Tambahan 0.1.92: Tidak tertutup sendiri saat memori penuh

- Laporan galat 0.1.90: aplikasi tertutup sendiri di utas `goresan-isi` (ColorDrop/pilihan) karena memori Java
  (512 MB) habis di kanvas besar (3200 × 2136 = 6,8 juta piksel; satu salinan layer ±27 MB).
- Riwayat urungkan kini paling banyak 25% memori (dulu 35%), dan selalu disisakan memori cadangan untuk kerja berat:
  bila kurang, langkah urungkan **terlama** dibuang dulu (diberi tahu lewat pesan), langkah terbaru tetap.
- Menggeser ambang ColorDrop dengan cepat tidak lagi menghitung ulang setiap nilai yang sudah lewat.
- Bila memori tetap habis di thread bantu, aplikasi tidak tertutup: langkah itu dibatalkan, riwayat lama
  dikosongkan, dan muncul pesan untuk mencoba lagi (ColorDrop mencoba ulang sekali secara otomatis).
- Pemakaian memori dikurangi: pemadatan/pembongkaran riwayat sepotong-sepotong (tanpa salinan penuh), ColorDrop
  melepas data sementara lebih awal, dan Perhalus tepi pilihan memakai 1 byte per piksel (dulu 4).

## Tambahan 0.1.93: Kanvas khusus & pengaturan latar

- **Kanvas baru** kini membuka layar **Kanvas khusus** seperti Procreate:
  - **Ukuran**: Lebar, Tinggi, DPI, dan perkiraan **Maksimal layer**; diisi lewat papan angka sendiri dengan satuan
    **Milimeter / Sentimeter / Inci / Piksel**, tombol Hapus, Berikutnya, Selesai. Mengubah DPI saat satuannya fisik
    (mm/cm/inci) menjaga ukuran cetak dan menyesuaikan jumlah piksel.
  - **Ukuran siap pakai**: layar Xiaomi Pad 7, persegi, A3/A4/A5 (300 DPI), 4K, Instagram 4:5.
  - **Properti kanvas**: warna latar (pemilih warna + contoh warna) dan **Latar disembunyikan** (transparan), dengan pratinjau.
- **Pengaturan latar** di panel Layer: ketuk baris **Warna latar** untuk memilih warna latar (langsung terlihat),
  memakai warna aktif, atau menyembunyikan latar. Selesai = satu langkah urungkan, Batal = kembali seperti semula.
- **DPI** disimpan bersama karya, bisa diubah di **Aksi › Ubah DPI** (piksel tetap), dan ikut ditulis ke file
  PNG/JPG (pHYs/JFIF), PSD (ResolutionInfo), serta menentukan ukuran halaman PDF.

## Tambahan 0.1.94: Kanvas khusus di tablet tegak

- Di tablet tegak (mis. Xiaomi Pad 7 diputar), tombol paling kanan papan angka (Hapus, Berikutnya, Selesai) terpotong.
  Sekarang menu Ukuran / Ukuran siap pakai / Properti kanvas pindah ke atas bila layar sempit, papan angka
  mengikuti lebar layar, dan pratinjau latar turun ke bawah bila tidak muat. Tampilan mendatar tetap seperti dulu.

## Tambahan 0.1.95: Impor kuas Photoshop (.abr)

- **Impor kuas** kini menerima berkas **.abr** Photoshop (versi 1, 2, 6, 7, 10): semua kuas bergambar (sampled)
  dalam satu berkas masuk ke kategori baru dengan nama preset aslinya (mis. "Underline 1" … "Underline 20").
  Diameter, jarak cap, sudut, dan kebulatan dari preset ikut terbaca. Kuas bulat bawaan Photoshop (computed) dilewati.
- Kuas yang gambarnya sangat lebar/besar (garis bawah, ornamen, tanda tangan) dijadikan **stempel**: sekali ketuk =
  satu cap utuh, ukuran tidak mengikuti tekanan; besarnya diatur dengan penggeser ukuran.
- Ujung kuas impor yang besar disimpan sampai 1024 × 1024 supaya cap besar tetap tajam.

## Tambahan 0.1.96: Kuas stempel mengikuti tarikan

- Kuas stempel (mis. Underline dari .abr) dulu tercap kecil berulang-ulang sepanjang goresan. Sekarang
  **satu tarikan = satu cap utuh** yang membentang dari titik awal ke titik akhir tarikan dan ikut arahnya
  (ditarik dari kanan ke kiri pun tetap tegak; hampir mendatar dibuat mendatar). **Ketuk** = satu cap seukuran kuas.
- Studio Kuas punya sakelar **Stempel** untuk menyalakan/mematikan mode ini di kuas mana pun. Kuas stempel yang sudah
  diimpor di 0.1.95 otomatis dikenali, tidak perlu impor ulang.

## Tambahan 0.1.97: Hapus daftar kuas

- Daftar kuas buatan sendiri/impor (mis. "20 Underline Brushes", "arabic 123", "Kuasku") kini bisa **dihapus
  sekaligus** beserta isinya, atau **diganti nama**: pilih daftarnya lalu ketuk **Hapus daftar** / **Ganti nama** di
  atas daftar kuas, atau tekan lama nama daftarnya. Ada konfirmasi sebelum menghapus. Daftar bawaan tidak bisa dihapus.
- **Tekan lama** sebuah kuas: Pengaturan kuas, Duplikat, Hapus kuas (kuas buatan sendiri), atau Kembalikan bawaan.
- Bila kuas yang sedang dipakai (kuas, baur, atau penghapus) ikut terhapus, alat itu kembali ke kuas bawaannya.
  Berkas gambar ujung/tekstur kuas impor ikut dibersihkan.

## Tambahan 0.1.98: Tanpa bulatan saat pena didekatkan

- Bulatan yang muncul saat ujung pena didekatkan ke kanvas kini **tidak tampil**: lingkaran kuas milik aplikasi mati
  bawaan (bisa dinyalakan lagi di **Aksi › Lingkaran kuas saat pena melayang**), dan penunjuk pena dari sistem
  (Android/HyperOS) disembunyikan di atas kanvas.

## Tambahan 0.1.99: Impor .abr lengkap seperti Procreate, dan pola

- Kuas coretan/ornamen dari .abr (mis. kuas tinta coretan) dulu tercap berulang-ulang sepanjang goresan. Sekarang
  gambar besar yang disimpan **seukuran aslinya tanpa dinamika** dikenali sebagai **stempel**: satu tarikan = satu
  coretan utuh, ketuk = satu cap.
- Preset Photoshop dibawa ke kuas seperti Studio Kuas Procreate: **bentuk** (gambar ujung, dibalik bila flip),
  **tekstur/grain** dari pola (pattern) yang dipakai kuas (skala, kedalaman, balik), dan **dinamika**: ukuran dan
  opasitas mengikuti tekanan/kemiringan pena, variasi ukuran/sudut/opasitas, sebaran dan jumlah cap, arah ujung
  mengikuti goresan atau pena. Jarak cap, sudut, dan kebulatan tetap terbaca.
- Kuas **bulat bawaan Photoshop** (computed) kini ikut diimpor sebagai bulat keras/lembut. Ukuran awal tiap kuas =
  diameter presetnya.
- **Impor pola**: berkas **.pat** Photoshop (abu-abu, RGB, indexed, CMYK) dan GIMP jadi **kuas pola**: melukis
  memunculkan pola itu dengan warna yang dipilih.
- Studio Kuas menampilkan **Kekuatan tekstur** dan **Skala tekstur** untuk kuas bertekstur pola hasil impor.

## Tambahan 0.1.100: Panduan menggambar (Drawing Guide) seperti Procreate

- **Aksi › Kanvas › Panduan menggambar** menyalakan/mematikan garis panduan di atas kanvas; **Edit panduan
  menggambar** membuka layar edit seperti Procreate: pita warna garis (plus putih/hitam), jenis panduan, Opasitas,
  Ketebalan, Ukuran grid, dan sakelar **Bantuan menggambar**. Atur ulang / Batal / Selesai di atas.
- Jenis panduan:
  - **Grid 2D** dan **Isometrik**: seret titik hijau untuk memindah, titik biru untuk memutar (menempel per 15°).
  - **Perspektif**: ketuk kanvas untuk membuat 1, 2, atau 3 titik hilang; seret untuk memindah; ketuk titik lalu
    **Hapus titik**. Garis horizon ikut tampil.
  - **Simetri**: Vertikal, Horizontal, Kuadran, Radial, dengan pilihan **Simetri putar** (salinan diputar, bukan
    dicerminkan). Titik hijau/biru untuk memindah/memutar sumbu.
- **Bantuan menggambar** berlaku per layer (seperti Drawing Assist): goresan dikunci ke sumbu grid, sumbu isometrik,
  atau arah ke titik hilang (plus garis tegak/mendatar); di Simetri goresan dicerminkan/diputar. Bisa juga dinyalakan
  dari menu layer (**Bantuan menggambar**); layer yang memakainya diberi label "Bantuan". Memilih Simetri otomatis
  menyalakan bantuan di layer aktif.
- Kuas, penghapus, kuas stempel, bentuk otomatis, dan prediksi goresan ikut tercermin. Alat baur tidak dicerminkan.
- Panduan disimpan bersama karya. Garis panduan hanya tampil di layar, tidak ikut ekspor.

## Tambahan 0.1.101: Kuas cat air natural dan kertas kanvas

- Kategori kuas baru **Cat Air**: Cat Air Utama, Cat Air Basah, Detail Cat Air, Lapisan Tipis, Kuas Kering, Percikan
  Cat Air. Sapuan cat air diolah seperti cat sungguhan: **tepi basah yang tak rata** dan sedikit **lebih gelap saat
  mengering**, **bercak** di dalam sapuan, pigmen **mengendap di lekukan kertas** (granulasi), dan **lapisan menumpuk**
  (sapuan di atas cat jadi lebih pekat). Penghapus dengan kuas cat air mengangkat cat dengan tepi lembut.
- Studio Kuas punya bagian **Cat air** (Efek cat air, Kepekatan pigmen, Tepi gelap, Tepi lembut, Bercak, Granulasi,
  Lapisan menumpuk) untuk kuas mana pun, termasuk kuas impor.
- **Kertas kanvas** (Aksi › Kertas kanvas, tombol Tekstur kertas di panel Latar, atau saat membuat kanvas baru):
  Cat air, Cat air kasar, Cat air halus, Kertas sketsa, Kanvas, Kertas serat, Bergaris, Kotak-kotak, Titik-titik, atau
  **gambar sendiri** (foto kertas PNG/JPG dibuat bersambung otomatis, atau pola .pat). Kekuatan dan skala bisa diatur.
  Tekstur kertas tampak di cat dan latar, ikut ekspor PNG/JPG/PDF (selama latar tidak disembunyikan).

## Tambahan 0.1.102: Teks di ruang gambar

- **Aksi › Tambah teks** membuka layar Teks: ketik teks (Enter = baris baru), lalu atur di tab:
  - **Font**: 26 font bawaan dalam kategori **Komik** (Bangers, Luckiest Guy, Comic Neue, Permanent Marker, Chewy,
    Titan One, Bubblegum Sans, Patrick Hand), **Artistik** (Pacifico, Lobster, Dancing Script, Caveat, Amatic SC,
    Shadows Into Light, Fredericka the Great, Rubik Wet Paint, Monoton, Righteous), **Eksklusif** (Playfair Display,
    Cinzel, Great Vibes, Pinyon Script, Parisienne, Italiana, Marcellus, Abril Fatface), dan **Dasar** (font sistem).
    Lisensi font (OFL/Apache) ada di assets/fonts/LISENSI-FONT.txt.
  - **Gaya**: tebal, miring, garis bawah, huruf besar semua, rata kiri/tengah/kanan, ukuran huruf, skala, jarak huruf,
    jarak baris, warna.
  - **Efek**: garis tepi (tebal + warna) dan bayangan (geser, kabur, warna).
  - **Bentuk**: baris **lurus**, **lengkung** (ke atas/ke bawah), **gelombang** (tinggi dan panjang), **lingkaran**,
    atau **jalur bebas** yang digambar sendiri di kanvas; bisa dibalik sisi.
- Di kanvas: seret teks untuk memindah, seret titik biru untuk memutar dan mengubah ukuran (menempel per 90°); dua
  jari tetap menggeser/memperbesar kanvas.
- Teks jadi **layer teks** (label "Teks") yang bisa diedit lagi lewat menu layer › **Edit teks**.

## Tambahan 0.1.103: Urungkan/ulangi responsif dan stabilisasi gaya Procreate

- **Urungkan/ulangi lebih cepat dan tidak lagi "diabaikan"**:
  - Ketuk dua jari (urungkan) dan tiga jari (ulangi) tetap bekerja walau pena sedang melayang di dekat layar;
    yang menahan ketukan hanya sentuhan pena yang baru saja terjadi (telapak tangan saat menggores).
  - **Tahan dua jari** = urungkan berulang, **tahan tiga jari** = ulangi berulang, makin lama makin cepat (seperti
    Procreate). Tombol Urungkan/Ulangi di bilah samping langsung bekerja saat disentuh dan mengulang bila ditahan.
  - Langkah riwayat terbaru disimpan mentah di memori (tidak perlu dibongkar dulu), hanya bagian kanvas yang berubah
    yang digambar ulang, dan urungkan yang ditekan saat goresan masih diproses tetap dijalankan.
  - Pesan singkat menyebut langkahnya ("Urungkan goresan", "Ulangi layer", "Tidak ada yang bisa diurungkan").
- **Stabilisasi seperti Procreate** (Studio kuas › bagian **Stabilisasi**):
  - **StreamLine** — Jumlah: goresan menyusul pena dengan halus dan menyusul penuh saat pena diangkat; Tekanan:
    perubahan tekanan dihaluskan.
  - **Stabilisasi** — Jumlah: makin cepat menggores makin halus hasilnya; gerak pelan tetap teliti.
  - **Penyaring gerak** — Jumlah: getaran tangan dibuang, garis lurus dan mulus berapa pun kecepatannya;
    **Ekspresi** mengembalikan sebagian karakter tangan.
  - Pengaturan global di **Aksi › Tekanan & penghalusan** (Jumlah stabilisasi, Penyaring gerak, Ekspresi) berlaku
    untuk semua kuas, ditambahkan pada pengaturan masing-masing kuas.
  - StreamLine kuas Procreate (.brush/.brushset) ikut terbaca saat diimpor.

## Tambahan 0.1.104: Goresan tanpa jeda

- **Tinta selalu muncul tepat di ujung pena.** Penghalus garis (StreamLine, Stabilisasi, Penyaring gerak) tetap
  menghaluskan goresan akhir, tetapi selama menggambar ada "ekor" sementara yang menyambung dari ujung jalur halus,
  menyusuri gerak pena, sampai tepat di bawah ujung pena. Garis tidak lagi tertinggal beberapa senti di belakang pena.
- **Pena diam tetapi masih menempel**: goresan yang dihaluskan langsung menyusul ke ujung pena dengan sendirinya
  (sebelumnya menunggu pena bergerak lagi atau diangkat).
- Prediksi goresan (Aksi › Prediksi goresan) kini juga bekerja untuk kuas yang memakai StreamLine/Stabilisasi.

## Tambahan 0.1.105: Ujung goresan mulus

- Ekor sementara di ujung pena kini memakai penghalus yang sama dengan goresannya: goresan seolah-olah menyusul ke
  ujung pena saat itu juga. Hasilnya selalu mulus, tersambung tanpa patahan, tanpa getar/kait/zig-zag di dekat ujung
  pena, dan tepat sama dengan garis yang tergambar bila pena diangkat saat itu.
- Kuas dengan getaran acak (scatter/jitter) tidak lagi "berkedip" di ujung pena.

## Tambahan 0.1.106: Ujung akhir goresan rapi

- Saat pena diangkat, gerakan ketika ujung pena sedang terangkat (tekanannya anjlok) tidak lagi ikut digambar, dan
  ujung goresan memakai tekanan yang sebenarnya. Sebelumnya tekanan sebelumnya dipakai, sehingga ujung akhir sering
  punya "kaki" tebal yang membelok (seperti huruf L).
- Kait kecil mendadak di ujung (posisi pena bergeser ke arah kemiringan saat terangkat) dibuang.
- **Runcing di akhir** (Studio kuas › Goresan, di bawah Runcing di awal): ujung terakhir goresan menipis seperti Taper
  di Procreate. Bagian ujung ditahan sebentar dan diruncingkan saat pena diangkat; selama menggambar tetap tampil
  penuh. Tinta Studio dan Kaligrafi Basah memakainya bawaan.

## Tambahan 0.1.107: Studio Kuas seperti Procreate (bagian 1)

**Studio Kuas** kini bertab seperti Brush Studio Procreate: Jalur goresan · Stabilisasi · Runcing · Bentuk ·
Tekstur · Cat air · Dinamika · Dinamika warna · Pena · Properti.
- **Jalur goresan:** Getaran jarak, Getaran lateral (tegak lurus goresan), Getaran linear (searah goresan),
  Memudar (fall off: goresan mulai pekat lalu memudar).
- **Runcing:** Runcing di awal/akhir, Opasitas runcing, Ujung (lancip ↔ tumpul).
- **Bentuk:** Jumlah cap per titik (1–16) dan getarannya, Putaran acak tiap goresan, Balik X/Y, Kebulatan yang
  ikut tekanan/kemiringan, getaran kebulatan tegak/mendatar.
- **Dinamika:** Kecepatan → ukuran / opasitas (−100%…+100%) dan → jarak cap; getaran ukuran/opasitas.
- **Dinamika warna:** getaran per cap dan per goresan (rona, saturasi, lebih terang, lebih gelap, warna kedua),
  warna menurut tekanan dan kemiringan pena. **Warna kedua** diatur di panel Warna (kotak "Kedua": ketuk = tukar,
  tahan = pakai warna sekarang).
- **Pena:** **Kurva tekanan per kuas** (ketuk = tambah titik, seret, ketuk titik = hapus), **Bleed** tekanan dan
  kemiringan (tekanan ringan/pena miring hanya menampakkan bagian pekat bentuk/tekstur), **Sudut mulai kemiringan**.
- **Properti:** **Hadap layar** (orient to screen), ukuran/opasitas maks-min, aliran, kekuatan baur.

**Pustaka kuas:**
- **Cari kuas** di semua daftar (nama kuas atau nama daftar).
- Perpustakaan **Bawaan** dan **Kuasku & impor**; **+ Daftar baru**; tahan kuas → **Pindahkan / Salin ke daftar…**;
  tahan daftar → **Ekspor daftar (.wkuas)** ke Download/WInote, bisa diimpor lagi lewat Impor kuas.
- **Sinkron:** kuas (pengaturan, daftar, gambar ujung/tekstur impor) ikut **cadangan WInote** dan digabung saat
  dipulihkan di tablet lain.
- **28 kuas bawaan baru** di daftar Pena, Spidol, Pastel & Arang, Lettering, Komik, Desain, Grunge, Kreatif (plus
  Pensil Mekanik dan Pensil Arsir), memakai dinamika baru di atas.

## Tambahan 0.1.108: Studio Kuas seperti Procreate (bagian 2)

Tab baru di Studio Kuas: **Rendering · Wet Mix · Kuas ganda**.
- **Rendering:** mode **Glaze ringan / merata / intens / berat** dan **Blending merata / intens**, **Tepi basah**,
  **Tepi terbakar** (beserta mode campurnya), **Mode campur kuas** (26 mode seperti mode layer), **Campuran
  luminans**, **Ambang alfa**, Aliran.
- **Wet Mix (cat basah):** **Pengenceran, Muatan cat, Daya tempel (attack), Tarikan (pull), Gradasi tekstur, Kabur**,
  getaran kabur dan getaran kebasahan. Kuas Wet Mix langsung bercampur dengan cat di layer (cat terseret, kuas
  kehabisan cat sepanjang goresan); satu goresan tetap satu langkah urungkan.
- **Kuas ganda:** pilih kuas kedua dari pustaka; kuas kedua menggores di jalur yang sama dan menjadi topeng goresan
  (Irisan, Gabung, Kurangi, Selisih, Gelapkan, Terangkan) dengan ukuran sendiri. Pratinjau kuas ikut menampilkannya.
- **Kuas bawaan baru:** Gouache Basah, Minyak Basah, Akrilik Kering, Pembaur Basah, Cat Air Campur (Wet Mix); Tinta
  Terbakar, Tinta Tegas Bertekstur, Spidol Tepi Basah, Bayangan Multiply, Cahaya (rendering); Kapur Ganda, Kuas
  Bertekstur Ganda (kuas ganda). **Stabilo** kini rata dan menembus tulisan (multiply) seperti stabilo sungguhan.

## Tambahan 0.1.109: Cat air hidup (merembes)

- Kuas cat air kini **merembes**: selama masih basah, cat terus menyebar keluar mengikuti serat dan lekukan kertas,
  makin tipis di ujung rembesan, melambat saat mengering, lalu kering dengan tepi gelap.
- **Basah di atas basah:** goresan berikutnya (kuas, opasitas, dan layer yang sama) di bagian yang masih basah ikut
  bercampur, termasuk warnanya. Setelah kering, goresan baru menumpuk (glaze) seperti biasa.
- Studio Kuas → **Cat air → Merembes**: *Rembesan* (0 = mati) dan *Lama basah* (0,5–8 detik). Pratinjau kuas
  menampilkan hasil rembesan.
- Cat Air Utama, Cat Air Basah, Detail Cat Air, dan Lapisan Tipis memakainya sejak awal.
- Satu goresan tetap satu langkah urungkan. Urungkan, ganti kuas/opasitas/layer, atau alat lain membuat cat yang masih
  basah langsung kering apa adanya.

## Tambahan 0.1.110: Pangkas & ubah ukuran kanvas, info kanvas, jendela referensi, time-lapse

- **Aksi › Kanvas › Pangkas & ubah ukuran:** ubah ukuran kanvas setelah karya dibuat. Seret bingkai atau isi Lebar,
  Tinggi, dan Geser X/Y; pilihan cepat Persegi, Tengah, 50 %, 200 %, Asli. *Ubah skala isi* = gambar ikut
  membesar/mengecil; tanpa itu kanvas dipangkas atau diperluas. Riwayat urungkan dikosongkan setelah diterapkan.
- **Aksi › Kanvas › Info kanvas:** ukuran piksel dan cetak, DPI, jumlah layer dan batasnya, waktu menggambar, jumlah
  goresan, tanggal dibuat, ukuran berkas.
- **Jendela referensi:** jendela kecil mengambang berisi kanvas ini (untuk melihat keseluruhan saat memperbesar) atau
  gambar contekan dari Galeri. Geser lewat judulnya, ubah ukuran di sudut kanan-bawah, cubit untuk perbesar, ketuk dua
  kali untuk kembali, tahan untuk mengambil warna.
- **Time-lapse:** proses menggambar direkam otomatis (Aksi › Time-lapse, bisa dimatikan per karya). *Putar ulang*
  memutar videonya; *Ekspor lengkap* atau *Ekspor 30 detik* menyimpan MP4 ke Galeri › Movies/WInote.
  Rekaman bisa dihapus untuk mulai lagi.

## Tambahan 0.1.111: Animation Assist, Page Assist, profil warna, melukis model 3D, Face Paint

- **Animation Assist** (Aksi › Animasi & halaman): tiap layer (atau grup) jadi satu frame, berurutan dari bawah ke
  atas. Garis waktu di bawah kanvas: putar/jeda, *+ Frame*, ketuk frame untuk memilih, ketuk lagi untuk *Tahan*
  (frame tampil lebih lama), *Duplikat*, *Hapus*. Pengaturan: Ulang / Bolak-balik / Sekali, frame per detik,
  jumlah dan kepekatan onion skin (sebelumnya merah, sesudahnya hijau), campur frame utama, frame pertama jadi latar,
  frame terakhir jadi latar depan. Ekspor **GIF** (Pictures/WInote) atau **MP4** (Movies/WInote).
- **Page Assist:** tiap layer/grup jadi satu halaman (komik, buku); jajaran halaman di bawah kanvas, dan **Ekspor PDF
  semua halaman** ke Download/WInote.
- **Profil warna** (saat membuat kanvas, atau Info kanvas › Ganti): **sRGB**, **Display P3** (gamut luas; di layar
  yang mendukung kanvas tampil dalam P3, PNG/JPG/PSD membawa profil P3), dan **CMYK (cetak)**: kanvas tampil seperti
  hasil cetak offset kertas coated, panel Warna menunjukkan persentase tinta C M Y K dan memperingatkan warna di luar
  gamut cetak (ketuk *Pakai* untuk warna tercetak terdekat), ekspor **TIFF CMYK** untuk percetakan. CMYK ini
  perkiraan dari model cetak, bukan profil ICC hasil ukur percetakan tertentu.
- **Melukis model 3D** (Kanvas baru › Model 3D): buka **GLB**, atau **OBJ** + MTL + gambar tekstur sekaligus. Lukis
  langsung di model; geser dari luar model untuk memutar, dua jari untuk perbesar dan menggeser. *Tekstur 2D* membuka
  teksturnya dengan semua alat biasa. Model tanpa peta UV memakai proyeksi kotak otomatis. **Ekspor model 3D (GLB)**
  bertekstur ke Download/WInote. USDZ belum didukung (ubah dulu ke GLB/OBJ).
- **Face Paint** (Kanvas baru › Model 3D › Mulai Face Paint): melukis di wajah sendiri lewat kamera depan. Wajah
  dikenali di tablet dengan ML Kit (tanpa internet, gambar kamera tidak disimpan), cat menempel dan ikut bergerak.
  *Tekstur 2D* membuka tekstur wajahnya; layer *Peta wajah* (tersembunyi) jadi panduan letak mata, hidung, dan mulut.
  *Foto* menyimpan gambar kamera beserta catnya ke Pictures/WInote.

## Tambahan 0.1.114: Mode Pena penyesuaian, efek yang lebih kuat dan halus

- **Mode Pena** di semua penyesuaian (kecuali Liquify dan Klon): pilih *Pena* di bar penyesuaian, atur efeknya, lalu
  sapukan pena/jari hanya di bagian yang ingin diberi efek (seperti mode Pencil di Procreate). Selama mode Pena,
  penggeser samping mengatur ukuran dan kekuatan sapuan (terpisah dari ukuran kuas); tekanan pena ikut mengatur
  kekuatan. *Hapus efek* (atau ujung penghapus pena)
  menghapus efek dari bagian tertentu, *Hapus semua* mengosongkan sapuan. Urungkan/ulangi (tombol samping atau ketuk
  dua/tiga jari) bekerja per sapuan selama penyesuaian masih terbuka. Penggeser efek tetap bisa diubah setelah
  menyapu. *Seluruh layer* kembali ke efek penuh.
- **Blur Gaussian bertingkat:** radius sampai sepertiga sisi kanvas, dan berapa pun radiusnya hasilnya membaur
  seperti gradien halus (tidak lagi berbayang/bergaris atau masih kontras di nilai maksimum). Penggeser memakai skala
  kuadrat: separuh kiri untuk radius kecil yang teliti, ujung kanan untuk blur yang sangat kuat.
- **Efek lain lebih kuat:** Blur Gerak sampai sepertiga sisi kanvas dan tetap rapat tanpa garis bayangan; Blur
  Perspektif sampai 200%; Pertajam sampai 200% dengan penggeser *Radius*; Bloom lebih terang dengan ukuran sampai
  seperlima kanvas; Noise lebih kontras; Glitch, Halftone, dan Aberasi Kromatik dengan rentang lebih besar.
  Blur kini juga menghormati *Kunci alfa* layer.

## Tambahan 0.1.115: Liquify lebih cepat, mulus, dan natural

- **Liquify dihitung di GPU:** medan pergeseran disimpan di tekstur float dan tiap sapuan dihitung shader, jadi tetap
  ringan walau ukuran sapuan besar atau kanvas besar (kisinya juga lebih rapat: tiap 2 px sampai ±16 MP). Hasil
  dorongan berulang tetap tajam karena gambar asli hanya diambil ulang sekali. Perangkat tanpa framebuffer float
  otomatis memakai cara lama (CPU).
- **Mulus saat pena ditahan:** Putar, Cubit, Kembangkan, Kristal, Tepi, dan Pulihkan bekerja tiap frame (dulu tiap
  30 ms), dan kekuatannya disebar rata di sepanjang jalur pena — jejaknya tidak putus saat pena digerakkan cepat.
- **Momentum** (seperti Procreate): setelah pena diangkat, sapuan berlanjut sebentar dengan kecepatan terakhir yang
  makin melambat. 0% = berhenti saat pena diangkat.
- Putar, Cubit/Kembangkan, Kristal, dan Tepi lebih kuat (Tepi cocok untuk menipiskan garis: sapukan di sepanjang
  garisnya). Penggeser Ukuran memakai skala kuadrat, dan bar Liquify dibuat dua kolom supaya lebih sedikit menutupi kanvas.

## Tambahan 0.1.116: Mode Pena penyesuaian lebih responsif

- Langkah urungkan sapuan disalin langsung di GPU (dulu dibaca ke prosesor tiap selesai menyapu, sehingga ada jeda
  sesaat setelah pena diangkat, makin terasa untuk sapuan lebar).
- Tekanan pena ringan sudah memberi efek penuh; hanya sentuhan sangat ringan yang memberi efek sebagian. Tepi sapuan
  sedikit lebih tegas, jadi bagian yang kena efek sesuai lingkaran pena.
- Saat memilih *Pena* dan efeknya masih 0, kekuatan sedang dipasang otomatis supaya sapuan pertama langsung terlihat.

## Tambahan 0.1.117: Panel penyesuaian bisa disembunyikan

- Tombol **⌃** di kanan atas panel penyesuaian (semua penyesuaian, termasuk Liquify dan Klon) menyembunyikan panel
  menjadi satu baris kecil berisi nama penyesuaian, **Atur** (tampilkan panel lagi), **Batal**, dan **Terapkan**,
  supaya kanvas tidak tertutup.
- Saat memilih **Pena**, panel otomatis disembunyikan; tombol **Hapus efek** tetap ada di baris kecil itu. Ketuk
  **Atur** untuk mengubah kekuatan efek, lalu sembunyikan lagi.

## Tambahan 0.1.118: Impor berlapis, ekspor lengkap, salin-tempel

**Impor berlapis** (galeri › **Impor**)

- **PSD/PSB** (Photoshop, Krita, Clip Studio, Photopea, dll.): tiap layer jadi layer, lengkap dengan nama, opasitas
  (termasuk *fill*), mode campur, tampil/sembunyi, clipping mask, kunci alfa, grup (grup bersarang diratakan ke grup
  teratas; sifat grup dalam ikut diterapkan), masker layer (langsung diterapkan ke piksel), dan DPI. Mendukung 8/16 bit,
  RGB/Grayscale/CMYK. Layer penyesuaian Photoshop dilewati.
- **.procreate**: layer, grup, opasitas, mode campur, tampil/sembunyi, clipping, kunci alfa, masker, warna latar, nama
  karya, dan DPI.
- **PDF**: tiap halaman menjadi satu layer (300 DPI, sisi terpanjang maksimal 4096 px) dan Page Assist menyala bila
  lebih dari satu halaman.
- Foto/gambar biasa tetap bisa diimpor seperti sebelumnya.
- Bila jumlah layer melebihi batas memori perangkat, layer paling bawah digabung menjadi satu layer "Gabungan N layer".

**Ekspor** (panel Aksi)

- **Simpan TIFF** → `Download/WInote` (tanpa latar = TIFF RGBA transparan; dengan DPI dan profil P3 bila karya P3).
- **Ekspor tiap layer: PNG** → satu folder di `Pictures/WInote` berisi `01 Nama layer.png`, `02 …` (ukuran kanvas penuh).
- **Ekspor tiap layer: PDF** → satu PDF di `Download/WInote`, satu halaman per layer.
- **Ekspor animasi GIF / MP4** sekarang bisa tanpa Animation Assist: tiap layer/grup dipakai sebagai satu frame.

**Potong / salin / tempel**

- Geser **tiga jari ke bawah** di kanvas membuka menu **Salin & Tempel**: Potong, Salin, Salin semua (gabungan semua
  layer), Duplikat, Pindahkan (potong ke layer baru lalu langsung bisa digeser), dan Tempel.
- Yang disalin adalah isi pilihan bila ada, atau isi layer aktif. Hasilnya juga masuk papan klip Android, jadi bisa
  ditempel ke aplikasi lain, dan gambar yang disalin dari aplikasi lain bisa ditempel ke WInote (jadi layer baru yang
  langsung bisa diubah posisi/ukurannya).
- Menu yang sama ada di panel Aksi › **Salin & tempel**. Di HyperOS, geser tiga jari ke bawah dipakai sistem untuk
  tangkapan layar; matikan di Setelan › Setelan tambahan › Pintasan gerakan › Ambil tangkapan layar bila ingin memakai
  gerakan ini di WInote.

## Tambahan 0.1.119: Masker layer, referensi, gestur layer, harmoni warna, palet

**Layer**

- **Masker layer** (menu layer › Masker layer): masker abu-abu yang tidak merusak gambar. Hitam menyembunyikan, putih
  menampilkan lagi. Gambar mininya ada di sebelah gambar mini layer; ketuk untuk menggambar di masker (kuas, penghapus,
  ColorDrop, penyesuaian, transformasi), ketuk lagi untuk menunya: Balik masker, Matikan/Nyalakan, Isi putih, Pilih isi
  masker, Terapkan masker, Hapus masker. Masker ikut saat layer dipindah, diduplikat, digabung, ditransformasi, diekspor
  ke PSD (jadi masker layer Photoshop), dan saat PSD/.procreate bermasker diimpor.
- **Referensi** (menu layer › Referensi): garis di layer referensi jadi batas ColorDrop, SwatchDrop, dan pilihan
  otomatis di layer lain, jadi warna bisa diisi di layer terpisah dari garisnya.
- **Balik warna (invert)** dan **Pilih isi layer** (isi layer langsung jadi pilihan, alat Pilihan terbuka).
- **Gestur di panel layer**: geser kanan = pilih beberapa layer; geser kiri = tombol Kunci/Duplikat/Hapus; ketuk dua
  jari = Alpha Lock; cubit dua layer (atau lebih) = gabungkan semua layer di antaranya.

**Warna**

- Tab **Harmoni**: komplementer, komplementer terpisah, analog, triadik, tetradik. Titik besar = warna sekarang, titik
  kecil = pasangannya (ketuk untuk dipakai); "Simpan" menjadikannya palet.
- **Banyak palet**: buat, ganti nama, hapus, jadikan palet utama (tampil ringkas di bawah Cakram/Klasik/Harmoni),
  simpan sebagai .swatches (bisa dibuka Procreate).
- **Impor palet**: ASE (Adobe), ACO (Photoshop), .swatches (Procreate), GPL (GIMP), atau daftar kode hex.
- **Palet dari foto/kamera**: sampai 30 warna utama diambil otomatis.
- **SwatchDrop**: tahan kotak warna palet lalu seret ke kanvas untuk mengisi area dengan warna itu.

## Tambahan 0.1.120: Melengkapi layer, gestur, teks, dan ekspor

**Layer**

- **Ketuk dua jari di layer** = atur opasitas: geser jari ke kiri/kanan di mana saja di kanvas (ada juga penggeser
  di bilah atas), ketuk kanvas untuk selesai. Persis seperti Procreate.
- **Geser dua jari ke kanan di layer** = Alpha Lock (sebelumnya ketuk dua jari).
- Menu layer: **Satukan ke grup dengan layer bawah** (Combine Down) dan **Salin layer** (ke papan klip).

**Gestur**

- **Gosok tiga jari** ke kiri-kanan di kanvas = bersihkan layer aktif (bisa diurungkan; masker kembali putih).
- **QuickMenu**: tahan tombol pipet di bilah samping (atau ketuk empat jari bila dinyalakan di Preferensi). Enam
  tombol aksi cepat; tahan salah satu tombol untuk menggantinya dari 21 aksi (layer baru, duplikat, gabung, bersihkan,
  salin/tempel/potong, balik warna, pilih isi, Alpha Lock, masker, transformasi, pilihan, balik tampilan, pas layar,
  urungkan/ulangi, sembunyikan alat, isi layer, opasitas, referensi).
- Preferensi › **Gestur**: nyalakan/matikan gosok tiga jari, geser tiga jari (Salin & Tempel; bisa dimatikan bila
  bentrok dengan tangkapan layar HyperOS), dan ketuk empat jari = QuickMenu.

**Teks**

- **Impor font TTF/OTF/TTC**: panel teks › kategori **Font saya** › + Impor font. Tahan nama font untuk menghapusnya.

**Ekspor**

- **Ekspor animasi PNG (APNG)**: warna penuh dan tepi halus, transparan bila latar disembunyikan (melengkapi GIF dan MP4).

## Perbaikan 0.1.121: Gestur di tablet asli

- Batas "telapak tangan" sekarang diukur dalam milimeter fisik: sentuhan dianggap telapak bila lebih lebar dari
  18 mm (ketukan banyak jari: 30 mm). Sebelumnya 60 dp (±9 mm di Xiaomi Pad 7), sehingga ujung jari biasa sering
  diabaikan dan ketuk 2/3/4 jari, cubit, serta tahan jari untuk pipet tidak jalan.
- Cubit/geser/putar kanvas tetap jalan walau pena baru saja dipakai atau sedang melayang di dekat layar.
- Bila HyperOS merebut gestur banyak jari (tangkapan layar tiga jari), muncul petunjuk cara mematikannya.
- Laporan teknis mencatat lebar jari asli dan alasan gestur tidak dihitung, untuk perbaikan berikutnya.

## Perubahan 0.1.122: Gestur tiga jari dan QuickMenu dihapus

- Atas permintaan (tidak jalan di Xiaomi Pad 7 karena HyperOS memakai sentuhan tiga jari untuk tangkapan layar):
  **gosok tiga jari**, **geser tiga jari ke bawah** (menu Salin & Tempel), dan **QuickMenu** (tahan tombol pipet /
  ketuk empat jari) dihapus beserta bagian **Gestur** di Preferensi.
- **Bersihkan layer** kembali jadi tombol: ikon di bilah samping, di bawah Urungkan/Ulangi (bisa diurungkan; pada
  masker, masker kembali putih). Tetap ada juga di menu layer › Bersihkan.
- Ketuk empat jari kembali untuk menyembunyikan/menampilkan alat; tombol pipet kembali hanya untuk pipet.
- **Duplikat ke layer baru** dan **Pindahkan ke layer baru** (dulu hanya di menu tiga jari) sekarang ada di panel Aksi ›
  Salin & tempel, bersama Potong, Salin, Salin semua, dan Tempel.

## Perubahan 0.1.123: Jarak antar cap sampai 1000%

- **Studio Kuas › Jalur goresan › Jarak antar cap** sekarang 1%–1000% (dulu mentok 150%). Penggesernya berskala
  logaritmik: sepertiga pertama 1–10%, sepertiga tengah 10–100%, sepertiga akhir 100–1000%, jadi jarak rapat tetap
  bisa diatur halus. Nilai dibulatkan ke persen bulat.
- Kuas ABR yang jarak capnya di atas 150% (Photoshop membolehkan sampai 1000%) kini diimpor apa adanya.
- Kuas campur (smudge) dan cat basah tetap memakai jarak rapat agar warnanya mengalir.
