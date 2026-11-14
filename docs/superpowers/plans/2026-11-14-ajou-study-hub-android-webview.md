# ajou-study-hub Android WebView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ajou-study-hub` 운영 웹앱(`https://ajou-study-hub.johnshin617.workers.dev/`)을 전체 화면으로 감싸고 탐색·다운로드·오류 복구를 안전하게 처리하는 독립 Kotlin Android 앱(`com.jjundev.ajoustudyhub`)과 검증된 디버그 APK를 구축한다.

**Architecture:** 루트 단일 Android Gradle 프로젝트(`:app`)에서 순수 Kotlin + Android Views(`WebView` + ViewBinding)로 구성한다. URL/새 창 판정(`NavigationPolicy`)과 PDF/파일 전달 검증(`DownloadPolicy`, `DownloadTransfer`)을 Activity와 분리해 JVM 단위 테스트로 먼저 고정하고, `MainActivity`는 몰입형 전체 화면·인셋·뒤로 가기·메인 프레임 오류 복구만 조율한다.

**Tech Stack:** Kotlin, Android Views (`WebView`, ViewBinding), AndroidX (`core-ktx`, `appcompat`, `activity-ktx`, `browser`, `webkit`), Material Components, Gradle 8.13 Wrapper, JUnit 4, JDK `HttpServer`, AndroidX Test / Espresso.

**Spec:** 대화 내 `/grill-yourself` 설계 확정 사항 (`#1`: `https://ajou-study-hub.johnshin617.workers.dev/` 단독 허용 [기본값 확정], `#2`: `com.jjundev.ajoustudyhub` / `"아주 학습 자료실"` [확정], `#3`: 몰입형 전체 화면 [기본값 확정]).

## Global Constraints

- `applicationId`와 `namespace`는 `com.jjundev.ajoustudyhub`, 앱 표시 이름은 `"아주 학습 자료실"`로 고정한다.
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, `JavaVersion.VERSION_17`을 사용한다.
- 운영 URL은 `BuildConfig.STUDY_HUB_URL`(기본값 `"https://ajou-study-hub.johnshin617.workers.dev/"`)로 주입하며 `-PstudyHubUrl=...`로 재정의할 수 있다.
- 정적 학습 자료를 APK에 번들링하거나 오프라인 동기화·기존 브라우저 데이터 이전·푸시 알림·Play 릴리스 서명을 구현하지 않는다.
- `usesCleartextTraffic="false"`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`, `allowFileAccess = false`, `allowContentAccess = false`, `javaScriptCanOpenWindowsAutomatically = false`를 유지한다.
- `javaScriptEnabled = true`, `domStorageEnabled = true`, `databaseEnabled = true`, `setSupportMultipleWindows(true)`, `cacheMode = LOAD_DEFAULT`, `CookieManager.setAcceptCookie(true)`를 활성화해 동일 출처 `<iframe>`과 `localStorage`/`sessionStorage`를 지원한다.
- 몰입형 전체 화면(`WindowCompat.setDecorFitsSystemWindows(window, false)`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`)과 컷아웃/`IME` 인셋 패딩을 적용한다.
- 소스·로그·테스트 결과에 인증 쿠키나 민감 쿼리 문자열을 기록하지 않는다.

## Review Focus

1. **부분 문자열/userinfo 호스트 우회**: `https://ajou-study-hub.johnshin617.workers.dev.evil.com/` 또는 `https://ajou-study-hub.johnshin617.workers.dev@evil.com/` URL이 허용 호스트로 오인되는 문제 → Task 2 `NavigationPolicyTest`에서 핀 고정.
2. **비사용자 제스처 팝업 및 위험 스킴**: `<iframe>` 내 스크립트가 `isUserGesture = false`로 `window.open`을 호출하거나 `javascript:`/`file:`/`content:`/`intent:` 스킴을 열려는 시도 → Task 2 `NavigationPolicyTest` 및 `MainActivity` `onCreateWindow`에서 차단.
3. **다운로드 리다이렉트 쿠키 유출 및 HTML 위장 PDF**: PDF 다운로드 요청이 외부 호스트로 `302` 리다이렉트되거나 `403`/`200 text/html` 응답이 `.pdf`로 저장되는 문제 → Task 3 `DownloadTransferTest`에서 핀 고정.
4. **하위 리소스 오류에 의한 전체 화면 차단**: 본문 내 외부 폰트(`400`)나 이미지/`iframe` 하위 요청 실패 시 전체 오류 화면이 정상 학습 자료를 덮는 문제 → Task 4 `MainActivityErrorAndBackTest`에서 `isForMainFrame == true`만 오류 UI를 띄우도록 핀 고정.
5. **회전·키보드 가림 및 웹 뒤로가기 유실**: 화면 회전 시 `WebView` 재생성으로 `iframe` 상태가 소실되거나 소프트 키보드가 검색창을 가리는 문제, 또는 `canGoBack()`이 참인데 앱이 종료되는 문제 → Task 1 `configChanges`, Task 4 인셋/뒤로 가기 테스트, Task 5 실기기 검증으로 핀 고정.

