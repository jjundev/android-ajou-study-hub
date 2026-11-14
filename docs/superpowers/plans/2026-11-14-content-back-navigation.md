# 콘텐츠 HTML WebView 뒤로가기 내비게이션 개선 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/content/...` 학습 자료(정리노트·복습문제 등) 내부에서 토픽(`#hash`/`pushState`)을 여러 번 이동한 뒤에도 기기 뒤로가기 1회로 직전 화면(메인 카탈로그 또는 직전 문서)으로 복귀하도록 `MainActivity` 뒤로가기 처리를 개선한다.

**Architecture:** 기존 단일 `WebView` 구조를 유지하면서 순수 Kotlin 정책 클래스 `BackNavigationPolicy`를 분리해 `WebBackForwardList`에서 역순 탐색으로 뒤로가기 스텝(`Int?`)을 계산한다. 신뢰 호스트의 `/content/` 경로 문서에 대해서만 `#fragment`를 제외한 정규화 URL이 현재 문서와 다른 가장 가까운 이전 인덱스로 `goBackOrForward(steps)`를 호출하고, 비-`/content/` 경로(메인 카탈로그 등)는 기존 1스텝 `goBack()` 동작을 유지한다.

**Tech Stack:** Kotlin 2.0.21, Android Views (`WebView`, `OnBackPressedCallback`), JUnit 4, AndroidX Test / Espresso, Gradle 8.13 Wrapper (Windows PowerShell).

**Spec:** 대화 내 `/grill-yourself` 설계 확정 사항 (`#6=A` [기본값 확정: `/content/` 문서 내 `#hash` 히스토리 전체 건너뛰고 1회 뒤로가기로 직전 화면 복귀], `#7=A` [기본값 확정: 단일 `WebView` 유지]).

## Global Constraints

- `applicationId` 및 `namespace`는 `com.jjundev.ajoustudyhub`, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, `JavaVersion.VERSION_17`을 유지한다.
- 기존 `NavigationPolicy`와 동일하게 신뢰 호스트(`BuildConfig.STUDY_HUB_URL` 기본 `"https://ajou-study-hub.johnshin617.workers.dev/"`)와 HTTPS 정책(테스트 전용 플래그 `allowInsecureHttpForTests` 제외)을 따른다.
- 원격 웹 HTML/JS(`/content/*.html`)를 수정하거나 페이지에 JS를 주입하지 않는다.
- 기존 `MainActivityErrorAndBackTest.backPressNavigatesWebHistoryBeforeFinishingActivity` 동작(`/page1` → `/page2` 1스텝 뒤로가기 후 종료)을 깨뜨리지 않는다.
- 소스·로그·테스트에 인증 쿠키나 민감 쿼리 파라미터를 하드코딩/기록하지 않는다.

## Review Focus

1. **문서 첫 화면 재푸시(`go(0)` → 무해시 URL) 혼합 히스토리**: 사용자가 토픽 이동 후 `이전` 버튼으로 `0 / N`까지 돌아왔을 때 히스토리에 `[/, /content/note.html, .../note.html#s1, .../note.html]`처럼 해시 유무가 섞여 쌓여도 1회 뒤로가기로 `/`까지 건너뛰어야 한다 → Task 1 `BackNavigationPolicyTest`에서 핀 고정.
2. **콘텐츠 간 교차 이동(`1113` → `함께 보면 좋은 자료` → `1111`)**: A 문서에서 B 문서로 이동해 B의 토픽을 여러 번 넘긴 뒤 뒤로가기 시 메인으로 튕기지 않고 A 문서로, 한 번 더 누르면 메인으로 복귀해야 한다 → Task 1 `BackNavigationPolicyTest`에서 핀 고정.
3. **이전 문서가 없는 초기 히스토리(`/content/` 단독 진입)**: 인덱스 0부터 모두 같은 `/content/` 문서 키일 때 `goBackOrForward`에 갇히지 않고 `null`을 반환해 `Activity`를 종료해야 한다 → Task 1 `BackNavigationPolicyTest` 및 Task 2 `MainActivity` 통합에서 핀 고정.
4. **SPA 해시 라우트(`/#/catalog` ↔ `/#/read/...`) 및 쿼리 분기(`?v=1` vs `?v=2`) 오판**: `/content/`가 아닌 해시 라우트나 쿼리 문자열이 다른 URL은 같은 문서로 건너뛰지 않아야 한다 → Task 1 `BackNavigationPolicyTest`에서 핀 고정.
5. **퍼센트 인코딩 경로(`/content/circuits-2/%EB%B3%B5%EC%8A%B5...`)와 실제 `WebView.copyBackForwardList()` 통합**: 한글 인코딩 경로에서 `URI` 정규화가 이중 인코딩 없이 동작하고, 실제 `WebView` + `OnBackPressedDispatcher`에서 1회 뒤로가기로 메인 복귀 및 `!activity.isFinishing`이 성립해야 한다 → Task 1 `BackNavigationPolicyTest` 및 Task 2 `MainActivityErrorAndBackTest`에서 핀 고정.

