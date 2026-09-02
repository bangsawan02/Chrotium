#!/bin/bash

# build.sh - Script otomatisasi Build Release APK dan Push ke GitHub
# Dibuat khusus untuk Chrotium Browser

# Setel warna untuk output log
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}          Chrotium Browser - Build & Push            ${NC}"
echo -e "${BLUE}=====================================================${NC}"

# 1. Konfigurasi Token dan Repository
TOKEN="${GITHUB_TOKEN:-$2}"
REPO_URL="$1"

if [ -z "$TOKEN" ]; then
    echo -e "${RED}[!] Error: GITHUB_TOKEN tidak ditemukan!${NC}"
    echo -e "${YELLOW}Silakan setel environment variable GITHUB_TOKEN atau masukkan token sebagai argumen kedua.${NC}"
    echo -e "${YELLOW}Penggunaan: GITHUB_TOKEN=your_token ./build.sh https://github.com/username/nama-repo.git${NC}"
    echo -e "${YELLOW}Atau: ./build.sh https://github.com/username/nama-repo.git your_token${NC}"
    exit 1
fi

# Inisialisasi Git jika belum ada
if [ ! -d ".git" ]; then
    echo -e "${YELLOW}[*] Menginisialisasi repositori Git...${NC}"
    git init
fi

# Set konfigurasi user jika belum disetel
git config user.name "AI Studio Agent"
git config user.email "agent@aistudio.google.com"

# Set branch ke 'main' sesuai instruksi
git branch -M main

# Cek remote URL
if [ -z "$REPO_URL" ]; then
    # Cari remote origin yang sudah ada
    REPO_URL=$(git remote get-url origin 2>/dev/null)
    if [ -z "$REPO_URL" ]; then
        echo -e "${RED}[!] Error: Silakan masukkan URL repositori GitHub Anda sebagai argumen!${NC}"
        echo -e "${YELLOW}Penggunaan: ./build.sh https://github.com/username/nama-repo.git${NC}"
        exit 1
    fi
fi

# Bersihkan dan sisipkan token ke URL untuk otentikasi otomatis
CLEAN_URL=$(echo "$REPO_URL" | sed -E 's|https://[^@]+@github.com/|https://github.com/|')
AUTH_URL=$(echo "$CLEAN_URL" | sed "s|https://github.com|https://x-access-token:${TOKEN}@github.com|")

# Setup remote origin
git remote remove origin 2>/dev/null
git remote add origin "$AUTH_URL"
echo -e "${GREEN}[✓] Remote origin berhasil disetel dengan token keamanan.${NC}"

# 2. Proses Build Release APK menggunakan Gradle
echo -e "${YELLOW}[*] Memulai proses kompilasi Release APK...${NC}"

# Menggunakan perintah 'gradle' (bukan gradlew) sesuai standar lingkungan ini
gradle :app:assembleRelease --no-daemon

if [ $? -ne 0 ]; then
    echo -e "${RED}[!] Gagal melakukan kompilasi APK. Silakan periksa log kesalahan di atas.${NC}"
    exit 1
fi

echo -e "${GREEN}[✓] Build Release APK berhasil diselesaikan!${NC}"

# 3. Menyalin APK ke Root Directory agar tidak terabaikan oleh .gitignore
APK_SRC="app/build/outputs/apk/release/app-release.apk"
APK_DEST="Chrotium.apk"

if [ -f "$APK_SRC" ]; then
    cp "$APK_SRC" "$APK_DEST"
    echo -e "${GREEN}[✓] File APK berhasil disalin ke root sebagai: $APK_DEST${NC}"
    echo -e "${BLUE}Ukuran APK: $(du -sh $APK_DEST | cut -f1)${NC}"
else
    echo -e "${RED}[!] File APK hasil build tidak ditemukan di: $APK_SRC${NC}"
    exit 1
fi

# 4. Commit dan Push ke GitHub
echo -e "${YELLOW}[*] Menyiapkan file untuk diunggah ke GitHub...${NC}"

# Tambahkan semua file (termasuk Chrotium.apk di root)
git add .

# Buat commit
git commit -m "Build: Release APK Chrotium dan pembaruan kode [skip ci]"

echo -e "${YELLOW}[*] Melakukan push ke branch main di GitHub...${NC}"
git push -u origin main --force

if [ $? -eq 0 ]; then
    echo -e "${GREEN}=====================================================${NC}"
    echo -e "${GREEN}        SUKSES: APK dan Kode Berhasil Di-push!       ${NC}"
    echo -e "${GREEN}=====================================================${NC}"
else
    echo -e "${RED}[!] Gagal melakukan push ke GitHub. Periksa koneksi internet atau token Anda.${NC}"
    exit 1
fi