---

## 파일 구조

- `.gitignore` — Gradle 캐시, 빌드 산출물, `local.properties`, IDE 설정 제외.
- `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` — Gradle 8.13 Wrapper.
- `gradle/libs.versions.toml` — AGP, Kotlin, AndroidX, Material, 테스트 라이브러리 버전 카탈로그.
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` — 루트 Gradle 구성 및 기본 `studyHubUrl`.
- `app/build.gradle.kts` — `:app` 모듈 빌드, `BuildConfig.STUDY_HUB_URL`, ViewBinding, 테스트 옵션.
- `app/src/main/AndroidManifest.xml` — 권한, HTTPS 전용, `MainActivity` `configChanges`, `FileProvider`.
- `app/src/main/res/xml/file_paths.xml` — `cache-path` (`downloads/`) 공유 경로.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml` — 앱 이름/문구 및 `Theme.Material3.DayNight.NoActionBar` 기반 테마.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`, `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` — 적응형 런처 아이콘.
- `app/src/main/res/layout/activity_main.xml` — `rootContainer`, `webView`, `progressBar`, `errorContainer`(`errorMessage`, `retryButton`).
- `app/src/main/java/com/jjundev/ajoustudyhub/NavigationPolicy.kt` — 최상위/새 창 URL 판정 순수 로직.
- `app/src/main/java/com/jjundev/ajoustudyhub/DownloadTransfer.kt` — 신뢰 호스트·리다이렉트·MIME 검증(`DownloadPolicy`) 및 캐시 저장·`FileProvider` 전달(`DownloadTransfer`).
- `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt` — WebView 설정, 전체 화면/인셋, 탐색·다운로드 연동, 오류 복구, 뒤로 가기.
- `app/src/test/java/com/jjundev/ajoustudyhub/BuildConfigTest.kt` — 기본 `STUDY_HUB_URL` 스모크 검증.
- `app/src/test/java/com/jjundev/ajoustudyhub/NavigationPolicyTest.kt` — `NavigationPolicy` 단위 테스트.
- `app/src/test/java/com/jjundev/ajoustudyhub/DownloadTransferTest.kt` — `DownloadPolicy` 및 `DownloadTransfer` 단위 테스트.
- `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt` — 메인 프레임/하위 리소스 오류 및 뒤로 가기 Instrumentation 테스트.
- `README.md` — 빌드, 테스트, 기기 설치, URL 주입 및 검증 기록.

---

### Task 1: Gradle 프로젝트 골격, 최소 `MainActivity` 및 디버그 APK 빌드

**Files:**
- Create: `.gitignore`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`, `app/src/main/res/drawable/ic_launcher_background.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt`
- Test: `app/src/test/java/com/jjundev/ajoustudyhub/BuildConfigTest.kt`

**Interfaces:**
- Produces: `BuildConfig.STUDY_HUB_URL: String`, `MainActivity` (`com.jjundev.ajoustudyhub.MainActivity`), `activity_main.xml` 뷰 ID (`rootContainer`, `webView`, `progressBar`, `errorContainer`, `errorMessage`, `retryButton`).

