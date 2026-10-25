import os
import sys
import subprocess
import shutil
import zipfile
from pathlib import Path

def print_step(msg):
    print("\n" + "=" * 60)
    print(f">> {msg}")
    print("=" * 60)

def find_android_sdk():
    candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.path.expanduser(r"~\AppData\Local\Android\Sdk"),
        r"C:\Android\Sdk",
        r"D:\Android\Sdk",
    ]
    for c in candidates:
        if c and os.path.isdir(c):
            return Path(c)
    return None

def main():
    print_step("Kiem tra moi truong xay dung (Android SDK & Java)")

    sdk_dir = find_android_sdk()
    if not sdk_dir:
        print("[ERROR] Khong tim thay thu muc Android SDK!")
        sys.exit(1)
    print(f"Android SDK: {sdk_dir}")

    # Check platforms
    platforms_dir = sdk_dir / "platforms"
    platform_candidates = sorted(list(platforms_dir.glob("android-*")), reverse=True)
    if not platform_candidates:
        print("[ERROR] Khong tim thay android platform nao trong SDK!")
        sys.exit(1)
    android_jar = platform_candidates[0] / "android.jar"
    print(f"Android platform: {platform_candidates[0].name} ({android_jar})")

    # Check build-tools
    build_tools_dir = sdk_dir / "build-tools"
    bt_candidates = sorted(list(build_tools_dir.iterdir()), reverse=True)
    if not bt_candidates:
        print("[ERROR] Khong tim thay build-tools trong SDK!")
        sys.exit(1)
    bt_dir = bt_candidates[0]
    print(f"Build tools: {bt_dir.name}")

    aapt2 = bt_dir / ("aapt2.exe" if os.name == "nt" else "aapt2")
    d8 = bt_dir / ("d8.bat" if os.name == "nt" else "d8")
    zipalign = bt_dir / ("zipalign.exe" if os.name == "nt" else "zipalign")
    apksigner = bt_dir / ("apksigner.bat" if os.name == "nt" else "apksigner")

    workspace = Path(__file__).parent.resolve()
    app_dir = workspace / "app"
    src_dir = app_dir / "src" / "main"
    res_dir = src_dir / "res"
    manifest_file = src_dir / "AndroidManifest.xml"
    java_dir = src_dir / "java"

    build_dir = workspace / "build"
    if build_dir.exists():
        shutil.rmtree(build_dir)
    build_dir.mkdir(parents=True, exist_ok=True)

    gen_dir = build_dir / "gen"
    gen_dir.mkdir(parents=True, exist_ok=True)

    classes_dir = build_dir / "classes"
    classes_dir.mkdir(parents=True, exist_ok=True)

    compiled_res = build_dir / "compiled_res.zip"
    unaligned_apk = build_dir / "unaligned.apk"
    aligned_apk = build_dir / "aligned.apk"

    # Step 1: AAPT2 compile
    print_step("Buoc 1: Bien dich tai nguyen giao dien (aapt2 compile)")
    res_zip_cmd = [
        str(aapt2), "compile",
        "--dir", str(res_dir),
        "-o", str(compiled_res)
    ]
    subprocess.check_call(res_zip_cmd)

    # Step 2: AAPT2 link
    print_step("Buoc 2: Lien ket tai nguyen va tao R.java (aapt2 link)")
    import re
    manifest_text = manifest_file.read_text(encoding="utf-8")
    vc_match = re.search(r'android:versionCode="(\d+)"', manifest_text)
    vn_match = re.search(r'android:versionName="([^"]+)"', manifest_text)
    ver_code = vc_match.group(1) if vc_match else "1"
    ver_name = vn_match.group(1) if vn_match else "1.0"
    print(f"Phien ban: {ver_name} (versionCode: {ver_code})")

    # Lay ten ung dung tu strings.xml
    strings_file = res_dir / "values" / "strings.xml"
    app_name_clean = "Custom_TTS_Dictionary"
    if strings_file.exists():
        strings_text = strings_file.read_text(encoding="utf-8")
        an_match = re.search(r'<string name="app_name">([^<]+)</string>', strings_text)
        if an_match:
            app_name_clean = re.sub(r'[\s/\\:*?"<>|]+', '_', an_match.group(1).strip())

    ver_suffix = ver_name if ver_name.lower().startswith("v") else f"v{ver_name}"
    final_apk = workspace / f"{app_name_clean}_{ver_suffix}.apk"

    link_cmd = [
        str(aapt2), "link",
        "-I", str(android_jar),
        str(compiled_res),
        "--manifest", str(manifest_file),
        "-o", str(unaligned_apk),
        "--java", str(gen_dir),
        "--auto-add-overlay",
        "--min-sdk-version", "21",
        "--target-sdk-version", "34",
        "--version-code", ver_code,
        "--version-name", ver_name
    ]
    subprocess.check_call(link_cmd)

    # Step 3: Compile Java with javac
    print_step("Buoc 3: Bien dich ma nguon Java (javac)")
    java_files = []
    for root, _, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))
    for root, _, files in os.walk(java_dir):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    sources_txt = build_dir / "sources.txt"
    with open(sources_txt, "w", encoding="utf-8") as f:
        for jf in java_files:
            # Dùng forward slash / để javac trên Windows không bị hiểu nhầm là escape character
            normalized_path = jf.replace("\\", "/")
            f.write(f'"{normalized_path}"\n')

    javac_cmd = [
        "javac",
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-target", "1.8",
        "-cp", str(android_jar),
        "-d", str(classes_dir),
        f"@{sources_txt}"
    ]
    subprocess.check_call(javac_cmd)

    # Step 4: Convert to DEX with d8
    print_step("Buoc 4: Chuyen doi ma bytecode sang Android DEX (d8)")
    class_files = []
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    dex_dir = build_dir / "dex"
    dex_dir.mkdir(parents=True, exist_ok=True)

    d8_cmd = [
        str(d8),
        "--lib", str(android_jar),
        "--output", str(dex_dir),
    ] + class_files
    subprocess.check_call(d8_cmd)

    # Step 5: Add classes.dex into unaligned.apk
    print_step("Buoc 5: Dong goi classes.dex vao tep APK")
    dex_file = dex_dir / "classes.dex"
    with zipfile.ZipFile(unaligned_apk, 'a') as zf:
        zf.write(dex_file, "classes.dex")

    # Step 6: zipalign
    print_step("Buoc 6: Can le bo nho tep tin (zipalign)")
    zipalign_cmd = [
        str(zipalign), "-f", "-p", "4",
        str(unaligned_apk),
        str(aligned_apk)
    ]
    subprocess.check_call(zipalign_cmd)

    # Step 7: Create keystore if not exists and sign with apksigner
    print_step("Buoc 7: Ky so tep APK (apksigner)")
    keystore = workspace / "debug.keystore"
    if not keystore.exists():
        print("Tao khoa ky tu dong debug.keystore...")
        keytool_cmd = [
            "keytool", "-genkeypair",
            "-keystore", str(keystore),
            "-alias", "androiddebugkey",
            "-storepass", "android",
            "-keypass", "android",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=CustomTTS, OU=Dev, O=CustomTTS, L=Hanoi, S=Hanoi, C=VN"
        ]
        subprocess.check_call(keytool_cmd)

    sign_cmd = [
        str(apksigner), "sign",
        "--ks", str(keystore),
        "--ks-key-alias", "androiddebugkey",
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--out", str(final_apk),
        str(aligned_apk)
    ]
    subprocess.check_call(sign_cmd)

    print("\n" + "=" * 60)
    print("BIEN DICH THANH CONG!")
    print(f"File APK da san sang de cai dat tai:\n{final_apk}")
    print("=" * 60)

if __name__ == "__main__":
    main()