---

## 파일 구조

- **Create:** `app/src/main/java/com/jjundev/ajoustudyhub/BackNavigationPolicy.kt` — `WebBackForwardList` URL 스냅샷과 현재 인덱스를 받아 `goBackOrForward` 스텝(`Int?`)을 계산하는 순수 Kotlin 정책 클래스.
- **Create:** `app/src/test/java/com/jjundev/ajoustudyhub/BackNavigationPolicyTest.kt` — 동일 문서 스킵, 교차 문서 복귀, 초기 단독 문서 종료, 비-`/content/` SPA 라우트 비스킵, 인코딩/쿼리/예외 URL을 검증하는 JVM 단위 테스트.
- **Modify:** `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt` — `backNavigationPolicy` 프로퍼티 추가 및 `setupBackNavigation()`에서 `copyBackForwardList()` + `goBackOrForward()` 연동.
- **Modify:** `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt` — `/content/` 문서 내 `history.pushState` 후 1회 뒤로가기 복귀를 검증하는 계측 테스트 추가.

---

### Task 1: `BackNavigationPolicy` 순수 Kotlin 정책 구현 및 JVM 단위 테스트

**Files:**
- Create: `app/src/main/java/com/jjundev/ajoustudyhub/BackNavigationPolicy.kt`
- Test: `app/src/test/java/com/jjundev/ajoustudyhub/BackNavigationPolicyTest.kt`

**Interfaces:**
- Consumes: `java.net.URI` (표준 JDK).
- Produces:
  ```kotlin
  class BackNavigationPolicy(
      allowedBaseUrl: String,
      additionalInAppHosts: Set<String> = emptySet(),
      internal val allowInsecureHttpForTests: Boolean = false
  ) {
      fun resolveBackSteps(currentIndex: Int, historyUrls: List<String?>): Int?
  }
  ```

- [ ] **Step 1: `BackNavigationPolicyTest.kt`에 실패하는 JVM 단위 테스트 작성**

  아래 시나리오를 검증하는 테스트 메서드를 `app/src/test/java/com/jjundev/ajoustudyhub/BackNavigationPolicyTest.kt`에 작성한다:
  - `skipsSameContentDocumentHashesAndTrailingPathnameRepush`:
    `historyUrls = listOf("https://ajou-study-hub.johnshin617.workers.dev/", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s2", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html")`, `currentIndex = 4` → `assertEquals(-4, policy.resolveBackSteps(4, historyUrls))`.
  - `crossDocumentContentNavigationReturnsToPreviousContentFirstThenCatalog`:
    `historyUrls = listOf("https://ajou-study-hub.johnshin617.workers.dev/", "https://ajou-study-hub.johnshin617.workers.dev/content/1113.html", "https://ajou-study-hub.johnshin617.workers.dev/content/1113.html#s2", "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html", "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html#t1", "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html#t2")`:
    - `currentIndex = 5` → `assertEquals(-3, policy.resolveBackSteps(5, historyUrls))` (`1113.html#s2`로 이동)
    - `currentIndex = 2` → `assertEquals(-2, policy.resolveBackSteps(2, historyUrls))` (메인 `/`로 이동)
  - `contentDocumentWithoutPriorDocumentReturnsNullToFinishActivity`:
    `historyUrls = listOf("https://ajou-study-hub.johnshin617.workers.dev/content/a.html", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s2")`, `currentIndex = 2` → `assertNull(policy.resolveBackSteps(2, historyUrls))`.
  - `nonContentRoutesAndDifferentQueriesStepBackOneByOne`:
    - `historyUrls = listOf("https://ajou-study-hub.johnshin617.workers.dev/#/catalog", "https://ajou-study-hub.johnshin617.workers.dev/#/read/doc-1")`, `currentIndex = 1` → `assertEquals(-1, ...)`
    - `historyUrls = listOf("https://ajou-study-hub.johnshin617.workers.dev/content/a.html?v=1", "https://ajou-study-hub.johnshin617.workers.dev/content/a.html?v=2#s1")`, `currentIndex = 1` → `assertEquals(-1, ...)`
  - `percentEncodedPathsAndMalformedOrOutOfBoundsInputsHandledSafely`:
    - 퍼센트 인코딩 경로(`/content/circuits-2/%EB%B3%B5%EC%8A%B5%EB%85%B8%ED%8A%B8/1113/%EC%A0%95%EB%A6%AC%EB%85%B8%ED%8A%B8.html` ↔ `...#s1`) → 동일 문서로 인식해 메인(`/`)으로 점프(`-2`).
    - `currentIndex = 0`, 음수/범위 초과 `currentIndex`, 빈 리스트, `null`/잘못된 URI 포함 히스토리에서 예외 없이 안전하게 `null` 또는 정상 스텝 반환.