- [ ] **Step 1: Git 저장소 초기화 및 Gradle 8.13 Wrapper / 빌드 메타데이터 준비**

  저장소에 `.gitignore`, `gradle.properties`(기본 `studyHubUrl=https://ajou-study-hub.johnshin617.workers.dev/`), `gradle/libs.versions.toml`(AGP `8.7.3`, Kotlin `2.0.21`, `coreKtx = "1.15.0"`, `appcompat = "1.7.0"`, `activityKtx = "1.9.3"`, `browser = "1.8.0"`, `webkit = "1.12.1"`, `material = "1.12.0"`, `junit = "4.13.2"`, `androidxJunit = "1.2.1"`, `espressoCore = "3.6.1"`), `settings.gradle.kts`, `build.gradle.kts`를 작성하고 로컬에 캐시된 Gradle 8.13으로 Wrapper를 생성한다.
  Run (PowerShell):
  ```powershell
  git init
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat" wrapper --gradle-version 8.13
  ```
  Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`가 생성되고 `.\gradlew.bat --version`이 `Gradle 8.13`을 출력한다.

- [ ] **Step 2: 스모크 테스트 작성 및 실패 확인**

  `app/src/test/java/com/jjundev/ajoustudyhub/BuildConfigTest.kt` 작성:
  ```kotlin
  package com.jjundev.ajoustudyhub

  import org.junit.Assert.assertEquals
  import org.junit.Test

  class BuildConfigTest {
      @Test
      fun defaultStudyHubUrlUsesPublicWorkersHost() {
          assertEquals("https://ajou-study-hub.johnshin617.workers.dev/", BuildConfig.STUDY_HUB_URL)
      }
  }
  ```
  Run: `.\gradlew.bat :app:testDebugUnitTest`
  Expected: FAIL (`app/build.gradle.kts` 및 소스 미생성으로 프로젝트/클래스 미존재).

- [ ] **Step 3: `:app` 빌드 설정, 매니페스트, 리소스 및 최소 `MainActivity.kt` 구현**

  - `app/build.gradle.kts`: `namespace = "com.jjundev.ajoustudyhub"`, `applicationId = "com.jjundev.ajoustudyhub"`, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, `buildFeatures { buildConfig = true; viewBinding = true }`, `studyHubUrl` 프로퍼티를 이스케이프해 `buildConfigField("String", "STUDY_HUB_URL", ...)` 주입.
  - `AndroidManifest.xml`: `INTERNET`, `ACCESS_NETWORK_STATE`, `android:usesCleartextTraffic="false"`, 적응형 아이콘 연결, `MainActivity`에 `android:exported="true"` 및 `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|uiMode"` 선언.
  - `activity_main.xml`: `FrameLayout`(`@+id/rootContainer`) 안에 `WebView`(`@+id/webView`), 상단 `ProgressBar`(`@+id/progressBar`, `style="?android:attr/progressBarStyleHorizontal"`, `visibility="gone"`), 중앙 오류 `LinearLayout`(`@+id/errorContainer`, `visibility="gone"`, `@+id/errorMessage`, `@+id/retryButton`) 배치.
  - `MainActivity.kt`: ViewBinding으로 `webView`를 바인딩하고 JS·DOM Storage·Database·`MIXED_CONTENT_NEVER_ALLOW`·`allowFileAccess = false`·`allowContentAccess = false`·`CookieManager.setAcceptCookie(true)`를 설정한 뒤 `savedInstanceState == null`일 때 `webView.loadUrl(BuildConfig.STUDY_HUB_URL)` 호출, `onSaveInstanceState`에서 `webView.saveState(outState)` 보존.

- [ ] **Step 4: 단위 테스트 및 디버그 APK 빌드 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  Expected: PASS (`BuildConfigTest` 통과 및 `app/build/outputs/apk/debug/app-debug.apk` 생성).

- [ ] **Step 5: 커밋**

  ```powershell
  git add .gitignore gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties app
  git commit -m "chore: bootstrap Android WebView app skeleton"
  ```

---

### Task 2: `NavigationPolicy` 구현 및 탐색·새 창 경계 연결

**Files:**
- Create: `app/src/main/java/com/jjundev/ajoustudyhub/NavigationPolicy.kt`
- Modify: `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt`
- Test: `app/src/test/java/com/jjundev/ajoustudyhub/NavigationPolicyTest.kt`

**Interfaces:**
- Consumes: `BuildConfig.STUDY_HUB_URL` (Task 1).
- Produces:
  - `enum class NavigationAction { LOAD_IN_WEBVIEW, OPEN_EXTERNAL, BLOCK }`
  - `class NavigationPolicy(allowedBaseUrl: String, additionalInAppHosts: Set<String> = emptySet())`
  - `fun classifyTopLevel(url: String?, isNewWindow: Boolean, isUserGesture: Boolean): NavigationAction`
  - `fun isTrustedInAppUrl(url: String?): Boolean`

