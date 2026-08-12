# সবচেয়ে সহজে APK পাওয়ার উপায় (Android Studio ছাড়াই)

এই প্রজেক্টে একটা GitHub Actions workflow যোগ করা আছে
(`.github/workflows/build-apk.yml`) যেটা GitHub-এর নিজস্ব সার্ভারে
(যেখানে পূর্ণ ইন্টারনেট অ্যাক্সেস আছে) স্বয়ংক্রিয়ভাবে APK build করে দেয়।
আপনাকে কিছু ইনস্টল করতে হবে না — শুধু নিচের ধাপগুলো অনুসরণ করুন।

## ধাপ

1. **GitHub অ্যাকাউন্ট না থাকলে** — github.com-এ গিয়ে ফ্রি একটা অ্যাকাউন্ট বানান।

2. **নতুন repository বানান** — github.com/new এ গিয়ে একটা নাম দিন (যেমন `freenet-app`),
   Public/Private যেকোনোটা ঠিক আছে, "Create repository" চাপুন।

3. **এই zip-এর `native/android` ফোল্ডারের পুরো কন্টেন্ট আপলোড করুন** —
   নতুন repo পেজে "uploading an existing file" লিংকে ক্লিক করে
   `native/android` ফোল্ডারের ভেতরের সব ফাইল/ফোল্ডার (app/, .github/,
   build.gradle, settings.gradle, gradle.properties ইত্যাদি) টেনে এনে
   (drag & drop) আপলোড করুন, তারপর "Commit changes"।

   *(বিকল্প: git ব্যবহার জানলে সহজে —*
   ```
   cd native/android
   git init
   git remote add origin https://github.com/<আপনার-ইউজারনেম>/freenet-app.git
   git add .
   git commit -m "FreeNet Android"
   git push -u origin main
   ```
   *)*

4. **"Actions" ট্যাবে যান** — commit হওয়ার সাথে সাথে build স্বয়ংক্রিয়ভাবে শুরু
   হয়ে যাবে ("Build FreeNet debug APK" workflow)। ৩-৫ মিনিট সময় লাগতে পারে।

5. **Build শেষ হলে** — সেই workflow run-এর পেজে নিচে "Artifacts" সেকশনে
   `freenet-debug-apk` নামে একটা zip পাবেন — সেটা ডাউনলোড করুন। ভেতরে
   `app-debug.apk` থাকবে।

6. **ফোনে ইনস্টল করুন** — APK ফাইলটা ফোনে নিয়ে ওপেন করুন। প্রথমবার
   "Install unknown apps" পারমিশন চাইবে, allow করে দিন।

## যদি build fail করে

Actions ট্যাবের run-এর ভেতরে লগ দেখা যাবে ঠিক কোথায় সমস্যা হয়েছে। সেই
error message কপি করে আমাকে দিলে আমি কোড ঠিক করে দিতে পারবো।