- [ ] **Step 2: 테스트 실행하여 실패 확인**

  Run (PowerShell):
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.jjundev.ajoustudyhub.BackNavigationPolicyTest"
  ```
  Expected: FAIL (`Unresolved reference: BackNavigationPolicy`).

- [ ] **Step 3: `BackNavigationPolicy` 구현**

  `app/src/main/java/com/jjundev/ajoustudyhub/BackNavigationPolicy.kt`에 아래 규칙으로 구현한다:
  - `NavigationPolicy`와 동일하게 `baseHost`, `basePort`, `normalizedAdditionalHosts`, `effectivePort(scheme, port)`로 신뢰 호스트 여부를 검증한다(`userInfo != null`이면 비신뢰).
  - `resolveBackSteps(currentIndex: Int, historyUrls: List<String?>): Int?`:
    1. `currentIndex <= 0 || currentIndex >= historyUrls.size`이면 `null` 반환.
    2. `currentUrl = historyUrls[currentIndex]`; 신뢰 호스트의 `/content/` 경로(`uri.rawPath?.startsWith("/content/") == true`)가 아니면 `-1` 반환.
    3. `currentKey = normalizedDocumentKey(currentUrl)` (`"$scheme://$host:$port${uri.rawPath}${uri.rawQuery?.let { "?$it" } ?: ""}"`, `rawPath`/`rawQuery`를 그대로 사용해 퍼센트 인코딩 이중 인코딩 방지).
    4. `i`를 `currentIndex - 1 downTo 0`으로 순회하며 `!historyUrls[i].isNullOrBlank()`이고 `normalizedDocumentKey(historyUrls[i]) != currentKey`인 첫 `i`를 찾으면 `i - currentIndex`를 반환하고, 끝까지 못 찾으면 `null`을 반환한다.
    5. (`normalizedDocumentKey`는 유효한 URI가 아니거나 신뢰 호스트/스킴이 아니면 `null`을 반환하므로, 직전 엔트리가 신뢰 호스트의 다른 경로이거나 비-`null` 공백이 아닌 URL이면 `!= currentKey`가 참이 된다.)

- [ ] **Step 4: 전체 JVM 단위 테스트 실행하여 통과 확인**

  Run (PowerShell):
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```
  Expected: `BUILD SUCCESSFUL`, `BackNavigationPolicyTest` 포함 모든 단위 테스트 PASS.

- [ ] **Step 5: 커밋**

  ```powershell
  git add app/src/main/java/com/jjundev/ajoustudyhub/BackNavigationPolicy.kt app/src/test/java/com/jjundev/ajoustudyhub/BackNavigationPolicyTest.kt
  git commit -m "feat: add BackNavigationPolicy to skip same-document content hash history"
  ```

---

### Task 2: `MainActivity` 뒤로 가기 통합, 계측 테스트 및 실기기 검증

**Files:**
- Modify: `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt:36-38,104-117`
- Modify: `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt:180-186`

**Interfaces:**
- Consumes: `BackNavigationPolicy.resolveBackSteps(currentIndex: Int, historyUrls: List<String?>): Int?`

