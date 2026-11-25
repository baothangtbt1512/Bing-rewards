$ErrorActionPreference = "Stop"

$baseDir = "d:\Projects\uprom\bing-rewards"
$appDir = "$baseDir\android_source\android-app"
$buildDir = "$appDir\build"
$sdkDir = "$baseDir\android_source\.sdk"

$signerJar = if (Test-Path "$baseDir\android_source\uber-apk-signer.jar") {
    "$baseDir\android_source\uber-apk-signer.jar"
} elseif (Test-Path "d:\Projects\uprom\tools\uber-apk-signer.jar") {
    "d:\Projects\uprom\tools\uber-apk-signer.jar"
} else {
    Get-ChildItem -Path "d:\Projects\uprom" -Filter "uber-apk-signer*.jar" -Recurse | Select-Object -First 1 -ExpandProperty FullName
}

$aapt = Get-ChildItem -Path $sdkDir -Filter "aapt.exe" -Recurse | Select-Object -First 1 -ExpandProperty FullName
$d8Jar = Get-ChildItem -Path $sdkDir -Filter "d8.jar" -Recurse | Select-Object -First 1 -ExpandProperty FullName
$androidJar = Get-ChildItem -Path $sdkDir -Filter "android.jar" -Recurse | Select-Object -First 1 -ExpandProperty FullName

Write-Host "Using aapt: $aapt"
Write-Host "Using d8.jar: $d8Jar"
Write-Host "Using android.jar: $androidJar"
Write-Host "Using signer: $signerJar"

if (Test-Path $buildDir) {
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Force -Path "$buildDir\gen" | Out-Null
New-Item -ItemType Directory -Force -Path "$buildDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$buildDir\dex" | Out-Null

Write-Host "1. Generating R.java and packaging resources..."
& $aapt package -f -m `
    -J "$buildDir\gen" `
    -M "$appDir\AndroidManifest.xml" `
    -S "$appDir\res" `
    -I "$androidJar" `
    -F "$buildDir\unsigned.apk"

Write-Host "2. Compiling Java sources..."
$javaFiles = Get-ChildItem -Path "$appDir\src", "$buildDir\gen" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& javac -encoding UTF-8 --release 8 -classpath "$androidJar" -d "$buildDir\classes" $javaFiles

Write-Host "3. Converting class files to DEX..."
& jar cf "$buildDir\classes.jar" -C "$buildDir\classes" .
& java -cp "$d8Jar" com.android.tools.r8.D8 --min-api 24 --output "$buildDir\dex" "$buildDir\classes.jar"

if (-not (Test-Path "$buildDir\dex\classes.dex")) {
    throw "classes.dex was not generated!"
}

Write-Host "4. Adding classes.dex into unsigned.apk..."
python -c "import zipfile; z = zipfile.ZipFile(r'$buildDir\unsigned.apk', 'a', zipfile.ZIP_DEFLATED); z.write(r'$buildDir\dex\classes.dex', 'classes.dex'); z.close()"

Write-Host "5. Signing APK with uber-apk-signer..."
& java -jar "$signerJar" --apks "$buildDir\unsigned.apk" --out "$baseDir"

$signedApk = "$baseDir\unsigned-aligned-debugSigned.apk"
$finalApk = "d:\Projects\uprom\BingRewardsAuto.apk"
$finalApk2 = "$baseDir\BingRewardsAuto.apk"

if (Test-Path $signedApk) {
    Copy-Item -Force $signedApk $finalApk
    Move-Item -Force $signedApk $finalApk2
    Write-Host "SUCCESS! Created APK:"
    Get-Item $finalApk, $finalApk2 | Select-Object FullName, Length, LastWriteTime
} else {
    throw "Signed APK not found!"
}