- [ ] **Step 1: 실패하는 `NavigationPolicyTest` 작성 (Review Focus #1, #2 포함)**

  `app/src/test/java/com/jjundev/ajoustudyhub/NavigationPolicyTest.kt` 작성:
  - `sameOriginDocumentAndHashRouteLoadInWebView`: `https://ajou-study-hub.johnshin617.workers.dev/#/read/doc-1?anchor=sec2` → `LOAD_IN_WEBVIEW`
  - `sameOriginNewTabWithUserGestureOpensExternal`: `isNewWindow = true, isUserGesture = true` → `OPEN_EXTERNAL` (`새 탭에서 열기` 지원)
  - `externalHttpsWithUserGestureOpensExternal`: `https://developer.android.com/` (`isNewWindow = false, isUserGesture = true`) → `OPEN_EXTERNAL`
  - `newWindowWithoutUserGestureIsBlocked`: `https://ajou-study-hub.johnshin617.workers.dev/` (`isNewWindow = true, isUserGesture = false`) → `BLOCK` (Review Focus #2)
  - `substringAndUserInfoSpoofedHostsDoNotLoadInWebView`:
    - `https://ajou-study-hub.johnshin617.workers.dev.evil.com/` → `OPEN_EXTERNAL` (`isTrustedInAppUrl == false`) (Review Focus #1)
    - `https://ajou-study-hub.johnshin617.workers.dev@evil.com/` → `OPEN_EXTERNAL` (`isTrustedInAppUrl == false`) (Review Focus #1)
  - `unsafeSchemesAndCleartextHttpAreBlocked`: `http://ajou-study-hub.johnshin617.workers.dev/`, `javascript:alert(1)`, `file:///etc/passwd`, `content://...`, `intent://...` → `BLOCK` (Review Focus #2)
  - `mailtoAndTelRequireUserGesture`: `mailto:test@ajou.ac.kr` with `isUserGesture = true` → `OPEN_EXTERNAL`, `isUserGesture = false` → `BLOCK`

- [ ] **Step 2: 테스트 실패 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jjundev.ajoustudyhub.NavigationPolicyTest"`
  Expected: FAIL (`NavigationPolicy` 미정의).

- [ ] **Step 3: `NavigationPolicy.kt` 구현 및 `MainActivity.kt` 연결**

  - `NavigationPolicy.kt`: `java.net.URI`로 스킴·`userInfo`·`host`·`port`를 엄격히 파싱한다(`userInfo != null`이면 허용 호스트로 취급하지 않고 외부/차단 처리; 허용 호스트 비교는 `host.equals(allowedHost, ignoreCase = true)`).
  - `MainActivity.kt`:
    - `WebViewClient.shouldOverrideUrlLoading(view, request)`: `if (!request.isForMainFrame) return false`를 먼저 두고, 메인 프레임만 `NavigationPolicy.classifyTopLevel(request.url.toString(), isNewWindow = false, isUserGesture = request.hasGesture())`로 분기한다 (`LOAD_IN_WEBVIEW` → `false`, `OPEN_EXTERNAL` → Custom Tabs/브라우저 실행 + `true`, `BLOCK` → `true`).
    - `WebChromeClient.onCreateWindow(view, isDialog, isUserGesture, resultMsg)`: `!isUserGesture`면 `false` 반환. 사용자 제스처인 경우 임시 `WebView`를 `WebViewTransport`에 연결해 첫 `shouldOverrideUrlLoading`에서 대상 URL을 꺼내 `NavigationPolicy.classifyTopLevel(url, isNewWindow = true, isUserGesture = true)`로 외부 실행 후 임시 `WebView`를 즉시 `destroy()`한다.
    - 외부 실행 헬퍼 `openExternally(url: String)`: `https:`는 `CustomTabsIntent.Builder().build().launchUrl(...)`, 실패 또는 `mailto:`/`tel:`은 `Intent(Intent.ACTION_VIEW, Uri.parse(url))` 폴백. 처리 가능한 앱이 없으면(`ActivityNotFoundException`) `Snackbar`로 안내한다.

- [ ] **Step 4: 단위 테스트 및 빌드 통과 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  Expected: PASS (`NavigationPolicyTest` 전체 통과, `assembleDebug` 성공).

- [ ] **Step 5: 커밋**

  ```powershell
  git add app/src/main/java/com/jjundev/ajoustudyhub/NavigationPolicy.kt app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt app/src/test/java/com/jjundev/ajoustudyhub/NavigationPolicyTest.kt
  git commit -m "feat: add NavigationPolicy and safe external/new-window routing"
  ```

---

### Task 3: `DownloadTransfer` 구현 (신뢰 호스트 PDF/파일 다운로드 및 `FileProvider` 전달)

**Files:**
- Create: `app/src/main/java/com/jjundev/ajoustudyhub/DownloadTransfer.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt`
- Test: `app/src/test/java/com/jjundev/ajoustudyhub/DownloadTransferTest.kt`

**Interfaces:**
- Consumes: `NavigationPolicy.isTrustedInAppUrl` (Task 2).
- Produces:
  - `sealed interface DownloadResult { data class Ready(val file: File, val mimeType: String) : DownloadResult; data object AuthRequired : DownloadResult; data object UntrustedRedirect : DownloadResult; data class Unsupported(val reason: String) : DownloadResult; data class Failed(val reason: String) : DownloadResult }`
  - `object DownloadPolicy` (`isValidResponse(statusCode: Int, contentType: String?, fileName: String?): Boolean`, `sanitizeFileName(rawName: String?, fallback: String): String`)
  - `class DownloadTransfer(private val policy: NavigationPolicy, private val cacheRoot: File, private val cookieProvider: (String) -> String?)`
  - `fun fetchToCache(url: String, suggestedMimeType: String?, contentDisposition: String?): DownloadResult`

- [ ] **Step 1: 실패하는 `DownloadTransferTest` 작성 (Review Focus #3 포함)**

  `app/src/test/java/com/jjundev/ajoustudyhub/DownloadTransferTest.kt`에서 `com.sun.net.httpserver.HttpServer` 로컬 테스트 서버와 `@Rule TemporaryFolder`를 사용해 아래 케이스를 검증한다:
  - `trustedHttpsPdf200SavesCleanFileAndSendsCookie`: 허용 `127.0.0.1` 테스트 서버(테스트 전용 로컬 허용 호스트 주입)에서 `200`, `Content-Type: application/pdf`, `%PDF-1.4...` 응답 시 `DownloadResult.Ready` 반환 및 요청 헤더에 쿠키 포함 확인.
  - `redirectToUntrustedHostIsBlockedWithoutForwardingCookie`: 허용 호스트가 비허용 포트/호스트로 `302` 리다이렉트할 때 `DownloadResult.UntrustedRedirect` 반환 및 비허용 대상 서버가 `Cookie` 헤더를 전혀 받지 않음을 단언 (Review Focus #3).
  - `http401Or403ReturnsAuthRequired`: `401`/`403` 응답 → `DownloadResult.AuthRequired`.
  - `htmlLoginPageDisguisedAsPdfIsRejected`: `200`, `Content-Type: text/html; charset=utf-8` 응답이 `.pdf` URL에서 오면 `DownloadResult.Unsupported` 반환 및 파일 미생성 (Review Focus #3).
  - `pathTraversalFileNameIsSanitized`: `../../secret.pdf` `Content-Disposition`이 안전한 단일 파일명(`secret.pdf`)으로 정규화되어 `cacheRoot/downloads` 밖으로 벗어나지 않음을 확인.

- [ ] **Step 2: 테스트 실패 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.jjundev.ajoustudyhub.DownloadTransferTest"`
  Expected: FAIL (`DownloadTransfer` 미정의).

- [ ] **Step 3: `FileProvider` 설정 및 `DownloadTransfer.kt`, `MainActivity` 다운로드 리스너 구현**

  - `app/src/main/res/xml/file_paths.xml`: `<paths><cache-path name="downloads" path="downloads/" /></paths>`.
  - `AndroidManifest.xml`: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">...</provider>`.
  - `DownloadTransfer.kt`:
    - `HttpURLConnection`을 `instanceFollowRedirects = false`로 열어 리다이렉트(최대 5홉)마다 `policy.isTrustedInAppUrl(nextUrl)`와 URL 스킴(`https`, 테스트용 로컬 바인딩 시 생성자 옵션 허용)을 재검증한다. 호스트를 벗어나면 즉시 `UntrustedRedirect`를 반환한다.
    - 응답 코드 `401`/`403` → `AuthRequired`, `200` 이외 → `Failed`, `text/html` 응답 → `Unsupported`.
    - `cacheRoot/downloads/`에 타임스탬프/기존 파일 정리 후 저장하고 `Ready(file, resolvedMime)` 반환.
  - `MainActivity.kt`: `webView.setDownloadListener`에서 `policy.isTrustedInAppUrl(url)`이 거짓이면 외부 브라우저로 위임하고, 참이면 백그라운드 스레드(`Executors.newSingleThreadExecutor()`)에서 `DownloadTransfer.fetchToCache` 실행 후 UI 스레드에서 `FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", result.file)` + `Intent(Intent.ACTION_VIEW)` + `FLAG_GRANT_READ_URI_PERMISSION`으로 연다. `ActivityNotFoundException`이나 실패 결과는 `Snackbar`로 안내한다.

- [ ] **Step 4: 단위 테스트 및 빌드 통과 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  Expected: PASS (`DownloadTransferTest` 포함 전체 단위 테스트 통과).

- [ ] **Step 5: 커밋**

  ```powershell
  git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/main/java/com/jjundev/ajoustudyhub/DownloadTransfer.kt app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt app/src/test/java/com/jjundev/ajoustudyhub/DownloadTransferTest.kt
  git commit -m "feat: add verified DownloadTransfer and FileProvider handoff"
  ```

---

### Task 4: 몰입형 전체 화면/인셋, 뒤로 가기, 메인 프레임 오류 복구 및 Instrumentation 테스트

**Files:**
- Modify: `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Test: `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt`

**Interfaces:**
- Consumes: `NavigationPolicy` (Task 2), `DownloadTransfer` (Task 3), `activity_main.xml` IDs (`rootContainer`, `webView`, `progressBar`, `errorContainer`, `errorMessage`, `retryButton`).
- Produces: 테스트 가능한 `internal fun handleFrameLoadError(isForMainFrame: Boolean, description: String?)` 및 `MainActivity`의 완성된 오류 복구·뒤로 가기·몰입형 인셋 동작.

- [ ] **Step 1: 실패하는 Instrumentation 테스트 작성 (Review Focus #4, #5 포함)**

  `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt` 작성 (`ActivityScenario.launch(MainActivity::class.java)`):
  - `subresourceErrorDoesNotShowErrorOverlay`: 메인 프레임이 아닌 하위 리소스 오류(`handleFrameLoadError(isForMainFrame = false, description = "HTTP 400")`) 호출 시 `errorContainer`가 `GONE`으로 유지됨을 Espresso로 단언 (Review Focus #4).
  - `mainFrameErrorShowsRetryOverlayAndRetryHidesIt`: `handleFrameLoadError(isForMainFrame = true, description = "ERR_NAME_NOT_RESOLVED")` 호출 시 `errorContainer`와 `retryButton`이 `VISIBLE`이 되고, `retryButton` 클릭 시 `errorContainer`가 다시 `GONE`이 됨을 단언.
  - `backPressNavigatesWebHistoryBeforeFinishingActivity`: `webView`에 로컬 HTML(`data:` 또는 `loadDataWithBaseURL`)로 2페이지 이력을 만든 뒤 `onBackPressedDispatcher.onBackPressed()`를 호출하면 Activity가 종료되지 않고 `webView.canGoBack()` 이력이 1단계 줄어들며, 이력이 없을 때 다시 호출하면 Activity가 `isFinishing` 상태가 됨을 단언 (Review Focus #5).

- [ ] **Step 2: Instrumentation 테스트 실패 확인**

  Run: `.\gradlew.bat :app:connectedDebugAndroidTest`
  Expected: FAIL (`handleFrameLoadError` 및 오류/뒤로 가기 제어 미구현).

- [ ] **Step 3: `MainActivity.kt` 전체 화면·인셋·오류 복구·뒤로 가기 완성**

  - 몰입형 전체 화면: `WindowCompat.setDecorFitsSystemWindows(window, false)`, `WindowInsetsControllerCompat(window, binding.rootContainer)`에 `systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 및 `hide(WindowInsetsCompat.Type.systemBars())` 적용(`onWindowFocusChanged(hasFocus = true)`에서도 재적용).
  - 인셋 처리: `ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer)`에서 `WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()` 인셋을 읽어 `rootContainer` 패딩으로 반영해 컷아웃·키보드 입력 중 검색창이 가려지지 않게 한다(Review Focus #5).
  - 진행바 및 오류 복구: `WebChromeClient.onProgressChanged`로 `progressBar` 갱신(`0..99` 표시, `100` 숨김). `WebViewClient.onReceivedError` / `onReceivedHttpError`에서 `request.isForMainFrame == true`일 때만 `handleFrameLoadError(true, ...)` 호출해 `errorContainer`를 표시하고 `retryButton`으로 `webView.reload()`(또는 초기 로드 실패 시 `loadUrl(BuildConfig.STUDY_HUB_URL)`) 실행.
  - 뒤로 가기: `onBackPressedDispatcher.addCallback(this)` 등록 → `binding.webView.canGoBack()`이면 `binding.webView.goBack()`, 아니면 콜백 `isEnabled = false` 후 `onBackPressedDispatcher.onBackPressed()` 호출.
  - 생명주기 정리: `onResume`/`onPause`에서 `webView.onResume()`/`onPause()`, `onDestroy`에서 `webView.destroy()`.

- [ ] **Step 4: 단위 + Instrumentation + APK 빌드 통과 확인**

  Run: `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`
  Expected: PASS (JVM 단위 테스트, `TB138FC` 연결 기기 Instrumentation 테스트, 디버그 APK 빌드 모두 성공).

- [ ] **Step 5: 커밋**

  ```powershell
  git add app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values/themes.xml app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt
  git commit -m "feat: add immersive fullscreen, back navigation, and main-frame error recovery"
  ```

---

### Task 5: 실기기(`TB138FC`) 인수 검증 및 `README.md` 작성

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: `app/build/outputs/apk/debug/app-debug.apk` (Task 4 산출물).

- [ ] **Step 1: 연결된 Android 기기(`TB138FC`)에 디버그 APK 설치 및 실행**

  Run:
  ```powershell
  .\gradlew.bat :app:installDebug
  adb shell am start -n com.jjundev.ajoustudyhub/.MainActivity
  ```
  Expected: 앱이 전체 화면으로 실행되며 `https://ajou-study-hub.johnshin617.workers.dev/` 카탈로그가 로드된다.

- [ ] **Step 2: 실기기 시나리오 검증 체크리스트 확인**

  아래 항목을 기기에서 직접 확인하고 결과를 기록한다(수동 조작이 필요한 항목은 미검증/검증 여부를 구분해 남긴다):
  1. 카탈로그 과목 필터 및 검색창(`#catalog-search`) 입력 시 소프트 키보드가 입력란을 가리지 않음.
  2. 정리노트/복습문제 HTML (`iframe`) 열람, 문제 풀이 후 앱 재실행 시 `localStorage` 상태 유지.
  3. 화면 회전 시 학습 본문 유지 및 시스템 가장자리 스와이프로 상태바 일시 표시.
  4. Android 뒤로 가기로 `#/read/...` → `#/catalog` 이동 후 이력이 없을 때 앱 종료.
  5. `새 탭에서 열기` 및 외부 링크가 Custom Tabs/브라우저로 열림.
  6. PDF 자료(있을 경우) 다운로드 후 외부 PDF 뷰어로 열림.
  7. 기기 오프라인(비행기 모드) 전환 후 재시도 오버레이 및 복구 동작 확인.

- [ ] **Step 3: `README.md` 작성**

  프로젝트 개요, 기본 운영 URL, `-PstudyHubUrl=...` 오버라이드 방법, `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug` 실행 명령, Task 5 실기기 검증 결과와 미검증 항목을 기록한다.

- [ ] **Step 4: 최종 전체 검증 실행**

  Run: `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`
  Expected: PASS (모든 테스트 통과 및 최신 `app-debug.apk` 생성).

- [ ] **Step 5: 커밋**

  ```powershell
  git add README.md
  git commit -m "docs: add build, verification, and device acceptance README"
  ```