- [ ] **Step 1: `MainActivityErrorAndBackTest.kt`에 실패하는 계측 테스트 추가**

  `app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt`에 `backPressSkipsSameContentDocumentHashHistoryAndReturnsToMain()` 테스트를 추가한다:
  - `ActivityScenario.launch(MainActivity::class.java)` 후:
    1. `webView.stopLoading()`, `webView.clearHistory()` 후 메인 URL(`"https://ajou-study-hub.johnshin617.workers.dev/"`)을 `loadDataWithBaseURL`로 로드하고 `"Main Catalog"` 텍스트 로드 대기.
    2. 콘텐츠 URL(`"https://ajou-study-hub.johnshin617.workers.dev/content/note.html"`)을 `loadDataWithBaseURL`로 로드하고 `"Note Body"` 텍스트 로드 대기.
    3. `webView.evaluateJavascript("history.pushState(null, '', '#s1'); history.pushState(null, '', '#s2'); location.hash;")`로 `#s2`까지 해시 히스토리 2단계 누적 확인.
    4. `activity.onBackPressedDispatcher.onBackPressed()`를 **단 1회** 호출한다.
    5. Assert: `!activity.isFinishing`이고, 폴링 대기(최대 5000ms) 후 `webView.url == "https://ajou-study-hub.johnshin617.workers.dev/"`이며 `!webView.canGoBack()`임.

- [ ] **Step 2: 신규 계측 테스트만 실행해 실패 확인**

  Run (PowerShell):
  ```powershell
  .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.jjundev.ajoustudyhub.MainActivityErrorAndBackTest#backPressSkipsSameContentDocumentHashHistoryAndReturnsToMain"
  ```
  Expected: FAIL (`webView.url`이 메인이 아닌 `.../content/note.html#s1`에 머물러 assertion 실패).

- [ ] **Step 3: `MainActivity.kt`에 `BackNavigationPolicy` 통합**

  `app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt`를 수정한다:
  - `navigationPolicy`([MainActivity.kt:36](file:///c:/Users/Hyunjun/Desktop/Projects/andoid-ajou-study-hub/app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt#L36)) 바로 아래에 `internal var backNavigationPolicy: BackNavigationPolicy = BackNavigationPolicy(BuildConfig.STUDY_HUB_URL)` 추가.
  - `setupBackNavigation()`([MainActivity.kt:104-117](file:///c:/Users/Hyunjun/Desktop/Projects/andoid-ajou-study-hub/app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt#L104-L117))의 `handleOnBackPressed()`에서:
    - `val history = binding.webView.copyBackForwardList()`
    - `val historyUrls = (0 until history.size).map { idx -> history.getItemAtIndex(idx).url }`
    - `val steps = backNavigationPolicy.resolveBackSteps(history.currentIndex, historyUrls)`
    - `if (steps != null && binding.webView.canGoBackOrForward(steps)) { binding.webView.goBackOrForward(steps) }`
    - `else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); finish() }`

- [ ] **Step 4: 전체 단위 테스트, 계측 테스트 및 디버그 APK 빌드 실행**

  Run (PowerShell):
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`, 기존 및 신규 모든 단위·계측 테스트 PASS.

- [ ] **Step 5: 연결된 실기기(`HA1VLF8S`) 설치 및 End-to-End 동작 검증**

  Run (PowerShell):
  ```powershell
  .\gradlew.bat :app:installDebug
  adb -s HA1VLF8S shell input keyevent KEYCODE_WAKEUP
  adb -s HA1VLF8S shell wm dismiss-keyguard
  adb -s HA1VLF8S shell am start -W -n com.jjundev.ajoustudyhub/.MainActivity
  ```
  이후 실기기 UI에서 `정리노트 >` 탭 → `시작하기` → `다음` × 2 → `adb -s HA1VLF8S shell input keyevent KEYCODE_BACK` **1회** 실행 후 스크린샷(`screencap`) 및 `uiautomator dump`로 메인 화면(`아주 학습 자료실`) 즉시 복귀를 확인한다. (교차 링크 `1113 → 1111 → Back → 1113 → Back → 메인`도 가능 시 함께 확인.)

- [ ] **Step 6: 커밋**

  ```powershell
  git add app/src/main/java/com/jjundev/ajoustudyhub/MainActivity.kt app/src/androidTest/java/com/jjundev/ajoustudyhub/MainActivityErrorAndBackTest.kt
  git commit -m "fix: return directly from content documents on single back press"
  ```
